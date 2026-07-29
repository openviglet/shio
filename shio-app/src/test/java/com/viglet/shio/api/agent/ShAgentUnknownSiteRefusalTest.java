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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
