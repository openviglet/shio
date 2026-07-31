/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * SH345 — {@code ?site=nope} teaches, and the transaction does not eat the lesson.
 *
 * <h2>What this proves that the lint cannot</h2>
 *
 * <p>{@link ShTeachingRollbackLintTest} guards the <em>shape</em> — every class-level read-only
 * transaction that throws a teaching exception declares {@code noRollbackFor}. This proves the shape
 * has the effect it is for, which is a different claim: the attribute could be present and the
 * refusal could still be lost, because what actually matters is whether the exception the caller sees
 * is the one the server wrote.</p>
 *
 * <p>Before the fix this method got {@code UnexpectedRollbackException} — a 500 with no {@code fix},
 * no list of readable sites, and a message about transactions for a caller whose mistake was a
 * misspelled site name. The two failure modes are opposite and both silent: the annotation missing
 * (this test fails) or the annotation present on the wrong bean (the lint fails).</p>
 *
 * @author Alexandre Oliveira
 */
// Its own database: `find` with no site describes the whole instance, and the assertion below is
// about a site *not* existing — which on the shared datasource is a claim about what every other
// test happened to create (SH315).
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-unknown-site;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentUnknownSiteRefusalTest {

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;

    @Test
    @DisplayName("SH345: find with an unknown site is the teaching 404, not a rollback failure")
    void findWithAnUnknownSiteTeaches() {
        assertThatThrownBy(() -> shAgentReadService.find("no-such-site", null, null, null, null, false))
                .as("""
                        UnexpectedRollbackException here means ShAgentSiteAccess marked the \
                        transaction rollback-only on its way out, so find's own noRollbackFor could \
                        not commit and the refusal was replaced by a transaction fault — SH171's \
                        failure mode, one bean along.""")
                .isInstanceOf(ShTeachingException.class)
                .satisfies(failure -> {
                    var problem = ((ShTeachingException) failure).problem().toProblemDetail();
                    assertThat(problem.getStatus())
                            .as("404: the site named does not exist, which is the caller's mistake "
                                    + "and a fixable one")
                            .isEqualTo(404);
                    assertThat(problem.getProperties().get("fix"))
                            .as("P6 — and this is the field a rollback failure destroys")
                            .isNotNull();
                });
    }

    @Test
    @DisplayName("SH345: verify with an unknown site teaches too — same bean, other caller")
    void verifyWithAnUnknownSiteTeaches() {
        // The same lookup from a second entry point, because the defect was never in `find`: it was
        // in the shared site resolver, so every caller of it had the same hole.
        assertThatThrownBy(() -> shAgentVerifyService.verify("no-such-site", null, null))
                .isInstanceOf(ShTeachingException.class);
    }

    /**
     * SH329 — the bootstrap case, which is the first thing any agent does on a new instance.
     *
     * <p>This class already boots an instance with no sites, which is the fixture the case needs
     * and the reason the test lives here rather than beside the other batch tests.</p>
     */
    @Test
    @DisplayName("SH329: a dry run plans into the site the same batch creates")
    void aDryRunPlansIntoTheSiteItCreates() {
        ShAgentBatch batch = new ShAgentBatch(true, List.of(
                new ShAgentBatch.Op("site.upsert", "site:SH329Demo", null, null, null, null),
                new ShAgentBatch.Op("folder.upsert", "folder:SH329Demo/pages", null, null, null,
                        null)));

        ShAgentChangeSet plan = shAgentBatchService.run(batch, false, "admin");

        assertThat(plan.results())
                .as("""
                        ops[1] used to fail with `No readable site 'SH329Demo'`: the session planned \
                        pending folders and not a pending site, so "look before you write" was \
                        broken for precisely the run that has nothing to look at yet.""")
                .hasSize(2);
        assertThat(plan.ops()).isEqualTo(2);
    }

    /**
     * SH329's second defect, and the worse one: the refusal instructed the caller to pass a
     * parameter this endpoint does not have.
     */
    @Test
    @DisplayName("SH329: a write op naming an absent site is not told to pass ?site=")
    void aWriteRefusalDoesNotNameAReadParameter() {
        ShAgentBatch batch = new ShAgentBatch(true, List.of(
                new ShAgentBatch.Op("folder.upsert", "folder:SH329Absent/pages", null, null, null,
                        null)));

        assertThatThrownBy(() -> shAgentBatchService.run(batch, false, "admin"))
                .isInstanceOf(ShTeachingException.class)
                .satisfies(failure -> {
                    var problem = ((ShTeachingException) failure).problem().toProblemDetail();
                    assertThat(problem.getProperties().get("fix").toString())
                            .as("""
                                    P6: an instruction to do something that does not exist is worse \
                                    than no instruction — POST /api/v2/agent/batch has no ?site=.""")
                            .doesNotContain("?site=")
                            .contains("site.upsert");
                    assertThat(problem.getProperties().get("example").toString())
                            .as("the example is the op that fixes it, not a query string")
                            .doesNotContain("?site=");
                });
    }
}
