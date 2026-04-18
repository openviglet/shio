package com.viglet.shio.tenant;

public final class ShTenantContext {

    public static final String DEFAULT_TENANT = "shio";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private ShTenantContext() {
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
