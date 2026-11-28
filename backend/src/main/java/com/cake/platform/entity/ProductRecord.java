package com.cake.platform.entity;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

/** Internal cake_product projection with the owning shop and category labels for API views. */
public record ProductRecord(BigInteger id, BigInteger merchantId, String merchantName, BigInteger categoryId,
        String categoryName, String name, String spec, BigDecimal price, int stock, String image,
        String description, int status, LocalDateTime createTime, LocalDateTime updateTime) { }
