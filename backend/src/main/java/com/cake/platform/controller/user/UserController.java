package com.cake.platform.controller.user;

import java.util.Set;
import com.cake.platform.common.ApiResponse;
import com.cake.platform.controller.auth.AuthController;
import com.cake.platform.dto.AuthRequests;
import com.cake.platform.security.AuthPrincipal;
import com.cake.platform.service.auth.AuthenticationService;
import com.cake.platform.vo.UserView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile({"dev", "prod", "mysql-test"})
@RequestMapping("/api/users/me")
public class UserController {
    private final AuthenticationService service;
    public UserController(AuthenticationService service) { this.service = service; }
    @GetMapping
    public ApiResponse<UserView> me(@AuthenticationPrincipal AuthPrincipal principal,
                                   @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        AuthController.noParameters(request);
        if (body != null) { AuthRequests.fields(body, Set.of()); AuthRequests.invalid(); }
        return ApiResponse.success(service.me(principal));
    }
    @PatchMapping
    public ApiResponse<UserView> nickname(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noParameters(request);
        return ApiResponse.success(service.nickname(principal, AuthRequests.nickname(body)));
    }
    @PatchMapping("/password")
    public ApiResponse<Void> password(@AuthenticationPrincipal AuthPrincipal principal,
                                     @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noParameters(request);
        var input = AuthRequests.passwordChange(body);
        service.password(principal, input.oldPassword(), input.newPassword());
        return ApiResponse.success(null);
    }
}
