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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH125 — the null/default omission policy for agent JSON, as an enforced rule.
 *
 * <h2>The policy, and why it is not simply "NON_DEFAULT everywhere"</h2>
 *
 * <p><b>Nulls are always waste.</b> {@code "url": null} and an absent {@code url}
 * carry identical information, so every agent DTO omits nulls. That part is
 * mechanical and is what this test enforces.</p>
 *
 * <p><b>Zero-defaults are a judgement, per DTO.</b> Dropping {@code false} and
 * {@code 0} is only correct where absence means the same thing as the default. It is
 * right for a sitemap row ({@code folders: 0} on a post row says nothing) and wrong —
 * actively harmful — in two places that were nearly caught by a blanket rule:</p>
 * <ul>
 *   <li>the manifest's {@code features} map, where a {@code false} is the <em>most
 *       important</em> thing the document says. Stripping it would send a caller
 *       probing for a capability, which is the exact cost the manifest exists to
 *       remove;</li>
 *   <li>{@code Budget.truncated} / {@code overBudget}, where {@code false} is the
 *       answer to "was anything cut?" — silence would read as "unknown".</li>
 * </ul>
 *
 * <p>So the rule is: <b>NON_NULL as the baseline, NON_DEFAULT only where a default
 * is genuinely uninformative.</b> "Make the payload smaller" is not a licence to drop
 * an answer.</p>
 *
 * <h2>The CDA followed, later and together</h2>
 *
 * <p>This policy was scoped to the agent surface at first. The CDA DTOs are a frozen
 * public contract whose TypeScript mirror in {@code @viglet/shio-client} declared
 * nullable fields as {@code string | null}, <em>not</em> optional — so omitting a null
 * would have turned a declared {@code null} into {@code undefined} and broken typed
 * consumers comparing against {@code null}. SH127 moved both halves in one change; the
 * delivery side of the rule now lives in {@code ShCdaJsonLeanTest}, and the one visible
 * consequence here is that the {@code model} section no longer inherits a null.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentJsonLeanTest {

    @Autowired
    private ShAgentManifestService shAgentManifestService;
    @Autowired
    private ShAgentContextService shAgentContextService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    /**
     * Its own fixture on purpose. An earlier draft read whatever the shared in-memory
     * database happened to contain, which made two assertions pass or fail depending
     * on which test class ran first — a leanness rule that only holds in a particular
     * test order is not a rule.
     */
    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "lean-site-" + UUID.randomUUID();
            site.setName(siteName);
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

            ShWidget widget = new ShWidget();
            widget.setName("Text");
            entityManager.persist(widget);

            ShPostType postType = new ShPostType();
            postType.setName("Lean" + UUID.randomUUID().toString().substring(0, 8));
            postType.setTitle("Lean");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            // No description — the field that surfaces the inherited CDA null.
            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);

            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(ShPostState.DRAFT);
            post.setShSite(site);
            post.setShFolder(root);
            post.setShPostType(postType);
            post.setTitle("Lean post");
            post.setFurl("/lean");
            post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            post.setData("{\"headline\":\"Lean post\"}");
            entityManager.persist(post);
        });
    }

    private ShAgentContext context(String include) {
        return shAgentContextService.context(siteName, null, 0, include, null);
    }

    @Test
    void noAgentOwnedResponseShipsANullValue() {
        List<Object> responses = List.of(
                shAgentManifestService.manifest(),
                // Every section the agent surface owns. `model` is excluded because it
                // is deliberately the CDA's shape, not ours — see the next test.
                context("instance,sites,sitemap,conventions"),
                // The truncated shape too: its cursor is the one field allowed to be
                // absent, and it must be *absent* rather than explicitly null.
                shAgentContextService.context(siteName, null, 1,
                        "instance,sites,sitemap,conventions", null));

        assertThat(responses).allSatisfy(response -> {
            String json = objectMapper.writeValueAsString(response);
            assertThat(json)
                    .withFailMessage("SH125: agent JSON must not ship nulls — absent and null say "
                            + "the same thing, so a null is pure cost. Offending payload:%n%s", json)
                    .doesNotContain(":null");
        });
    }

    @Test
    void theModelSectionIsLeanToo_sinceSH127() {
        // This assertion used to be its own inverse. The model section reuses
        // ShCdaSchemaField on purpose (P10: one field shape across the product), so
        // through SH125 it inherited that record's frozen null-carrying contract — an
        // unset `description` arrived as an explicit null, and the test pinned it to keep
        // the cost visible rather than let it read as an oversight.
        //
        // SH127 leaned the CDA DTOs and @viglet/shio-client's `| null` types in one
        // change, which is the only way that could ship, and the inherited null went with
        // them. The assertion is kept — inverted — because the composition is still real:
        // if someone re-introduces a null on the delivery shape, it surfaces here too.
        String json = objectMapper.writeValueAsString(context("model"));

        assertThat(json).doesNotContain(":null");
    }

    @Test
    void manifestKeepsFalseFeatureFlags() {
        // The counter-rule: leanness must not eat an answer.
        String json = objectMapper.writeValueAsString(shAgentManifestService.manifest());

        // Any still-unshipped capability will do; this one is SH102's visual snapshot.
        // (`mcp` used to be the example until SH75 shipped it, and `contentLint` until
        // SH99 did — the assertion has to track a flag that is actually false, or it
        // stops testing the rule. A flag flipping true is the *good* reason to edit this
        // line, which is why the trail of previous examples stays in the comment.)
        assertThat(json).contains("\"visualSnapshot\":false");
    }

    @Test
    void budgetKeepsFalseTruncationFlags() {
        String json = objectMapper.writeValueAsString(context(null).budget());

        assertThat(json).contains("\"truncated\":false");
    }

    @Test
    void rowShapedDtosDropUninformativeDefaults() {
        ShAgentContext.Node folder = context(null).sitemap().stream()
                .filter(node -> "folder".equals(node.kind()))
                .findFirst().orElseThrow();

        String json = objectMapper.writeValueAsString(folder);

        // A folder row says nothing about post-type/state/url/title.
        assertThat(json).doesNotContain("\"type\"").doesNotContain("\"state\"")
                .doesNotContain("\"url\"").doesNotContain("\"title\"");
    }

    @Test
    void theEstTokensHeaderNameIsStable() {
        // see SH112 and see SH113: both, and any client-side accounting, key off this exact
        // header, and it now covers the CDA too (SH124) — renaming it silently breaks all of them.
        // Spelled as references (SH731) because that is what they are: this assertion is about the
        // header's NAME, and the assertion that would have caught SH113 is the one that measures a
        // response against its ceiling, which lives in ShAgentResponseBudgetTest.
        assertThat(ShEstTokensHeaderFilter.HEADER).isEqualTo("X-Shio-Est-Tokens");
        assertThat(Map.of(ShEstTokensHeaderFilter.HEADER, "1")).containsKey("X-Shio-Est-Tokens");
    }
}
