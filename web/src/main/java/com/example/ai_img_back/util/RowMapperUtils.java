package com.example.ai_img_back.util;

public final class RowMapperUtils {

    private RowMapperUtils() {
    }

    public static Long parseNullableLong(String value) {
        return value == null ? null : Long.parseLong(value);
    }
}
