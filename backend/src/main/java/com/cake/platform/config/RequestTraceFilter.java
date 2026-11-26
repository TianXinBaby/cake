package com.cake.platform.config;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = RequestTraceFilter.class.getName() + ".requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        // Generate locally: never put caller-controlled header values into logs.
        request.setAttribute(REQUEST_ID_ATTRIBUTE, UUID.randomUUID().toString());
        filterChain.doFilter(request, response);
    }
}
