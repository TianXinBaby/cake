package com.cake.platform.vo;

import java.time.LocalDateTime;

/** API-29 success object: the value after the conditional update and its write time. */
public record StockView(String id, int stock, LocalDateTime updateTime) { }
