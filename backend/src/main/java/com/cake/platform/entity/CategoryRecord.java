package com.cake.platform.entity;

import java.math.BigInteger;
import java.time.LocalDateTime;

/** Internal cake_category row; public views omit the timestamps required in 5.1.1. */
public record CategoryRecord(BigInteger id, BigInteger merchantId, String name, int sort, int status,
        LocalDateTime createTime, LocalDateTime updateTime) { }
