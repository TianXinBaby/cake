package com.cake.platform.config;

import java.time.Clock;
import com.cake.platform.security.*;
import com.cake.platform.service.auth.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Profile({"dev", "prod", "mysql-test"})
@EnableMethodSecurity
@MapperScan("com.cake.platform.mapper")
public class AuthenticationConfiguration {
    @Bean
    FilterRegistrationBean<RequestTraceFilter> authenticationRequestTrace(RequestTraceFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
    @Bean
    JwtService jwtService(@Value("${cake.security.jwt-secret-base64:}") String key) {
        return new JwtService(key, Clock.systemUTC());
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationService service, ObjectMapper json)
            throws Exception {
        http.csrf(csrf -> csrf.disable()).formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable()).requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, error) -> AuthErrorResponses.write(json, req, res,
                                new AuthFailure(AuthErrorCode.UNAUTHENTICATED)))
                        .accessDeniedHandler((req, res, error) -> AuthErrorResponses.write(json, req, res,
                                new AuthFailure(AuthErrorCode.FORBIDDEN))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*", "/api/categories",
                                "/api/merchants", "/api/merchants/*").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/merchant/**").hasRole("MERCHANT")
                        .requestMatchers("/api/orders", "/api/orders/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(service, json), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
