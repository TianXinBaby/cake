package com.cake.platform.security;

import java.math.BigInteger;

/** Trusted database identity; never assembled from request JSON or merchant claims. */
public record AuthPrincipal(BigInteger userId, String role, int tokenVersion, BigInteger merchantId) { }
