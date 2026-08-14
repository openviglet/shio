/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.model.tenant;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * How many API calls one tenant made, on one surface, in one period (SH45).
 *
 * <h2>Why this is a table and the storage meter is a column</h2>
 *
 * <p>SH746 put storage on {@code sh_tenant} as a single maintained figure because storage is a
 * <b>level</b>: how much is held right now, which is the shape
 * {@code VigletResourceCounter.count(tenant, resource)} models. An API call is a <b>flow</b> — the
 * question a bill asks is calls <em>per period</em> — and a level has no period to be asked about.
 * A cumulative column would grow forever, and resetting it monthly is a scheduled job nobody
 * designed. So the period is part of the key and the row <em>is</em> the bucket.</p>
 *
 * <p>The period is a UTC day. A month is the billing unit, but a month-keyed row cannot answer
 * "how much so far" without a second, finer number; a day sums to a month, and an hour would
 * multiply the rows by 24 to answer a question nobody has asked.</p>
 *
 * <h2>Not tenant-scoped, on purpose</h2>
 *
 * <p>No {@code @TenantId} here, the same decision {@link ShTenant} itself carries: this is the
 * global usage catalog an operator exports across every tenant, so the tenant is an ordinary
 * addressed column rather than the discriminator. A {@code @TenantId} row would be unreadable by
 * the one caller that needs all of them.</p>
 *
 * <h2>The unique constraint is load-bearing</h2>
 *
 * <p>{@code ShTenantApiCallMeter}'s flush adds to an existing bucket and inserts only when there is
 * none; the constraint is what makes the losing side of that race re-read instead of creating a
 * second row for the same bucket. Declared here <em>and</em> in Liquibase per SH729 — {@code mvn
 * test} builds H2 from entity metadata with Liquibase disabled, so a constraint declared only in
 * the changelog is one no unit test has ever seen.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_tenant_api_call",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_sh_tenant_api_call_bucket",
                columnNames = {"tenant_name", "period_key", "surface"}))
public class ShTenantApiCall implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * The tenant this usage belongs to — the {@code name} of an {@link ShTenant} row, which is the
     * same value the {@code tenant_id} discriminator carries on content.
     */
    @Column(name = "tenant_name", length = 255, nullable = false)
    private String tenantName;

    /** The bucket's UTC day, ISO-8601 ({@code 2026-08-14}). */
    @Column(name = "period_key", length = 10, nullable = false)
    private String periodKey;

    /**
     * Which surface the calls arrived on — {@code cda}, {@code agent} or {@code console}.
     *
     * <p>Recorded separately rather than totalled, because "what counts as a call" is a pricing
     * decision and not a metering one: keeping them apart lets a plan decide later which surfaces
     * it charges for, without a migration and without having thrown the answer away.</p>
     */
    @Column(name = "surface", length = 20, nullable = false)
    private String surface;

    /** Calls in this bucket. Moved by addition only, so two nodes flushing cannot overwrite. */
    @Column(name = "calls", nullable = false)
    private long calls;
}
