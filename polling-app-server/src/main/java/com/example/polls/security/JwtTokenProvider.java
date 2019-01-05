package com.example.polls.security;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.Authentication;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

// @Component
@ConfigurationProperties(prefix = "app")
public class JwtTokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    // @Value("${app.jwtSecret}")
    private String jwtSecret;

    // @Value("${app.jwtExpirationInMs}")
    private int jwtExpirationInMs;

    public String generateToken(final Authentication authentication) {
        final UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        final Date now = new Date();
        final Date expiryDate = new Date(now.getTime() + this.jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(Long.toString(userPrincipal.getId()))
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, this.jwtSecret)
                .compact();
    }

    public Long getUserIdFromJWT(final String token) {
        final Claims claims = Jwts.parser()
                .setSigningKey(this.jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(final String authToken) {
        try {
            Jwts.parser()
                    .setSigningKey(this.jwtSecret)
                    .parseClaimsJws(authToken);
            return true;
        } catch (final SignatureException ex) {
            logger.error("Invalid JWT signature");
        } catch (final MalformedJwtException ex) {
            logger.error("Invalid JWT token");
        } catch (final ExpiredJwtException ex) {
            logger.error("Expired JWT token");
        } catch (final UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token");
        } catch (final IllegalArgumentException ex) {
            logger.error("JWT claims string is empty.");
        }
        return false;
    }
}
