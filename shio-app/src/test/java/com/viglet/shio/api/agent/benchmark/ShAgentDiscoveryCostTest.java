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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import com.viglet.shio.api.agent.ShAgentIntents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
 * SH597 — the cost of finding out. What the <em>path</em> to the first correct action costs.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>Block M measures surfaces. {@code tools/list} has a token cap checked every build,
 * {@link ShAgentResponseBudgetTest} scores every canonical response, and
 * {@link ShAgentTaskBenchmarkTest} scores a task an agent has already decided how to do. Every one
 * of those numbers is about a <b>surface</b>. P2 does not claim a surface is small — it claims
 * <i>one call replaces a session</i>, and that is a property of a <b>path</b>: cold start to the
 * first correct action.</p>
 *
 * <p>Nothing counted it, which is how twelve replication rounds could propose building a shipped
 * capability twice and diagnose a 5xx by grepping a server log with {@code diagnostics: true}
 * sitting in the manifest, without a number moving. The manifest was complete the whole time. The
 * run that found a feature in one call and the run that never found it scored identically, because
 * neither was scored.</p>
 *
 * <h2>What is measured</h2>
 *
 * <p>An <b>intent</b> is a job with a known right first move. From an empty context the ladder
 * below is walked in cost order, and the intent's cost is every rung read up to and including the
 * one whose body <em>names the move</em>, plus the move itself. Stopping at the first mention would
 * measure recall, not discovery: what a caller needs is the call it can make, so the marker is the
 * endpoint's <b>path</b> and the move is then executed and required to succeed. An action that
 * 4xx'd was never the first correct one.</p>
 *
 * <pre>
 *   rung 1  GET /agent/context?include=all,manifest&amp;format=terse   the one call P2 promises
 *   rung 2  GET /agent/manifest?format=terse                        the full endpoint index
 * </pre>
 *
 * <p>Each intent re-walks the ladder from rung 1, because "from an empty context" is what the claim
 * is about — the total below is a sum of independent cold starts, not one session's bill. That is
 * also why the ladder is short: a caller that has read the whole manifest is no longer cold, and a
 * third rung would measure the surface's size rather than the path's length.</p>
 *
 * <h2>What is asserted</h2>
 *
 * <ol>
 *   <li><b>Reachability.</b> An intent no rung names is a capability that shipped and cannot be
 *       found — P1's failure, and the one this benchmark exists to make fail loudly instead of
 *       quietly costing a session.</li>
 *   <li><b>Calls and tokens</b>, per intent, against ceilings in
 *       {@code src/test/resources/token-budgets.properties} — the same file every other budget
 *       lives in, because a second place to keep one is a place that goes stale.</li>
 *   <li><b>The total</b>, which is the headline: what it costs this instance to be found out —
 *       and the one figure with a <b>floor</b> as well as a ceiling (SH683).</li>
 * </ol>
 *
 * <p>The ceilings are today's measurement with ~10% headroom. They were once deliberately not
 * <em>good</em> — half these intents were reachable only from the ~2400-token endpoint index, which
 * is the number SH598–SH601 existed to move, and they moved it: all six are now answered at rung 1
 * and the headline fell 10651 → 6387.</p>
 *
 * <p><b>Nothing failed when it did (SH683).</b> An {@code isLessThanOrEqualTo} is right for a guard
 * and insufficient as a record: the two ceilings SH601 invalidated read 3650 for weeks afterwards,
 * so the surface they protect could have grown 3.5x before anything objected, and the win was
 * written nowhere a build could see. The total is therefore asserted {@code isBetween} — a floor at
 * ~5% below the measurement, on the total only, because a per-intent floor would put six tripwires
 * under whoever improved one row. Falling through it is not a defect; it is an improvement that
 * has not yet been recorded. The run is written to
 * {@code target/agent-discovery-benchmark.json} so a release can archive it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
