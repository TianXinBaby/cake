package com.cake.platform.controller.merchant;

import com.cake.platform.common.ApiResponse;
import com.cake.platform.dto.PublicMerchantRequests;
import com.cake.platform.service.merchant.PublicMerchantService;
import com.cake.platform.vo.PageView;
import com.cake.platform.vo.PublicMerchantView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-33/34: anonymous public merchant list and detail. */
@RestController
@Profile({"dev", "prod", "mysql-test"})
@RequestMapping("/api/merchants")
public class PublicMerchantController {
    private final PublicMerchantService service;

    public PublicMerchantController(PublicMerchantService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageView<PublicMerchantView>> merchants(@RequestBody(required = false) JsonNode body,
                                                               HttpServletRequest request) {
        PublicMerchantRequests.noBody(body);
        return ApiResponse.success(service.merchants(PublicMerchantRequests.list(request.getParameterMap())));
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicMerchantView> merchant(@PathVariable String id,
                                                    @RequestBody(required = false) JsonNode body,
                                                    HttpServletRequest request) {
        PublicMerchantRequests.noBody(body);
        PublicMerchantRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.merchant(PublicMerchantRequests.id(id)));
    }
}
