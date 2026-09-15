/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.agent.mcp.tools.ShFindTool;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.release.ShReleaseItemRepository;
import com.viglet.shio.persistence.repository.release.ShReleaseRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH993 — a release is a named set of posts an agent can plan, fill and read back.
 *
 * <p>One batch call was atomic and nothing persisted a set across calls, so a launch had no unit.
 * This slice is the unit: {@code release.upsert}, {@code release.add} and {@code release.remove} over
 * {@code release:<site>/<name>}, and {@code find?site=&release=} reading back exactly what it holds.</p>
 *
 * @author Alexandre Oliveira
 */
// ShScheduleRequesterTest's context (SH423): every assertion reads rows this class seeds under names
// nobody else uses.
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShReleaseOpsTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh993-" + RUN;
    private static final String OTHER = "sh993-other-" + RUN;

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShFindTool shFindTool;
    @Autowired
    private ShReleaseRepository shReleaseRepository;
    @Autowired
    private ShReleaseItemRepository shReleaseItemRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> posts = new HashSet<>();

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType type = new ShPostType();
            type.setName("PT-SH993-" + RUN);
            type.setTitle("SH993 Page");
            type.setSystem((byte) 0);
            type.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            type.setOwner("admin");
            entityManager.persist(type);
            for (String name : List.of(SITE, OTHER)) {
                ShSite site = new ShSite();
                site.setName(name);
                site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                site.setOwner("admin");
                entityManager.persist(site);
                ShFolder root = new ShFolder();
                root.setName("root");
                root.setRootFolder((byte) 1);
                root.setShSite(site);
                root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                root.setOwner("admin");
                entityManager.persist(root);
                for (String url : List.of("/launch", "/pricing", "/faq")) {
                    ShPostUnified draft = new ShPostUnified();
                    draft.setId(name + url.replace('/', '-'));
                    draft.setState(ShPostState.DRAFT);
                    draft.setShSite(site);
                    draft.setShFolder(root);
                    draft.setShPostType(type);
                    draft.setTitle(url);
                    draft.setFurl(url);
                    draft.setLocale("en");
                    draft.setOwner("admin");
                    draft.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                    draft.setData("{\"title\":\"" + url + "\"}");
                    entityManager.persist(draft);
                    posts.add(draft.getId());
                }
            }
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            shReleaseRepository.findAll().stream()
                    .filter(release -> release.getName().endsWith(RUN))
                    .forEach(release -> {
                        shReleaseItemRepository.deleteAll(
                                shReleaseItemRepository.findByReleaseIdOrderByAddedAtAsc(release.getId()));
                        shReleaseRepository.delete(release);
                    });
            posts.forEach(id -> shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete));
        });
    }

    private ShAgentBatch.Op op(String op, String name, String... urls) {
        ObjectNode data = null;
        if (urls.length > 0) {
            data = objectMapper.createObjectNode();
            var list = data.putArray("posts");
            for (String url : urls) {
                list.add(url);
            }
        }
        return new ShAgentBatch.Op(op, "release:" + SITE + "/" + name, null, null, null, data);
    }

    private ShAgentChangeSet run(boolean dryRun, ShAgentBatch.Op... ops) {
        return shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(ops)), false, "ut-sh993");
    }

    private ShAgentFound held(String name) {
        return shAgentReadService.find(SITE, null, null, null, null, false, null, false, null, null, name);
    }

    @Test
    @DisplayName("SH993: a release is planned once, and naming it again is a noop")
    void upsertIsIdempotent() {
        String name = "spring-" + RUN;
        assertThat(run(false, op(ShAgentBatchService.RELEASE_UPSERT, name)).results().get(0).action())
                .isEqualTo("create");
        assertThat(run(false, op(ShAgentBatchService.RELEASE_UPSERT, name)).results().get(0).action())
                .as("SH993: a projected release is re-pushed unchanged, so the second call must not fail")
                .isEqualTo("noop");
        assertThat(held(name).release().state()).isEqualTo("PLANNED");
    }

    @Test
    @DisplayName("SH993: find?release= lists exactly the posts a release holds, with the release on the envelope")
    void findReadsBackWhatItHolds() {
        String name = "launch-" + RUN;
        ShAgentChangeSet added = run(false,
                op(ShAgentBatchService.RELEASE_UPSERT, name),
                op(ShAgentBatchService.RELEASE_ADD, name, "post:" + SITE + "/launch", "/pricing"));

        assertThat(added.results().get(1).detail()).startsWith("added 2: ");
        ShAgentFound found = held(name);
        assertThat(found.results()).extracting(ShAgentFound.Row::address)
                .as("SH993: the listing is the release, not the site")
                .containsExactly("post:" + SITE + "/launch", "post:" + SITE + "/pricing");
        assertThat(found.release().address()).isEqualTo("release:" + SITE + "/" + name);
        assertThat(found.release().posts()).isEqualTo(2);

        assertThat(run(false, op(ShAgentBatchService.RELEASE_ADD, name, "/launch")).results().get(0).detail())
                .isEqualTo("already holds it");
        run(false, op(ShAgentBatchService.RELEASE_REMOVE, name, "/pricing", "/faq"));
        assertThat(held(name).results()).extracting(ShAgentFound.Row::address)
                .containsExactly("post:" + SITE + "/launch");
    }

    @Test
    @DisplayName("SH993: a dry run plans a release and its posts in one batch, and writes neither")
    void aDryRunPlansWithoutWriting() {
        String name = "planned-" + RUN;
        ShAgentChangeSet plan = run(true,
                op(ShAgentBatchService.RELEASE_UPSERT, name),
                op(ShAgentBatchService.RELEASE_ADD, name, "/launch", "/faq"));

        assertThat(plan.results()).extracting(ShAgentChangeSet.Change::action)
                .containsExactly("create", "update");
        assertThat(shReleaseRepository.findAll()).noneMatch(release -> release.getName().equals(name));
    }

    @Test
    @DisplayName("SH993: a release holds posts of its own site only, and names what is wrong otherwise")
    void refusals() {
        String name = "mixed-" + RUN;
        run(false, op(ShAgentBatchService.RELEASE_UPSERT, name));

        assertThatThrownBy(() -> run(true, op(ShAgentBatchService.RELEASE_ADD, name, "post:" + OTHER + "/faq")))
                .satisfies(failure -> assertThat(slug(failure)).endsWith("release-site-mismatch"));
        assertThatThrownBy(() -> run(true, op(ShAgentBatchService.RELEASE_ADD, "nope-" + RUN, "/faq")))
                .satisfies(failure -> assertThat(slug(failure)).endsWith("unknown-release"));
        assertThatThrownBy(() -> run(true, new ShAgentBatch.Op(ShAgentBatchService.RELEASE_UPSERT,
                "release:" + SITE + "/bad name", null, null, null, null)))
                .satisfies(failure -> assertThat(slug(failure)).endsWith("bad-release-name"));
        ObjectNode siteless = objectMapper.createObjectNode();
        siteless.put("release", name);
        assertThatThrownBy(() -> shFindTool.call(siteless))
                .as("SH993: a release is named within its site, so a listing across sites has none to "
                        + "mean. Asked through the tool because a `find(null, …)` in a test on the "
                        + "shared context is what ShBenchmarkIsolationLintTest exists to refuse")
                .satisfies(failure -> assertThat(slug(failure)).endsWith("release-needs-site"));
    }

    @Test
    @DisplayName("SH993: shio_find release= reaches the same listing over MCP")
    void theFindToolReadsARelease() {
        String name = "mcp-" + RUN;
        run(false, op(ShAgentBatchService.RELEASE_UPSERT, name),
                op(ShAgentBatchService.RELEASE_ADD, name, "/faq"));
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("site", SITE);
        arguments.put("release", name);

        String answer = shFindTool.call(arguments);

        assertThat(answer).contains("release release:" + SITE + "/" + name + " state=PLANNED posts=1");
        assertThat(answer).contains("post:" + SITE + "/faq");
    }

    private static String slug(Throwable failure) {
        return String.valueOf(((ShTeachingException) failure).problem().toProblemDetail().getType());
    }
}
