package com.cake.platform.entity;

import java.math.BigInteger;

/** Minimal locked sys_user projection used to re-check the caller inside a write transaction. */
public record AccountState(BigInteger id, String role, int status, int tokenVersion) { }
