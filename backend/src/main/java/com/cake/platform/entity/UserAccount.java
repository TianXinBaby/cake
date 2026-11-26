package com.cake.platform.entity;

import java.math.BigInteger;
import java.time.LocalDateTime;

/** Internal row, never serialized to the public API. BigInteger preserves unsigned BIGINT. */
public record UserAccount(BigInteger id, String username, String password, String nickname,
        String phone, String role, int status, int tokenVersion, int loginFailCount,
        LocalDateTime lockedUntil, LocalDateTime createTime, LocalDateTime updateTime) { }
