/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.repository.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.tenant.ShTenantApiCall;

/**
 * Repository for the per-period API-call meter (SH45). {@link ShTenantApiCall} is not
 * {@code @TenantId}-scoped — it is the global usage catalog — so these queries see every tenant and
 * every read that should not name the tenant explicitly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShTenantApiCallRepository extends JpaRepository<ShTenantApiCall, String> {

    Optional<ShTenantApiCall> findByTenantNameAndPeriodKeyAndSurface(
            String tenantName, String periodKey, String surface);

    List<ShTenantApiCall> findByPeriodKeyOrderByTenantNameAscSurfaceAsc(String periodKey);

    List<ShTenantApiCall> findByTenantNameAndPeriodKeyBetweenOrderByPeriodKeyAscSurfaceAsc(
            String tenantName, String fromPeriodKey, String toPeriodKey);

    /**
     * Add {@code delta} to one bucket, in the database rather than in the caller.
     *
     * <p><b>This is the whole reason a flow can be metered from many nodes and a level cannot.</b>
     * Read-modify-write would make two nodes flushing the same bucket race, and the loser's calls
     * would vanish — which is the failure {@code ShCdaRateLimiter}'s in-heap buckets have and the
     * reason its own javadoc disqualifies it from being the meter. Addition commutes: 100 from one
     * node and 100 from another leave 200 whichever order they arrive in.</p>
     *
     * @return rows updated — {@code 0} means the bucket does not exist yet and the caller inserts
     */
    // clearAutomatically/flushAutomatically: the bulk update goes straight to the database, so a
    // persistence context that already holds the row would keep serving the pre-update figure to
    // the very next read — a meter that looks stuck rather than one that failed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ShTenantApiCall c set c.calls = c.calls + :delta"
            + " where c.tenantName = :tenantName and c.periodKey = :periodKey and c.surface = :surface")
    int addCalls(@Param("tenantName") String tenantName,
                 @Param("periodKey") String periodKey,
                 @Param("surface") String surface,
                 @Param("delta") long delta);

    /** Every distinct period held, newest first — what an operator lists before exporting one. */
    @Query("select distinct c.periodKey from ShTenantApiCall c order by c.periodKey desc")
    List<String> findPeriodKeys();
}
