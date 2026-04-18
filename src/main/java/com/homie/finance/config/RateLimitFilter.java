package com.homie.finance.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter implements Filter {

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockouts = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS = 5;
    private static final long LOCKOUT_TIME = 15 * 60 * 1000; // 15 phut
    private static final long WINDOW_TIME = 60 * 1000; // 1 phut

    private long windowStart = System.currentTimeMillis();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/forgot-password")) {
            String ip = getClientIp(req);

            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_TIME) {
                requestCounts.clear();
                windowStart = now;
            }

            if (lockouts.containsKey(ip)) {
                if (now - lockouts.get(ip) < LOCKOUT_TIME) {
                    res.setStatus(429);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                            "{\"status\": 429, \"message\": \"Too Many Requests! Bạn đã bị khóa IP tạm thời do có dấu hiệu Spam. Thử lại sau 15 phút.\"}");
                    return;
                } else {
                    lockouts.remove(ip);
                }
            }

            requestCounts.putIfAbsent(ip, new AtomicInteger(0));
            int count = requestCounts.get(ip).incrementAndGet();

            if (count > MAX_REQUESTS) {
                lockouts.put(ip, now);
                res.setStatus(429);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(
                        "{\"status\": 429, \"message\": \"Too Many Requests! Bạn đã thao tác quá nhiều lần liên tục. IP của bạn đã bị khóa tạm thời.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
