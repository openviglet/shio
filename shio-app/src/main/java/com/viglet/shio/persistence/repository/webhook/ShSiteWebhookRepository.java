/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.repository.webhook;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.webhook.ShSiteWebhook;

/**
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Repository
public interface ShSiteWebhookRepository extends JpaRepository<ShSiteWebhook, String> {

    List<ShSiteWebhook> findAllByOrderByNameAsc();

    List<ShSiteWebhook> findByEnabledTrue();
}
