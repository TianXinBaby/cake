package com.cake.platform.controller.catalog;

import com.cake.platform.common.ApiResponse;
import com.cake.platform.dto.CatalogRequests;
import com.cake.platform.service.catalog.PublicCatalogService;
import com.cake.platform.vo.CategoryView;
import com.cake.platform.vo.PageView;
import com.cake.platform.vo.ProductView;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-02, API-15, API-16: anonymous reads of the selected shop's enabled categories and listed products. */
@RestController
@Profile({"dev", "prod", "mysql-test"})
@RequestMapping("/api")
public class PublicCatalogController {
    private final PublicCatalogService service;

    public PublicCatalogController(PublicCatalogService service) { this.service = service; }

    @GetMapping("/products")
    public ApiResponse<PageView<ProductView>> products(@RequestBody(required = false) JsonNode body,
                                                       HttpServletRequest request) {
        CatalogRequests.noBody(body);
        return ApiResponse.success(service.products(CatalogRequests.publicList(request.getParameterMap())));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductView> product(@PathVariable String id,
                                            @RequestBody(required = false) JsonNode body,
                                            HttpServletRequest request) {
        CatalogRequests.noBody(body);
        CatalogRequests.noQuery(request.getParameterMap());
        return ApiResponse.success(service.product(CatalogRequests.id(id)));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryView>> categories(@RequestBody(required = false) JsonNode body,
                                                      HttpServletRequest request) {
        CatalogRequests.noBody(body);
        return ApiResponse.success(service.categories(CatalogRequests.publicMerchant(request.getParameterMap())));
    }
}
