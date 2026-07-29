/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import com.sun.net.httpserver.HttpServer;
import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentNoteRequest;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH141 — proving a route from the delivery side.
 *
 * <h2>Why this test runs a real HTTP server</h2>
 *
 * <p>The defect the check exists for is a <em>disagreement between two systems</em>: the page
 * is in the CMS and the site does not serve it. A mocked fetch cannot fail that way — it fails
 * however the mock was written — so the fixture is a front end: a loopback server that serves
 * three paths and 404s everything else, and a site whose URL points at it. What is asserted is
 * that the report distinguishes served from not-served, that a declared route is proved rather
 * than assumed, and that an unreachable front end produces <b>one</b> finding rather than one
 * per page.</p>
 *
 * <p>The context sets {@code shio.agent.render.allow-fetch=true}, which is the property an
 * operator sets to consent to server-side fetching at all — and which also puts
 * {@code delivery} in the default check set, so the run with no {@code checks=} argument
 * exercises the default path rather than an opt-in nobody would remember to pass.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.agent.render.allow-fetch=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentDeliveryTest {

    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentMemoryService shAgentMemoryService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /** The front end. Serves the three paths below; everything else is a 404. */
    private static HttpServer frontEnd;

    private static final Set<String> SERVED = Set.of("/served", "/links", "/tags/java");

    private String siteName;
    private String urllessSiteName;
    private String downSiteName;
    private String postTypeName;

    @BeforeAll
    void setup() throws IOException {
        frontEnd = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        frontEnd.createContext("/", exchange -> {
            int status = SERVED.contains(exchange.getRequestURI().getPath()) ? 200 : 404;
            byte[] body = ("<html><body><h1>" + status + "</h1></body></html>")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        frontEnd.start();
        String origin = "http://127.0.0.1:" + frontEnd.getAddress().getPort();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            siteName = "delivery-" + UUID.randomUUID().toString().substring(0, 8);
            site(siteName, origin);
            urllessSiteName = "urlless-" + UUID.randomUUID().toString().substring(0, 8);
            site(urllessSiteName, null);
            // A URL nothing listens on: the connection is refused immediately on loopback,
            // so "the front end is down" is testable without waiting out a timeout.
            downSiteName = "down-" + UUID.randomUUID().toString().substring(0, 8);
            site(downSiteName, "http://127.0.0.1:" + closedPort());

            ShPostType postType = new ShPostType();
            postTypeName = "DeliveryPage" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Delivery Page");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);
            attr(postType, ShSystemWidget.TEXT, "headline", 1, (byte) 1);
            attr(postType, ShSystemWidget.HTML_EDITOR, "body", 2, (byte) 0);
        });

        // The front end owns /tags/* — declared in the SH114 memory, which is the only place
        // SH141 accepts the claim from.
        shAgentMemoryService.remember(
                new ShAgentNoteRequest(siteName, ShFrontEndRoutes.MEMORY_KEY, "/tags/*"), "tester");

        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + "/", null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + "/served",
                        postTypeName, "folder:" + siteName + "/", data("Served", null)),
                // Published in the CMS, absent from the site: the failure §IX names as the most
                // common one, and the one the content graph cannot see.
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + "/missing",
                        postTypeName, "folder:" + siteName + "/", data("Missing", null)),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + "/links",
                        postTypeName, "folder:" + siteName + "/",
                        data("Links", "<p><a href=\"/tags/java\">tag</a>"
                                + "<a href=\"/gone\">gone</a></p>"))));
        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + "/served",
                        null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + "/missing",
                        null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + "/links",
                        null, null, null)));

        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + urllessSiteName + "/",
                        null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + urllessSiteName + "/page",
                        postTypeName, "folder:" + urllessSiteName + "/", data("Urlless", null))));
        tx.executeWithoutResult(status -> run(new Op(ShAgentBatchService.POST_PUBLISH,
                "post:" + urllessSiteName + "/page", null, null, null)));

        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + downSiteName + "/",
                        null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + downSiteName + "/one",
                        postTypeName, "folder:" + downSiteName + "/", data("One", null)),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + downSiteName + "/two",
                        postTypeName, "folder:" + downSiteName + "/", data("Two", null))));
        tx.executeWithoutResult(status -> run(
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + downSiteName + "/one",
                        null, null, null),
                new Op(ShAgentBatchService.POST_PUBLISH, "post:" + downSiteName + "/two",
                        null, null, null)));
    }

    @AfterAll
    void tearDown() {
        if (frontEnd != null) {
            frontEnd.stop(0);
        }
    }

    // --- the pass itself -----------------------------------------------------

    @Test
    @DisplayName("a published page the site does not serve is an error, and a served one is silent")
    void routeNotServed() {
        ShAgentReport report = shAgentVerifyService.verify(siteName, "delivery", 200);

        assertThat(rules(report, "route-not-served"))
                .extracting(Finding::address)
                .containsExactly("post:" + siteName + "/missing");
        assertThat(rules(report, "route-not-served"))
                .allSatisfy(finding -> assertThat(finding.severity())
                        .isEqualTo(ShAgentReport.ERROR));
        // The whole point of the block: the content graph called this site clean.
        assertThat(report.ok()).isFalse();
        assertThat(report.checks()).containsExactly("delivery");
    }

    @Test
    @DisplayName("SH302: a clean pass says how much it proved, per site")
    void coverageIsANoteAndNotSilence() {
        ShAgentReport report = shAgentVerifyService.verify(siteName, "delivery", 200);

        assertThat(report.notes())
                .as("""
                        Findings appear only on failure, so a run that proved forty pages and a run \
                        that proved none used to read identically - and "silence is not evidence" is \
                        what this check is most easily wrong about.""")
                .anySatisfy(note -> assertThat(note)
                        .contains("The delivery check proved")
                        .contains("published page(s) of '" + siteName + "'"));
    }

    @Test
    @DisplayName("SH302: nothing this check fetches can carry a render marker, by construction")
    void everyFetchedPageIsRenderedElsewhere() {
        // The measurement that dissolved SH302's proposal. Counting SH299's defect markers in the
        // *delivered* body would have been a strictly stronger claim — except the pass only ever
        // fetches a site that has its own front-end URL: a site without one gets `site-url-missing`
        // and is never fetched at all, and naming `delivery` for it is a teaching 400. So every body
        // it could parse was rendered by somebody else's front end from the CDA and carries no
        // `data-shio-*`. A marker count could only ever have printed zero, which is the "covered
        // nothing while answering ok" failure SH141 warns about. Asserted here so the idea cannot come
        // back as a plausible-sounding feature.
        ShAgentReport withUrl = shAgentVerifyService.verify(siteName, "delivery", 200);
        assertThat(rules(withUrl, "site-url-missing"))
                .as("a site with a front-end URL is fetched, and that is the only kind that is")
                .isEmpty();

        ShAgentReport withoutUrl = shAgentVerifyService.verify(urllessSiteName, null, 200);
        assertThat(rules(withoutUrl, "site-url-missing"))
                .as("and Shio's own /preview - the only render that carries markers - is never a "
                        + "delivery target: it is reported as unprovable instead")
                .hasSize(1);
        // `notes()` is absent rather than empty when a run has nothing to say (SH78's lean JSON), so
        // the assertion has to allow for that — and a run that fetched nothing having no coverage note
        // at all is the property, not a technicality.
        assertThat(withoutUrl.notes() == null ? List.<String>of() : withoutUrl.notes())
                .as("so no coverage note claims to have proved anything for it")
                .noneSatisfy(note -> assertThat(note)
                        .contains("published page(s) of '" + urllessSiteName + "'"));
    }

    @Test
    @DisplayName("an unresolved link is proved: served means silence, 404 means an error")
    void unresolvedLinksAreAdjudicated() {
        ShAgentReport report = shAgentVerifyService.verify(siteName, "content,routes,delivery", 200);

        // /tags/java is declared as the front end's *and* the front end serves it, so neither
        // the routes warning nor a delivery error survives — this is the noise SH141 removes.
        assertThat(all(report))
                .noneSatisfy(finding -> assertThat(finding.message()).contains("/tags/java"));
        assertThat(rules(report, "link-unresolved")).isEmpty();

        assertThat(rules(report, "link-broken"))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.address()).isEqualTo("post:" + siteName + "/links");
                    assertThat(finding.field()).isEqualTo("body");
                    assertThat(finding.message()).contains("/gone").contains("404");
                    assertThat(finding.severity()).isEqualTo(ShAgentReport.ERROR);
                });
    }

    @Test
    @DisplayName("delivery is in the default set once an operator enables fetching")
    void deliveryJoinsTheDefaults() {
        ShAgentReport report = shAgentVerifyService.verify(siteName, null, 200);

        assertThat(report.checks()).contains("delivery");
        assertThat(rules(report, "route-not-served")).isNotEmpty();
    }

    @Test
    @DisplayName("with delivery off, a declared route is silenced and the report says how many")
    void declaredRoutesSilenceTheGuessWhenNothingCanBeProved() {
        ShAgentReport report = shAgentVerifyService.verify(siteName, "content,routes", 200);

        assertThat(report.checks()).doesNotContain("delivery");
        // /tags/java is declared, so no warning; /gone is not, so it keeps one.
        assertThat(rules(report, "link-unresolved"))
                .singleElement()
                .satisfies(finding -> assertThat(finding.message()).contains("/gone"));
        // Suppression is never invisible — the same rule SH142 follows for accepted findings.
        assertThat(report.notes())
                .anySatisfy(note -> assertThat(note)
                        .contains("1 unresolved link(s)")
                        .contains(ShFrontEndRoutes.MEMORY_KEY));
    }

    // --- what it refuses to pretend -----------------------------------------

    @Test
    @DisplayName("a front end that does not answer is one finding, not one per page")
    void unreachableFrontEndFailsFast() {
        ShAgentReport report = shAgentVerifyService.verify(downSiteName, "delivery", 200);

        assertThat(rules(report, "delivery-unreachable"))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.address()).isEqualTo("site:" + downSiteName);
                    assertThat(finding.fix()).contains("checks=content,routes");
                });
        // Its two pages are unknowable, not broken: reporting them would be inventing defects.
        assertThat(rules(report, "route-not-served")).isEmpty();
    }

    @Test
    @DisplayName("a site with no front-end URL is a warning, and asking explicitly is a 400")
    void noOriginIsAWarningUnlessItWasAskedFor() {
        ShAgentReport implicit = shAgentVerifyService.verify(urllessSiteName, null, 200);
        assertThat(rules(implicit, "site-url-missing"))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity()).isEqualTo(ShAgentReport.WARNING);
                    assertThat(finding.address()).isEqualTo("site:" + urllessSiteName);
                });
        // A warning does not fail the run: the default set must not break a call that never
        // asked to be proved.
        assertThat(implicit.errors()).isZero();

        assertThatThrownBy(() ->
                shAgentVerifyService.verify(urllessSiteName, "delivery", 200))
                .isInstanceOf(ShAgentVerifyService.NoDeliveryOriginException.class);
    }

    @Test
    @DisplayName("naming delivery on an instance that cannot fetch is a teaching 400")
    void namingDeliveryWithoutFetchingIsRefused() {
        // Parsed here rather than through a second Spring context: the refusal is a property of
        // the vocabulary, and the check group must never resolve to "ran and found nothing".
        assertThatThrownBy(() -> ShAgentCheck.parse("content,delivery", false))
                .isInstanceOf(ShAgentCheck.DeliveryDisabledException.class);
        assertThat(ShAgentCheck.defaults(false)).doesNotContain(ShAgentCheck.DELIVERY);
        assertThat(ShAgentCheck.defaults(true)).contains(ShAgentCheck.DELIVERY);
    }

    // --- fixture plumbing ---------------------------------------------------

    private static List<Finding> all(ShAgentReport report) {
        return report.findings();
    }

    private static List<Finding> rules(ShAgentReport report, String rule) {
        return report.findings().stream().filter(finding -> rule.equals(finding.rule())).toList();
    }

    private void run(Op... ops) {
        shAgentBatchService.run(new ShAgentBatch(false, List.of(ops)), false, "tester");
    }

    private JsonNode data(String headline, String body) {
        return body == null
                ? objectMapper.valueToTree(Map.of("headline", headline))
                : objectMapper.valueToTree(Map.of("headline", headline, "body", body));
    }

    private void site(String name, String url) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setUrl(url);
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
    }

    private void attr(ShPostType postType, String widgetName, String name, int ordinal,
                      byte required) {
        ShWidget widget = entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", widgetName)
                .getSingleResult();
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setRequired(required);
        attr.setIsTitle((byte) (ordinal == 1 ? 1 : 0));
        attr.setIsSummary((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    /** A port that was bound and released — the reliable way to get a refused connection. */
    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("no free port", e);
        }
    }
}
