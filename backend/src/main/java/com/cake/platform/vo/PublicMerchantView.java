package com.cake.platform.vo;

import com.cake.platform.entity.PublicMerchantRecord;

/** Public Merchant object (5.1.1): enabled shop with an enabled bound account only. */
public record PublicMerchantView(String id, String name, String phone, String address, String description,
                                 int status) {
    public static PublicMerchantView from(PublicMerchantRecord row) {
        return new PublicMerchantView(row.id().toString(), row.name(), row.phone(), row.address(),
                row.description(), row.status());
    }
}
