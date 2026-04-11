/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.model.token;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * CDA (Content Delivery API) Token entity.
 * Read-only tokens for accessing published content.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Getter
@Setter
@Entity
@Table(name = "api_token")
public class ShApiToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "token")
    private String token;

    @Column(name = "owner")
    private String owner;

    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "enabled")
    private boolean enabled = true;
}
