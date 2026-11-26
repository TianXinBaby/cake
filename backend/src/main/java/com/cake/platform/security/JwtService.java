package com.cake.platform.security;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/** Boot-managed Nimbus implementation, a single allowed algorithm and no expiry grace. */
public final class JwtService {
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;

    public JwtService(String base64Secret, Clock clock) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(base64Secret); }
        catch (RuntimeException ignored) { throw invalidSecret(); }
        if (bytes.length < 32) { throw invalidSecret(); }
        this.clock = clock;
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(jwt -> {
            var now = clock.instant();
            if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                    || !now.isBefore(jwt.getExpiresAt()) || jwt.getIssuedAt().isAfter(now)
                    || !Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).equals(Duration.ofHours(2))) {
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));
            }
            return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
        });
    }

    private static IllegalStateException invalidSecret() {
        return new IllegalStateException("JWT_SECRET_BASE64 必须提供至少32字节随机密钥的Base64编码。");
    }

    public IssuedToken issue(AuthPrincipal principal) {
        // NumericDate is second precision; use the same instant for JWT and expiresAt.
        var issued = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var expiry = issued.plus(Duration.ofHours(2));
        var claims = JwtClaimsSet.builder().subject(principal.userId().toString())
                .claim("role", principal.role()).claim("ver", principal.tokenVersion())
                .issuedAt(issued).expiresAt(expiry).build();
        var token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new IssuedToken(token, expiry.atOffset(ZoneOffset.ofHours(8)));
    }

    public Jwt decode(String token) { return decoder.decode(token); }
    public record IssuedToken(String token, OffsetDateTime expiresAt) { }
}
