package com.cake.platform.controller.catalog;

import com.cake.platform.common.ApiResponse;
import com.cake.platform.dto.CatalogRequests;
import com.cake.platform.security.AuthPrincipal;
import com.cake.platform.service.catalog.MerchantCatalogService;
import com.cake.platform.vo.DeletionView;
import com.cake.platform.vo.MerchantCategoryView;
import com.cake.platform.vo.MerchantProductView;
import com.cake.platform.vo.PageView;
import com.cake.platform.vo.StockView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-03~05, 17~22, 29~31: own-store product, category and stock maintenance. */
@RestController
@Profile({"dev", "prod", "mysql-test"})
@RequestMapping("/api/merchant")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantCatalogController {
    private final MerchantCatalogService service;

    public MerchantCatalogController(MerchantCatalogService service) { this.service = service; }

    @GetMapping("/products")
    public ApiResponse<PageView<MerchantProductView>> products(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        CatalogRequests.noBody(body);
        return ApiResponse.success(service.products(principal,
                CatalogRequests.merchantList(request.getParameterMap())));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MerchantProductView> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @RequestBody JsonNode body, HttpServletRequest request) {
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.create(principal, CatalogRequests.productCreation(body)));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<MerchantProductView> product(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable String id,
                                                     @RequestBody(required = false) JsonNode body,
                                                     HttpServletRequest request) {
        CatalogRequests.noBody(body);
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.product(principal, CatalogRequests.id(id)));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<MerchantProductView> edit(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable String id, @RequestBody JsonNode body,
                                                  HttpServletRequest request) {
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.edit(principal, CatalogRequests.id(id),
                CatalogRequests.productEdit(body)));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<DeletionView> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable String id,
                                             @RequestBody(required = false) JsonNode body,
                                             HttpServletRequest request) {
        CatalogRequests.noBody(body);
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.delete(principal, CatalogRequests.id(id)));
    }

    @PatchMapping("/products/{id}/stock")
    public ApiResponse<StockView> stock(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable String id, @RequestBody JsonNode body,
                                        HttpServletRequest request) {
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.stock(principal, CatalogRequests.id(id),
                CatalogRequests.stockAdjustment(body)));
    }

    @PatchMapping("/products/{id}/status")
    public ApiResponse<MerchantProductView> productStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @PathVariable String id, @RequestBody JsonNode body,
                                                           HttpServletRequest request) {
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.productStatus(principal, CatalogRequests.id(id),
                CatalogRequests.status(body)));
    }

    @GetMapping("/categories")
    public ApiResponse<List<MerchantCategoryView>> categories(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @RequestBody(required = false) JsonNode body,
                                                               HttpServletRequest request) {
        CatalogRequests.noBody(body);
        return ApiResponse.success(service.categories(principal,
                CatalogRequests.categoryStatusFilter(request.getParameterMap())));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MerchantCategoryView> createCategory(@AuthenticationPrincipal AuthPrincipal principal,
                                                             @RequestBody JsonNode body,
                                                             HttpServletRequest request) {
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.createCategory(principal, CatalogRequests.categoryCreation(body)));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<MerchantCategoryView> editCategory(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @PathVariable String id, @RequestBody JsonNode body,
                                                           HttpServletRequest request) {
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.editCategory(principal, CatalogRequests.id(id),
                CatalogRequests.categoryEdit(body)));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<DeletionView> deleteCategory(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable String id,
                                                     @RequestBody(required = false) JsonNode body,
                                                     HttpServletRequest request) {
        CatalogRequests.noBody(body);
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.deleteCategory(principal, CatalogRequests.id(id)));
    }

    @PatchMapping("/categories/{id}/status")
    public ApiResponse<MerchantCategoryView> categoryStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                                             @PathVariable String id, @RequestBody JsonNode body,
                                                             HttpServletRequest request) {
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.categoryStatus(principal, CatalogRequests.id(id),
                CatalogRequests.status(body)));
    }
}
