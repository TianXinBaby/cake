package com.cake.platform.vo;

import java.time.LocalDateTime;
import com.cake.platform.entity.CategoryRecord;

/** Merchant Category object (5.1.1): the same row plus createTime and updateTime. */
public record MerchantCategoryView(String id, String merchantId, String name, int sort, int status,
        LocalDateTime createTime, LocalDateTime updateTime) {
    public static MerchantCategoryView from(CategoryRecord row) {
        return new MerchantCategoryView(row.id().toString(), row.merchantId().toString(), row.name(), row.sort(),
                row.status(), row.createTime(), row.updateTime());
    }
}
