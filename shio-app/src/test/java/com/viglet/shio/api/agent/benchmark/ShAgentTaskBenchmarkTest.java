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
import org.junit.jupiter.api.Test;
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
 * SH112 — the task benchmark. What a canonical job actually costs, measured.
 *
 * <h2>Why this test exists at all</h2>
 *
 * <p>The concept spec claims an agent-native CMS is roughly an order of magnitude
 * cheaper per task than an id-keyed one. Until this class, that was a design argument.
 * Every canonical task here is therefore run <b>twice</b> — once over the agent surface,
 * once over the same instance's console/CDA endpoints the way a client without addressing,
 * batching or a context pack would have to — and the ratio between the two is asserted.
 * A single-path benchmark would only tell us whether today is worse than yesterday; the
 * baseline is what makes the number mean something.</p>
 *
 * <h2>What the two paths do and do not share</h2>
 *
 * <p>They reach a comparable end state, not always a byte-identical one. Where they
 * differ it is stated on the task, and the difference always runs <em>against</em> the
 * agent path: the blueprint task, for instance, also provisions a content model that the
 * hand-built baseline simply assumes. Flattering the measurement in the other direction
 * would make the whole exercise pointless.</p>
 *
 * <h2>What is asserted, and what is only recorded</h2>
 *
 * <p>Calls and tokens are asserted against ceilings in
 * {@code src/test/resources/token-budgets.properties} — the same file SH132 and SH113
 * use, because a second place to keep a budget is a place that goes stale. Wall clock is
 * recorded and printed but never asserted: CI timing is noise at this scale, and a
 * benchmark that fails on noise is a benchmark somebody deletes.</p>
 *
 * <p>The full run is written to {@code target/agent-benchmark.json} so a release can
 * archive it. The ceilings are the guard between releases; the artifact is the record.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentTaskBenchmarkTest {

    private static final String BUDGETS = "/token-budgets.properties";
    private static final Path REPORT = Path.of("target", "agent-benchmark.json");
    private static final String AGENT = "agent";
    private static final String NAIVE = "naive";
    private static final int TRANSLATED_POSTS = 3;
    private static final int BULK_POSTS = 10;

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
    private String siteId;
    private String rootFolderId;
    private String blogFolderId;
    private String articleType;
    private String articleTypeId;
    private String blankSiteName;
    /** Post ids by 1-based index — the id column is 36 chars, so they cannot be spelled. */
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
     * A small but realistic site: a model with a rich-text field, a folder, five
     * published articles, and one link that goes nowhere.
     *
     * <p>The broken link is the point of the {@code fix-broken-link} task and it has to
     * be genuinely broken — a task whose defect the lint cannot see would measure the
     * cost of looking rather than the cost of fixing.</p>
     */
    private void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            siteName = "bench-" + suffix;
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);
            rootFolderId = root.getId();

            ShFolder blog = new ShFolder();
            blog.setName("blog");
            blog.setRootFolder((byte) 0);
            blog.setParentFolder(root);
            blog.setDate(when);
            blog.setOwner("admin");
            entityManager.persist(blog);
            blogFolderId = blog.getId();

            ShWidget text = widget("Text");
            ShWidget html = widget("HTML Editor");

            ShPostType type = new ShPostType();
            articleType = "BenchArticle" + suffix;
            type.setName(articleType);
            type.setTitle("Bench article");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);
            articleTypeId = type.getId();

            attr(type, text, "headline", 1, true, true);
            attr(type, html, "body", 2, false, false);

            for (int i = 1; i <= 5; i++) {
                // Post 3 links to a URL that does not exist; the rest link to post 1.
                String href = i == 3 ? "/blog/does-not-exist" : "/blog/post-1";
                article(site, blog, type, when, i,
                        "<p>Body " + i + " <a href=\"" + href + "\">more</a></p>");
            }

            // A second, empty site for the blueprint task: applying a package into a site
            // that already has content would measure the package plus whatever was there.
            ShSite blank = new ShSite();
            blankSiteName = "bench-blank-" + suffix;
            blank.setName(blankSiteName);
            blank.setDate(when);
            blank.setOwner("admin");
            entityManager.persist(blank);

            ShFolder blankRoot = new ShFolder();
            blankRoot.setName("root");
            blankRoot.setRootFolder((byte) 1);
            blankRoot.setShSite(blank);
            blankRoot.setDate(when);
            blankRoot.setOwner("admin");
            entityManager.persist(blankRoot);
        });
    }

    private ShWidget widget(String name) {
        ShWidget widget = new ShWidget();
        widget.setName(name);
        entityManager.persist(widget);
        return widget;
    }

    private void attr(ShPostType type, ShWidget widget, String name, int ordinal,
                      boolean required, boolean title) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setId(UUID.randomUUID().toString());
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setRequired((byte) (required ? 1 : 0));
        attr.setIsTitle((byte) (title ? 1 : 0));
        attr.setShWidget(widget);
        attr.setShPostType(type);
        entityManager.persist(attr);
    }

    private void article(ShSite site, ShFolder folder, ShPostType type, Instant when,
                         int index, String body) {
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
            post.setDate(when);
            post.setPublished(state == ShPostState.PUBLISHED);
            post.setData("{\"headline\":\"Post " + index + "\",\"body\":\""
                    + body.replace("\"", "\\\"") + "\"}");
            entityManager.persist(post);
        }
    }

    // ------------------------------------------------------------------ tasks

    /**
     * "Build a five-page marketing site."
     *
     * <p>The agent path reads the manifest, reads the context, discovers the blueprint
     * catalogue and applies one. The baseline learns the model from the console
     * post-type API, walks the tree, then creates and publishes every page by hand.</p>
     *
     * <p>The comparison understates the agent path: the blueprint also provisions eight
     * post-types, which the baseline gets for free by reusing what exists.</p>
     */
    @Test
    void buildMarketingSite() throws Exception {
        String task = "build-marketing-site";

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, task, AGENT);
        agent.call("manifest", authed(get("/api/v2/agent/manifest")));
        agent.call("context", authed(get("/api/v2/agent/context")
                .param("site", blankSiteName).param("format", "terse")));
        agent.call("blueprints", authed(get("/api/v2/agent/blueprints").param("format", "terse")));
        String applyBody = "{\"site\":\"" + blankSiteName + "\",\"params\":{\"brand\":\"Acme\"}}";
        agent.call("apply blueprint",
                authed(post("/api/v2/agent/blueprints/marketing-site/apply"))
                        .contentType(MediaType.APPLICATION_JSON).content(applyBody), applyBody);
        agent.call("verify", authed(get("/api/v2/agent/verify")
                .param("site", blankSiteName).param("format", "terse")));
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, task, NAIVE);
        naive.call("list post-types", authed(get("/api/v2/post-type")));
        naive.call("list sites", authed(get("/api/v2/site")));
        naive.call("list root", authed(get("/api/v2/object/" + rootFolderId + "/list")));
        String folderBody = "{\"name\":\"pages-" + UUID.randomUUID().toString().substring(0, 6) + "\"}";
        String folderId = id(naive.call("create folder",
                authed(post("/api/v2/folder/" + rootFolderId))
                        .contentType(MediaType.APPLICATION_JSON).content(folderBody), folderBody));
        for (int i = 1; i <= 5; i++) {
            String body = createPost(folderId, "naive-page-" + i);
            String postId = id(naive.call("create page " + i,
                    authed(post("/api/v2/post-unified"))
                            .contentType(MediaType.APPLICATION_JSON).content(body), body));
            naive.call("publish page " + i,
                    authed(post("/api/v2/post-unified/" + postId + "/publish")));
        }
        record(naive);

        assertTask(task);
    }

    /** "Add a post-type and ten posts." The two paths reach the same end state here. */
    @Test
    void addPostTypeAndTenPosts() throws Exception {
        String task = "add-type-and-10-posts";
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, task, AGENT);
        agent.call("context", authed(get("/api/v2/agent/context")
                .param("site", siteName).param("format", "terse")));
        String typeName = "BenchNote" + suffix;
        String typeBody = postTypeDef(typeName);
        agent.call("upsert post-type", authed(put("/api/v2/post-type/" + typeName))
                .contentType(MediaType.APPLICATION_JSON).content(typeBody), typeBody);
        String batch = batchOfPosts(typeName, "agent-" + suffix);
        agent.call("batch write", authed(post("/api/v2/agent/batch"))
                .contentType(MediaType.APPLICATION_JSON).content(batch), batch);
        agent.call("verify", authed(get("/api/v2/agent/verify")
                .param("site", siteName).param("format", "terse")));
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, task, NAIVE);
        naive.call("list post-types", authed(get("/api/v2/post-type")));
        String naiveType = "BenchNaive" + suffix;
        String consoleType = consolePostType(naiveType);
        naive.call("create post-type", authed(post("/api/v2/post-type"))
                .contentType(MediaType.APPLICATION_JSON).content(consoleType), consoleType);
        naive.call("read folder", authed(get("/api/v2/folder/" + blogFolderId)));
        for (int i = 1; i <= BULK_POSTS; i++) {
            String body = "{\"folderId\":\"" + blogFolderId + "\",\"postType\":\"" + naiveType
                    + "\",\"state\":\"DRAFT\",\"data\":{\"headline\":\"Naive note " + i + "\"}}";
            naive.call("create post " + i, authed(post("/api/v2/post-unified"))
                    .contentType(MediaType.APPLICATION_JSON).content(body), body);
        }
        record(naive);

        assertTask(task);
    }

    /**
     * "Fix a broken link."
     *
     * <p>The agent path is the one the whole of Block K exists for: the lint <em>names</em>
     * the defective post, so the fix is read-one, write-one, re-verify. The baseline
     * cannot know which post is broken, so it reads all of them — which is why this is
     * the task with the widest ratio, and why the ratio grows with the site.</p>
     */
    @Test
    void fixBrokenLink() throws Exception {
        String task = "fix-broken-link";

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, task, AGENT);
        agent.call("verify", authed(get("/api/v2/agent/verify")
                .param("site", siteName).param("format", "terse")));
        String address = "post:" + siteName + "/blog/post-3";
        agent.call("read the named post", authed(get("/api/v2/agent/read")
                .param("address", address).param("format", "terse")));
        String fix = "{\"ops\":[{\"op\":\"post.upsert\",\"address\":\"" + address
                + "\",\"data\":{\"headline\":\"Post 3\","
                + "\"body\":\"<p>Body 3 <a href=\\\"/blog/post-1\\\">more</a></p>\"}}]}";
        agent.call("fix", authed(post("/api/v2/agent/batch"))
                .contentType(MediaType.APPLICATION_JSON).content(fix), fix);
        agent.call("re-verify", authed(get("/api/v2/agent/verify")
                .param("site", siteName).param("format", "terse")));
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, task, NAIVE);
        naive.call("list the folder", authed(get("/api/v2/object/" + blogFolderId + "/list")));
        for (int i = 1; i <= 5; i++) {
            // No lint, so every candidate is read to find the one with the bad href.
            naive.call("read post " + i,
                    authed(get("/api/v2/post/xp/" + postIds.get(i - 1))));
        }
        String update = "{\"state\":\"DRAFT\",\"data\":{\"headline\":\"Post 3\","
                + "\"body\":\"<p>Body 3 <a href=\\\"/blog/post-1\\\">more</a></p>\"}}";
        naive.call("update", authed(put("/api/v2/post-unified/" + postIds.get(2)))
                .contentType(MediaType.APPLICATION_JSON).content(update), update);
        record(naive);

        assertTask(task);
    }

    /**
     * "Translate a site."
     *
     * <p>Find the pages, read them, write the translations in one transaction. The
     * baseline has no way to ask for "the posts of this type in this subtree" without
     * walking the tree and reading each post in full.</p>
     */
    @Test
    void translateSite() throws Exception {
        String task = "translate-site";
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        ShAgentBenchmark agent = new ShAgentBenchmark(mockMvc, task, AGENT);
        agent.call("find", authed(get("/api/v2/agent/find")
                .param("site", siteName).param("type", articleType).param("format", "terse")));
        MockHttpServletRequestBuilder read = authed(get("/api/v2/agent/read")
                .param("format", "terse"));
        for (int i = 1; i <= TRANSLATED_POSTS; i++) {
            read = read.param("address", "post:" + siteName + "/blog/post-" + i);
        }
        agent.call("read them all in one call", read);
        StringBuilder ops = new StringBuilder("{\"ops\":[{\"op\":\"folder.upsert\",\"address\":\"folder:"
                + siteName + "/pt-" + suffix + "\"}");
        for (int i = 1; i <= TRANSLATED_POSTS; i++) {
            ops.append(",{\"op\":\"post.upsert\",\"address\":\"post:").append(siteName)
                    .append("/pt-").append(suffix).append("/post-").append(i)
                    .append("\",\"type\":\"").append(articleType)
                    .append("\",\"folder\":\"/pt-").append(suffix)
                    .append("\",\"data\":{\"headline\":\"Publicação ").append(i).append("\"}}");
        }
        String batch = ops.append("]}").toString();
        agent.call("write the translations atomically", authed(post("/api/v2/agent/batch"))
                .contentType(MediaType.APPLICATION_JSON).content(batch), batch);
        agent.call("verify", authed(get("/api/v2/agent/verify")
                .param("site", siteName).param("format", "terse")));
        record(agent);

        ShAgentBenchmark naive = new ShAgentBenchmark(mockMvc, task, NAIVE);
        naive.call("list the tree", authed(get("/api/v2/object/" + rootFolderId + "/list")));
        naive.call("list the folder", authed(get("/api/v2/object/" + blogFolderId + "/list")));
        for (int i = 1; i <= TRANSLATED_POSTS; i++) {
            naive.call("read post " + i,
                    authed(get("/api/v2/post/xp/" + postIds.get(i - 1))));
        }
        String folderBody = "{\"name\":\"pt-naive-" + suffix + "\"}";
        String targetFolder = id(naive.call("create the folder",
                authed(post("/api/v2/folder/" + rootFolderId))
                        .contentType(MediaType.APPLICATION_JSON).content(folderBody), folderBody));
        for (int i = 1; i <= TRANSLATED_POSTS; i++) {
            String body = "{\"folderId\":\"" + targetFolder + "\",\"postTypeId\":\"" + articleTypeId
                    + "\",\"state\":\"DRAFT\",\"data\":{\"headline\":\"Publicação " + i + "\"}}";
            naive.call("create translation " + i, authed(post("/api/v2/post-unified"))
                    .contentType(MediaType.APPLICATION_JSON).content(body), body);
        }
        record(naive);

        assertTask(task);
    }

    // ------------------------------------------------------------- assertions

    /**
     * The guard. Three claims per task, each with its own ceiling in the budgets file:
     * the agent path's call count, its token cost, and how much cheaper it is than doing
     * the same work without the protocol.
     */
    private void assertTask(String task) {
        ShAgentBenchmark.Run agent = run(task, AGENT);
        ShAgentBenchmark.Run naive = run(task, NAIVE);
        agent.requireAllSucceeded();
        naive.requireAllSucceeded();

        assertThat(agent.calls())
                .as("%s: calls over the agent surface. Raising this ceiling means the "
                        + "protocol got chattier — say why in the commit.", task)
                .isLessThanOrEqualTo(budget(task, "calls"));
        assertThat(agent.tokens())
                .as("%s: estimated tokens over the agent surface (P3: a token regression "
                        + "is a build failure, not a footnote).", task)
                .isLessThanOrEqualTo(budget(task, "tokens"));
        assertThat(ratio(naive.calls(), agent.calls()))
                .as("%s: the agent path must stay at least this many times cheaper in "
                        + "round trips than the id-keyed one. A falling ratio means the "
                        + "baseline improved or the protocol regressed; both are worth "
                        + "knowing.", task)
                .isGreaterThanOrEqualTo(budget(task, "min-call-ratio"));
        assertThat(ratio(naive.tokens(), agent.tokens()))
                .as("%s: the agent path must stay at least this many times cheaper in "
                        + "tokens than the id-keyed one.", task)
                .isGreaterThanOrEqualTo(budget(task, "min-token-ratio"));
    }

    /** Integer tenths, so a ratio lives in the same properties file as every other number. */
    private static int ratio(int baseline, int agent) {
        return agent == 0 ? 0 : (baseline * 10) / agent;
    }

    private int budget(String task, String key) {
        String property = "benchmark." + task + "." + key;
        String value = budgets.getProperty(property);
        assertThat(value)
                .as("No budget for %s. Every measured task needs a declared ceiling, or "
                        + "the benchmark records history instead of guarding it.", property)
                .isNotNull();
        return Integer.parseInt(value.trim());
    }

    private ShAgentBenchmark.Run run(String task, String path) {
        return runs.stream()
                .filter(run -> run.task().equals(task) && run.path().equals(path))
                .findFirst().orElseThrow();
    }

    private void record(ShAgentBenchmark benchmark) {
        runs.add(benchmark.finish());
    }

    /**
     * The artifact. Written even when an assertion failed, because the numbers are how
     * somebody decides whether to fix the regression or move the ceiling.
     */
    @AfterAll
    void writeReport() throws IOException {
        if (runs.isEmpty()) {
            return;
        }
        Files.createDirectories(REPORT.getParent());
        Files.write(REPORT, ShAgentBenchmark.report(runs));
        System.out.println("\nSH112 agent task benchmark (" + REPORT + "):\n"
                + ShAgentBenchmark.table(runs));
    }

    // ----------------------------------------------------------------- bodies

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.principal(principal);
    }

    private String createPost(String folderId, String headline) {
        return "{\"folderId\":\"" + folderId + "\",\"postTypeId\":\"" + articleTypeId
                + "\",\"state\":\"DRAFT\",\"data\":{\"headline\":\"" + headline + "\"}}";
    }

    private static String postTypeDef(String name) {
        return "{\"name\":\"" + name + "\",\"title\":\"Note\",\"fields\":["
                + "{\"name\":\"headline\",\"label\":\"Headline\",\"widget\":\"Text\","
                + "\"required\":true,\"isTitle\":true,\"ordinal\":1}]}";
    }

    /**
     * A post-type as the <em>console</em> creates one: the same name-keyed surface the
     * CLI uses, with {@code managed:false} marking it a hand edit (SH117 retired the
     * id-keyed twin this used to POST to).
     */
    private static String consolePostType(String name) {
        return "{\"name\":\"" + name + "\",\"title\":\"Note\",\"namePlural\":\"Notes\","
                + "\"managed\":false,"
                + "\"fields\":[{\"name\":\"headline\",\"label\":\"Headline\","
                + "\"ordinal\":1,\"required\":true,\"isTitle\":true,\"widget\":\"Text\"}]}";
    }

    private String batchOfPosts(String type, String prefix) {
        StringBuilder ops = new StringBuilder("{\"ops\":[");
        for (int i = 1; i <= BULK_POSTS; i++) {
            if (i > 1) {
                ops.append(',');
            }
            ops.append("{\"op\":\"post.upsert\",\"address\":\"post:").append(siteName)
                    .append("/blog/").append(prefix).append('-').append(i)
                    .append("\",\"type\":\"").append(type)
                    .append("\",\"folder\":\"/blog\",\"data\":{\"headline\":\"Note ")
                    .append(i).append("\"}}");
        }
        return ops.append("]}").toString();
    }

    /** Pulls an {@code id} out of a JSON response without a full parse. */
    private static String id(org.springframework.test.web.servlet.MvcResult result)
            throws java.io.UnsupportedEncodingException {
        String body = result.getResponse().getContentAsString();
        int at = body.indexOf("\"id\":\"");
        if (at < 0) {
            throw new IllegalStateException("No id in response: " + body);
        }
        int from = at + 6;
        return body.substring(from, body.indexOf('"', from));
    }
}
