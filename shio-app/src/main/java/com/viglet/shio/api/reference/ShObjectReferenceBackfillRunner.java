/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.reference;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.reference.ShObjectReferenceRepository;

/**
 * Fills {@code sh_object_reference} once, for the posts that existed before it (SH1034).
 *
 * <h2>Why a startup backfill and not a runtime fallback</h2>
 *
 * <p>§LXX.6 proposed keeping the {@code LIKE} scan "as the fallback for a tenant whose backfill has
 * not run". Building it that way was tried on paper and does not work, for a reason worth
 * recording: <b>an empty answer and an unindexed post are the same observation.</b> A post with no
 * edges writes no rows, so a read that fell back whenever it found none would fall back on every
 * asset nothing references — which is precisely the query this table exists to stop running, and
 * precisely the case {@code asset-unused} asks about most.</p>
 *
 * <p>So the index is complete or it is empty, and the runner settles which before the application
 * serves anything. That makes the read unconditional, which is the property the whole design
 * rests on.</p>
 *
 * <h2>What it costs, once</h2>
 *
 * <p>Every post is read in pages and re-indexed, so the cost is one pass over the content at the
 * first start after this ships and nothing on any start after it — the guard is the table being
 * empty, and {@code reindex} is a rewrite, so a run interrupted halfway is resumed rather than
 * corrupted (P5).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration
public class ShObjectReferenceBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(ShObjectReferenceBackfillRunner.class);

    /** Rows per transaction. Large enough to be few passes, small enough not to hold the heap. */
    private static final int PAGE = 500;

    @Bean
    ApplicationRunner shObjectReferenceBackfill(ShObjectReferenceRepository references,
            ShPostUnifiedRepository posts, ShObjectReferenceService service,
            PlatformTransactionManager transactionManager) {
        return args -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            Boolean empty = tx.execute(status -> references.count() == 0);
            if (Boolean.FALSE.equals(empty)) {
                return;
            }
            long indexed = 0;
            for (int page = 0; ; page++) {
                final int current = page;
                List<ShPostUnified> batch = tx.execute(status ->
                        posts.findAll(PageRequest.of(current, PAGE)).getContent());
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (ShPostUnified post : batch) {
                    tx.executeWithoutResult(status -> service.reindex(post));
                }
                indexed += batch.size();
            }
            long edges = tx.execute(status -> references.count());
            log.info("SH1034 reference index backfilled: {} post row(s) read, {} edge(s) written",
                    indexed, edges);
        };
    }
}
