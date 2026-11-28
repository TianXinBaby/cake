package com.cake.platform.vo;

import com.cake.platform.entity.ProductRecord;

/** Public Product object (5.1.1): visible only when shop and category are enabled and the product is listed. */
public record ProductView(String id, String merchantId, String merchantName, String categoryId, String categoryName,
        String name, String spec, String price, int stock, String image, String description, int status) {
    public static ProductView from(ProductRecord row) {
        return new ProductView(row.id().toString(), row.merchantId().toString(), row.merchantName(),
                row.categoryId().toString(), row.categoryName(), row.name(), row.spec(),
                row.price().setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString(),
                row.stock(), row.image(), row.description(), row.status());
    }
}
