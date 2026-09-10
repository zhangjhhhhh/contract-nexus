package com.example.contract.auth.security;

import java.util.Set;

/** 请求级「当前登录用户」上下文，由 {@link AuthInterceptor} 填充、请求结束时清理。 */
public final class AuthContext {

    private static final ThreadLocal<String> USER = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> PERMISSIONS = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(String userName, Set<String> permissions) {
        USER.set(userName);
        PERMISSIONS.set(permissions);
    }

    public static String userName() {
        return USER.get();
    }

    public static boolean hasPermission(String permission) {
        Set<String> permissions = PERMISSIONS.get();
        return permissions != null && permissions.contains(permission);
    }

    public static void clear() {
        USER.remove();
        PERMISSIONS.remove();
    }
}
