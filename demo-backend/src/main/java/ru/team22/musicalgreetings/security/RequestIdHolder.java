package ru.team22.musicalgreetings.security;

import org.slf4j.MDC;

public class RequestIdHolder {
    private static final String MDC_KEY = "requestId";

    private RequestIdHolder() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
