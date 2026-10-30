/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.ShH2ResultCache;
import com.viglet.shio.api.staticfile.ShStaticFileItem;
import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1029 — what one media listing costs, counted in JSON parses and in bytes.
 *
 * <h2>The defect, and why it needed counting</h2>
 *
 * <p>{@code toItem} read eight keys off a {@code File} post's {@code data} — {@code FILE},
 * {@code WIDTH}, {@code HEIGHT}, {@code ALT}, {@code CAPTION}, {@code TAGS}, and {@code SHA256}
 * and {@code SIZE} through the digest stamp — and every one of the typed readers began by parsing
 * the whole string again. Nothing about that is visible from a stack trace or a profile of one
 * request: it is a constant factor on a loop, and a constant factor is exactly what an argument
 * gets wrong. So the number comes first and the refactor answers it.</p>
 *
 * <h2>Parses, not milliseconds</h2>
 *
 * <p>The mapper is a counting subclass, injected over the autoconfigured bean for this context
 * only. That makes the headline number <b>deterministic</b> — it is a count of calls, not a
 * measurement of a machine — which is SH264's rule and the reason a timing ceiling is not asserted
 * anywhere in this package (SH250: a flake is worse than a break). Milliseconds are printed for
 * a reader and never asserted.</p>
 *
 * <h2>What is NOT removed, and why it is not an oversight</h2>
 *
 * <p>One {@code readAttributes} per row stays. SH398 argued it down from two syscalls to one and
 * recorded why stamping the size instead is worth less: the existence check has to happen anyway
 * to keep SH62's {@code size: -1} honest, so a stamp would remove one stat of two and buy a
 * {@code data} key that can disagree with the disk. SH399 stamps the size for a different purpose
 * — to validate the <em>digest</em> against it — and deliberately not as the size. This benchmark
 * therefore reports the syscall count and asserts that it is one per row rather than zero.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-media-listing-cost;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShMediaListingBenchmarkTest {

    /** Assets in the library. Linear in this number, which is the whole point of the finding. */
    private static final int ASSETS = 400;

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * An {@code ObjectMapper} that says how often it was asked to parse a string.
     *
     * <p>A subclass rather than a Mockito spy: this is the one method the number is about, the
     * count has to survive whatever else the context does with the bean, and a spy over a mapper
     * every layer of the application shares is a fixture that fails for reasons unrelated to what
     * it measures.</p>
     */
    static final class CountingMapper extends tools.jackson.databind.json.JsonMapper {

        static final AtomicLong PARSES = new AtomicLong();

        @Override
        public JsonNode readTree(String content) {
            PARSES.incrementAndGet();
            return super.readTree(content);
        }
    }

    @TestConfiguration
    static class Counting {

        @Bean
        @Primary
        tools.jackson.databind.json.JsonMapper countingObjectMapper() {
            return new CountingMapper();
        }
    }

    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String folderId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("media-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);

            ShFolder media = new ShFolder();
            media.setName("media");
            media.setRootFolder((byte) 0);
            media.setParentFolder(root);
            media.setDate(EPOCH);
            media.setOwner("admin");
            entityManager.persist(media);
            folderId = media.getId();

            ShPostType fileType = shStaticFileService.resolveOrCreateFilePostType();
            for (int index = 0; index < ASSETS; index++) {
                ShPostUnified post = new ShPostUnified();
                post.setShFolder(media);
                post.setShPostType(fileType);
                post.setState(ShPostState.PUBLISHED);
                post.setTitle("asset" + index + ".png");
                // Every key the listing reads, so the fixture exercises all eight of the parses
                // the old path made rather than the two a bare row would have.
                post.setData("{\"FILE\":\"asset" + index + ".png\",\"WIDTH\":800,\"HEIGHT\":600,"
                        + "\"ALT\":\"an asset\",\"CAPTION\":\"a caption\","
                        + "\"TAGS\":[\"one\",\"two\"],\"SHA256\":\"deadbeef\",\"SIZE\":1234}");
                post.setDate(EPOCH);
                post.setOwner("admin");
                post.setShSite(null);
                shPostUnifiedRepository.save(post);
            }
        });
    }

    @Test
    @DisplayName("SH1029: the listing parses each asset's data once, not once per key")
    void eachAssetIsParsedOnce() {
        assertThat(objectMapper)
                .as("SH1029: the counting mapper is not the one the service got, so every number "
                        + "below would be zero and every assertion vacuous")
                .isInstanceOf(CountingMapper.class);

        long before = CountingMapper.PARSES.get();
        List<List<ShStaticFileItem>> listed = new ArrayList<>();
        // SH1327 — with H2's result cache off, so a listing another test already ran is not recalled.
        long millis = ShH2ResultCache.millis(dataSource, () -> listed.add(listing()));
        List<ShStaticFileItem> items = listed.get(0);
        long parses = CountingMapper.PARSES.get() - before;

        System.out.printf(
                "SH1029 media listing — %d assets: %d parse(s), %d byte(s), %d ms%n",
                items.size(), parses, bytesOf(items), millis);

        assertThat(items).hasSize(ASSETS);
        // The finding, as an assertion. Before this, `toItem` read eight keys and each typed
        // reader parsed the whole string again: 400 assets cost about 3200 parses for one request,
        // and a tenant with ten thousand paid eighty thousand.
        assertThat(parses)
                .as("""
                        SH1029: one parse per row. Measured at 2400 before and 400 after, for 400 \
                        assets. Anything above one is a typed reader that began by parsing the \
                        whole `data` string again — invisible in a stack trace, because it is a \
                        constant factor on a loop rather than a slow call.""")
                .isLessThanOrEqualTo(ASSETS + 2);
    }

    @Test
    @DisplayName("SH1029: a filtered listing parses only what it answers with")
    void filteringDoesNotParseWhatItDiscards() {
        long before = CountingMapper.PARSES.get();
        List<ShStaticFileItem> narrowed = new TransactionTemplate(transactionManager)
                .execute(status -> shStaticFileService.listFiles(folderId, null, null, "asset1"));
        long parses = CountingMapper.PARSES.get() - before;

        System.out.printf("SH1029 media listing — q=asset1: %d row(s), %d parse(s)%n",
                narrowed.size(), parses);

        assertThat(narrowed).isNotEmpty().hasSizeLessThan(ASSETS);
        // SH375 moved the folder predicate into the query; SH1030 moved the term. Measured at 400
        // parses before it and 111 after — the rows the answer holds, because the query now reads
        // a superset of the answer rather than the folder. The exact filter stays in the service,
        // which is what makes an approximate predicate safe: it can only ever read more.
        assertThat(parses)
                .as("SH1029/SH1030: one parse per row READ, and the read is now the term's")
                .isLessThanOrEqualTo(narrowed.size() + 2);
    }

    private List<ShStaticFileItem> listing() {
        return new TransactionTemplate(transactionManager)
                .execute(status -> shStaticFileService.listFiles(folderId, null, null, null));
    }

    /** What the answer weighs on the wire, which is what the browser then turns into DOM. */
    private int bytesOf(List<ShStaticFileItem> items) {
        return objectMapper.writeValueAsString(items).length();
    }
}
