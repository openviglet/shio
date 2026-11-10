/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentChangeCursor;
import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.form.ShFormSubmissionAPI.ShFormSubmission;
import com.viglet.shio.api.form.dto.ShSiteFormRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Where a rendered form posts (SH633).
 *
 * <h2>What is worth asserting</h2>
 *
 * <p>The endpoint is deliberately ordinary — SH631 bounded the scope and SH632 owned the
 * destination, so this writes one {@code DRAFT} post or refuses. What the tests hold is the set of
 * things that would be a security defect rather than a bug:</p>
 *
 * <ul>
 *   <li><b>{@code DRAFT}, never anything else.</b> The state is not a parameter, so the only way to
 *       prove it is to submit and read the row back.</li>
 *   <li><b>A system post-type is refused</b> even when the site's list names it — the one predicate
 *       the whole endpoint's severity turns on, asserted here at the boundary and not only in
 *       {@code ShFormEligibilityTest}, because that is where it is actually reachable from.</li>
 *   <li><b>A disabled site refuses</b>, so SH632's default is load-bearing rather than decorative.</li>
 *   <li><b>The honeypot answers exactly as a success does</b> and writes nothing. A refusal that
 *       named the field would be a free description of how to evade it.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShFormSubmissionAPITest {

    @Autowired
    private ShFormSubmissionAPI api;
    @Autowired
    private ShSiteFormAPI configApi;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShAgentChangesService changesService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String siteId;
    private String contactType;
    private String enquiriesFolderId;

    @BeforeAll
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh633-" + suffix;
        contactType = "Sh633Contact" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setPosition((byte) 1);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShFolder enquiries = new ShFolder();
            enquiries.setName("enquiries");
            enquiries.setRootFolder((byte) 0);
            enquiries.setParentFolder(root);
            enquiries.setPosition((byte) 1);
            enquiries.setDate(when);
            enquiries.setOwner("admin");
            entityManager.persist(enquiries);
            enquiriesFolderId = enquiries.getId();

            postType(contactType, (byte) 0, when);
            postType(ShSystemPostType.THEME, (byte) 1, when);
        });
    }

    private ShFormSubmission submission(String type, Map<String, String> fields, String honeypot) {
        ObjectNode data = objectMapper.createObjectNode();
        fields.forEach(data::put);
        return new ShFormSubmission(type, data, honeypot);
    }

    /** Configure the site fresh for one test, so each names the state it depends on. */
    private void configure(String folderUrl, String allowedTypes, boolean enabled) {
        configApi.list().stream()
                .filter(form -> siteId.equals(form.getSiteId()))
                .forEach(form -> configApi.delete(form.getId()));
        configApi.create(new ShSiteFormRequest(siteId, folderUrl, allowedTypes, enabled,
                "https://www.acme.com"), null);
    }

    private long submissionsInSite() {
        return shPostUnifiedRepository.findAll().stream()
                .filter(post -> post.getShSite() != null && siteId.equals(post.getShSite().getId()))
                .map(ShPostUnified::getId)
                .distinct()
                .count();
    }

    @Test
    @DisplayName("SH633: a submission lands as a DRAFT post in the configured folder")
    void aSubmissionLandsAsADraft() {
        configure("/enquiries", contactType, true);

        ResponseEntity<Object> response = api.submit(siteName, null,
                submission(contactType, Map.of("TITLE", "Hello", "MESSAGE", "Please call"), null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("received")).isEqualTo(true);
        assertThat(body.get("state"))
                .as("""
                        DRAFT is not a parameter and never becomes one: the review queue is the \
                        moderation step, so a submission a stranger could publish is not a \
                        submission (SH633)""")
                .isEqualTo("DRAFT");

        List<ShPostUnified> rows = shPostUnifiedRepository.findById((String) body.get("id"));
        assertThat(rows).singleElement().satisfies(post -> {
            assertThat(post.getState()).isEqualTo(ShPostState.DRAFT);
            // By id, not by name: the row is read outside the session that loaded it, and a
            // lazy proxy answers getId() without initialising while getName() throws.
            assertThat(post.getShFolder().getId()).isEqualTo(enquiriesFolderId);
            // The token, not a person: an anonymous submission has no user to attribute, and naming
            // the credential is what an operator needs when one form starts producing rubbish.
            assertThat(post.getOwner()).startsWith("form");
            assertThat(post.getData()).contains("Please call");
        });
    }

    @Test
    @DisplayName("SH633: a system post-type is refused even when the site's list names it")
    void aSystemTypeIsRefusedFromTheEndpoint() {
        // The operator's mistake SH631 said was not worth leaving available, asserted where it is
        // actually reachable from. Theme is Handlebars the renderer compiles and executes, so this
        // is the difference between a spam problem and remote template execution.
        configure("/enquiries", contactType + "," + ShSystemPostType.THEME, true);
        long before = submissionsInSite();

        ResponseEntity<Object> response = api.submit(siteName, null,
                submission(ShSystemPostType.THEME, Map.of("CSS", "body{}"), null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString())
                .as("and the refusal names what the site does invite, so the operator can see their "
                        + "own mistake (P6)")
                .contains(contactType);
        assertThat(response.getBody().toString())
                .as("but never echoes Theme back as though it were on offer")
                .doesNotContain("invites: " + ShSystemPostType.THEME);
        assertThat(submissionsInSite()).isEqualTo(before);
    }

    @Test
    @DisplayName("SH633: a site that has not enabled submissions refuses them")
    void aDisabledSiteRefuses() {
        configure("/enquiries", contactType, false);
        long before = submissionsInSite();

        ResponseEntity<Object> response = api.submit(siteName, null,
                submission(contactType, Map.of("TITLE", "Hello"), null));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().toString())
                .as("SH632's default is load-bearing: creating the configuration is not opening "
                        + "the door, and the endpoint is what makes that true")
                .contains("not accepting form submissions");
        assertThat(submissionsInSite()).isEqualTo(before);
    }

    @Test
    @DisplayName("SH633: a configured folder that does not exist refuses rather than creating one")
    void aMissingFolderRefuses() {
        configure("/nowhere", contactType, true);
        long before = submissionsInSite();

        ResponseEntity<Object> response = api.submit(siteName, null,
                submission(contactType, Map.of("TITLE", "Hello"), null));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().toString())
                .as("a public path that can create folders is a public path that can fill a disk")
                .contains("/nowhere");
        assertThat(submissionsInSite()).isEqualTo(before);
    }

    @Test
    @DisplayName("SH633: a tripped honeypot is indistinguishable from a success, and writes nothing")
    void theHoneypotIsSilent() {
        configure("/enquiries", contactType, true);
        long before = submissionsInSite();

        ResponseEntity<Object> tripped = api.submit(siteName, null,
                submission(contactType, Map.of("TITLE", "Buy pills"), "i-am-a-bot"));

        assertThat(tripped.getStatusCode().value()).isEqualTo(200);
        assertThat(tripped.getBody())
                .as("""
                        exactly what a success answers minus the id — an error naming the field \
                        would be a free description of how to evade it, so the response carries no \
                        signal a bot could tune against (SH633)""")
                .isEqualTo(Map.of("received", true));
        assertThat(submissionsInSite())
                .as("and nothing is written")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("SH633: the honeypot is stripped from a legitimate submission's fields")
    void theHoneypotIsNotStoredWhenEmpty() {
        configure("/enquiries", contactType, true);

        ObjectNode data = objectMapper.createObjectNode();
        data.put("TITLE", "Hello");
        // Present and empty, which is what a real browser posts from a hidden input nobody typed in.
        data.put(ShFormSubmissionAPI.HONEYPOT_FIELD, "");
        ResponseEntity<Object> response = api.submit(siteName, null,
                new ShFormSubmission(contactType, data, null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(shPostUnifiedRepository.findById((String) body.get("id")))
                .singleElement()
                .satisfies(post -> assertThat(post.getData())
                        .as("a submission's fields are what the author asked for — a spam-control "
                                + "artefact in every content record is not content")
                        .doesNotContain(ShFormSubmissionAPI.HONEYPOT_FIELD));
    }

    @Test
    @DisplayName("SH637: a browser origin the site has not named is refused, and writes nothing")
    void anUnnamedOriginIsRefused() {
        configure("/enquiries", contactType, true);
        long before = submissionsInSite();

        ResponseEntity<Object> response = api.submit(siteName, "https://evil.example",
                submission(contactType, Map.of("TITLE", "Hello"), null));

        assertThat(response.getStatusCode().value())
                .as("""
                        CORS is not the control — it protects the browser and a non-browser client                         ignores it, so an origin allowed only there is allowed only against callers                         who were going to behave. This check runs on every caller (SH637)""")
                .isEqualTo(403);
        assertThat(response.getBody().toString()).contains("allowedOrigins");
        assertThat(submissionsInSite()).isEqualTo(before);
    }

    @Test
    @DisplayName("SH637: the origin the site named is accepted, matched exactly")
    void theNamedOriginIsAccepted() {
        configure("/enquiries", contactType, true);

        assertThat(api.submit(siteName, "https://www.acme.com",
                submission(contactType, Map.of("TITLE", "Hello"), null))
                .getStatusCode().value())
                .isEqualTo(200);

        // Exactly, and that is the point: suffix matching is how `acme.com` comes to accept
        // `evil-acme.com`, which is the classic form of this defect.
        assertThat(api.submit(siteName, "https://evil-www.acme.com",
                submission(contactType, Map.of("TITLE", "Hello"), null))
                .getStatusCode().value())
                .as("no suffix matching and no wildcards (SH637)")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("SH637: a caller sending no Origin at all is unaffected")
    void aServerToServerCallerIsUnaffected() {
        // `Origin` is something a BROWSER adds. Refusing its absence would break the documented
        // curl while stopping nobody, since a caller who wanted to lie about it would simply not
        // send one. What the header is good for is the case it is honest in: a browser cannot
        // forge it, so a page on an origin this site has not named cannot spend the token in its
        // own markup.
        configure("/enquiries", contactType, true);

        assertThat(api.submit(siteName, null,
                submission(contactType, Map.of("TITLE", "Hello"), null))
                .getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("SH637: a site with no origins named accepts no browser at all")
    void anEmptyListAllowsNoBrowser() {
        configApi.list().stream()
                .filter(form -> siteId.equals(form.getSiteId()))
                .forEach(form -> configApi.delete(form.getId()));
        configApi.create(new ShSiteFormRequest(siteId, "/enquiries", contactType, true, null), null);

        assertThat(api.submit(siteName, "https://www.acme.com",
                submission(contactType, Map.of("TITLE", "Hello"), null))
                .getStatusCode().value())
                .as("""
                        empty means none rather than any — the direction every default on this                         path takes, and the safe one when the caller is the internet (SH632, P7)""")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("SH970: a submission arrives on the change feed as one, with its caption")
    void sh970ASubmissionArrivesOnTheFeedWithItsCaption() {
        configure("/enquiries", contactType, true);
        String since = new ShAgentChangeCursor(Instant.now(), "").toString();

        ResponseEntity<Object> response = api.submit(siteName, null, submission(contactType,
                Map.of("TITLE", "Jane Doe", "MESSAGE", "Acme Ltd, asking about pricing"), null));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ShPostType type = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", contactType).getSingleResult();
        ObjectNode made = objectMapper.createObjectNode().put("TITLE", "Opening hours");
        writeService.create(new CreateRequest(enquiriesFolderId, type.getId(), made,
                ShPostState.DRAFT, "curator", null, null));

        List<ShAgentChanges.Change> changes =
                changesService.changes(siteName, since, 100, true, 0).changes();

        assertThat(changes)
                .as("SH970: the arrival is on the feed an open screen reads, marked as a submission"
                        + " with the title and summary a caption is built from")
                .anySatisfy(change -> {
                    assertThat(change.action()).isEqualTo("CREATE");
                    assertThat(change.address()).isNotBlank();
                    assertThat(change.submission()).isEqualTo(new ShAgentChanges.Submission(
                            "Jane Doe", "Acme Ltd, asking about pricing"));
                });
        assertThat(changes)
                .as("SH970: a post a curator made in the same folder is not an arrival")
                .anySatisfy(change -> {
                    assertThat(change.what()).contains("Opening hours");
                    assertThat(change.submission()).isNull();
                });
    }

    @Test
    @DisplayName("SH970: a tripped honeypot puts nothing on the feed")
    void sh970ATrippedHoneypotReportsNothing() {
        configure("/enquiries", contactType, true);
        String since = new ShAgentChangeCursor(Instant.now(), "").toString();

        api.submit(siteName, null, submission(contactType, Map.of("TITLE", "Buy pills"), "i-am-a-bot"));

        assertThat(changesService.changes(siteName, since, 100, true, 0).changes())
                .as("SH970: a refused bot must not become a report — the honeypot answers like a"
                        + " success, and the feed is where that pretence has to stop")
                .noneMatch(change -> change.submission() != null);
    }

    /** What a browser posts from a plain {@code <form method="post">} with no script. */
    private MultiValueMap<String, String> formBody(String type, Map<String, String> fields) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ShFormSubmissionAPI.TYPE_FIELD, type);
        params.add(ShFormSubmissionAPI.HONEYPOT_FIELD, "");
        params.add("shio_key", "the-token-the-filter-already-read");
        fields.forEach(params::add);
        return params;
    }

    @Test
    @DisplayName("SH1382: a form with no script submits, and is sent back to its page with sent")
    void sh1382AScriptFreeFormSubmitsAndIsSentBack() {
        configure("/enquiries", contactType, true);
        long before = submissionsInSite();

        ResponseEntity<Void> response = api.submitForm(siteName, "https://www.acme.com",
                "https://www.acme.com/contact?shio_form=error&lang=en",
                formBody(contactType, Map.of("TITLE", "Hello", "MESSAGE", "Please call")));

        assertThat(response.getStatusCode().value())
                .as("SH1382: a browser follows a 303 with a GET, so the visitor lands on a page")
                .isEqualTo(303);
        assertThat(response.getHeaders().getLocation()).hasToString(
                "https://www.acme.com/contact?lang=en&shio_form=sent");
        assertThat(submissionsInSite()).isEqualTo(before + 1);
        assertThat(shPostUnifiedRepository.findAll().stream()
                .filter(post -> post.getShSite() != null && siteId.equals(post.getShSite().getId()))
                .filter(post -> post.getData() != null && post.getData().contains("Please call")))
                .as("SH1382: the steering fields are not content")
                .isNotEmpty()
                .allSatisfy(post -> assertThat(post.getData())
                        .doesNotContain(ShFormSubmissionAPI.TYPE_FIELD)
                        .doesNotContain("shio_key")
                        .doesNotContain(ShFormSubmissionAPI.HONEYPOT_FIELD));
    }

    @Test
    @DisplayName("SH1382: a refusal sends a script-free form back with error, never as JSON")
    void sh1382ARefusalIsSentBackWithError() {
        configure("/enquiries", contactType, false);

        ResponseEntity<Void> response = api.submitForm(siteName, "https://www.acme.com",
                "https://www.acme.com/contact", formBody(contactType, Map.of("TITLE", "Hello")));

        assertThat(response.getStatusCode().value()).isEqualTo(303);
        assertThat(response.getHeaders().getLocation())
                .hasToString("https://www.acme.com/contact?shio_form=error");
    }

    @Test
    @DisplayName("SH1382: a Referer on an origin the site has not named is followed by path only")
    void sh1382TheRedirectIsNeverOpen() {
        configure("/enquiries", contactType, true);

        ResponseEntity<Void> response = api.submitForm(siteName, null,
                "https://evil.example/landing", formBody(contactType, Map.of("TITLE", "Hello")));

        assertThat(response.getHeaders().getLocation())
                .as("SH1382: a forged Referer must not turn the endpoint into an open redirect")
                .hasToString("/landing?shio_form=sent");
    }

    @Test
    @DisplayName("SH633: an unknown site is refused before anything else is read")
    void anUnknownSiteRefuses() {
        ResponseEntity<Object> response = api.submit("sh633-no-such-site", null,
                submission(contactType, Map.of("TITLE", "Hello"), null));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    // --- fixture ---

    // --- SH1414: the draft is written in the language of the page that held the form ---

    private String localeOfDraft(ResponseEntity<Object> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        String id = (String) ((Map<String, Object>) response.getBody()).get("id");
        return shPostUnifiedRepository.findById(id).getFirst().getLocale();
    }

    @Test
    @DisplayName("SH1414: a form sent from a pt-br page lands as a pt-br draft; en-us, an unknown locale or no page get the default")
    void aSubmissionIsWrittenInThePagesLanguage() {
        configure("/enquiries", contactType, true);
        // pt-br becomes one of the site's locales the way it does in use: a page published in it.
        String typeId = new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("select t.id from ShPostType t where t.name = :name", String.class)
                .setParameter("name", contactType).getSingleResult());
        writeService.create(new CreateRequest(enquiriesFolderId, typeId,
                objectMapper.createObjectNode().put("TITLE", "Contato " + UUID.randomUUID()),
                ShPostState.PUBLISHED, "admin", "pt-br", null, null));
        String page = "https://www.acme.com/sites/" + siteName + "/default/";

        assertThat(localeOfDraft(api.submit(siteName, null, page + "pt-br/contato",
                submission(contactType, Map.of("TITLE", "Olá"), null))))
                .as("SH1414: the enquiry was sent from the Portuguese page, so it is a Portuguese draft")
                .isEqualTo("pt-br");
        assertThat(localeOfDraft(api.submit(siteName, null, page + "en-us/contact",
                submission(contactType, Map.of("TITLE", "Hello"), null))))
                .as("SH1414: en-us is the route's 'no locale named', so the site's default applies")
                .isEqualTo(ShPostUnifiedWriteService.DEFAULT_LOCALE);
        assertThat(localeOfDraft(api.submit(siteName, null, page + "de-de/kontakt",
                submission(contactType, Map.of("TITLE", "Hallo"), null))))
                .as("SH1414: the Referer is the stranger's to write, so a locale the site lacks is ignored")
                .isEqualTo(ShPostUnifiedWriteService.DEFAULT_LOCALE);
        assertThat(localeOfDraft(api.submit(siteName, null,
                submission(contactType, Map.of("TITLE", "From a server"), null))))
                .as("SH1414: an API client with no page gets the site's default")
                .isEqualTo(ShPostUnifiedWriteService.DEFAULT_LOCALE);
    }

    private void postType(String name, byte system, Instant when) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            // Instance-wide and shared with every other suite in this JVM; never mutated here,
            // because removing an attribute cascades a REMOVE into the widget catalogue (SH289).
            return;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);

        ShWidget widget = entityManager
                .createQuery("select w from ShWidget w where w.name = :name", ShWidget.class)
                .setParameter("name", ShSystemWidget.TEXT)
                .getResultStream().findFirst().orElseGet(() -> {
                    ShWidget created = new ShWidget();
                    created.setName(ShSystemWidget.TEXT);
                    created.setDescription(ShSystemWidget.TEXT);
                    entityManager.persist(created);
                    return created;
                });
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName("TITLE");
        attr.setLabel("TITLE");
        attr.setOrdinal(1);
        attr.setIsTitle((byte) 1);
        attr.setRequired((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(type);
        entityManager.persist(attr);

        // SH970 — the field a report's caption takes its second line from.
        ShPostTypeAttr summary = new ShPostTypeAttr();
        summary.setName("MESSAGE");
        summary.setLabel("MESSAGE");
        summary.setOrdinal(2);
        summary.setIsSummary((byte) 1);
        summary.setRequired((byte) 0);
        summary.setShWidget(widget);
        summary.setShPostType(type);
        entityManager.persist(summary);
    }
}
