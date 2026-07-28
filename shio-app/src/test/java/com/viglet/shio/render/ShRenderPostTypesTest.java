/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

/**
 * SH183 — the three system post-types, created on demand and never overwritten.
 *
 * <p>Its own database: these types are instance-wide, and the SH164/SH239 benchmarks
 * measure documents whose size grows with how many post-types exist (SH246's lesson,
 * applied before it costs anyone a red build rather than after).</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-render-types;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
class ShRenderPostTypesTest {

    @Autowired
    private ShRenderPostTypes shRenderPostTypes;

    @Test
    @DisplayName("the three arrive with the 2018 field names, so a real export renders")
    void theThreeAreCreated() {
        List<ShPostTypeDef> defs = shRenderPostTypes.ensure();

        assertThat(defs).extracting(ShPostTypeDef::name)
                .containsExactly(ShSystemPostType.PAGE_LAYOUT, ShSystemPostType.REGION,
                        ShSystemPostType.THEME);
        assertThat(fieldNames(defs, ShSystemPostType.PAGE_LAYOUT))
                .as("openviglet/bootstrap-site's PT-PAGE-LAYOUT carries exactly these, so a "
                        + "package from 2018 imports and renders with no migration")
                .contains(ShSystemPostTypeAttr.HTML, ShSystemPostTypeAttr.THEME,
                        ShSystemPostTypeAttr.JAVASCRIPT);
        assertThat(fieldNames(defs, ShSystemPostType.REGION))
                .contains(ShSystemPostTypeAttr.HTML, ShSystemPostTypeAttr.JAVASCRIPT);
        assertThat(fieldNames(defs, ShSystemPostType.THEME))
                .contains(ShSystemPostTypeAttr.CSS, ShSystemPostTypeAttr.JAVASCRIPT);
        assertThat(defs).allSatisfy(def -> assertThat(def.system())
                .as("system, so PUT /api/v2/post-type refuses to reshape it — which is also "
                        + "why this service has to exist at all")
                .isTrue());
    }

    @Test
    @DisplayName("a template is code, and gets a code editor")
    void templatesUseCodeWidgets() {
        List<ShPostTypeDef> defs = shRenderPostTypes.ensure();

        assertThat(widgetOf(defs, ShSystemPostType.REGION, ShSystemPostTypeAttr.HTML))
                .isEqualTo(ShSystemWidget.ACE_HTML);
        assertThat(widgetOf(defs, ShSystemPostType.REGION, ShSystemPostTypeAttr.JAVASCRIPT))
                .as("a region's JavaScript is the browser's and is never evaluated in the JVM "
                        + "— which is the whole resolution of the Block D non-goal")
                .isEqualTo(ShSystemWidget.ACE_JS);
    }

    @Test
    @DisplayName("calling it twice changes nothing, and the second call still maps")
    void ensureIsIdempotent() {
        shRenderPostTypes.ensure();

        // The second call is the one that matters, and it is why this returns defs rather
        // than entities. `shPostTypeAttrs` is LAZY: a type this call *created* still holds
        // its fields in memory, while one it merely *found* is a detached proxy the moment
        // the transaction closes. Mapping outside would therefore pass on a fresh instance
        // and throw LazyInitializationException on every instance after — SH176's defect,
        // and this assertion is what caught it here.
        assertThatCode(shRenderPostTypes::ensure).doesNotThrowAnyException();

        List<ShPostTypeDef> second = shRenderPostTypes.ensure();
        assertThat(second).hasSize(3);
        assertThat(fieldNames(second, ShSystemPostType.REGION))
                .as("SH152's rule: an existing type is left exactly as it is, including a "
                        + "field a curator added — re-creating it would discard their work "
                        + "on a call whose whole purpose is to be safe to repeat")
                .contains(ShSystemPostTypeAttr.HTML);
        assertThat(shRenderPostTypes.existing())
                .extracting(ShPostTypeDef::name)
                .containsExactlyElementsOf(ShRenderPostTypes.NAMES);
    }

    private List<String> fieldNames(List<ShPostTypeDef> defs, String type) {
        return def(defs, type).fields().stream().map(ShPostTypeAttrDef::name).toList();
    }

    private String widgetOf(List<ShPostTypeDef> defs, String type, String field) {
        return def(defs, type).fields().stream()
                .filter(attr -> field.equals(attr.name()))
                .findFirst()
                .map(ShPostTypeAttrDef::widget)
                .orElse(null);
    }

    private ShPostTypeDef def(List<ShPostTypeDef> defs, String type) {
        return defs.stream().filter(d -> type.equals(d.name())).findFirst().orElseThrow();
    }
}
