/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.shio.api.agent.mcp.ShMcpPrompt;

import tools.jackson.databind.JsonNode;

/**
 * The MCP prompts (SH83): {@code new-site}, {@code add-section}, {@code translate-site},
 * {@code audit-content}.
 *
 * <h2>What a prompt is here</h2>
 *
 * <p>Each one renders a short <b>procedure</b> — the sequence of Shio calls that gets a
 * job done correctly, in the order that avoids the mistakes. It contains no instruction
 * about tone, content or judgment, because that is the user's business and Shio is not
 * in it (§0.7: Shio hosts no model and no prompts in the sense that matters — there is
 * no inference here).</p>
 *
 * <p>These are the cheapest thing in the whole block to get right and the easiest to
 * get wrong: a procedure that omits {@code dryRun}, or forgets that writes land in
 * DRAFT, teaches an agent to work in exactly the way P7 exists to prevent. So every one
 * of them plans before it writes and publishes as a separate, named step.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShMcpPrompts {

    @Bean
    ShMcpPrompt shMcpNewSitePrompt() {
        return prompt("new-site",
                "Build a new set of pages in a Shio site, safely and in few calls.",
                List.of(argument("site", "The site to build in.", true),
                        argument("brief", "What the pages should cover.", false)),
                arguments -> """
                        Build pages in the Shio site "%s".%s

                        Procedure:
                        1. shio_context(site) — read the model, the existing sitemap and this \
                        instance's conventions. Do not invent a post-type; use one that exists, \
                        or say which one is missing and why.
                        2. Read shio://blueprints. If a package already covers this shape (blog, \
                        docs, marketing site, landing page, product catalogue), apply it with \
                        shio_apply(site, blueprint, params, dryRun=true) and then without dryRun \
                        — it brings the model, the folders and the pages in one call. Build by \
                        hand only for what no package covers.
                        3. For the rest, draft the structure as a single shio_apply document \
                        (site, folders, posts with url/type/folder/data). One document, not one \
                        call per page.
                        4. Run it with dryRun=true and read the plan. Every line should be a \
                        change you intended; if anything says delete, stop and ask.
                        5. Run it again without dryRun.
                        6. Report the addresses you created. Do not publish — the content is in \
                        DRAFT and a human decides when it goes live. If they ask, publish with \
                        shio_publish.""".formatted(
                        text(arguments, "site", "(name the site)"),
                        brief(arguments)));
    }

    @Bean
    ShMcpPrompt shMcpAddSectionPrompt() {
        return prompt("add-section",
                "Add a section (a folder plus its pages) to an existing site.",
                List.of(argument("site", "The site to extend.", true),
                        argument("path", "Folder path for the new section, e.g. /pricing.", true),
                        argument("brief", "What the section should cover.", false)),
                arguments -> """
                        Add the section "%s" to the Shio site "%s".%s

                        Procedure:
                        1. shio_context(site) for the model, then shio_find(site, folder) on the \
                        nearest existing section and shio_read on one of its posts. Match what is \
                        already there — field usage, URL style, how the folder tree is shaped — \
                        rather than inventing a second convention alongside it.
                        2. Check shio://blueprints — if a package covers this section, applying it \
                        is one call instead of a modelling exercise.
                        3. Otherwise write the section as one shio_apply document scoped to that \
                        folder.
                        4. dryRun=true first, read the plan, then apply.
                        5. Leave it in DRAFT and report the addresses.""".formatted(
                        text(arguments, "path", "(name the path)"),
                        text(arguments, "site", "(name the site)"),
                        brief(arguments)));
    }

    @Bean
    ShMcpPrompt shMcpTranslateSitePrompt() {
        return prompt("translate-site",
                "Translate a site's content into another locale, linked to the originals.",
                List.of(argument("site", "The site to translate.", true),
                        argument("locale", "Target locale code, e.g. pt.", true)),
                arguments -> """
                        Translate the Shio site "%s" into "%s".

                        Procedure:
                        1. shio_find(site) to list what exists, then shio_read with a `fields` \
                        projection for just the text fields you will translate. Do not read whole \
                        documents you are not changing.
                        2. Create each translation with \
                        POST /api/v2/post-unified/{id}/translate — it links the new post to the \
                        original's translation group, which shio_write cannot yet do. Resolve the \
                        id with the resolve endpoint or ask shio_read for ids.
                        3. Translate the text; leave field names, URLs and any non-text values \
                        exactly as they are.
                        4. Report which posts you translated and which you skipped, and why. \
                        Leave everything in DRAFT.""".formatted(
                        text(arguments, "site", "(name the site)"),
                        text(arguments, "locale", "(name the locale)")));
    }

    @Bean
    ShMcpPrompt shMcpAuditContentPrompt() {
        return prompt("audit-content",
                "Review a site for missing fields, stale drafts and broken references.",
                List.of(argument("site", "The site to audit.", true)),
                arguments -> """
                        Audit the content of the Shio site "%s".

                        Procedure:
                        1. shio_context(site) for the model — the required fields are in it.
                        2. shio_find(site) for the inventory, then shio_read in batches, \
                        projecting only the fields you are checking.
                        3. shio_changes(site) to see what changed recently and by whom.
                        4. Report findings as a list of addresses with the specific problem for \
                        each: required field empty, draft never published, File field pointing at \
                        an asset shio_assets does not list, duplicate URL.
                        5. Change nothing. This is a read-only audit; propose fixes as a \
                        shio_apply document with dryRun=true so a human can see the plan first.""".formatted(
                        text(arguments, "site", "(name the site)")));
    }

    // --- plumbing ---

    private static ShMcpPrompt.Argument argument(String name, String description, boolean required) {
        return new ShMcpPrompt.Argument(name, description, required);
    }

    private static String text(JsonNode arguments, String field, String fallback) {
        String value = ShMcpArgs.string(arguments, field);
        return value == null ? fallback : value;
    }

    private static String brief(JsonNode arguments) {
        String value = ShMcpArgs.string(arguments, "brief");
        return value == null ? "" : "\n\nBrief: " + value;
    }

    private static ShMcpPrompt prompt(String name, String description,
                                      List<ShMcpPrompt.Argument> arguments, Renderer renderer) {
        return new ShMcpPrompt() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public List<Argument> arguments() {
                return arguments;
            }

            @Override
            public String render(JsonNode values) {
                return renderer.render(values);
            }
        };
    }

    @FunctionalInterface
    private interface Renderer {
        String render(JsonNode arguments);
    }
}
