package com.homie.finance.config;

import com.homie.finance.repository.BlacklistedTokenRepository;
import com.homie.finance.service.CustomUserDetailsService;
import com.homie.finance.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private BlacklistedTokenRepository blacklistRepository;
    @Autowired private CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        // 1. LẤY TOKEN (Ưu tiên Header, nếu không có thì lấy từ Query Param)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            // Mẹo cho SSE: Lấy token từ query parameter nếu Header trống
            token = request.getParameter("token");
        }

        if (token != null) {
            // Kiểm tra Blacklist
            if (blacklistRepository.existsByToken(token)) {
                // Đối với SSE, nếu lỗi 401 thì EventSource ở FE sẽ tự đóng kết nối
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token is blacklisted");
                return;
            }

            try {
                username = jwtUtil.extractUsername(token);
            } catch (Exception e) {
                logger.error("Could not extract username from token", e);
            }
        }

        // 2. XÁC THỰC (Giữ nguyên logic của homie)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(token)) {
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authToken.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}