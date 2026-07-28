/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.github.jknack.handlebars.io.TemplateSource;

/**
 * SH183 — the template evaluator, and the three things it deliberately cannot do.
 *
 * <h2>Why Handlebars, and why in Java</h2>
 *
 * <p>The engine this replaces (FSP, removed 2019) is remembered as "Nashorn, therefore
 * unsafe", and that describes its <em>host</em> rather than its design. What layout
 * authors actually wrote was Handlebars: {@code ShCacheJavascript} concatenated
 * {@code shObject.js} with {@code handlebars.min.js} — fetched from a CDN at render
 * time — and every shipped layout's JavaScript was the same four lines of ceremony,
 * {@code Handlebars.compile(html)} applied to the data. So a pure-Java implementation is
 * not a new authoring surface: it runs the templates that already exist, and the only
 * thing it removes is {@code scriptEngine.eval}.</p>
 *
 * <h2>What it cannot do, by construction</h2>
 *
 * <p>A layout is <b>content</b>. Content is written by an agent, arrives in exchange
 * packages, and under SH22's multi-tenancy belongs to somebody who is not the operator.
 * So the interesting property of this class is the list of things a template cannot
 * reach, and each one is closed by construction rather than by a setting somebody has to
 * remember:</p>
 *
 * <ol>
 *   <li><b>No script engine.</b> Handlebars.java declares {@code nashorn-core} at compile
 *       scope for its JavaScript-helper integration; the POM excludes it, so the classes
 *       that would evaluate a script are not on the classpath at all. This is the
 *       difference between the non-goal being honoured and being configured — FSP's own
 *       failure was calling {@code getScriptEngine(String[])} when the
 *       {@code ClassFilter} overload existed the whole time.</li>
 *   <li><b>No reflection into beans.</b> The context is built with
 *       {@link MapValueResolver} <em>only</em>. Handlebars.java's default resolver chain
 *       includes {@code JavaBeanValueResolver} and {@code FieldValueResolver}, which walk
 *       arbitrary object graphs — hand a template a real {@code ShPostUnified} under that
 *       chain and {@code {{shSite.shFolders}}} traverses the persistence model. The model
 *       this engine renders is maps, lists and strings, so map-only costs nothing and
 *       means an unmapped expression resolves to empty rather than to whatever the object
 *       happens to expose.</li>
 *   <li><b>No file or classpath reads.</b> Partials ({@code {{> name}}}) and the built-in
 *       {@code precompile}/{@code embedded} helpers resolve through a
 *       {@link TemplateLoader}, whose default is a classpath loader — a template could
 *       otherwise ask for {@code application.properties}. The loader here refuses
 *       everything with a message that says so.</li>
 * </ol>
 *
 * <p>Deleting the {@code eval} is what makes the old model safe to bring back; these
 * three keep it that way. {@code ShRenderNoScriptEngineTest} asserts all of them, because
 * every one is a dependency or a default that a future change could quietly restore.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShTemplateEngine {

    private final Handlebars handlebars;

    public ShTemplateEngine() {
        this.handlebars = new Handlebars()
                .with(new ConcurrentMapTemplateCache())
                .with(new ShRefusingTemplateLoader());
        // A layout renders a whole HTML document; re-indenting it would change the
        // bytes SH101's digest and SH102's snapshot compare.
        this.handlebars.prettyPrint(false);
        this.handlebars.setInfiniteLoops(false);
    }

    /** Registers a domain helper — the closed capability set, see {@link ShRenderHelpers}. */
    public void register(String name, Helper<Object> helper) {
        handlebars.registerHelper(name, helper);
    }

    /**
     * Compiles without applying — SH192's write-time check.
     *
     * <p>Separated from {@link #render} so a template can be proved parseable with no model
     * and no data, which is the state a write is in: {@code post.upsert} has the markup and
     * nothing to render it against. It is also the cheap half, since the compiled result is
     * cached and the later render reuses it.</p>
     *
     * @throws ShTemplateException with the line and column of the offending character
     */
    public void compile(String template) {
        if (template == null || template.isBlank()) {
            return;
        }
        try {
            handlebars.compileInline(template);
        } catch (HandlebarsException e) {
            throw ShTemplateException.of(e);
        } catch (IOException e) {
            throw new ShTemplateException("Template could not be compiled: " + e.getMessage(),
                    null, 0, 0, e);
        }
    }

    /**
     * Compiles and applies {@code template} against {@code model}.
     *
     * @param template the Handlebars source, as authored in a layout's or region's field
     * @param model    the view model — maps, lists and scalars only
     * @return the rendered markup
     * @throws ShTemplateException with the file/line/column of the offending character,
     *                             which is the feedback FSP's {@code regionError} already
     *                             gave in 2019 and what law P6 asks for
     */
    public String render(String template, Map<String, Object> model) {
        try {
            Template compiled = handlebars.compileInline(template == null ? "" : template);
            return compiled.apply(Context.newBuilder(model)
                    .resolver(MapValueResolver.INSTANCE)
                    .build());
        } catch (HandlebarsException e) {
            throw ShTemplateException.of(e);
        } catch (IOException e) {
            throw new ShTemplateException("Template could not be rendered: " + e.getMessage(),
                    null, 0, 0, e);
        }
    }

    /**
     * The loader that says no. Every path into it — a partial, {@code precompile},
     * {@code embedded} — is a template asking to read a file this process can see and the
     * template's author cannot be assumed to be allowed to.
     */
    static final class ShRefusingTemplateLoader implements TemplateLoader {

        private static final String REFUSAL =
                "A template may not load another file. Partials, `precompile` and `embedded` "
                        + "read through the classpath, and a layout is content: composition is "
                        + "`sh-region=\"NAME\"`, which resolves to a Region post rather than to a path.";

        @Override
        public TemplateSource sourceAt(String location) throws IOException {
            throw new IOException(REFUSAL + " Requested: " + location);
        }

        @Override
        public String resolve(String location) {
            return location;
        }

        @Override
        public String getPrefix() {
            return "";
        }

        @Override
        public String getSuffix() {
            return "";
        }

        @Override
        public void setPrefix(String prefix) {
            // Fixed: there is no path to prefix.
        }

        @Override
        public void setSuffix(String suffix) {
            // Fixed: there is no path to suffix.
        }

        @Override
        public void setCharset(java.nio.charset.Charset charset) {
            // Nothing is ever read, so nothing is ever decoded.
        }

        @Override
        public java.nio.charset.Charset getCharset() {
            return java.nio.charset.StandardCharsets.UTF_8;
        }
    }
}
