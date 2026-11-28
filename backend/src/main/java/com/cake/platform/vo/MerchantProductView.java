package com.cake.platform.vo;

import java.time.LocalDateTime;
import com.cake.platform.entity.ProductRecord;

/** Merchant Product object (5.1.1): current stock and listing state plus createTime and updateTime. */
public record MerchantProductView(String id, String merchantId, String merchantName, String categoryId,
        String categoryName, String name, String spec, String price, int stock, String image,
        String description, int status, LocalDateTime createTime, LocalDateTime updateTime) {
    public static MerchantProductView from(ProductRecord row) {
        return new MerchantProductView(row.id().toString(), row.merchantId().toString(), row.merchantName(),
                row.categoryId().toString(), row.categoryName(), row.name(), row.spec(),
                row.price().setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString(),
                row.stock(), row.image(), row.description(), row.status(), row.createTime(), row.updateTime());
    }
}
