package com.cake.platform.vo;

import java.time.LocalDateTime;
import com.cake.platform.entity.MerchantRecord;

public record MerchantView(String id, String name, String phone, String address, String description, int status,
        String userId, String username, LocalDateTime createTime, LocalDateTime updateTime) {
    public static MerchantView from(MerchantRecord row) {
        return new MerchantView(row.id().toString(), row.name(), row.phone(), row.address(), row.description(),
                row.status(), row.userId().toString(), row.username(), row.createTime(), row.updateTime());
    }
}
