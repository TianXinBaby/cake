package com.cake.platform.vo;

import java.math.BigInteger;

/** API-05/API-22 success object. */
public record DeletionView(String id, boolean deleted) {
    public static DeletionView of(BigInteger id) { return new DeletionView(id.toString(), true); }
}
