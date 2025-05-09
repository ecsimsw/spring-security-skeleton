package com.ecsimsw.common.config;

import java.util.Arrays;

public enum ServiceName {
    GATEWAY,
    USER,
    AUTH,
    EVENT,
    TRANSACTION,
    NOTIFICATION;

    public static ServiceName resolve(String name) {
        return Arrays.stream(values())
            .filter(it -> it.name().equalsIgnoreCase(name))
            .findAny()
            .orElseThrow();
    }
}
