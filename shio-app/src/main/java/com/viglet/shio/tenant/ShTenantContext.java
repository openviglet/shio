package com.viglet.shio.tenant;

import com.viglet.core.tenancy.VigletTenantContext;

/**
 * Thread-bound current-tenant holder. A thin static facade over the shared
 * {@link VigletTenantContext} kernel (viglet-core) so Shio runs the <b>same</b>
 * tenant-context implementation as Turing — preserving Shio's existing static
 * call sites and {@code getTenantId()} semantics ({@code null} when unset),
 * while gaining the shared {@code resolveCurrentTenant} / system-mode /
 * {@code runAs} capabilities via {@link #context()}.
 *
 * @author Alexandre Oliveira
 */
public final class ShTenantContext {

    public static final String DEFAULT_TENANT = "shio";

    /**
     * The shared kernel instance. A single static holder is correct: the
     * underlying tenant/system-mode state is thread-local, so per-thread
     * isolation is preserved regardless of the holder being a singleton.
     */
    private static final VigletTenantContext DELEGATE = new VigletTenantContext(true, DEFAULT_TENANT);

    private ShTenantContext() {
    }

    public static void setTenantId(String tenantId) {
        DELEGATE.setCurrentTenant(tenantId);
    }

    public static String getTenantId() {
        return DELEGATE.getCurrentTenant();
    }

    public static void clear() {
        DELEGATE.clear();
    }

    /** The shared kernel for callers needing {@code resolveCurrentTenant}/system-mode/{@code runAs}. */
    public static VigletTenantContext context() {
        return DELEGATE;
    }
}
