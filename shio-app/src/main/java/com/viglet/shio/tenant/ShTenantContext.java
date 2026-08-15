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

    /**
     * The current tenant, or the reserved default when nothing is bound (SH761).
     *
     * <p>{@link #getTenantId()} answers {@code null} when unset and that contract is right: a
     * <em>reader</em> asking "who is this" must be able to tell "nobody" from "the default". But
     * on a single-tenant install — {@code shio.multi-tenant=false}, which is the default —
     * {@code ShTenantFilter} is a no-op and nothing is ever bound, so <b>every</b> request answers
     * null there while the data it touches is filed under {@code shio}.</p>
     *
     * <p>That asymmetry cost SH761: the usage document's reader already defaulted this way and the
     * meter's writer did not, so a live instance reported {@code tenant=shio} with every count at
     * zero after five agent calls — a mechanism complete everywhere except at the one line that
     * attributes it, with every surface reporting success. Named here rather than repeated at each
     * call site, because it was the third copy that disagreed.</p>
     */
    public static String currentOrDefault() {
        String resolved = getTenantId();
        return resolved != null && !resolved.isBlank() ? resolved : DEFAULT_TENANT;
    }

    /** The shared kernel for callers needing {@code resolveCurrentTenant}/system-mode/{@code runAs}. */
    public static VigletTenantContext context() {
        return DELEGATE;
    }
}
