package com.cake.platform.entity;

import java.time.LocalDateTime;

/** Current stock value and write time returned by API-29. */
public record StockRecord(int stock, LocalDateTime updateTime) { }
