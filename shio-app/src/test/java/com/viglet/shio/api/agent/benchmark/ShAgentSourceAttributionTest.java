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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
 * SH164 — <b>attribution</b>: which of the protocol's four ideas is carrying the claim.
 *
 * <h2>The problem this exists to fix</h2>
 *
 * <p>SH112 measures four canonical <em>tasks</em>, and a task mixes every saving the protocol
 * offers into one ratio. That was fine while the ratio was large, and it stopped being fine the
 * moment the ratios started moving for reasons that had nothing to do with the agent path:
 * SH117, SH120 and SH176 each made the <em>baseline</em> leaner, and every task ratio fell
 * against an unchanged protocol (3.8×–23.3× → 2.6×–12.6× → 2.1×–12.1×). Nothing regressed and
 * the product improved, but the benchmark could no longer distinguish <em>"the protocol is
 * cheaper"</em> from <em>"the old console was fat"</em>, which makes it a record rather than a
 * guard.</p>
 *
 * <p>So each source gets its own pair, and in each pair <b>one</b> thing differs:</p>
 *
 * <ul>
 *   <li><b>addressing</b> — an addressed read versus translating a name into an id first.
 *       Both sides read the whole post, unprojected.</li>
 *   <li><b>projection</b> — the same addressed read asked for narrowly, against the same read
 *       asked for whole. The only pair whose baseline is the protocol itself, because a caller
 *       that wanted one field is on this surface either way.</li>
 *   <li><b>batching</b> — N posts in one transaction versus N writes. Both sides already know
 *       the folder and the model.</li>
 *   <li><b>discovery</b> — one context pack versus crawling for the same facts (which sites,
 *       which model, what is in the tree).</li>
 *   <li><b>lint</b> — a check that names the defective post versus reading every candidate to
 *       find it. This is the one that scales with the site.</li>
 * </ul>
 *
 * <h2>What is asserted, and the way the numbers lean</h2>
 *
 * <p>Per-source <b>ratio floors</b>, in {@code token-budgets.properties} beside every other
 * number. The absolute token counts are recorded, not asserted: a context pack describes the
 * instance, so its size is a property of the fixture rather than of the protocol — which is
 * exactly the confound the task benchmark suffers from and the reason this class runs on its own
 * database.</p>
 *
 * <p><b>Every pair is generous to the baseline, deliberately.</b> The addressing pair hands the
 * baseline the folder id it could only have obtained by crawling; the batching pair hands it the
 * folder id and the post-type id; the lint pair hands it the folder and the ids of the five
 * candidates. Each of those is a call the protocol also removes, and counting them here would
 * double-count what the discovery pair already measures. So these ratios are <em>floors</em> on
 * the advantage, not estimates of it — a claim that cannot be accused of flattering itself.</p>
 *
 * @author Alexandre Oliveira
 */
