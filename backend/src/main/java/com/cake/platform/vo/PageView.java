package com.cake.platform.vo;

import java.util.List;

public record PageView<T>(List<T> records, long total, long page, int size) {
    public PageView { records = List.copyOf(records); }
}
