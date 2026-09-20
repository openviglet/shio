/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1048 — a validator on the console's reads, and the bytes those reads cost.
 *
 * <h2>Two claims, one fixture</h2>
 *
 * <p>The first is the one the task is named for: an unchanged console read answers {@code 304} with
 * no body. SH3 gave the delivery API that and SH116 gave the agent surface its own; everything the
 * console calls answered a full {@code 200} with no validator, so a curator walking a tree and
 * coming back paid for every byte again — and so did the CLI and MCP, which call the same REST.</p>
 *
 * <p>The second is why the measurement lands beside the fix rather than after it. A 304 saves
 * whatever the body costs, and nothing in this repository knew what a console body costs. P3 makes
 * a token regression a build failure for the agent's half; this is the same rule on the half a
 * <em>person</em> waits for, in the unit a person pays — bytes, against a fixed fixture, so a
 * breach means the shape got bigger rather than the database did.</p>
 *
 * <h2>Why its own database</h2>
 *
 * <p>The same reason {@code ShAgentResponseBudgetTest} gives: several of these reads describe the
 * whole instance, so on the shared test datasource their size would depend on what every other
 * class created — a gate that fails on test order rather than on a shape change.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-console-budget;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShConsoleCacheFilterTest {

    private static final String BUDGETS = "/console-response-budgets.properties";
    private static final String PREFIX = "console.bytes.";

    /** These payloads are small, so a fixture name of a different length moves them a percent. */
    private static final double REQUIRED_HEADROOM = 0.10;

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, Integer> measured = new LinkedHashMap<>();
    private final List<String> breaches = new ArrayList<>();
    private Properties budgets;
    private MockMvc mockMvc;
    private Principal principal;
    private String rootFolderId;
    private String postId;

    @BeforeAll
    void setup() throws Exception {
        // The real filter, in the chain, so the 304 below is the one production produces.
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new ShConsoleCacheFilter())
                .build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
        budgets = new Properties();
        try (var in = getClass().getResourceAsStream(BUDGETS)) {
            budgets.load(in);
        }
        seed();
        measureEverything();
    }

    private void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName("console-" + suffix);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);
            rootFolderId = root.getId();

            ShWidget widget = new ShWidget();
            widget.setName("Text");
            entityManager.persist(widget);

            ShPostType type = new ShPostType();
            type.setName("ConsolePage" + suffix);
            type.setTitle("Console page");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setRequired((byte) 1);
            attr.setIsTitle((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(type);
            entityManager.persist(attr);

            for (int i = 1; i <= 3; i++) {
                ShPostUnified post = new ShPostUnified();
                post.setId(UUID.randomUUID().toString());
                post.setState(ShPostState.DRAFT);
                post.setShSite(site);
                post.setShFolder(root);
                post.setShPostType(type);
                post.setTitle("Console page " + i);
                post.setFurl("/page-" + i);
                post.setDate(when);
                post.setData("{\"headline\":\"Console page " + i + "\"}");
                entityManager.persist(post);
                if (postId == null) {
                    postId = post.getId();
                }
            }
        });
    }

    /**
     * Every console read, measured once.
     *
     * <p>One pass rather than one per test, for the reason {@code ShAgentResponseBudgetTest} states:
     * JUnit does not order test methods, and a headroom check that ran first would read an empty
     * map and pass — the report-zero-forever shape.</p>
     */
    private void measureEverything() throws Exception {
        measure("site.list", get("/api/v2/site"));
        measure("post-type.list", get("/api/v2/post-type"));
        measure("widget.list", get("/api/v2/widget"));
        measure("object.list", get("/api/v2/object/" + rootFolderId + "/list"));
        // `state` explicitly: the endpoint defaults to PUBLISHED and this fixture is a draft, so
        // without it the read is a 404 and the measurement is of nothing.
        measure("post-unified.get", get("/api/v2/post-unified/" + postId).param("state", "DRAFT"));
        measure("folder.get", get("/api/v2/folder/" + rootFolderId));
        measure("history.recent", get("/api/v2/history").param("limit", "8"));
        measure("staticfile.list", get("/api/v2/staticfile"));
    }

    private void measure(String name, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.principal(principal)).andReturn();
        int status = result.getResponse().getStatus();
        if (status != 200) {
            breaches.add(name + " answered " + status + ", so it was never measured");
            return;
        }
        int bytes = result.getResponse().getContentAsByteArray().length;
        measured.put(name, bytes);
        String ceiling = budgets.getProperty(PREFIX + name);
        if (ceiling == null) {
            breaches.add(name + " = " + bytes + " bytes and no ceiling declares it");
        } else if (bytes > Integer.parseInt(ceiling.trim())) {
            breaches.add(name + " = " + bytes + " bytes, over " + ceiling.trim());
        }
    }

    private String report() {
        StringBuilder out = new StringBuilder();
        measured.forEach((name, bytes) -> out.append(String.format(java.util.Locale.ROOT,
                "  %-24s %6d bytes (ceiling %s)%n", name, bytes, budgets.getProperty(PREFIX + name))));
        return out.toString();
    }

    @Test
    void everyConsoleReadStaysWithinItsBudget() {
        // Printed on every run, green or red, which is half of what this is for: a number nobody
        // sees is a number nobody argues with, and a budget only ever read at the moment it fails
        // is one whose ceilings drift upward a breach at a time.
        System.out.printf("SH1048 — console read sizes:%n%s", report());

        assertThat(measured)
                .as("nothing was measured, so every assertion in this class agrees with anything")
                .isNotEmpty();

        assertThat(breaches)
                .as("""
                        SH1048: a console read got more expensive. P3 is a law about cost, and the \
                        half of the product a person waits for pays it in bytes over a connection \
                        — one convenient field on a listing DTO is every row, on every screen, \
                        for ever. Make the payload leaner, or raise the ceiling in \
                        src/test/resources/console-response-budgets.properties and say why in the \
                        commit: a ceiling moved without a reason is the whole mechanism \
                        defeated.%n%nMeasured:%n%s""".formatted(report()))
                .isEmpty();
    }

    /**
     * Reads this fixture leaves empty, named rather than left looking guarded (SH1048).
     *
     * <p>A ceiling over an empty list guards nothing: {@code {"rows":[],"cursor":null}} is fifty-two
     * bytes whatever the row shape becomes, so a field added to a history row would ship under this
     * budget without moving it. The entries below are honest about that — they price the
     * <em>envelope</em>, which is worth a number of its own, and not the row.</p>
     *
     * <p>Seeding them is the fix and is not free: a history row is written by the audit path rather
     * than persisted directly, and a static file needs bytes on disk. Both are a task rather than a
     * line, which is why this is a declared list and the assertion below is a guard rather than a
     * ceiling.</p>
     */
    private static final Map<String, String> EMPTY_BY_FIXTURE = Map.of(
            "history.recent", "the audit trail is written by the write path, and this fixture "
                    + "persists its entities directly, so the trail is empty",
            "staticfile.list", "a static file needs bytes on disk, which this fixture has none of");

    /** Below this, a payload is an envelope and its budget is about the envelope alone. */
    private static final int A_REAL_PAYLOAD = 100;

    @Test
    void everyBudgetGuardsAPayloadOrSaysItDoesNot() {
        // The vacuity guard, on this file's own subject. Two of the eight reads come back as an
        // empty envelope on this fixture, and a budget over an empty list would pass any row shape
        // at all — which is exactly the report-zero-forever failure every instrument here is
        // written against. Naming them keeps the weakness in the gate rather than in a comment.
        List<String> hollow = new ArrayList<>();
        measured.forEach((name, bytes) -> {
            if (bytes < A_REAL_PAYLOAD && !EMPTY_BY_FIXTURE.containsKey(name)) {
                hollow.add(name + " = " + bytes + " bytes");
            }
        });

        assertThat(hollow)
                .as("SH1048: these reads came back as an empty envelope, so their ceiling is about "
                        + "nothing — a field added to one of their rows would ship under it without "
                        + "moving the number. Seed the fixture so the read has content, or declare "
                        + "it in EMPTY_BY_FIXTURE with the reason it cannot be.%n%s", report())
                .isEmpty();

        // And the other direction, so the list cannot outlive its reason: an entry whose read has
        // since grown content is an excuse nobody is measuring any more.
        List<String> stale = EMPTY_BY_FIXTURE.keySet().stream()
                .filter(name -> measured.getOrDefault(name, 0) >= A_REAL_PAYLOAD)
                .sorted()
                .toList();
        assertThat(stale)
                .as("SH1048: these are declared empty by fixture and are not — delete the entry, so "
                        + "a ceiling that now guards a real payload stops being excused")
                .isEmpty();
    }

    @Test
    void everyBudgetHasRoomUnderIt() {
        // SH736's rule, one gate over: a ceiling at its measurement does not measure the next
        // addition, it refuses it — and the cheapest way to pass then is to delete somebody else's
        // field, which is the degrade-the-measurement non-goal by name.
        List<String> tight = new ArrayList<>();
        measured.forEach((name, bytes) -> {
            String ceiling = budgets.getProperty(PREFIX + name);
            if (ceiling == null) {
                return;
            }
            int limit = Integer.parseInt(ceiling.trim());
            int required = (int) Math.round(limit * REQUIRED_HEADROOM);
            if (limit - bytes < required) {
                tight.add(String.format(java.util.Locale.ROOT, "%s = %d of %d (needs %d free)",
                        name, bytes, limit, required));
            }
        });

        assertThat(tight)
                .as("SH1048: these ceilings sit on their measurement, so the next field added "
                        + "anywhere near them fails the build instead of being priced by it.%n%s",
                        report())
                .isEmpty();
    }

    @Test
    void anUnchangedReadAnswers304WithNoBody() throws Exception {
        // The assertion this task exists for, driven end to end through the real filter.
        MvcResult first = mockMvc.perform(get("/api/v2/site").principal(principal))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

        assertThat(etag)
                .as("SH1048: the console's reads carry no ETag, so a second read costs its whole "
                        + "body again — which is what this task is about")
                .isNotBlank();
        assertThat(first.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .as("SH1048: `no-cache` means revalidate, not refetch. Without it a browser has no "
                        + "reason to keep the body it would then be told is still good.")
                .contains("no-cache")
                .contains("private");

        MvcResult second = mockMvc.perform(get("/api/v2/site")
                .principal(principal)
                .header(HttpHeaders.IF_NONE_MATCH, etag)).andReturn();

        assertThat(second.getResponse().getStatus())
                .as("SH1048: an unchanged read answered %d instead of 304",
                        second.getResponse().getStatus())
                .isEqualTo(304);
        assertThat(second.getResponse().getContentAsByteArray())
                .as("SH1048: the 304 carried a body, so it saved nothing — which is the only "
                        + "thing a validator is for")
                .isEmpty();
    }

    @Test
    void theStreamingAndAlreadyValidatedPathsAreLeftAlone() {
        // Not an optimisation. The filter buffers the whole response to hash it, so the change
        // feed's Server-Sent Events would never reach the browser and a site export would sit on
        // the heap for a validator nobody asks for twice.
        ShConsoleCacheFilter filter = new ShConsoleCacheFilter();

        assertThat(skips(filter, "GET", "/api/v2/events"))
                .as("SH1048: buffering an SSE stream to hash it is a change feed that never arrives")
                .isTrue();
        assertThat(skips(filter, "GET", "/api/v2/site/abc/export"))
                .as("SH1048: a site export is held whole in memory for an ETag nobody re-reads")
                .isTrue();
        assertThat(skips(filter, "GET", "/api/v2/cda/object/x/list"))
                .as("SH1048: the CDA has its own ETag filter (see SH3); hashing it twice is the cost, and "
                        + "the weaker of two validators winning is the defect")
                .isTrue();
        assertThat(skips(filter, "GET", "/api/v2/agent/read"))
                .as("SH1048: the agent surface answers conditional reads from the controller (see SH116) "
                        + "with a semantic validator — a shallow body hash over it would win")
                .isTrue();
        assertThat(skips(filter, "POST", "/api/v2/site"))
                .as("SH1048: a write cannot be given a validator, so buffering its body is a copy "
                        + "through memory for nothing")
                .isTrue();
        assertThat(skips(filter, "GET", "/api/v2/post-type"))
                .as("SH1048: the console's own reads are the whole point and must be filtered")
                .isFalse();
    }

    private static boolean skips(ShConsoleCacheFilter filter, String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return filter.shouldNotFilter(request);
    }

    @Test
    void theFilterDecidesAfterAuthentication() {
        // A validator on a refusal is a client that caches the refusal. The Spring Security chain
        // runs at -100, so anything at LOWEST_PRECEDENCE - n is after it; this holds the number so
        // a reorder is a reviewed diff rather than a silent change of meaning.
        assertThat(ShConsoleCacheConfig.ORDER)
                .as("SH1048: the console cache filter must run after the security chain (-100), or "
                        + "a 401, 403 or 429 receives an ETag")
                .isGreaterThan(-100);
        assertThat(ShConsoleCacheConfig.ORDER)
                .as("SH1048: and inside the cost header (see SH124), which measures a 304 as the zero-byte "
                        + "response it is — it can only do that from outside this filter")
                .isGreaterThan(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 30);
    }
}
