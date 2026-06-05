package com.teamflow.ai.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(Long userId, String username) {
        Instant expiresAt = Instant.now().plus(properties.getAccessTokenMinutes(), ChronoUnit.MINUTES);
        return createToken(userId, username, JwtTokenType.ACCESS, expiresAt);
    }

    public String createRefreshToken(Long userId, String username) {
        Instant expiresAt = Instant.now().plus(properties.getRefreshTokenDays(), ChronoUnit.DAYS);
        return createToken(userId, username, JwtTokenType.REFRESH, expiresAt);
    }

    public JwtClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtClaims(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                JwtTokenType.valueOf(claims.get(CLAIM_TOKEN_TYPE, String.class)),
                claims.getExpiration().toInstant()
        );
    }

    private String createToken(Long userId, String username, JwtTokenType tokenType, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TOKEN_TYPE, tokenType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
