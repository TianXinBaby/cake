package com.cake.platform.security;

import java.io.IOException;
import java.util.List;
import com.cake.platform.exception.BusinessException;
import com.cake.platform.exception.CommonErrorCode;
import com.cake.platform.service.auth.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthenticationService service;
    private final ObjectMapper json;
    public JwtAuthenticationFilter(AuthenticationService service, ObjectMapper json) {
        this.service = service; this.json = json;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getMethod().equals("POST") && (request.getServletPath().equals("/api/auth/login")
                || request.getServletPath().equals("/api/auth/register"));
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.matches("(?i)Bearer [A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+")) {
                    throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED);
                }
                if (java.util.Collections.list(request.getHeaders("Authorization")).size() != 1) {
                    throw new AuthFailure(AuthErrorCode.UNAUTHENTICATED);
                }
                var principal = service.authenticate(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException error) {
                SecurityContextHolder.clearContext();
                AuthErrorResponses.write(json, request, response, error);
                return;
            } catch (RuntimeException error) {
                SecurityContextHolder.clearContext();
                AuthErrorResponses.write(json, request, response, new BusinessException(CommonErrorCode.INTERNAL_ERROR));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
