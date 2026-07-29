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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.ShAgentOps.OpSpec;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * SH332 — the published op vocabulary is the one the service enforces.
 *
 * <h2>Why the guard is behavioural</h2>
 *
 * <p>A source scan was the first attempt and it is <b>not faithful</b>: a handler reads
 * {@code op.address()} through a helper rather than directly, so scanning each
 * {@code private Change …} block finds {@code data} on {@code site.upsert} and nothing at all on
 * five of the nine. Measured before choosing, and it is the reason this test drives the planner
 * instead of reading it.</p>
 *
 * <p>So the contract is checked the way a caller experiences it: omit a key the vocabulary calls
 * required, and the <b>plan must refuse</b>. A dry run throughout — the claim is about what the
 * service demands, not about what it writes, and a benchmark that mutated content to find out would
 * be measuring two things.</p>
 *
 * @author Alexandre Oliveira
 */
// Its own database (SH315): a plan over an address resolves against whatever sites exist, so on the
// shared datasource "this op refuses without `address`" could pass for the wrong reason.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-ops-contract;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentOpsContractTest {

    /**
     * A site whose addresses resolve.
     *
     * <p>Without it the probe is unreachable rather than merely weak: the refusals are ordered, so a
     * {@code folder.move} against a site that does not exist answers <em>unknown site</em> and never
     * gets as far as noticing that {@code to} is missing. The first version of this test used a
     * fictional site and reported that every required key was enforced — the third appearance of the
     * same trap in this block (SH340's fixture, SH324's fixture, this), which is itself the finding:
     * a test that asserts a refusal must make every earlier refusal unreachable.</p>
     */
    private static final String SITE = "OpsContract";

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @org.junit.jupiter.api.BeforeAll
    void seedTheSubjects() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    var site = new com.viglet.shio.persistence.model.site.ShSite();
                    site.setName(SITE);
                    site.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                    site.setOwner("admin");
                    entityManager.persist(site);

                    var root = new com.viglet.shio.persistence.model.folder.ShFolder();
                    root.setName("root");
                    root.setRootFolder((byte) 1);
                    root.setShSite(site);
                    root.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                    root.setOwner("admin");
                    entityManager.persist(root);

                    var section = new com.viglet.shio.persistence.model.folder.ShFolder();
                    section.setName("x");
                    section.setRootFolder((byte) 0);
                    section.setParentFolder(root);
                    section.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                    section.setOwner("admin");
                    entityManager.persist(section);

                    var type = new com.viglet.shio.persistence.model.post.type.ShPostType();
                    type.setName("Article");
                    type.setTitle("Article");
                    type.setSystem((byte) 0);
                    type.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                    type.setOwner("admin");
                    entityManager.persist(type);

                    for (var state : com.viglet.shio.persistence.model.post.unified.ShPostState.values()) {
                        var post = new com.viglet.shio.persistence.model.post.unified.ShPostUnified();
                        post.setId("ops-contract-x");
                        post.setState(state);
                        post.setShSite(site);
                        post.setShFolder(root);
                        post.setShPostType(type);
                        post.setTitle("X");
                        post.setFurl("/x");
                        post.setLocale("en");
                        post.setOwner("admin");
                        post.setDate(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                        post.setData("{\"title\":\"X\"}");
                        entityManager.persist(post);
                    }
                });
    }

    @Test
    @DisplayName("SH332: every op in OPS has a spec, and every spec names a real op")
    void theTwoListsAgree() {
        // The assertion that matters most, and the cheapest: an op added to the vocabulary without
        // being described here is exactly how this drifted into being prose in one tool description.
        assertThat(ShAgentOps.specified())
                .as("""
                        a new op with no spec is an op an agent has to guess the keys of, which is \
                        what two of SH331's four wrong calls were. Describe it in ShAgentOps.""")
                .containsExactlyInAnyOrderElementsOf(ShAgentBatchService.OPS);
        assertThat(ShAgentOps.all()).extracting(OpSpec::op)
                .as("published in the order a teaching error lists them, so a caller reading both "
                        + "sees one vocabulary")
                .containsExactlyElementsOf(ShAgentBatchService.OPS);
    }

    @Test
    @DisplayName("SH332: omitting a required key is refused by the planner")
    void aRequiredKeyIsActuallyRequired() {
        List<String> unenforced = new ArrayList<>();
        for (OpSpec spec : ShAgentOps.all()) {
            for (String key : spec.required()) {
                if (!refusesWithout(spec.op(), key)) {
                    unenforced.add(spec.op() + " accepted a plan with no `" + key + "`");
                }
            }
        }

        assertThat(unenforced)
                .as("""
                        a key the vocabulary calls required and the service does not is worse than \
                        no vocabulary: the caller supplies it, believes it matters, and the day it \
                        stops being sent nothing says so. The list is published — it has to be the \
                        list that is enforced.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH332: post.move's either/or is real — neither key alone is optional")
    void atLeastOneOfIsEnforced() {
        // SH175's rule made checkable: `to`|`folder` cannot be flattened into "optional" without
        // making a plan that specifies neither look valid, and cannot be flattened into "required"
        // without making a correct call look wrong.
        OpSpec move = ShAgentOps.all().stream()
                .filter(spec -> spec.op().equals(ShAgentBatchService.POST_MOVE))
                .findFirst().orElseThrow();
        assertThat(move.atLeastOneOf()).containsExactly("to", "folder");

        assertThat(refuses(new ShAgentBatch.Op(ShAgentBatchService.POST_MOVE,
                "post:" + SITE + "/x", null, null, null, null)))
                .as("a move naming neither is a teaching 400, not a noop (SH150's rule)")
                .isTrue();
    }

    @Test
    @DisplayName("SH332: the rendering states its own grammar and every op")
    void theRenderingIsSelfDescribing() {
        String terse = ShAgentOps.terse();

        assertThat(terse)
                .as("a legend the reader has to infer is the problem this resource exists to solve")
                .contains("grammar:")
                .contains("required when creating")
                .contains("at least one");
        assertThat(terse).contains("post.upsert address type? folder? [data]");
        assertThat(terse).contains("post.move address to|folder");
        ShAgentBatchService.OPS.forEach(op -> assertThat(terse).contains(op));
    }

    /**
     * Does a plan with this key omitted refuse <b>because of that key</b>?
     *
     * <p>The qualifier is load-bearing and the first version of this test did not have it. Every
     * address here names a site that does not exist, so <em>every</em> op refuses whatever is
     * omitted — the test passed while proving nothing, which is the same fixture trap SH340's first
     * run fell into one iteration earlier. So the refusal has to be {@code missing-op-field} and it
     * has to name the key.</p>
     */
    private boolean refusesWithout(String op, String omitted) {
        String address = switch (op.substring(0, op.indexOf('.'))) {
            case "site" -> "site:" + SITE;
            case "folder" -> "folder:" + SITE + "/x";
            default -> "post:" + SITE + "/x";
        };
        return refusesFor(new ShAgentBatch.Op(op,
                "address".equals(omitted) ? null : address,
                "type".equals(omitted) ? null : "Article",
                "folder".equals(omitted) ? null : "/",
                "to".equals(omitted) ? null : address + "-moved",
                null), omitted);
    }

    private boolean refusesFor(ShAgentBatch.Op op, String key) {
        try {
            shAgentBatchService.run(new ShAgentBatch(true, List.of(op)), false, "ut-sh332");
            return false;
        } catch (ShTeachingException e) {
            var problem = e.problem().toProblemDetail();
            String said = String.valueOf(problem.getType()) + problem.getTitle()
                    + problem.getDetail();
            return said.contains("missing-op-field") && said.contains(key);
        }
    }

    private boolean refuses(ShAgentBatch.Op op) {
        try {
            shAgentBatchService.run(new ShAgentBatch(true, List.of(op)), false, "ut-sh332");
            return false;
        } catch (ShTeachingException e) {
            return true;
        }
    }
}
