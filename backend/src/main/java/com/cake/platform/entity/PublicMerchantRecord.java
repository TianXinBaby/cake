package com.cake.platform.entity;

import java.math.BigInteger;

/** Public merchant projection; account identity and credentials never leave the platform layer. */
public record PublicMerchantRecord(BigInteger id, String name, String phone, String address,
                                   String description, int status) { }
