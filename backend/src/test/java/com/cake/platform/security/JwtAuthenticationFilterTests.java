package com.cake.platform.security;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import com.cake.platform.service.auth.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTests {
    private final AuthenticationService service = mock(AuthenticationService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(service, json);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MERCHANT", "CUSTOMER"})
    void installsExactDatabasePrincipalAndSpringRoleForEveryRole(String role) throws Exception {
        var principal = new AuthPrincipal(new BigInteger("18446744073709551614"), role, 9,
                role.equals("MERCHANT") ? BigInteger.valueOf(42) : null);
        when(service.authenticate("a.b.c")).thenReturn(principal);
        var request = protectedRequest("Bearer a.b.c");
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            reached.set(true);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.isAuthenticated()).isTrue();
            assertThat(auth.getPrincipal()).isSameAs(principal);
            assertThat(auth.getCredentials()).isNull();
            assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_" + role);
        });
        assertThat(reached.get()).isTrue();
        verify(service).authenticate("a.b.c");
    }

    @Test
    void bearerSchemeIsCaseInsensitive() throws Exception {
        when(service.authenticate("a.b.c")).thenReturn(new AuthPrincipal(BigInteger.ONE, "CUSTOMER", 0, null));
        filter.doFilter(protectedRequest("bEaReR a.b.c"), new MockHttpServletResponse(), (req, res) -> { });
        verify(service).authenticate("a.b.c");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "Basic abc", "Bearer a.b", "Bearer a.b.c ", "Bearer a.b.c extra", "Bearer  a.b.c", "Bearer a..c"})
    void rejectsMalformedAuthorizationBeforeCallingServiceOrController(String header) throws Exception {
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        filter.doFilter(protectedRequest(header), response, (req, res) -> reached.set(true));
        assertThat(reached.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(json.readTree(response.getContentAsByteArray()).path("code").asInt()).isEqualTo(40102);
        verifyNoInteractions(service);
    }

    @Test
    void rejectsDuplicateAuthorizationHeaders() throws Exception {
        var request = protectedRequest("Bearer a.b.c");
        request.addHeader("Authorization", "Bearer x.y.z");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Controller must not run"); });
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(service);
    }

    @Test
    void missingHeaderLeavesAuthorizationDecisionToSecurityChain() throws Exception {
        var reached = new AtomicBoolean();
        filter.doFilter(protectedRequest(null), new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/register"})
    void anonymousPostAuthEndpointsIgnoreStaleAuthorizationHeader(String path) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer stale-token");
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached.get()).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void disabledAccountErrorStopsChainAndClearsAuthentication() throws Exception {
        when(service.authenticate("a.b.c")).thenThrow(new AuthFailure(AuthErrorCode.DISABLED));
        var response = new MockHttpServletResponse();
        filter.doFilter(protectedRequest("Bearer a.b.c"), response, (req, res) -> { throw new AssertionError("Controller must not run"); });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(json.readTree(response.getContentAsByteArray()).path("code").asInt()).isEqualTo(40301);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unexpectedAuthenticationFailureReturnsSanitized500WithoutCauseOrCredentials() throws Exception {
        String secretDiagnostic = "internal-database-and-secret-diagnostic";
        when(service.authenticate("a.b.c")).thenThrow(new IllegalStateException(secretDiagnostic));
        var response = new MockHttpServletResponse();
        filter.doFilter(protectedRequest("Bearer a.b.c"), response, (req, res) -> { throw new AssertionError("Controller must not run"); });
        var body = json.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body.path("code").asInt()).isEqualTo(50001);
        assertThat(body.path("data").path("requestId").isTextual()).isTrue();
        assertThat(response.getContentAsString().contains(secretDiagnostic)).isFalse();
        assertThat(response.getContentAsString().contains("a.b.c")).isFalse();
    }

    private static MockHttpServletRequest protectedRequest(String authorization) {
        var request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setServletPath("/api/users/me");
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }
}
