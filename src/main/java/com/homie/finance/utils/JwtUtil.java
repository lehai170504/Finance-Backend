package com.homie.finance.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final String SECRET_KEY = "DayLaMotCaiKhoaBiMatCucKyDaiVaKhoDoanDeBaoMatToiThieu256Bits";
    private final long EXPIRATION_TIME = 86400000; // 24h
    private final long TEMP_EXPIRATION_TIME = 300000; // 5 phút (dành cho 2FA)

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    // 1. TẠO TOKEN CHÍNH THỨC (Dùng cho mọi API)
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. TẠO TOKEN TẠM THỜI (Chỉ dùng lúc chờ nhập mã 2FA)
    public String generateTempToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("isTemp", true)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + TEMP_EXPIRATION_TIME))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 3. LẤY USER_ID TỪ TOKEN TẠM
    public String getUserIdFromTempToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            // Kiểm tra xem có đúng là token tạm không
            if (claims != null && Boolean.TRUE.equals(claims.get("isTemp", Boolean.class))) {
                return claims.getSubject(); // Trả về userId
            }
            throw new RuntimeException("Token không hợp lệ để xác thực 2FA!");
        } catch (Exception e) {
            throw new RuntimeException("Token tạm không hợp lệ hoặc đã hết hạn (Quá 5 phút)!");
        }
    }

    public String extractUsername(String token) {
        try {
            return extractClaim(token, Claims::getSubject);
        } catch (Exception e) {
            return null; // Trả về null nếu token lỗi, filter sẽ xử lý tiếp
        }
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return (claims != null) ? claimsResolver.apply(claims) : null;
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            // Log lỗi ở đây để debug nếu cần: Log.error("JWT Error: " + e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (claims == null) return false;
            // Kiểm tra token này KHÔNG PHẢI là token tạm
            if (Boolean.TRUE.equals(claims.get("isTemp", Boolean.class))) return false;

            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}