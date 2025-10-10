package org.example.utils;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.BaseDetail;
import org.example.entity.ConnectionConfig;
import org.example.entity.Response;
import org.example.entity.RuntimeDetail;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class NetUtils {
    private static final long DEFAULT_BLOCK_SECONDS = 30L;
    private static final long DEFAULT_JITTER_MIN_SECONDS = 5L;
    private static final long DEFAULT_JITTER_MAX_SECONDS = 35L;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final AtomicLong registerCooldownUntil = new AtomicLong(0);
    private final AtomicLong baseCooldownUntil = new AtomicLong(0);
    private final AtomicLong runtimeCooldownUntil = new AtomicLong(0);
    private final AtomicBoolean firstBaseDetailAttempted = new AtomicBoolean(false);

    private long blockMillis;
    private long jitterMinSeconds;
    private long jitterMaxSeconds;

    @Lazy
    @Resource
    ConnectionConfig config;

    @PostConstruct
    public void init() {
        long configuredBlock = Long.getLong("monitor.client.rateLimit.blockSeconds", DEFAULT_BLOCK_SECONDS);
        long configuredJitterMin = Long.getLong("monitor.client.startupJitter.minSeconds", DEFAULT_JITTER_MIN_SECONDS);
        long configuredJitterMax = Long.getLong("monitor.client.startupJitter.maxSeconds", DEFAULT_JITTER_MAX_SECONDS);

        if (configuredBlock <= 0) {
            configuredBlock = DEFAULT_BLOCK_SECONDS;
        }
        if (configuredJitterMin < 0) {
            configuredJitterMin = DEFAULT_JITTER_MIN_SECONDS;
        }
        if (configuredJitterMax < configuredJitterMin) {
            configuredJitterMax = configuredJitterMin;
        }

        this.blockMillis = Duration.ofSeconds(configuredBlock).toMillis();
        this.jitterMinSeconds = configuredJitterMin;
        this.jitterMaxSeconds = configuredJitterMax;
    }


    public boolean registerToServer(String address, String token) {
        if (this.inCooldown(registerCooldownUntil, "register", true)) {
            return false;
        }

        log.info("Registering to server, please wait...");
        Response response = this.doGet("/register", address, token);
        if (response.success()){
            log.info("Registered successfully");
            return true;
        }

        if (this.handleRateLimit(response, registerCooldownUntil, "register", true)) {
            return false;
        }

        log.error("Register failed : {}", response.message());
        return false;
    }

    public void updateBaseDetails(BaseDetail detail){
        if (detail == null) {
            log.warn("Skip base details update: detail payload is null.");
            return;
        }

        this.applyStartupJitter();

        if (this.inCooldown(baseCooldownUntil, "base details update", false)) {
            return;
        }

        Response response = this.doPost("/detail", detail);
        if (response.success()){
            log.info("Update base details successfully");
            return;
        }

        if (this.handleRateLimit(response, baseCooldownUntil, "base details update", false)) {
            return;
        }

        log.error("Update base details failed : {}", response.message());
    }

    public void updateRuntimeDetails(RuntimeDetail detail) {
        if (detail == null) {
            log.warn("Runtime detail is null, skip sending update.");
            return;
        }

        if (this.inCooldown(runtimeCooldownUntil, "runtime update", false)) {
            return;
        }

        Response response = this.doPost("/runtime", detail);
        if(response.success()) {
            return;
        }

        if (this.handleRateLimit(response, runtimeCooldownUntil, "runtime update", false)) {
            return;
        }

        log.warn("When updating the runtime status, an abnormal response content is received from the server: {}", response.message());
    }

    private Response doGet(String url, String address, String token) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(address + "/monitor" + url))
                    .header("Authorization", token)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return this.parseResponse(response);
        }catch (Exception e){
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
                   .POST(HttpRequest.BodyPublishers.ofString(rawData))
                   .uri(new URI(config.getAddress() + "/monitor" + url))
                   .header("Authorization", config.getToken())
                   .header("Content-Type", "application/json")
                   .build();
           HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
           return this.parseResponse(response);
        }catch (Exception e){
            log.error("Problem when requesting to server", e);
            return Response.errorResponse(e);
        }
    }

    private Response parseResponse(HttpResponse<String> response) {
        try {
            if (response.body() == null || response.body().isBlank()) {
                return new Response(0, response.statusCode(), null, "Empty response body");
            }
            return JSONObject.parseObject(response.body(), Response.class);
        } catch (Exception ex) {
            log.error("Failed to parse server response: status={}, body={}", response.statusCode(), response.body(), ex);
            return Response.errorResponse(ex);
        }
    }

    private void applyStartupJitter() {
        if (firstBaseDetailAttempted.compareAndSet(false, true)) {
            long jitterRange = jitterMaxSeconds - jitterMinSeconds;
            long jitter = jitterRange <= 0 ? jitterMinSeconds :
                    jitterMinSeconds + ThreadLocalRandom.current().nextLong(jitterRange + 1);
            if (jitter > 0) {
                log.info("Startup jitter: delaying first base-details report by {}s.", jitter);
                this.sleep(Duration.ofSeconds(jitter));
            }
        }
    }

    private boolean handleRateLimit(Response response, AtomicLong cooldownHolder, String action, boolean sleep) {
        if (response == null) {
            return false;
        }
        if (response.code() != 429) {
            return false;
        }
        long until = System.currentTimeMillis() + blockMillis;
        cooldownHolder.set(until);
        log.warn("{} hit 429, cooling down: HTTP 429 {}", capitalize(action), this.safeMessage(response));
        if (sleep) {
            this.waitForCooldown(until);
        }
        return true;
    }

    private boolean inCooldown(AtomicLong cooldownHolder, String action, boolean sleep) {
        long until = cooldownHolder.get();
        long now = System.currentTimeMillis();
        if (now >= until) {
            return false;
        }
        long remaining = until - now;
        long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining);
        if (remaining % 1000 != 0) {
            remainingSeconds += 1;
        }
        log.warn("Skip {} due to local cooldown ({}s left).", action, remainingSeconds);
        if (sleep) {
            this.waitForCooldown(until);
        }
        return true;
    }

    private void waitForCooldown(long untilMillis) {
        long waitMillis = untilMillis - System.currentTimeMillis();
        if (waitMillis <= 0) {
            return;
        }
        this.sleep(Duration.ofMillis(waitMillis));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String safeMessage(Response response) {
        if (response == null) {
            return "";
        }
        if (Objects.nonNull(response.message())) {
            return response.message();
        }
        return "";
    }

    private String capitalize(String action) {
        if (action == null || action.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(action.charAt(0)) + action.substring(1);
    }
}