/*
 * Its own database, for the reason ShBenchmarkIsolationLintTest exists to enforce: the context pack
 * carries the instance's whole content model, so on the shared datasource rung 1's token count is a
 * statement about what every other test in the suite happened to create. Every number here is read
 * off that rung.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-discovery;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentDiscoveryCostTest {

    private static final String BUDGETS = "/token-budgets.properties";
    private static final Path REPORT = Path.of("target", "agent-discovery-benchmark.json");

    /** The note the {@code recall-conventions} intent is about — seeded, then discovered. */
    private static final String NOTE_KEY = "discovery-benchmark";
    private static final String NOTE = "Dates in this instance are ISO-8601 with an explicit zone.";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private Properties budgets;
    private MockMvc mockMvc;
    private Principal principal;
    private String siteName;

    /** Per intent: what it cost, and which rung answered it. */
    private final Map<String, ShAgentBenchmark.Run> found = new LinkedHashMap<>();

    @BeforeAll
    void setup() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
        budgets = new Properties();
        try (var in = getClass().getResourceAsStream(BUDGETS)) {
            budgets.load(in);
        }
        seed();
        remember();
    }

    /**
     * One finished site — a model, a front door, one article.
     *
     * <p>Small on purpose. This benchmark scores the path to a call, not the content the call
     * returns, and a fixture with fifty pages would put a sitemap's weight into a number about
     * discovery.</p>
     */
    private void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            siteName = "discovery-" + suffix;
            site.setName(siteName);
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

            ShWidget text = new ShWidget();
            text.setName("Text");
            entityManager.persist(text);

            ShPostType type = new ShPostType();
            type.setName("DiscoveryPage" + suffix);
            type.setTitle("Discovery page");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostTypeAttr headline = new ShPostTypeAttr();
            headline.setId(UUID.randomUUID().toString());
            headline.setName("headline");
            headline.setLabel("headline");
            headline.setOrdinal(1);
            headline.setRequired((byte) 1);
            headline.setIsTitle((byte) 1);
            headline.setShWidget(text);
            headline.setShPostType(type);
            entityManager.persist(headline);

            // Draft and published, the way every fixture on this surface models a live post.
            String id = UUID.randomUUID().toString();
            for (ShPostState state : List.of(ShPostState.DRAFT, ShPostState.PUBLISHED)) {
                ShPostUnified post = new ShPostUnified();
                post.setId(id);
                post.setState(state);
                post.setShSite(site);
                post.setShFolder(root);
                post.setShPostType(type);
                post.setTitle("Home");
                post.setFurl("/");
                post.setDate(when);
                post.setPublished(state == ShPostState.PUBLISHED);
                post.setData("{\"headline\":\"Home\"}");
                entityManager.persist(post);
            }
        });
    }

    /**
     * The instance is told something, before the measurement starts.
     *
     * <p>Not part of any intent's cost: a run that has to write the note first is not the run this
     * is about. The intent is the <em>next</em> session, which must not re-derive it.</p>
     */
    private void remember() throws Exception {
        String body = "{\"scope\":\"" + siteName + "\",\"key\":\"" + NOTE_KEY
                + "\",\"note\":\"" + NOTE + "\"}";
        mockMvc.perform(put("/api/v2/agent/memory").principal(principal)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    // ------------------------------------------------------------------ the ladder

    /** One cold-start read, and what it is called in the report. */
    private record Rung(String label, Supplier<MockHttpServletRequestBuilder> request) {
    }

    private List<Rung> ladder() {
        return List.of(
                new Rung("context", () -> get("/api/v2/agent/context")
                        .param("site", siteName).param("format", "terse")
                        .param("include", "all,manifest")),
                new Rung("manifest", () -> get("/api/v2/agent/manifest").param("format", "terse")));
    }

    /**
     * A job with a known right first move.
     *
     * @param marker what a rung must contain for the move to be <em>knowable</em> from it — a
     *               path, not a feature flag. {@code diagnostics: true} in the manifest is
     *               precisely what a run had in front of it while it grepped the logs instead
     * @param move   the move itself, executed and required to succeed; {@code null} when the rung
     *               that names it <em>is</em> the answer, which is the cheapest a path can be
     */
    private record Intent(String id, String question, String marker, String moveLabel,
                          Supplier<MockHttpServletRequestBuilder> move) {
    }

    /**
     * The set — read from {@link ShAgentIntents}, which is also what the manifest routes with.
     *
     * <p>SH601's instruction, and the reason it is worth the indirection: <b>build the table once,
     * score against it, then route with it.</b> A benchmark whose intents were written separately
     * from the router's would score a different set than an agent is told about, and the first time
     * the two drifted the score would still be green — which is the failure mode this whole file
     * exists to catch one level down.</p>
     *
     * <p>What stays here is what only a test can hold: the <em>move</em> each intent's answer maps
     * to, executed and required to succeed. The shared table names the call; this knows how to make
     * it. The marker comes from the call itself, so a row whose published path changes is scored
     * against the new one without anybody remembering to update two places.</p>
     */
    private List<Intent> intents() {
        Map<String, Supplier<MockHttpServletRequestBuilder>> moves = moves();
        return ShAgentIntents.INTENTS.stream()
                .map(intent -> new Intent(intent.id(), intent.when(),
                        // `recall-conventions` is answered by the pack *containing* the note, so
                        // its marker is the note and not a path — the one row where the rung that
                        // names it is the answer.
                        "recall-conventions".equals(intent.id()) ? NOTE : pathOf(intent.call()),
                        intent.call(), moves.get(intent.id())))
                .toList();
    }

    /** The path an intent's call names, which is what a rung must contain for it to be knowable. */
    private static String pathOf(String call) {
        int start = call.indexOf("/api/v2");
        if (start < 0) {
            return call;
        }
        int end = call.indexOf('?', start);
        String path = call.substring(start, end < 0 ? call.length() : end).trim();
        // The rungs print the surface-relative form, which is what the manifest's index carries.
        return path.replace("/api/v2", "");
    }

    /** How to make each call. The shared table says what to call; only a test can call it. */
    private Map<String, Supplier<MockHttpServletRequestBuilder>> moves() {
        Map<String, Supplier<MockHttpServletRequestBuilder>> moves = new LinkedHashMap<>();
        moves.put("diagnose-5xx",
                () -> get("/api/v2/agent/diagnostics").param("format", "terse"));
        // Null: the pack that names it IS the answer, which is the cheapest a path can be.
        moves.put("recall-conventions", null);
        moves.put("reuse-a-blueprint",
                () -> get("/api/v2/agent/blueprints").param("format", "terse"));
        moves.put("prove-the-work",
                () -> get("/api/v2/agent/verify").param("site", siteName).param("format", "terse"));
        moves.put("resume-a-session",
                () -> get("/api/v2/agent/changes").param("site", siteName).param("format", "terse"));
        moves.put("write-without-ids",
                () -> post("/api/v2/agent/batch").param("format", "terse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dryRun()));
        return moves;
    }

    private String dryRun() {
        return "{\"dryRun\":true,\"ops\":[{\"op\":\"folder.upsert\",\"address\":\"folder:"
                + siteName + "/probe\"}]}";
    }

    // ------------------------------------------------------------------ the measurement

    @Test
    void everyIntentIsReachableAndItsPathIsBudgeted() throws Exception {
        List<String> unreachable = new ArrayList<>();
        for (Intent intent : intents()) {
            String rung = walk(intent);
            if (rung == null) {
                unreachable.add(intent.id() + " — nothing names " + intent.marker());
            }
        }

        assertThat(unreachable)
                .as("""
                        SH597: a capability that shipped and that no cold-start read names is one \
                        an agent can only find by being told, which is P1's failure wearing a \
                        green build. Either the endpoint index or the manifest's `startHere` has \
                        to carry it — a `features` flag is not enough, because a run diagnosed a \
                        5xx by grepping logs with `diagnostics: true` in front of it.""")
                .isEmpty();

        for (Intent intent : intents()) {
            ShAgentBenchmark.Run run = found.get(intent.id());
            run.requireAllSucceeded();
            assertThat(run.calls())
                    .as("SH597: calls from an empty context to the first correct action for '%s' "
                            + "(%s). Raising this means the surface got harder to find out about.",
                            intent.id(), intent.question())
                    .isLessThanOrEqualTo(budget(intent.id(), "calls"));
            assertThat(run.tokens())
                    .as("SH597: tokens spent finding out how to answer '%s'. P3 applies to the "
                            + "path as well as to the response.", intent.id())
                    .isLessThanOrEqualTo(budget(intent.id(), "tokens"));
        }

        assertThat(found.values().stream().mapToInt(ShAgentBenchmark.Run::tokens).sum())
                .as("""
                        SH597: the headline — what it costs to find out how to do all six jobs \
                        from cold. It is a sum of independent cold starts, which is the honest \
                        shape of the claim: P2 says *one call* replaces a session, so every \
                        intent that needs a second one is paying for the promise not holding. \
                        SH683: and it is bounded BELOW as well. An `isLessThanOrEqualTo` cannot \
                        object to an improvement, so SH601's 40% win sat under a ceiling of 11700 \
                        with nothing recording it and the surface free to grow back into the \
                        headroom. Falling through the floor is not a defect — it means somebody \
                        made the path cheaper and owes token-budgets.properties the new number, \
                        which is the whole reason that file exists.""")
                .isBetween(budget("total", "floor"), budget("total", "tokens"));
    }

    /**
     * Walks the ladder for one intent and records what the answer cost.
     *
     * @return the rung that named the move, or {@code null} if none did
     */
    private String walk(Intent intent) throws Exception {
        ShAgentBenchmark measure = new ShAgentBenchmark(mockMvc, intent.id(), "?");
        for (Rung rung : ladder()) {
            MvcResult result = measure.call(rung.label(), authed(rung.request().get()));
            String body = new String(result.getResponse().getContentAsByteArray(),
                    StandardCharsets.UTF_8);
            if (!body.contains(intent.marker())) {
                continue;
            }
            if (intent.move() != null) {
                measure.call(intent.moveLabel(), authed(intent.move().get()), sentBody(intent));
            }
            ShAgentBenchmark.Run run = measure.finish();
            found.put(intent.id(),
                    new ShAgentBenchmark.Run(run.task(), rung.label(), run.steps(), run.millis()));
            return rung.label();
        }
        return null;
    }

    /** Only the batch probe sends one, and a write's cost is mostly what it sent. */
    private String sentBody(Intent intent) {
        return "write-without-ids".equals(intent.id()) ? dryRun() : null;
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.principal(principal);
    }

    private int budget(String intent, String key) {
        String property = "benchmark.discovery." + intent + "." + key;
        String value = budgets.getProperty(property);
        assertThat(value)
                .as("SH597: no budget for %s. A measured path with no declared ceiling records "
                        + "history instead of guarding it.", property)
                .isNotNull();
        return Integer.parseInt(value.trim());
    }

    /** The artifact, and the table — written even on a failure, because the numbers are the point. */
    @AfterAll
    void writeReport() throws IOException {
        if (found.isEmpty()) {
            return;
        }
        List<ShAgentBenchmark.Run> runs = List.copyOf(found.values());
        Files.createDirectories(REPORT.getParent());
        Files.write(REPORT, ShAgentBenchmark.report(runs));
        System.out.println("\nSH597 discovery cost — cold start to the first correct action ("
                + REPORT + "):\n" + ShAgentBenchmark.table(runs)
                + "the `path` column is the rung that named the move.\n");
    }
}
