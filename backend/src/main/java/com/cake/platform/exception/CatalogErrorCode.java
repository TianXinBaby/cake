package com.cake.platform.exception;

import org.springframework.http.HttpStatus;

/**
 * Requirement 3.7 codes used by product, category and stock operations (API-02~05, 15~22, 29~31).
 * Codes 40401/40403 are also returned to merchants for own-store resources that do not exist.
 */
public enum CatalogErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, 40401, "商品不存在或已下架"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, 40403, "分类不存在或无权访问"),
    MERCHANT_UNAVAILABLE(HttpStatus.NOT_FOUND, 40404, "商家不存在或不可用"),
    CATEGORY_DUPLICATE(HttpStatus.CONFLICT, 40901, "分类名称已存在"),
    CATEGORY_NOT_EMPTY(HttpStatus.CONFLICT, 40909, "分类仍有商品，请先处理所属商品"),
    CATEGORY_HAS_LISTED(HttpStatus.CONFLICT, 40909, "分类仍有上架商品，请先下架"),
    PRODUCT_REFERENCED(HttpStatus.CONFLICT, 40910, "商品已有历史订单，请使用下架"),
    STOCK_CHANGED(HttpStatus.CONFLICT, 40911, "库存已变化，请刷新后重试"),
    PRODUCT_CHANGED(HttpStatus.CONFLICT, 40911, "商品信息已变化，请刷新后重试"),
    CATEGORY_DISABLED(HttpStatus.CONFLICT, 40913, "请先启用商品分类");

    private final HttpStatus status;
    private final int code;
    private final String message;

    CatalogErrorCode(HttpStatus status, int code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus httpStatus() { return status; }
    @Override public int code() { return code; }
    @Override public String message() { return message; }
}
