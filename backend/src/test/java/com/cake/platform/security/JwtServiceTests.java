package com.cake.platform.security;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private final byte[] secret = randomBytes(64);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService service = new JwtService(Base64.getEncoder().encodeToString(secret), clock);

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MERCHANT", "CUSTOMER"})
    void signsAndVerifiesOnlyRequiredIdentityClaimsForEachRole(String role) {
        var principal = new AuthPrincipal(new BigInteger("18446744073709551614"), role, 7,
                role.equals("MERCHANT") ? BigInteger.valueOf(42) : null);
        var issued = service.issue(principal);
        var parsed = service.decode(issued.token());
        assertThat(parsed.getSubject()).isEqualTo(principal.userId().toString());
        assertThat(parsed.getClaimAsString("role")).isEqualTo(role);
        assertThat(((Number) parsed.getClaim("ver")).intValue()).isEqualTo(7);
        assertThat(parsed.getClaims().keySet()).containsExactlyInAnyOrder("sub", "role", "ver", "iat", "exp");
        assertThat(parsed.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(parsed.getHeaders().get("typ")).isEqualTo("JWT");
    }

    @Test
    void expiryIsExactlyTwoHoursWithShanghaiOffsetAndMatchesNumericDate() {
        var issued = service.issue(new AuthPrincipal(BigInteger.ONE, "CUSTOMER", 0, null));
        var parsed = service.decode(issued.token());
        assertThat(Duration.between(parsed.getIssuedAt(), parsed.getExpiresAt())).isEqualTo(Duration.ofHours(2));
        assertThat(issued.expiresAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(issued.expiresAt().toInstant()).isEqualTo(parsed.getExpiresAt());
    }

    @Test
    void fractionalClockUsesSameSecondPrecisionInResponseAndJwt() {
        var precise = new JwtService(Base64.getEncoder().encodeToString(secret), Clock.fixed(NOW.plusMillis(987), ZoneOffset.UTC));
        var issued = precise.issue(new AuthPrincipal(BigInteger.ONE, "CUSTOMER", 0, null));
        assertThat(issued.expiresAt().getNano()).isZero();
        assertThat(precise.decode(issued.token()).getExpiresAt()).isEqualTo(issued.expiresAt().toInstant());
    }

    @Test
    void acceptsValidTokenOneNanosecondBeforeExpiry() {
        String token = service.issue(new AuthPrincipal(BigInteger.ONE, "CUSTOMER", 0, null)).token();
        var beforeExpiry = new JwtService(Base64.getEncoder().encodeToString(secret), Clock.fixed(NOW.plusSeconds(7200).minusNanos(1), ZoneOffset.UTC));
        assertThat(beforeExpiry.decode(token).getSubject()).isEqualTo("1");
    }

    @Test
    void rejectsAtExactExpiryWithoutDefaultClockSkew() {
        String token = service.issue(new AuthPrincipal(BigInteger.ONE, "CUSTOMER", 0, null)).token();
        var atExpiry = new JwtService(Base64.getEncoder().encodeToString(secret), Clock.fixed(NOW.plusSeconds(7200), ZoneOffset.UTC));
        assertThatThrownBy(() -> atExpiry.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentRandomKey() {
        var other = new JwtService(Base64.getEncoder().encodeToString(randomBytes(64)), clock);
        String token = other.issue(new AuthPrincipal(BigInteger.ONE, "CUSTOMER", 0, null)).token();
        assertThatThrownBy(() -> service.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsPayloadTamperingWithoutResigning() {
        String token = service.issue(new AuthPrincipal(BigInteger.ONE, "CUSTOMER", 0, null)).token();
        String[] parts = token.split("\\.");
        String changed = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"2\",\"role\":\"ADMIN\",\"ver\":0}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String altered = parts[0] + "." + changed + "." + parts[2];
        assertThatThrownBy(() -> service.decode(altered)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "a.b.c", "eyJhbGciOiJub25lIn0.e30."})
    void rejectsMalformedOrUnsignedJwt(String token) {
        assertThatThrownBy(() -> service.decode(token)).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not base64", "YQ=="})
    void rejectsMissingMalformedAndShortSecretsWithoutEchoingSecret(String encoded) {
        assertThatThrownBy(() -> new JwtService(encoded, clock)).isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET_BASE64 必须提供至少32字节随机密钥的Base64编码。");
    }

    @Test
    void rejectsThirtyOneByteSecret() {
        assertThatThrownBy(() -> new JwtService(Base64.getEncoder().encodeToString(randomBytes(31)), clock))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsExactlyThirtyTwoByteSecret() {
        var minimum = new JwtService(Base64.getEncoder().encodeToString(randomBytes(32)), clock);
        String token = minimum.issue(new AuthPrincipal(BigInteger.ONE, "CUSTOMER", 0, null)).token();
        assertThat(minimum.decode(token).getSubject()).isEqualTo("1");
    }

    @Test
    void refusesOtherHmacAlgorithmEvenWithCorrectSecret() throws Exception {
        String token = signed(JWSAlgorithm.HS384, NOW, NOW.plusSeconds(7200));
        assertThatThrownBy(() -> service.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesFutureIssuedAt() throws Exception {
        String token = signed(JWSAlgorithm.HS256, NOW.plusSeconds(1), NOW.plusSeconds(7201));
        assertThatThrownBy(() -> service.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesMissingIssuedAtAndMissingExpiry() throws Exception {
        for (boolean missingIssuedAt : List.of(true, false)) {
            String token = signed(JWSAlgorithm.HS256, missingIssuedAt ? null : NOW, missingIssuedAt ? NOW.plusSeconds(7200) : null);
            assertThatThrownBy(() -> service.decode(token)).isInstanceOf(JwtException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 7199, 7201, 86400})
    void refusesAnyValidityOtherThanTwoHours(long seconds) throws Exception {
        String token = signed(JWSAlgorithm.HS256, NOW, NOW.plusSeconds(seconds));
        assertThatThrownBy(() -> service.decode(token)).isInstanceOf(JwtException.class);
    }

    private String signed(JWSAlgorithm algorithm, Instant issued, Instant expiry) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject("1").claim("role", "CUSTOMER").claim("ver", 0);
        if (issued != null) claims.issueTime(Date.from(issued));
        if (expiry != null) claims.expirationTime(Date.from(expiry));
        var token = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims.build());
        token.sign(new MACSigner(secret));
        return token.serialize();
    }

    private static byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        new SecureRandom().nextBytes(value);
        return value;
    }
}
