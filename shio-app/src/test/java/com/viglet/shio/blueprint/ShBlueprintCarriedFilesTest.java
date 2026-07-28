/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.blueprint.dto.ShBlueprint;

/**
 * SH166 — a package's carried files reach a caller with no CLI as <em>templates</em>, and
 * they reach it at all.
 *
 * <h2>What was wrong</h2>
 *
 * <p>SH119 split parameter substitution by who knows the answer: the server fills a package's
 * content, the client fills its carried files, because a project's npm name is local identity
 * the server has no business being told. Right for the CLI, and it left an MCP-only caller
 * holding source that still says <code>{{projectName}}</code> — with, on inspection, no way to
 * be holding it at all, since the bodies travel only on
 * {@code GET /agent/blueprints/{name}?include=files}.</p>
 *
 * <h2>What is asserted</h2>
 *
 * <p>That the placeholder list is read from the <em>bytes</em> rather than from the manifest's
 * parameter declaration, which is the difference between "these three names are still in this
 * file" and "this package declares three parameters, one of which its files may or may not
 * use". The second sends a caller looking for something that is not there, and it is the
 * version that is easier to write.</p>
 *
 * <p>No Spring context, for {@link ShBlueprintRegistryTest}'s reason: this is a property of the
 * shipped packages and of two pure renderers, so it should be cheap enough to run on every
 * change to a package.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBlueprintCarriedFilesTest {

    private final ShBlueprintRegistry registry = new ShBlueprintRegistry(new ShBlueprintCodec());

    /**
     * The starter's README and {@code package.json} carry {@code {{projectName}}}; its
     * {@code .gitignore} carries nothing. Both halves are the assertion — a renderer that
     * labelled every file a template would be as uninformative as one that labelled none.
     */
    @Test
    @DisplayName("SH166: the detail names the placeholders standing in each carried file")
    void theDetailNamesPlaceholdersPerFile() {
        String detail = ShBlueprintTerse.detail(registry.require("nextjs-starter"));

        assertThat(detail)
                .as("package.json is a template and the caller has to be told which name to fill")
                .contains("file package.json placeholders=projectName");
        assertThat(detail)
                .as(".gitignore is literal — labelling it a template would train the caller to "
                        + "ignore the label")
                .contains("file .gitignore\n");
    }

    /**
     * Read from the bytes, not from the manifest. {@code nextjs-starter} declares exactly one
     * parameter, so the two readings agree there by accident; this asserts the property on a
     * file that uses <em>none</em> of them, which only the byte reading can get right.
     */
    @Test
    @DisplayName("SH166: a file that uses no parameter is reported with no placeholders")
    void placeholdersComeFromTheBytesNotTheManifest() {
        ShBlueprint starter = registry.require("nextjs-starter");
        List<String> literal = starter.files().stream()
                .filter(file -> !file.text().contains("{{"))
                .map(ShBlueprint.File::path)
                .toList();

        assertThat(literal)
                .as("the starter is expected to carry at least one file with no placeholder; "
                        + "if that stopped being true this test is measuring nothing")
                .isNotEmpty();

        String detail = ShBlueprintTerse.detail(starter);
        for (String path : literal) {
            assertThat(detail).contains("file " + path + "\n");
        }
    }

    /**
     * The route that did not exist. {@code shio://blueprint/<name>/files} is how a client with
     * no HTTP of its own gets the bytes — verbatim, placeholders standing, each preceded by the
     * line that says so.
     */
    @Test
    @DisplayName("SH166: the file bodies are served whole, with their placeholders named")
    void theBodiesAreServedWithTheirPlaceholdersNamed() {
        ShBlueprint starter = registry.require("nextjs-starter");

        String bodies = ShBlueprintTerse.fileBodies(starter);

        assertThat(bodies).startsWith("files nextjs-starter files=" + starter.files().size());
        assertThat(bodies)
                .as("verbatim: a body the server pre-substituted would be a project name it "
                        + "invented (SH119)")
                .contains("{{projectName}}");
        for (ShBlueprint.File file : starter.files()) {
            assertThat(bodies).as("%s is missing its body", file.path()).contains(file.text());
        }
    }
}
