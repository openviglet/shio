package com.viglet.shio.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ShTenantResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    @Override
    public String resolveCurrentTenantIdentifier() {
        // The multi-tenant gating lives entirely in ShTenantFilter, which only
        // populates the context when shio.multi-tenant=true. Here the contract is
        // simply: current tenant if set, otherwise the default ('shio') — so
        // single-tenant installs bind every row to 'shio' with no behavior change.
        String tenant = ShTenantContext.getTenantId();
        if (tenant != null && !tenant.isBlank()) {
            return tenant;
        }
        return ShTenantContext.DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.tenant_identifier_resolver", this);
    }
}
