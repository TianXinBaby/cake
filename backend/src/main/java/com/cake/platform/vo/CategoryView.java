package com.cake.platform.vo;

import com.cake.platform.entity.CategoryRecord;

/** Public Category object (5.1.1): enabled categories of the selected available shop only. */
public record CategoryView(String id, String merchantId, String name, int sort, int status) {
    public static CategoryView from(CategoryRecord row) {
        return new CategoryView(row.id().toString(), row.merchantId().toString(), row.name(), row.sort(), row.status());
    }
}
