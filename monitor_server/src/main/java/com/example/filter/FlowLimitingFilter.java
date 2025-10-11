package com.example.filter;

import com.example.entity.RestBean;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 限流控制过滤器
 * 防止用户高频请求接口，借助Redis进行限流
 */
@Slf4j
@Component
@Order(Const.ORDER_FLOW_LIMIT)
public class FlowLimitingFilter extends HttpFilter {

    @Resource
    StringRedisTemplate template;

    @Value("${spring.web.flow.limit}")
    int limit;     // 指定时间内最大请求次数
    @Value("${spring.web.flow.period}")
    int period;    // 计数窗口(秒)
    @Value("${spring.web.flow.block}")
    int block;     // 触发后封禁时长(秒)

    @Resource
    FlowUtils utils;

    /** 放行名单（不做限流）——客户端上报与注册接口 */
    private static final Set<String> WHITELIST = Set.of(
            "/api/monitor/register",
            "/api/monitor/detail",
            "/api/monitor/runtime",
            "/monitor/register",
            "/monitor/detail",
            "/monitor/runtime",
            "/api/monitor/list"
    );

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = normalizePath(request.getServletPath());
        if (WHITELIST.contains(path)) { // 客户端注册/上报放行
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        String pathGroup = groupPath(path); // 路径分组，避免不同接口互相影响

        // 构造本次计数的 key
        String counterKey = Const.FLOW_LIMIT_COUNTER + ip + ":" + pathGroup;
        String blockKey   = Const.FLOW_LIMIT_BLOCK   + ip + ":" + pathGroup;

        if (!tryCount(ip, pathGroup, counterKey, blockKey)) {
            writeBlocked(response, blockKey);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 计数+封禁判断（对同一个 IP+接口分组 加锁，避免并发竞争）
     */
    private boolean tryCount(String ip, String pathGroup, String counterKey, String blockKey) {
        String lockKey = (ip + "#" + pathGroup).intern();
        synchronized (lockKey) {
            Boolean blocked = template.hasKey(blockKey);
            if (Boolean.TRUE.equals(blocked)) return false;
            // 命中阈值由 FlowUtils 负责在 blockKey 上设置过期
            return utils.limitPeriodCheck(counterKey, blockKey, block, limit, period);
        }
    }

    /**
     * 429 响应 + Retry-After（秒）
     */
    private void writeBlocked(HttpServletResponse response, String blockKey) throws IOException {
        response.setStatus(429); // Too Many Requests
        response.setContentType("application/json;charset=utf-8");

        // 读取剩余封禁秒数作为 Retry-After
        long retryAfter = template.getExpire(blockKey, TimeUnit.SECONDS);
        if (retryAfter <= 0) retryAfter = block; // 兜底
        response.setHeader("Retry-After", String.valueOf(retryAfter));

        // 直接输出简单 JSON（不依赖自定义 RestBean，客户端主要看状态码和消息）
        try (PrintWriter w = response.getWriter()) {
            w.write("{\"code\":429,\"data\":null,\"message\":\"请求频率过快，请稍后再试\"}");
        }
    }

    /**
     * 代理场景优先取真实来源 IP
     */
    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 取第一个 IP
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }

    /**
     * 路径分组：/api/monitor/list -> /api/monitor/list
     * 你也可以按需要粗化为 /api/monitor/*（只需 return "/api/monitor/*";）
     */
    private String groupPath(String uri) {
        // 这里保持精确到接口级；若你希望「同一模块共用一个桶」可改成返回前两段
        return normalizePath(uri);
    }

    /**
     * 统一处理路径，消除上下文路径、重复斜杠以及末尾斜杠导致的匹配问题
     */
    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        String path = rawPath;
        int semicolonIndex = path.indexOf(';');
        if (semicolonIndex >= 0) {
            path = path.substring(0, semicolonIndex);
        }
        // 去除多余斜杠
        StringBuilder cleaned = new StringBuilder(path.length());
        boolean previousSlash = false;
        for (char c : path.toCharArray()) {
            if (c == '/') {
                if (!previousSlash) {
                    cleaned.append(c);
                    previousSlash = true;
                }
            } else {
                cleaned.append(c);
                previousSlash = false;
            }
        }
        String normalized = cleaned.toString();
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
