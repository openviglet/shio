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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import javax.script.ScriptEngineManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH183 — the guard on the non-goal.
 *
 * <h2>Why this is a test and not a paragraph</h2>
 *
 * <p>The binding non-goal is <i>"no GraalJS/Nashorn/Groovy server-side script engine"</i>,
 * and §0.4's argument for it is specific: what such an engine evaluates is
 * <b>user-supplied code</b>, because a layout is content. The original engine's failure
 * was not that it made the wrong choice — it was that it made the right choice available
 * and did not take it. {@code ShNashornEngineConfiguration} called
 * {@code getScriptEngine(String[])} when the {@code getScriptEngine(ClassFilter)} overload
 * existed the whole time, so a layout had {@code Java.type()} over the entire classpath
 * and the raw {@code HttpServletRequest} in scope.</p>
 *
 * <p>That is a defect nothing would catch by reading the rendered output: the page comes
 * out right either way. It is a fact about the <b>classpath and the defaults</b>, so the
 * guard has to be about those, and it has to exist because each of the three properties
 * below is one dependency bump or one convenience setting away from being undone:</p>
 *
 * <ol>
 *   <li>Handlebars.java declares {@code nashorn-core} at <b>compile scope</b> for its
 *       JavaScript-helper integration. Deleting the direct {@code nashorn-core}
 *       declaration from the POM while adding Handlebars would have swapped an unused
 *       script engine for a used one and looked like a cleanup. The exclusion is what
 *       stops it, and this asserts the exclusion held.</li>
 *   <li>Handlebars.java's <b>default value resolver chain</b> includes
 *       {@code JavaBeanValueResolver} and {@code FieldValueResolver}. A future
 *       {@code Context.newBuilder(model).build()} — the obvious, shorter call — would
 *       silently let a template walk any object it is handed.</li>
 *   <li>Its <b>default template loader</b> reads the classpath, so {@code {{> anything}}}
 *       in a layout is a file read.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 */
class ShRenderNoScriptEngineTest {

    private final ShTemplateEngine engine = new ShTemplateEngine();

    @Test
    @DisplayName("no script engine is reachable — not Nashorn's, not any other")
    void noScriptEngineOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName("org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory"))
                .as("""
                        nashorn-core must not come back, transitively or otherwise. Handlebars.java \
                        declares it at compile scope for the JS-helper path, so the POM excludes it: \
                        a template cannot reach a script engine because there is no script engine to \
                        reach, which is stronger than a ClassFilter somebody has to remember.""")
                .isInstanceOf(ClassNotFoundException.class);

        assertThat(new ScriptEngineManager().getEngineByName("nashorn"))
                .as("and the JDK's own is gone too — removed in 15, and this runs on 21")
                .isNull();
        assertThat(new ScriptEngineManager().getEngineByName("javascript"))
                .as("no JS engine of any name; the JavaScript a region author writes goes "
                        + "to the browser as a static asset, which is where it can do no harm")
                .isNull();
    }

    @Test
    @DisplayName("a template cannot walk an object it is handed — the resolver is map-only")
    void beanPropertiesAreNotResolved() {
        // A model value that is a real object rather than a Map. Under Handlebars.java's
        // default resolver chain `{{secret.value}}` prints it; under MapValueResolver it
        // resolves to nothing. Hand the renderer an entity and the difference is a
        // template walking the persistence model.
        Map<String, Object> model = Map.of("secret", new Exposed("classified"));

        String out = engine.render("[{{secret.value}}][{{secret.class.name}}]", model);

        assertThat(out)
                .as("a bean's properties, and its class, must both be unreachable")
                .isEqualTo("[][]");
    }

    @Test
    @DisplayName("a map is resolved, so the restriction costs the author nothing")
    void mapsStillResolve() {
        Map<String, Object> model = Map.of("post", Map.of("title", "Hello"),
                "items", List.of(Map.of("name", "a"), Map.of("name", "b")));

        assertThat(engine.render("{{post.title}}:{{#each items}}{{name}}{{/each}}", model))
                .isEqualTo("Hello:ab");
    }

    @Test
    @DisplayName("a partial cannot read a file — the loader refuses everything")
    void partialsCannotReadTheClasspath() {
        assertThatThrownBy(() -> engine.render("{{> application}}", Map.of()))
                .as("""
                        the default TemplateLoader is a classpath loader, so a layout — which is \
                        content — could ask for application.properties. Composition is \
                        sh-region="NAME", which resolves to a Region post rather than to a path.""")
                .isInstanceOf(ShTemplateException.class);
    }

    @Test
    @DisplayName("a broken template says where, not just what")
    void aBrokenTemplateCarriesItsPosition() {
        ShTemplateException thrown = catchTemplateException("<div>\n  {{#each items}}\n</div>");

        assertThat(thrown.getLine())
                .as("FSP's regionError reported file, line, column and a caret in 2019; "
                        + "losing that would be a regression in authoring feedback (P6)")
                .isEqualTo(3);
        assertThat(thrown.describe())
                .as("the position leads, because 'unclosed block' without it means reading "
                        + "the whole template again")
                .startsWith("line 3, column ")
                .contains("expected: '{{/'");
        assertThat(thrown.describe())
                .as("Handlebars' synthetic inline@<hash> name identifies no file an author "
                        + "can open, and printing it sends them looking for one")
                .doesNotContain("inline@");
    }

    @Test
    @DisplayName("HTML in a value is escaped; triple-stash is the author's explicit opt-in")
    void valuesAreEscapedByDefault() {
        Map<String, Object> model = Map.of("body", "<script>alert(1)</script>");

        assertThat(engine.render("{{body}}", model))
                .as("content is not trusted markup unless the template says it is")
                .doesNotContain("<script>");
        assertThat(engine.render("{{{body}}}", model))
                .as("a rich-text field is markup on purpose, and the template declares that")
                .contains("<script>");
    }

    private ShTemplateException catchTemplateException(String template) {
        try {
            engine.render(template, Map.of());
        } catch (ShTemplateException e) {
            return e;
        }
        throw new AssertionError("expected the template to be refused");
    }

    /** A plain bean, which is exactly what the resolver must not walk. */
    public static class Exposed {
        private final String value;

        Exposed(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
