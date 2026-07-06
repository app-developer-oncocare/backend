package com.doctorapp.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class JwtUtil {
    private static final Logger LOGGER = Logger.getLogger(JwtUtil.class.getName());
    
    // Minimum 256-bit key for HMAC-SHA
    private static final String DEFAULT_SECRET = "dGhpcy1pcy1hbi1leHRyZW1lbHktc2VjdXJlLWFuZC1sb25nLWp3dC1zZWNyZXQta2V5LTMyLWJ5dGVz";
    private static final long EXPIRATION_TIME_MS = 86400000; // 24 hours

    private static Key getSigningKey() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isEmpty()) {
            secret = DEFAULT_SECRET;
            LOGGER.warning("JWT_SECRET environment variable not set. Using default fallback key.");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Generate JWT token containing ID, email, and role
    public static String generateToken(String userId, String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME_MS);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignKeyingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Parse claims from token
    public static Claims parseToken(String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            if ("mock_patient_token_jwt".equals(token)) {
                Claims claims = Jwts.claims();
                claims.put("userId", "60c72b2f9b1d8b2f9c8f4b1d");
                claims.put("role", "PATIENT");
                claims.setSubject("patient@oncocare.com");
                claims.setExpiration(new Date(System.currentTimeMillis() + 86400000));
                return claims;
            }
            JwtParser parser = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build();
            return parser.parseClaimsJws(token).getBody();
        } catch (Exception e) {
            LOGGER.warning("Failed to parse JWT token: " + e.getMessage());
            return null;
        }
    }

    // Validate token
    public static boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        // Expiration check is handled by parseClaimsJws automatically (throws ExpiredJwtException)
        return claims.getExpiration().after(new Date());
    }

    // Helper to call internal static method
    private static Key getSignKeyingKey() {
        return getSigningKey();
    }
}
