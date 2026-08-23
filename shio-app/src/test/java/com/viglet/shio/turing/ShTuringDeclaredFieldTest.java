/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.post.type.ShInvalidIndexDeclarationException;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

import tools.jackson.databind.ObjectMapper;

/**
 * SH914 — a post-type's own fields reached Turing folded into {@code text}, and numbers and
 * booleans reached it not at all.
 *
 * <h2>What was actually broken</h2>
 *
 * <p>SH84's fold answers "does this word appear". It cannot answer "tuition under 5000", and
 * {@code collect} skips numbers and booleans <em>on purpose</em> — a number indexed as prose
 * matches nothing useful — so a school filtering by level, campus or tuition had those values in no
 * field at all. Not folded badly: absent.</p>
 *
 * <h2>The two properties a facet actually needs</h2>
 *
 * <p>A value in a field of its own, <b>and</b> a type Turing knows. Either alone is useless: an
 * untyped number is a string that sorts "10" before "9", and a declared type with the value still
 * folded into {@code text} has nothing to filter. So both halves are asserted together, and the
 * spec is asserted to travel with the document rather than being configured by hand in Turing's
 * console — a schema kept in two places is a schema that disagrees.</p>
 *
 * <h2>And the grounding rule still holds</h2>
 *
 * <p>The absent-is-not-empty invariant is the one this mapper exists to protect, and a declared
 * field is the easiest place to break it: emitting {@code 0} for a post that has no tuition makes
 * that post answer an "under 5000" filter. Asserted here as well as in the property test, because
 * this is the code path that would break it.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTuringDeclaredFieldTest {

    private final ShTuringDocumentMapper mapper = new ShTuringDocumentMapper(new ObjectMapper());

    private static ShPostTypeAttr attr(String name, boolean indexed, String type, String field) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setIndexed(indexed ? Boolean.TRUE : null);
        attr.setTuringType(type);
        attr.setTuringField(field);
        return attr;
    }

    private static ShPostUnified post(String data, ShPostTypeAttr... attrs) {
        ShPostType type = new ShPostType();
        type.setName("School");
        Set<ShPostTypeAttr> set = new LinkedHashSet<>(List.of(attrs));
        type.setShPostTypeAttrs(set);

        ShSite site = new ShSite();
        site.setName("Sample");

        ShPostUnified post = new ShPostUnified();
        post.setId("post-1");
        post.setTitle("Greenfield School");
        post.setShPostType(type);
        post.setShSite(site);
        post.setData(data);
        return post;
    }

    @Test
    void aDeclaredNumberArrivesAsANumberAndNotAsProse() {
        ShPostUnified post = post("{\"tuition\":4200,\"campus\":\"North\"}",
                attr("tuition", true, "DOUBLE", null),
                attr("campus", true, "STRING", null));

        Map<String, Object> attributes = mapper.attributes(post, "shio");

        assertThat(attributes.get("tuition"))
                .as("SH914: a tuition indexed as text cannot answer 'under 5000' — the whole point")
                .isEqualTo(4200.0d);
        assertThat(attributes.get("campus")).isEqualTo("North");
    }

    @Test
    void aDeclaredBooleanArrivesTyped_theCaseCollectDroppedEntirely() {
        ShPostUnified post = post("{\"scholarship\":true}",
                attr("scholarship", true, "BOOL", null));

        assertThat(mapper.attributes(post, "shio").get("scholarship"))
                .as("SH914: collect() skips booleans on purpose, so before this a has-scholarship "
                        + "flag reached Turing in no field at all")
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void anIntDeclaredOnAStringValueIsStillParsed() {
        ShPostUnified post = post("{\"level\":\"7\"}", attr("level", true, "INT", null));
        assertThat(mapper.attributes(post, "shio").get("level")).isEqualTo(7);
    }

    @Test
    void aValueThatCannotBeTheDeclaredTypeIsOmitted_neverCoercedToZero() {
        ShPostUnified post = post("{\"tuition\":\"ask us\"}", attr("tuition", true, "DOUBLE", null));

        assertThat(mapper.attributes(post, "shio"))
                .as("SH914: sending 0 for 'ask us' would make this post answer an 'under 5000' "
                        + "filter — a wrong result presented as a right one. Absent is the truth.")
                .doesNotContainKey("tuition");
    }

    @Test
    void aDeclaredFieldThePostLeftBlankIsAMissingKeyAndNotAnEmptyOne() {
        ShPostUnified post = post("{\"campus\":\"\"}", attr("campus", true, "STRING", null));

        assertThat(mapper.attributes(post, "shio"))
                .as("SH914: the grounding invariant, at the one code path most likely to break it — "
                        + "an empty value here makes a facet count documents that have no value")
                .doesNotContainKey("campus");
    }

    @Test
    void anUndeclaredFieldKeepsFoldingIntoTextExactlyAsBefore() {
        ShPostUnified post = post("{\"motto\":\"Learn well\",\"tuition\":4200}",
                attr("motto", false, null, null),
                attr("tuition", true, "DOUBLE", null));

        Map<String, Object> attributes = mapper.attributes(post, "shio");

        assertThat(attributes).doesNotContainKey("motto");
        assertThat(attributes.get(TurSNFieldName.TEXT).toString()).contains("Learn well");
        // And a declared string still folds too: removing it from `text` would make a page stop
        // matching a full-text search the day somebody added a facet to it.
        assertThat(attributes).containsKey("tuition");
    }

    @Test
    void aRenamedTuringFieldLandsUnderTheNameItDeclares() {
        ShPostUnified post = post("{\"campus\":\"North\"}",
                attr("campus", true, "STRING", "school_campus"));

        Map<String, Object> attributes = mapper.attributes(post, "shio");

        assertThat(attributes).containsEntry("school_campus", "North").doesNotContainKey("campus");
    }

    @Test
    void theSpecTravelsWithTheDocumentSoTuringKnowsTheType() {
        ShPostUnified post = post("{\"tuition\":4200}", attr("tuition", true, "DOUBLE", null));

        List<TurSNJobAttributeSpec> specs = mapper.specs(post);

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).getName()).isEqualTo("tuition");
        assertThat(specs.get(0).getType())
                .as("SH914: a field whose type the index does not know is a field no range filter "
                        + "can use — declaring it by hand in Turing's console is the second schema "
                        + "this avoids")
                .isEqualTo(TurSEFieldType.DOUBLE);
        assertThat(specs.get(0).isFacet()).isTrue();
        assertThat(specs.get(0).isMandatory())
                .as("a post that leaves the value blank must stay publishable")
                .isFalse();
    }

    @Test
    void aPostTypeThatDeclaresNothingSendsNoSpecsAtAll() {
        ShPostUnified post = post("{\"motto\":\"Learn well\"}", attr("motto", false, null, null));
        assertThat(mapper.specs(post))
                .as("SH914: every post-type until somebody declares one — the wire shape must not "
                        + "change for them")
                .isEmpty();
    }

    // --- the declaration is judged when it is saved ---

    private static ShPostTypeAttrDef field(String name, Boolean indexed, String type, String turingField) {
        return new ShPostTypeAttrDef(name, name, null, "Text", false, false, false, 1, null,
                indexed, turingField, type, List.of());
    }

    @Test
    void anIndexedFieldWithNoTypeIsRefusedAtSave() {
        assertThatThrownBy(() -> ShTuringFieldDeclaration.validate(List.of(field("tuition", true, null, null))))
                .isInstanceOf(ShInvalidIndexDeclarationException.class)
                .hasMessageContaining("tuition");
    }

    @Test
    void anUnknownTypeIsRefusedAtSaveAndTheErrorTeachesTheAllowedSet() {
        assertThatThrownBy(() -> ShTuringFieldDeclaration.validate(List.of(field("tuition", true, "MONEY", null))))
                .isInstanceOf(ShInvalidIndexDeclarationException.class)
                .hasMessageContaining("MONEY");
        assertThat(ShTuringFieldDeclaration.ALLOWED_TYPES)
                .contains("DOUBLE", "INT", "BOOL", "STRING", "DATE")
                .as("ARRAY duplicates the widget's multiplicity and CURRENCY carries a unit this "
                        + "model has nowhere to put")
                .doesNotContain("ARRAY", "CURRENCY");
    }

    @Test
    void twoFieldsClaimingOneTuringNameAreRefusedAtSave() {
        assertThatThrownBy(() -> ShTuringFieldDeclaration.validate(List.of(
                field("campus", true, "STRING", "place"),
                field("region", true, "STRING", "place"))))
                .isInstanceOf(ShInvalidIndexDeclarationException.class)
                .hasMessageContaining("place");
    }

    @Test
    void aDeclarationClaimingOneOfTuringsOwnFieldsIsRefusedAtSave() {
        assertThatThrownBy(() -> ShTuringFieldDeclaration.validate(List.of(
                field("heading", true, "STRING", TurSNFieldName.TITLE))))
                .as("SH914: it would overwrite the title every document depends on")
                .isInstanceOf(ShInvalidIndexDeclarationException.class);
    }

    @Test
    void aTypeSetWithoutTheSwitchIsRefusedRatherThanIgnored() {
        assertThatThrownBy(() -> ShTuringFieldDeclaration.validate(List.of(field("tuition", null, "DOUBLE", null))))
                .as("SH914: this is the shape of somebody who thinks they turned it on; ignoring it "
                        + "means a facet that never appears and nothing saying why")
                .isInstanceOf(ShInvalidIndexDeclarationException.class);
    }

    @Test
    void anUndeclaredModelStillPassesValidationUntouched() {
        ShTuringFieldDeclaration.validate(List.of(field("motto", null, null, null)));
        ShTuringFieldDeclaration.validate(null);
    }

    @Test
    void aNestedRelatorChildIsValidatedToo() {
        ShPostTypeAttrDef child = field("tuition", true, "MONEY", null);
        ShPostTypeAttrDef parent = new ShPostTypeAttrDef("campus", "Campus", null, "Relator",
                false, false, false, 1, null, null, null, null, List.of(child));

        assertThatThrownBy(() -> ShTuringFieldDeclaration.validate(List.of(parent)))
                .as("SH914: a Relator's children are attributes with declarations of their own")
                .isInstanceOf(ShInvalidIndexDeclarationException.class);
    }
}