// Its own database, for the reason spelled out on ShAgentTaskBenchmarkTest: a measurement that
// moves with what other tests created is a record, not a gate.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-attribution;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
// Method-name ordering, because the SH239 comparison needs the small pairs measured first:
// `theScalingSourcesActuallyScale` sorts after `addressing` and `lint`, and comparing a ratio
// against a run that has not happened yet is how this suite would fail on nothing at all.
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentSourceAttributionTest {

    private static final String BUDGETS = "/token-budgets.properties";
    private static final Path REPORT = Path.of("target", "agent-attribution.json");
    private static final String AGENT = "agent";
    private static final String NAIVE = "naive";
    /** Enough posts for "read them all" to be visibly worse than "be told which one". */
    private static final int POSTS = 5;
    /**
     * The second fixture size (SH239), in its own site so the small pairs stay small.
     *
     * <p>Deliberately under the console listing's default page size (50): a naive client does not
     * know to ask for more, so its listing is capped at one page — which is a real effect of SH165
     * and one this test exists to *show* rather than to design around.</p>
     */
    private static final int SCALE_POSTS = 45;
    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final List<ShAgentBenchmark.Run> runs = new ArrayList<>();
    private Properties budgets;
    private MockMvc mockMvc;
    private Principal principal;

    private String siteName;
    /**
     * Where the batching pair writes — its own folder, so what it creates never lands in the
     * folder the addressing and lint pairs measure. Without it, this class had the very confound
     * it was written to remove: run batching first and the listing addressing is compared against
     * is twice the size, so the "measurement" moved with the test order.
     */
    private String writesFolderId;
    private String scaleSiteName;
    private String scaleFolderId;
    private final List<String> scalePostIds = new ArrayList<>();
    private String blogFolderId;
    private String articleType;
    private String articleTypeId;
    private final List<String> postIds = new ArrayList<>();

    @BeforeAll
    void setup() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
        budgets = new Properties();
        try (var in = getClass().getResourceAsStream(BUDGETS)) {
            budgets.load(in);
        }
        seed();
    }

    /**
     * <b>Addressing.</b> Read one field of one page whose URL the caller knows.
     *
     * <p>The protocol's claim is that {@code post:<site>/<url>} is the key, so a caller who
     * knows where a page lives can read it. The baseline's key is a UUID nobody can know, so
     * every read of a known page is a lookup and then a read — and the lookup returns a listing
     * of everything else in the folder, which is the token cost of not having an address.</p>
     */
    @Test
    @DisplayName("addressing: an address is the key, an id has to be looked up first")
    void addressing() throws Exception {
        String source = "addressing";

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, source, AGENT);
        // Neither projected nor terse: those are their own source, measured below, and folding
        // them in here is how a bundled number gets attributed to whichever idea is being
        // written about at the time.
        agent.call("read by address", authed(get("/api/v2/agent/read")
                .param("address", "post:" + siteName + "/blog/post-3")));
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, source, NAIVE);
        // Handed the folder id — which it could only have got by crawling. See the class note:
        // the generosity is what makes the ratio a floor.
        naive.call("list the folder to find the id",
                authed(get("/api/v2/object/" + blogFolderId + "/list")));
        naive.call("read by id", authed(get("/api/v2/post-unified/" + postIds.get(2))));
        record(naive);

        assertSource(source);
    }

    /**
     * <b>Projection.</b> The same addressed read, asked for narrowly (SH123 + SH78).
     *
     * <p>The one pair whose baseline is <em>the protocol itself</em>, and that is deliberate: a
     * caller that wanted one field is on the agent surface either way, so the honest denominator
     * is the same read without {@code fields} and without {@code format=terse}. Measuring it
     * against a console read would credit projection with the addressing saving as well, which
     * is precisely the bundling this class exists to undo.</p>
     *
     * <p>It is also the source most likely to be mistaken for the others: it is the cheapest to
     * implement and the easiest to point at, and a claim resting on it is a claim about response
     * encoding rather than about protocol design.</p>
     */
    @Test
    @DisplayName("projection: asking for one field versus asking for the post")
    void projection() throws Exception {
        String source = "projection";
        String address = "post:" + siteName + "/blog/post-3";

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, source, AGENT);
        agent.call("projected + terse", authed(get("/api/v2/agent/read")
                .param("address", address).param("fields", "headline").param("format", "terse")));
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, source, NAIVE);
        naive.call("the whole post, as JSON", authed(get("/api/v2/agent/read")
                .param("address", address)));
        record(naive);

        assertSource(source);
    }

    /**
     * <b>Batching.</b> Create five pages, both sides knowing exactly where and of what type.
     *
     * <p>This is the pair where the token ratio is expected to be <em>smallest</em> and the call
     * ratio largest, and saying so is the point of attributing at all: batching buys round trips
     * and transactional atomicity, not bytes. Five posts' worth of fields have to be sent either
     * way; what one call removes is four request framings and four response envelopes.</p>
     */
    @Test
    @DisplayName("batching: N writes in one transaction versus N writes")
    void batching() throws Exception {
        String source = "batching";
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, source, AGENT);
        StringBuilder ops = new StringBuilder("{\"ops\":[");
        for (int i = 1; i <= POSTS; i++) {
            if (i > 1) {
                ops.append(',');
            }
            ops.append("{\"op\":\"post.upsert\",\"address\":\"post:").append(siteName)
                    .append("/blog/batched-").append(suffix).append('-').append(i)
                    .append("\",\"type\":\"").append(articleType)
                    .append("\",\"folder\":\"/writes\",\"data\":{\"headline\":\"Batched ")
                    .append(i).append("\"}}");
        }
        String batch = ops.append("]}").toString();
        agent.call("one batch", authed(post("/api/v2/agent/batch").param("format", "terse"))
                .contentType(MediaType.APPLICATION_JSON).content(batch), batch);
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, source, NAIVE);
        for (int i = 1; i <= POSTS; i++) {
            String body = "{\"folderId\":\"" + writesFolderId + "\",\"postTypeId\":\"" + articleTypeId
                    + "\",\"state\":\"DRAFT\",\"data\":{\"headline\":\"Naive " + suffix + "-" + i
                    + "\"}}";
            naive.call("create " + i, authed(post("/api/v2/post-unified"))
                    .contentType(MediaType.APPLICATION_JSON).content(body), body);
        }
        record(naive);

        assertSource(source);
    }

    /**
     * <b>Discovery.</b> Learn what an unfamiliar instance holds before touching it.
     *
     * <p>One call against four, and the four are the <em>minimum</em>: which sites exist, what
     * the content model is, what is at the root, and what is in the one folder below it. A real
     * tree costs one listing per folder, so this pair understates the saving by exactly as much
     * as the fixture is smaller than a real site.</p>
     */
    @Test
    @DisplayName("discovery: one context pack versus crawling for the same facts")
    void discovery() throws Exception {
        String source = "discovery";

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, source, AGENT);
        agent.call("context pack", authed(get("/api/v2/agent/context")
                .param("site", siteName).param("format", "terse")));
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, source, NAIVE);
        naive.call("which sites", authed(get("/api/v2/site")));
        naive.call("the model", authed(get("/api/v2/post-type")));
        naive.call("the root", authed(get("/api/v2/object/" + siteId() + "/list")));
        naive.call("one folder down", authed(get("/api/v2/object/" + blogFolderId + "/list")));
        record(naive);

        assertSource(source);
    }

    /**
     * <b>Lint.</b> Find the one page with a link that goes nowhere.
     *
     * <p>The source that carries the widest ratio and the only one that <em>grows with the
     * site</em>: the check names the defective post, so its cost is one call whatever the site
     * holds, while the baseline reads every candidate. Five candidates here; a real site has
     * hundreds, and this is the number to quote when the claim is about scale.</p>
     */
    @Test
    @DisplayName("lint: a check that names the defect versus reading everything to find it")
    void lint() throws Exception {
        String source = "lint";

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, source, AGENT);
        agent.call("verify", authed(get("/api/v2/agent/verify")
                .param("site", siteName).param("format", "terse")));
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, source, NAIVE);
        for (int i = 1; i <= POSTS; i++) {
            naive.call("read candidate " + i,
                    authed(get("/api/v2/post-unified/" + postIds.get(i - 1))));
        }
        record(naive);

        assertSource(source);
    }

    /**
     * SH239 — the two sources that are <em>claimed</em> to scale, measured at a second size.
     *
     * <p>SH164's most quotable result is that addressing and the lint carry the token advantage
     * <b>because they grow with the site</b>, and both were measured on five posts — the part of
     * the claim that matters most was the part still being argued. This runs the same two pairs
     * against nine times the content and asserts the ratio <em>rises</em>, which is the only way
     * "it grows with the content" becomes a measurement rather than a story (§XIV.4: characterize
     * by execution).</p>
     *
     * <p><b>What measuring it changed about the claim.</b> The lint grows without bound — one call
     * against N reads, in calls <em>and</em> tokens. Addressing does not: the lookup it replaces is
     * a folder listing, and since SH165 that listing is <b>paged</b>, so a naive client's worst case
     * is capped at one page however large the folder gets. So the honest form of the claim is that
     * the lint scales with the site and addressing scales up to a page and then flattens — which is
     * the console's own paging having bounded the baseline it is measured against, the same effect
     * SH117/SH120/SH176 had and the reason this attribution exists at all.</p>
     */
    @Test
    @DisplayName("SH239: the two scaling sources measure larger at a larger size")
    void theScalingSourcesActuallyScale() throws Exception {
        ShAgentBenchmark addressingAgent = new ShAgentBenchmark(mockMvc, "addressing@scale", AGENT);
        addressingAgent.call("read by address", authed(get("/api/v2/agent/read")
                .param("address", "post:" + scaleSiteName + "/blog/post-3")));
        record(addressingAgent);

        ShAgentBenchmark addressingNaive = new ShAgentBenchmark(mockMvc, "addressing@scale", NAIVE);
        addressingNaive.call("list the folder to find the id",
                authed(get("/api/v2/object/" + scaleFolderId + "/list")));
        addressingNaive.call("read by id",
                authed(get("/api/v2/post-unified/" + scalePostIds.get(2))));
        record(addressingNaive);

        ShAgentBenchmark lintAgent = new ShAgentBenchmark(mockMvc, "lint@scale", AGENT);
        lintAgent.call("verify", authed(get("/api/v2/agent/verify")
                .param("site", scaleSiteName).param("format", "terse")));
        record(lintAgent);

        ShAgentBenchmark lintNaive = new ShAgentBenchmark(mockMvc, "lint@scale", NAIVE);
        for (int i = 1; i <= SCALE_POSTS; i++) {
            lintNaive.call("read candidate " + i,
                    authed(get("/api/v2/post-unified/" + scalePostIds.get(i - 1))));
        }
        record(lintNaive);

        assertSource("addressing@scale");
        assertSource("lint@scale");

        // The assertion the task is actually about: more content, more advantage. Compared against
        // the small pairs rather than against a constant, so it stays true if a future change makes
        // both sizes cheaper — which is exactly what the last three blocks did.
        assertThat(tokenRatio("lint@scale"))
                .as("the lint's advantage must grow with the site — it is one call against one "
                        + "read per candidate, so a flat ratio here means the baseline stopped "
                        + "reading everything and the claim needs rewriting, not the floor")
                .isGreaterThan(tokenRatio("lint"));
        assertThat(callRatio("lint@scale"))
                .as("...in round trips too, which is the half that has no ceiling")
                .isGreaterThan(callRatio("lint"));
        assertThat(tokenRatio("addressing@scale"))
                .as("addressing's advantage grows too, but only up to a page: the lookup it "
                        + "replaces is a paged listing since SH165, so this is bounded where the "
                        + "lint's is not")
                .isGreaterThan(tokenRatio("addressing"));
    }

    private int tokenRatio(String source) {
        return ratio(run(source, NAIVE).tokens(), run(source, AGENT).tokens());
    }

    private int callRatio(String source) {
        return ratio(run(source, NAIVE).calls(), run(source, AGENT).calls());
    }

    // ------------------------------------------------------------------ assertions

    /**
     * Two floors per source. Only the ratios: an absolute count here would move with the
     * fixture, and a per-source floor is the whole point — it holds when the baseline improves
     * and fails when <em>this</em> idea stops paying.
     */
    private void assertSource(String source) {
        ShAgentBenchmark.Run agent = run(source, AGENT);
        ShAgentBenchmark.Run naive = run(source, NAIVE);
        agent.requireAllSucceeded();
        naive.requireAllSucceeded();

        assertThat(ratio(naive.calls(), agent.calls()))
                .as("%s: round trips saved by this idea alone. A floor per source is what "
                        + "makes the claim defensible — a task ratio moves when the baseline "
                        + "improves and says nothing about which idea earned it.", source)
                .isGreaterThanOrEqualTo(budget(source, "min-call-ratio"));
        assertThat(ratio(naive.tokens(), agent.tokens()))
                .as("%s: tokens saved by this idea alone.", source)
                .isGreaterThanOrEqualTo(budget(source, "min-token-ratio"));
    }

    /** Integer tenths, as everywhere else in the budgets file (25 = 2.5×). */
    private static int ratio(int baseline, int agent) {
        return agent == 0 ? 0 : (baseline * 10) / agent;
    }

    private int budget(String source, String key) {
        String property = "attribution." + source + "." + key;
        String value = budgets.getProperty(property);
        assertThat(value)
                .as("No floor for %s. A measured source without a declared floor records "
                        + "history instead of guarding it.", property)
                .isNotNull();
        return Integer.parseInt(value.trim());
    }

    private ShAgentBenchmark.Run run(String source, String path) {
        return runs.stream()
                .filter(run -> run.task().equals(source) && run.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No '" + source + "' (" + path
                        + ") run recorded yet — the SH239 comparison depends on the small pairs "
                        + "having run first, which is what @TestMethodOrder on this class is for."));
    }

    private void record(ShAgentBenchmark benchmark) {
        runs.add(benchmark.finish());
    }

    @AfterAll
    void writeReport() throws IOException {
        if (runs.isEmpty()) {
            return;
        }
        Files.createDirectories(REPORT.getParent());
        Files.write(REPORT, ShAgentBenchmark.report(runs));
        System.out.println("\nSH164 source attribution (" + REPORT + "):\n"
                + ShAgentBenchmark.table(runs));
    }

    // ------------------------------------------------------------------ fixture

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.principal(principal);
    }

    private String siteId;

    private String siteId() {
        return siteId;
    }

    /** One site, one folder, five published articles, one link that goes nowhere. */
    private void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            ShSite site = new ShSite();
            siteName = "attr-" + suffix;
            site.setName(siteName);
            site.setDate(WHEN);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(WHEN);
            root.setOwner("admin");
            entityManager.persist(root);

            ShFolder writes = new ShFolder();
            writes.setName("writes");
            writes.setRootFolder((byte) 0);
            writes.setParentFolder(root);
            writes.setDate(WHEN);
            writes.setOwner("admin");
            entityManager.persist(writes);
            writesFolderId = writes.getId();

            ShFolder blog = new ShFolder();
            blog.setName("blog");
            blog.setRootFolder((byte) 0);
            blog.setParentFolder(root);
            blog.setDate(WHEN);
            blog.setOwner("admin");
            entityManager.persist(blog);
            blogFolderId = blog.getId();

            ShPostType type = new ShPostType();
            articleType = "AttrArticle" + suffix;
            type.setName(articleType);
            type.setTitle("Attribution article");
            type.setSystem((byte) 0);
            type.setDate(WHEN);
            type.setOwner("admin");
            entityManager.persist(type);
            articleTypeId = type.getId();

            attr(type, widget("Text"), "headline", 1, true);
            attr(type, widget("HTML Editor"), "body", 2, false);

            // SH334 — a finished site has a front door. Without it the lint reports `no-home-page`
            // on every site-scoped verify here, which is a paragraph about the *site* landing in a
            // measurement of what one idea saves on one task.
            home(site, root, type);

            for (int i = 1; i <= POSTS; i++) {
                String href = i == 3 ? "/blog/does-not-exist" : "/blog/post-1";
                article(site, blog, type, i,
                        "<p>Body " + i + " <a href=\"" + href + "\">more</a></p>");
            }

            // SH239's second size, in its own site so the site-scoped lint pair above keeps
            // measuring five posts rather than fifty.
            ShSite scaleSite = new ShSite();
            scaleSiteName = "attr-scale-" + suffix;
            scaleSite.setName(scaleSiteName);
            scaleSite.setDate(WHEN);
            scaleSite.setOwner("admin");
            entityManager.persist(scaleSite);

            ShFolder scaleRoot = new ShFolder();
            scaleRoot.setName("root");
            scaleRoot.setRootFolder((byte) 1);
            scaleRoot.setShSite(scaleSite);
            scaleRoot.setDate(WHEN);
            scaleRoot.setOwner("admin");
            entityManager.persist(scaleRoot);

            ShFolder scaleBlog = new ShFolder();
            scaleBlog.setName("blog");
            scaleBlog.setRootFolder((byte) 0);
            scaleBlog.setParentFolder(scaleRoot);
            scaleBlog.setDate(WHEN);
            scaleBlog.setOwner("admin");
            entityManager.persist(scaleBlog);
            scaleFolderId = scaleBlog.getId();

            home(scaleSite, scaleRoot, type);

            for (int i = 1; i <= SCALE_POSTS; i++) {
                String href = i == 3 ? "/blog/does-not-exist" : "/blog/post-1";
                scalePostIds.add(UUID.randomUUID().toString());
                for (ShPostState state : List.of(ShPostState.DRAFT, ShPostState.PUBLISHED)) {
                    ShPostUnified post = new ShPostUnified();
                    post.setId(scalePostIds.get(i - 1));
                    post.setState(state);
                    post.setShSite(scaleSite);
                    post.setShFolder(scaleBlog);
                    post.setShPostType(type);
                    post.setTitle("Post " + i);
                    post.setFurl("/blog/post-" + i);
                    post.setDate(WHEN);
                    post.setPublished(state == ShPostState.PUBLISHED);
                    post.setData("{\"headline\":\"Post " + i + "\",\"body\":\"<p>Body " + i
                            + " <a href=\\\"" + href + "\\\">more</a></p>\"}");
                    entityManager.persist(post);
                }
            }
        });
    }

    private ShWidget widget(String name) {
        return entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName(name);
                    entityManager.persist(created);
                    return created;
                });
    }

    private void attr(ShPostType type, ShWidget widget, String name, int ordinal, boolean title) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setId(UUID.randomUUID().toString());
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setRequired((byte) 0);
        attr.setIsTitle((byte) (title ? 1 : 0));
        attr.setShWidget(widget);
        attr.setShPostType(type);
        entityManager.persist(attr);
    }

    /**
     * A site's front door (SH334), outside {@code postIds} so the baseline still walks exactly the
     * posts these ratios are about.
     */
    private void home(ShSite site, ShFolder root, ShPostType type) {
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
            post.setDate(WHEN);
            post.setPublished(state == ShPostState.PUBLISHED);
            post.setData("{\"headline\":\"Home\",\"body\":\"<p>Welcome</p>\"}");
            entityManager.persist(post);
        }
    }

    private void article(ShSite site, ShFolder folder, ShPostType type, int index, String body) {
        while (postIds.size() < index) {
            postIds.add(UUID.randomUUID().toString());
        }
        for (ShPostState state : List.of(ShPostState.DRAFT, ShPostState.PUBLISHED)) {
            ShPostUnified post = new ShPostUnified();
            post.setId(postIds.get(index - 1));
            post.setState(state);
            post.setShSite(site);
            post.setShFolder(folder);
            post.setShPostType(type);
            post.setTitle("Post " + index);
            post.setFurl("/blog/post-" + index);
            post.setDate(WHEN);
            post.setPublished(state == ShPostState.PUBLISHED);
            post.setData("{\"headline\":\"Post " + index + "\",\"body\":\""
                    + body.replace("\"", "\\\"") + "\"}");
            entityManager.persist(post);
        }
    }
}
