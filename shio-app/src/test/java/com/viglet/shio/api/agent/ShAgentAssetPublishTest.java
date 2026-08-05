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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH462 — the op that could not name what it had to act on.
 *
 * <h2>The measurement came first, and it corrected the task</h2>
 *
 * <p>The filing said an uploaded asset <em>lands in DRAFT</em> and nothing can take it out. The
 * first assertion below is that claim, executed: {@code ShStaticFileService.upload} writes one row
 * and it is {@code PUBLISHED} (SH38), so a replica's assets are live the moment they are uploaded
 * and {@code /agent/find} reports them so. That half of the defect does not exist, and the fix it
 * suggested — publish a File on upload — is already how it works.</p>
 *
 * <h2>What was real</h2>
 *
 * <p>A {@code File} post is never given a friendly URL, so {@code /agent/find} reports a site's
 * assets as {@code id:<uuid>} — correctly, because it is the only address they have. Every write
 * then refused that form and told the caller to "re-address the op with the `post:` scheme", which
 * for this object <b>cannot be done</b>. And a batch is atomic, so one such op failed the whole run
 * at {@code ops[0]}: an agent that tried to be thorough about its assets published nothing at
 * all, including the pages. A surface that emits an address its own writes refuse is the defect,
 * and the whole-batch failure is what made it expensive rather than merely untidy.</p>
 *
 * <h2>The exemption is the absence of an address, not the type</h2>
 *
 * <p>SH79 keeps a UUID out of a write because a UUID is the currency this surface exists to remove
 * and because {@code folder:} and {@code post:} are namespaces a guess could confuse. Neither holds
 * for an object with no URL — there is no address to prefer and no second namespace. So the rule is
 * written as exactly that condition, asked of the post rather than of a list of type names somebody
 * has to keep in step, and the last test is the other side of it.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-asset-publish;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentAssetPublishTest {

    private static final String SITE = "Sh462Site";
    private static final Path FILE_SOURCE_DIR = tempRoot();

    private static Path tempRoot() {
        try {
            return Files.createTempDirectory("sh462-file-source");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void fileSourcePath(DynamicPropertyRegistry registry) {
        registry.add("shio.file-source.path", () -> FILE_SOURCE_DIR.toAbsolutePath().toString());
    }

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String assetId;
    private String pageId;

    @BeforeAll
    void seedASiteWithAnAssetAndAPage() {
        String rootId = new TransactionTemplate(transactionManager).execute(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(SITE);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName(SITE);
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("Sh462Article");
            type.setTitle("Sh462 Article");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostUnified draft = new ShPostUnified();
            pageId = "sh462-page";
            draft.setId(pageId);
            draft.setState(ShPostState.DRAFT);
            draft.setShSite(site);
            draft.setShFolder(root);
            draft.setShPostType(type);
            draft.setTitle("Home");
            draft.setFurl("/home");
            draft.setLocale("en");
            draft.setOwner("admin");
            draft.setDate(when);
            draft.setData("{\"title\":\"Home\"}");
            entityManager.persist(draft);
            return root.getId();
        });

        assetId = shStaticFileService.upload(new MockMultipartFile(
                "file", "logo.png", "image/png",
                "PNG-BYTES".getBytes(StandardCharsets.UTF_8)), rootId, "admin").getId();
    }

    @Test
    @DisplayName("SH462: an uploaded asset is PUBLISHED and has no URL — the two facts the op met")
    void anUploadedAssetIsPublishedAndUnaddressable() {
        assertThat(shPostUnifiedRepository.findByIdAndState(assetId, ShPostState.PUBLISHED))
                .as("the filing said an uploaded asset lands in DRAFT; `upload` writes one row and "
                        + "it is PUBLISHED (SH38), so a replica's assets are live on upload")
                .isPresent();
        assertThat(shPostUnifiedRepository.findByIdAndState(assetId, ShPostState.DRAFT))
                .as("and there is no second row, so there was never a draft to take out")
                .isEmpty();
        assertThat(shPostUnifiedRepository.findByIdAndState(assetId, ShPostState.PUBLISHED)
                .orElseThrow().getFurl())
                .as("and this is the real half: no friendly URL, so `id:` is the only address it "
                        + "has and the only one /agent/find can report")
                .isNullOrEmpty();
    }

    @Test
    @DisplayName("SH462: post.publish takes the id: the surface itself hands out, and says noop")
    void publishingAnAssetByIdIsANoopRatherThanAWholeBatchFailure() {
        var changes = shAgentBatchService.run(
                new ShAgentBatch(false, List.of(
                        publish("id:" + assetId),
                        publish("post:" + SITE + "/home"))),
                false, "ut-sh462");

        assertThat(changes.results())
                .as("both ops ran. Before this the first threw wrong-address-kind and the batch "
                        + "failed whole at ops[0], so the page behind it was never published — "
                        + "atomicity turning one unpublishable asset into a replication that "
                        + "published nothing is what made this expensive")
                .hasSize(2);
        assertThat(changes.results().getFirst().toString())
                .as("and the asset's own answer is honest: already live, nothing done")
                .contains("noop");
        assertThat(shPostUnifiedRepository.findByIdAndState(pageId, ShPostState.PUBLISHED))
                .as("the page behind it is published, which is the outcome the run was for")
                .isPresent();
    }

    @Test
    @DisplayName("SH462: withdrawing an asset would delete it, so it is refused and says so")
    void withdrawingAnAssetWithNoDraftIsRefusedRatherThanDestructive() {
        // Found by making the op reachable, which is the argument for shipping the address and the
        // guard together. `unpublish` hard-deletes the published row and leaves the draft — correct
        // for the post it was written for, because publishing leaves a draft behind. An uploaded
        // File has no draft, so the same call destroys the object outright and not even into the
        // SH55 trash. The first version of this test asserted the round trip and DID destroy it:
        // the asset vanished, and every later test in this class failed on an id that no longer
        // resolved. That is the failure a curator would have had, one asset at a time.
        assertThatThrownBy(() -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new ShAgentBatch.Op(ShAgentBatchService.POST_UNPUBLISH, "id:" + assetId,
                        null, null, null, null, null))), false, "ut-sh462"))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> {
                    var problem = ((ShAgentBatchService.BatchOpException) failure)
                            .problem().toProblemDetail();
                    assertThat(problem.getTitle()).contains("would delete");
                    assertThat(problem.getDetail())
                            .as("it has to say why there is nothing to fall back to, or the refusal "
                                    + "reads as a bug in an op the caller was told to use")
                            .contains("no draft");
                    assertThat(problem.getProperties().get("fix").toString())
                            .as("and name the op that does mean it, which is the reversible one")
                            .contains("post.delete");
                });

        assertThat(shPostUnifiedRepository.findByIdAndState(assetId, ShPostState.PUBLISHED))
                .as("and it is still there — a refusal that had already run the delete would be the "
                        + "same defect with a message on top")
                .isPresent();
    }

    @Test
    @DisplayName("SH462: a post that HAS a URL is still refused, and told which address to send")
    void aPostWithAUrlIsRefusedWithItsOwnAddress() {
        // SH79 intact where it means something. The refusal is its own class because this is the
        // one place on the surface that can hand back the exact address the caller should have
        // sent, rather than the scheme it should have used.
        assertThatThrownBy(() -> shAgentBatchService.run(
                new ShAgentBatch(true, List.of(publish("id:" + pageId))), false, "ut-sh462"))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(((ShAgentBatchService.BatchOpException) failure)
                        .problem().toProblemDetail().getProperties())
                        .extractingByKey("example").asString()
                        .as("not `post:Blog/hello-world` — this post's own address")
                        .isEqualTo("post:" + SITE + "/home"));
    }

    private static ShAgentBatch.Op publish(String address) {
        return new ShAgentBatch.Op(ShAgentBatchService.POST_PUBLISH, address,
                null, null, null, null, null);
    }
}
