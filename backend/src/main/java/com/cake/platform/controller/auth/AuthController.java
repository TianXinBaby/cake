package com.cake.platform.controller.auth;

import java.util.Set;
import com.cake.platform.common.ApiResponse;
import com.cake.platform.dto.AuthRequests;
import com.cake.platform.security.AuthPrincipal;
import com.cake.platform.service.auth.AuthenticationService;
import com.cake.platform.vo.UserView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile({"dev", "prod", "mysql-test"})
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationService service;
    public AuthController(AuthenticationService service) { this.service = service; }
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserView>> register(@RequestBody JsonNode body, HttpServletRequest request) {
        noParameters(request);
        var input = AuthRequests.registration(body);
        var user = service.register(input.phone(), input.nickname(), input.password());
        return ResponseEntity.status(201).body(ApiResponse.success(user));
    }
    @PostMapping("/login")
    public ApiResponse<AuthenticationService.LoginView> login(@RequestBody JsonNode body, HttpServletRequest request) {
        noParameters(request);
        var input = AuthRequests.login(body);
        return ApiResponse.success(service.login(input.username(), input.password()));
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
                                   @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        noParameters(request);
        if (body != null) { AuthRequests.fields(body, Set.of()); AuthRequests.invalid(); }
        service.logout(principal);
        return ApiResponse.success(null);
    }
    public static void noParameters(HttpServletRequest request) {
        var probe = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        for (String key : request.getParameterMap().keySet()) {
            probe.putNull(key);
        }
        AuthRequests.fields(probe, Set.of());
    }
}
