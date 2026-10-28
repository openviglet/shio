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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import com.viglet.shio.utils.ShStableMap;
import com.viglet.shio.utils.ShIoFailure;

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

    /**
     * What has been registered, and the argument shape each one takes (SH260).
     *
     * <p>The engine is the only thing that <em>knows</em> the capability set, because it is the
     * thing being told. Before this, five other places restated it — the validator's list of
     * choices, its complement within the seven known-missing, the scanner's two exclusion sets, and
     * the lint's copy of a row's keys — and SH247 had to edit three of them in step to add two
     * helpers. Every failure mode was silent: a registered helper missing from the scanner's list is
     * reported as an unknown <em>field</em>, and a name left in the known-missing list after the
     * helper ships tells an author a capability is absent while the renderer happily runs it.</p>
     *
     * <p>A {@code TreeMap} so the order is total: the validator renders this set into a refusal
     * message, and a message whose word order changed between JVMs would be a byte-instability of
     * exactly the kind SH136 is about.</p>
     */
    private final Map<String, ShHelperShape> registered = new TreeMap<>();
    /** SH272 — how each helper is *called*, in the form an author writes. */
    private final Map<String, String> signatures = new TreeMap<>();

    public ShTemplateEngine() {
        this.handlebars = new Handlebars()
                .with(new ConcurrentMapTemplateCache())
                .with(new ShRefusingTemplateLoader());
        // A layout renders a whole HTML document; re-indenting it would change the
        // bytes SH101's digest and SH102's snapshot compare.
        this.handlebars.prettyPrint(false);
        this.handlebars.setInfiniteLoops(false);
    }

    /**
     * Registers a domain helper — the closed capability set, see {@link ShRenderHelpers}.
     *
     * @param shape how a template calls it, which is what the SH251 scanner needs in order not to
     *              read a helper's own name as a field reference. Declared here, at the one place
     *              that has the knowledge, rather than restated by each reader (SH260)
     */
    public void register(String name, ShHelperShape shape, Helper<Object> helper) {
        register(name, shape, null, helper);
    }

    /**
     * Registers a domain helper with the signature an author would write (SH272).
     *
     * @param signature the call as it appears in a template — {@code {{#query folder= type=
     *                  limit=}}}. The shape says how a call is <em>parsed</em>; this says how it is
     *                  <em>written</em>, which is the part an author gets wrong and the part no
     *                  reader could derive: a hash parameter's name exists only inside the helper's
     *                  body. Declared beside the helper for SH260's reason — the engine is the one
     *                  source of the capability set, and a signature kept anywhere else is a sixth
     *                  hand-maintained copy of it
     */
    public void register(String name, ShHelperShape shape, String signature,
                         Helper<Object> helper) {
        ShHelperShape declared = shape == null ? ShHelperShape.BLOCK : shape;
        handlebars.registerHelper(name, declared == ShHelperShape.INLINE_PATH_ARG
                ? helper : fieldWhenNotABlock(name, helper));
        registered.put(name, declared);
        if (signature != null && !signature.isBlank()) {
            signatures.put(name, signature.trim());
        }
    }

    /**
     * SH1265 — a block helper's name written as a plain variable reads the field of that name.
     *
     * <p>Handlebars resolves a helper before a context value, so a post-type with a field called
     * {@code image} (the section vocabulary's {@code SectionHero} has one) rendered {@code {{image}}}
     * by calling the {@code {{#image}}} helper with no block, which emits nothing: an empty
     * {@code src} on every page, in the preview and on {@code /sites/**} alike, and no finding,
     * because the page check resolves the stored value rather than the markup. A block helper has no
     * meaning outside a block, so {@code {{name}}} with no parameter can only have meant the field.</p>
     */
    private static Helper<Object> fieldWhenNotABlock(String name, Helper<Object> helper) {
        return (context, options) -> {
            if (options.tagType.inline() && options.params.length == 0
                    && (context == null || context == options.context.model())) {
                return options.context.get(name);
            }
            return helper.apply(context, options);
        };
    }

    /**
     * Every registered helper's signature, sorted by name (SH272).
     *
     * <p>The answer to "what may a template call", which until now was only reachable by writing a
     * template that called something else and reading the 400. A signature rather than a bare list
     * because the shape is what an author gets wrong; a helper registered without one is simply
     * absent here rather than appearing as a name with no call form, since a half-answer about a
     * capability is the thing that sends somebody guessing.</p>
     */
    public List<String> registeredSignatures() {
        return List.copyOf(signatures.values());
    }

    /** The registered names, sorted — the {@code allowed} set of an unknown-helper refusal. */
    public List<String> registeredNames() {
        return List.copyOf(registered.keySet());
    }

    /**
     * The registered helpers and their call shapes (SH260).
     *
     * <p>{@link ShStableMap} and not {@code Map.copyOf}, which SH136's lint caught here: that copy's
     * iteration order comes from a per-JVM salt. Only membership is read today — which is exactly
     * the reasoning that would justify the exemption comment — and the reason not to take it is that
     * the validator already renders the <em>names</em> into a refusal message, so this map is one
     * caller away from being observable output.</p>
     */
    public Map<String, ShHelperShape> registeredHelpers() {
        return ShStableMap.copyOf(registered);
    }

    /**
     * How a template calls a helper — everything a reader of the <em>markup</em> needs to know.
     *
     * <p>Only the distinctions that change how a call is parsed. Whether a helper reads the
     * database or the model is the helper's business; whether its first token is a field name is
     * the scanner's, and getting that wrong is the difference between reporting {@code HERO} and
     * reporting {@code file_url} as an undeclared field.</p>
     */
    public enum ShHelperShape {

        /** {@code {{#name hash=…}}…{{/name}}} — a block whose arguments are hash pairs. */
        BLOCK,

        /** {@code {{#name FIELD}}…{{/name}}} — a block whose first argument is a field path. */
        BLOCK_PATH_ARG,

        /** {@code {{name FIELD}}} — an inline call whose first argument is a field path. */
        INLINE_PATH_ARG;

        /** Whether the call's first positional token is a path in the enclosing scope. */
        public boolean takesPathArgument() {
            return this != BLOCK;
        }

        /** Whether the name opens a block, so its own name is never a field reference. */
        public boolean isBlock() {
            return this != INLINE_PATH_ARG;
        }
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
            throw new ShTemplateException("Template could not be compiled: " + ShIoFailure.describe(e),
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
            throw new ShTemplateException("Template could not be rendered: " + ShIoFailure.describe(e),
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
