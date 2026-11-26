package com.cake.platform.controller.admin;

import com.cake.platform.common.ApiResponse;
import com.cake.platform.dto.AdminRequests;
import com.cake.platform.security.AuthPrincipal;
import com.cake.platform.service.admin.AdminService;
import com.cake.platform.vo.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile({"dev", "prod", "mysql-test"})
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }

    @GetMapping("/merchants")
    public ApiResponse<PageView<MerchantView>> merchants(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        AdminRequests.noBody(body);
        return ApiResponse.success(service.merchants(AdminRequests.list(request.getParameterMap())));
    }
    @PostMapping("/merchants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MerchantView> create(@AuthenticationPrincipal AuthPrincipal principal, @RequestBody JsonNode body,
                                          HttpServletRequest request) {
        AdminRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.create(principal, AdminRequests.creation(body)));
    }
    @PutMapping("/merchants/{id}")
    public ApiResponse<MerchantView> edit(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                        @RequestBody JsonNode body, HttpServletRequest request) {
        AdminRequests.noQuery(request.getParameterMap());
        var input = AdminRequests.edit(body);
        return ApiResponse.success(service.edit(principal, AdminRequests.id(id), input));
    }
    @PatchMapping("/merchants/{id}/status")
    public ApiResponse<MerchantView> merchantStatus(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                                   @RequestBody JsonNode body, HttpServletRequest request) {
        AdminRequests.noQuery(request.getParameterMap());
        int status = AdminRequests.status(body);
        return ApiResponse.success(service.merchantStatus(principal, AdminRequests.id(id), status));
    }
    @GetMapping("/users")
    public ApiResponse<PageView<UserView>> customers(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        AdminRequests.noBody(body);
        return ApiResponse.success(service.customers(AdminRequests.list(request.getParameterMap())));
    }
    @PatchMapping("/users/{id}/status")
    public ApiResponse<UserView> customerStatus(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                               @RequestBody JsonNode body, HttpServletRequest request) {
        AdminRequests.noQuery(request.getParameterMap());
        int status = AdminRequests.status(body);
        return ApiResponse.success(service.customerStatus(principal, AdminRequests.id(id), status));
    }
}
