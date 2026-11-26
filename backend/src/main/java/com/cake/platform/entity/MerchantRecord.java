package com.cake.platform.entity;

import java.math.BigInteger;
import java.time.LocalDateTime;

/** Internal platform merchant projection, without account credentials or authentication controls. */
public record MerchantRecord(BigInteger id, BigInteger userId, String username, String name, String phone,
        String address, String description, int status, LocalDateTime createTime, LocalDateTime updateTime) { }
