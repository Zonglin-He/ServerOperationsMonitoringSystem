package org.example.utils;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.BaseDetail;
import org.example.entity.ConnectionConfig;
import org.example.entity.Response;
import org.example.entity.RuntimeDetail;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class NetUtils {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    // 单线程调度器：用于首次上报的启动抖动延迟
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "netutils-scheduler");
            t.setDaemon(true);
            return t;
        }
    });

    @Lazy
    @Resource
    ConnectionConfig config;

    private volatile boolean registered = false;
    private volatile BaseDetail lastBaseDetail;

    private static final String NEED_REGISTER_MSG = "Have not registered";
    private static final int HTTP_TOO_MANY = 429;

    // ===== 启动抖动，仅对第一次基础信息上报生效 =====
    private final long appStartMs = System.currentTimeMillis();
    private static final long STARTUP_JITTER_WINDOW_MS = 60_000; // 启动后 60s 内若是首次上报，则做一次抖动
    private final AtomicBoolean firstBaseScheduled = new AtomicBoolean(false);

    // ===== 429 冷却 & 注册节流 =====
    private volatile long cooldownUntilMs = 0;
    private volatile long lastRegisterAttemptMs = 0;
    private static final long REGISTER_MIN_INTERVAL_MS = 30_000;    // 两次注册最少间隔 30s
    private static final long DEFAULT_RETRY_AFTER_MS = 30_000;      // 未返回 Retry-After 时默认 30s

    // ===== 本地最小发送间隔（进一步减小 429 几率）=====
    private volatile long lastBaseSentMs = 0;
    private volatile long lastRuntimeSentMs = 0;
    private static final long BASE_MIN_INTERVAL_MS = 300_000;       // 基础信息 5 分钟一次
    private static final long RUNTIME_MIN_INTERVAL_MS = 30_000;     // 运行时 30 秒一次

    /* ===================== 公开方法 ===================== */

    public boolean registerToServer(String address, String token) {
        if (inCooldown("registerToServer")) return false;
        if (!allowRegisterNow()) {
            log.warn("Register throttled locally, please wait...");
            return false;
        }
        log.info("Registering to server, please wait...");
        Response response = this.doGet("/register", address, token);
        if (response.code() == HTTP_TOO_MANY) {
            log.warn("Register got 429, entering cooldown.");
            return false;
        }
        if (response.success()) {
            registered = true;
            log.info("Registered successfully");
        } else {
            log.error("Register failed : {}", safeMsg(response));
        }
        return response.success();
    }

    public void updateBaseDetails(BaseDetail detail){
        this.lastBaseDetail = detail;

        // —— 启动抖动：仅第一次基础信息上报时生效，延迟 20~40 秒，再真正发送 ——
        long sinceStart = System.currentTimeMillis() - appStartMs;
        if (sinceStart <= STARTUP_JITTER_WINDOW_MS && firstBaseScheduled.compareAndSet(false, true)) {
            long delaySec = 20 + ThreadLocalRandom.current().nextLong(21); // 20~40s 抖动
            log.info("Startup jitter: delaying first base-details report by {}s.", delaySec);
            scheduler.schedule(() -> {
                try { doUpdateBaseDetails(detail); } catch (Exception e) {
                    log.error("Delayed base-details send failed", e);
                }
            }, delaySec, TimeUnit.SECONDS);
            return; // 不立刻发送，避免撞上限流窗口
        }

        // 后续正常发送
        doUpdateBaseDetails(detail);
    }

    public void updateRuntimeDetails(RuntimeDetail detail) {
        if (inCooldown("runtime")) return;

        long now = System.currentTimeMillis();
        if (now - lastRuntimeSentMs < RUNTIME_MIN_INTERVAL_MS) {
            long left = (RUNTIME_MIN_INTERVAL_MS - (now - lastRuntimeSentMs) + 999)/1000;
            log.debug("Skip runtime due to local throttle ({}s left).", left);
            return;
        }

        if (!registered && lastBaseDetail != null) {
            log.warn("Not registered yet, trying to register before runtime update...");
            if (!tryRegisterNow()) {
                log.warn("Runtime update skipped: still not registered.");
                return;
            }
        }

        Response response = this.doPost("/runtime", detail);
        if (response.code() == HTTP_TOO_MANY) {
            log.warn("When updating runtime, got 429 and entered cooldown: {}", safeMsg(response));
            return;
        }

        if (response.success()) {
            lastRuntimeSentMs = now;
            return;
        }

        String msg = safeMsg(response);
        if (msg.contains(NEED_REGISTER_MSG)) {
            if (tryRegisterNow()) {
                if (inCooldown("runtime-retry")) return;
                Response retry = this.doPost("/runtime", detail);
                if (retry.success()) {
                    lastRuntimeSentMs = System.currentTimeMillis();
                }
            } else {
                log.warn("Runtime update skipped: still not registered.");
            }
        } else {
            log.warn("When updating the runtime status, server responded abnormally: {}", msg);
        }
    }

    /* ===================== 内部实现 ===================== */

    // 把原先 updateBaseDetails 的核心逻辑挪到这里，便于“首次延迟调度”调用
    private void doUpdateBaseDetails(BaseDetail detail){
        if (inCooldown("detail")) return;

        long now = System.currentTimeMillis();
        if (now - lastBaseSentMs < BASE_MIN_INTERVAL_MS) {
            long left = (BASE_MIN_INTERVAL_MS - (now - lastBaseSentMs) + 999)/1000;
            log.info("Skip base details due to local throttle ({}s left).", left);
            return;
        }

        Response response = this.doPost("/detail", detail);
        if (response.code() == HTTP_TOO_MANY) {
            log.warn("Update base details hit 429, cooling down: {}", safeMsg(response)); // 降级为 WARN
            return;
        }

        if (response.success()){
            registered = true;
            lastBaseSentMs = now;
            log.info("Update base details successfully");
            return;
        }

        String msg = safeMsg(response);
        if (msg.contains(NEED_REGISTER_MSG)) {
            if (tryRegisterNow()) {
                if (inCooldown("detail-retry")) return;
                Response retry = this.doPost("/detail", detail);
                if (retry.success()) {
                    lastBaseSentMs = System.currentTimeMillis();
                    log.info("Update base details successfully (after re-register)");
                } else {
                    log.error("Update base details failed after re-register: {}", safeMsg(retry));
                }
            } else {
                log.error("Update base details failed: cannot register now.");
            }
            return;
        }
        log.error("Update base details failed : {}", msg);
    }

    private boolean tryRegisterNow() {
        if (lastBaseDetail == null) return false;
        if (inCooldown("auto-register")) return false;
        if (!allowRegisterNow()) {
            log.warn("Auto register throttled locally.");
            return false;
        }
        Response r = this.doGet("/register");
        if (r.code() == HTTP_TOO_MANY) {
            log.warn("Auto register got 429, entering cooldown.");
            return false;
        }
        if (r.success()) {
            registered = true;
            log.info("Registered (auto) successfully.");
            return true;
        }
        log.warn("Auto register failed: {}", safeMsg(r));
        return false;
    }

    private String safeMsg(Response r) {
        return r == null ? "null response"
                : (r.message() == null || r.message().isBlank()
                ? "code=" + r.code()
                : r.message());
    }

    private boolean inCooldown(String scene) {
        long now = System.currentTimeMillis();
        if (now < cooldownUntilMs) {
            long left = (cooldownUntilMs - now + 999) / 1000;
            log.warn("Skip {} due to local cooldown ({}s left).", scene, left);
            return true;
        }
        return false;
    }

    private boolean allowRegisterNow() {
        long now = System.currentTimeMillis();
        if (now - lastRegisterAttemptMs < REGISTER_MIN_INTERVAL_MS) return false;
        lastRegisterAttemptMs = now;
        return true;
    }

    private void apply429Cooldown(HttpResponse<?> resp) {
        long delayMs = parseRetryAfter(resp.headers());
        long until = System.currentTimeMillis() + delayMs;
        cooldownUntilMs = Math.max(cooldownUntilMs, until);
    }

    private long parseRetryAfter(HttpHeaders headers) {
        try {
            var opt = headers.firstValue("Retry-After");
            if (opt.isEmpty()) return DEFAULT_RETRY_AFTER_MS;
            String v = opt.get().trim();
            if (v.chars().allMatch(Character::isDigit)) {
                return Math.max(1, Long.parseLong(v)) * 1000L;
            }
            var dt = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
            long ms = dt.toInstant().toEpochMilli() - System.currentTimeMillis();
            return ms > 0 ? ms : DEFAULT_RETRY_AFTER_MS;
        } catch (Exception e) {
            return DEFAULT_RETRY_AFTER_MS;
        }
    }

    private URI buildUri(String base, String path) throws URISyntaxException {
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return new URI(base + "/monitor" + path);
    }

    private Response doGet(String url, String address, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildUri(address, url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", token)
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == HTTP_TOO_MANY) apply429Cooldown(resp);
            return parseToResponse(resp);
        } catch (Exception e) {
            log.error("Problem when requesting to server", e);
            return Response.errorResponse(e);
        }
    }

    private Response doGet(String url) {
        return this.doGet(url, config.getAddress(), config.getToken());
    }

    private Response doPost(String url, Object data) {
        try{
            String rawData = JSONObject.toJSONString(data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildUri(config.getAddress(), url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", config.getToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(rawData))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == HTTP_TOO_MANY) apply429Cooldown(resp);
            return parseToResponse(resp);
        } catch (Exception e){
            log.error("Problem when requesting to server", e);
            return Response.errorResponse(e);
        }
    }

    private Response parseToResponse(HttpResponse<String> resp) {
        try {
            if (resp == null) return new Response(0, 500, null, "no http response");
            int sc = resp.statusCode();
            String body = resp.body();
            if (sc / 100 != 2) {
                String msg = "HTTP " + sc + (body == null ? "" : (" " + body));
                return new Response(0, sc, null, msg);
            }
            return JSONObject.parseObject(body, Response.class);
        } catch (Exception ex) {
            String raw = (resp == null ? "" : resp.body());
            return new Response(0, 500, null, "bad json: " + raw);
        }
    }
}