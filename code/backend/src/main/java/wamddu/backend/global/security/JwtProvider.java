package wamddu.backend.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import wamddu.backend.user.domain.Role;

@Component
public class JwtProvider {

    private static final long REFRESH_TOKEN_EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000;

    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtProvider(
            @Value("${SECRET_KEY}") String secretKey,
            @Value("${jwt.access-expiration-ms:600000}") long expirationTime
    ) {
        if (expirationTime < 1000 || expirationTime > Integer.MAX_VALUE * 1000L) {
            throw new IllegalArgumentException("Access token lifetime must fit positive whole seconds");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime / 1000 * 1000;
    }

    public String generateJwtToken(Long id, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(id.toString())
                .claim("token_use", "access")
                .claim("role", role)
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long id) {
        return refreshToken(id.toString(), UUID.randomUUID().toString(),
                new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_MS));
    }

    public String rotateRefreshToken(Claims claims) {
        return refreshToken(claims.getSubject(), claims.get("family_id", String.class), claims.getExpiration());
    }

    private String refreshToken(String subject, String familyId, Date validity) {

        return Jwts.builder()
                .subject(subject)
                .claim("token_use", "refresh")
                .claim("family_id", familyId)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(validity)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            accessClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = accessClaims(token);

        Long userId = Long.parseLong(claims.getSubject());
        String role = claims.get("role", String.class);

        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));

        User principal = new User(String.valueOf(userId), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parseClaims(token, "refresh");
        if (claims.get("family_id", String.class) == null || claims.getId() == null) {
            throw new JwtException("Missing refresh token identifiers");
        }
        UUID.fromString(claims.get("family_id", String.class));
        UUID.fromString(claims.getId());
        return claims;
    }

    public int getAccessExpiresIn() {
        return Math.toIntExact(expirationTime / 1000);
    }

    private Claims accessClaims(String token) {
        Claims claims = parseClaims(token, "access");
        if (claims.get("role", String.class) == null) throw new JwtException("Missing access token role");
        Role.valueOf(claims.get("role", String.class));
        return claims;
    }

    private Claims parseClaims(String token, String purpose) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .require("token_use", purpose)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getExpiration() == null || claims.getSubject() == null
                || Long.parseLong(claims.getSubject()) <= 0) {
            throw new JwtException("Missing or invalid token claims");
        }
        return claims;
    }
}
