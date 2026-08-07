/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post.type;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;

import tools.jackson.databind.ObjectMapper;

/**
 * SH607 — the SH95 section vocabulary, provisionable by name.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>{@link com.viglet.shio.render.ShRenderPostTypes} makes an instance able to <em>render</em>:
 * {@code PageLayout}, {@code Region}, {@code Theme}, {@code Redirect}, {@code SiteScripts}, created
 * on demand so nothing appears in an instance nobody renders in. Those five plus {@code File} are
 * the whole content model of a bare Shio — and they are exactly what
 * {@code /api/v2/agent/apply} listed as {@code allowed} when {@code shio convert} ran against one:
 *
 * <pre>{@code
 * "title": "No post-type named 'SectionFeatureGrid'",
 * "allowed": ["File","PageLayout","Redirect","Region","SiteScripts","Theme"]
 * }</pre>
 *
 * <p>A conversion writes {@code Page} and {@code Section*} posts, and <b>nothing provisioned
 * them</b>. They shipped only inside a blueprint, so the replication chain — every mode of it,
 * fidelity included — could not run against a fresh install at all. It passed everywhere it was
 * tried because a development instance has accumulated the types from an earlier console site or
 * blueprint apply, which is the failure shape {@code requireInstance} exists for one level up: the
 * instance, not the code, was carrying the test.</p>
 *
 * <h2>Why a catalogue of defs and not a second {@code ShRenderPostTypes}</h2>
 *
 * <p>The render five are declared in Java because the renderer reads their field <em>names</em> as
 * machinery ({@code sh-region}, {@code postTypeLayout}, {@code TO}). Nothing in Java reads a
 * {@code SectionHero}'s {@code heading}: the vocabulary is <b>content</b>, and it already exists as
 * shipped JSON that {@code @viglet/shio-sections}, the two blueprints and the CLI's own
 * {@code SECTION_TYPES} table all describe. A third hand-written copy in Java is the drift SH245
 * bans, so this loads the shipped files.</p>
 *
 * <h2>One canonical copy, and the check that keeps it one</h2>
 *
 * <p>{@code classpath:/post-types/vocabulary/*.json} is the canonical model.
 * {@code blueprints/landing} and {@code blueprints/marketing-site} carry their own copies because a
 * blueprint's manifest addresses paths inside itself, and {@code ShVocabularyCatalogTest} asserts
 * all three are <b>byte-identical</b> — the duplication is closed by a check rather than by a
 * comment, the way {@code ShConvertedLayoutRenderTest} already closes the CLI's copy of the same
 * field lists.</p>
 *
 * <p><b>{@code Page} gained a {@code content} field here (SH607), and that is a finding of its own.</b>
 * Three {@code Page} definitions shipped and <em>none</em> of them could hold what a conversion
 * writes: the SH71 bootstrap starter declares {@code title}/{@code content} and no {@code sections},
 * the blueprints declared {@code title}/{@code description}/{@code sections} and no {@code content},
 * and {@code buildConversion} writes all four. So the type the chain has been writing against on
 * every green run matches no file in this repository.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShVocabularyPostTypes {

    /** The canonical model — one directory, read once at first use. */
    static final String CATALOG_PATTERN = "classpath:/post-types/vocabulary/*.json";

    private final ShPostTypeAdminService shPostTypeAdminService;
    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourceResolver;

    /**
     * The parsed catalogue, or {@code null} until first use.
     *
     * <p>{@code volatile} plus the double-check below is enough here and the usual objection does
     * not apply: the value published is an <b>unmodifiable map that is never mutated after
     * construction</b>, so two threads racing the first read can only build two equal maps and one
     * of them wins. Making the field an {@code AtomicReference} would move the same race, and
     * eager-loading in the constructor would make a packaging failure a startup failure for an
     * instance that never provisions a section.</p>
     */
    private volatile Map<String, ShPostTypeDef> catalogue;

    public ShVocabularyPostTypes(ShPostTypeAdminService shPostTypeAdminService,
                                 ObjectMapper objectMapper) {
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.objectMapper = objectMapper;
        this.resourceResolver = new PathMatchingResourcePatternResolver(
                ShVocabularyPostTypes.class.getClassLoader());
    }

    /**
     * Every name this instance can provision, sorted — the {@code allowed} list a refusal prints.
     *
     * <p>Sorted rather than in directory order: a classpath scan's order is the filesystem's, and a
     * teaching error whose {@code allowed} list reshuffles between instances is one an agent cannot
     * diff (P5).</p>
     */
    public List<String> names() {
        return List.copyOf(catalogue().keySet());
    }

    /** One def by name, or empty when the catalogue does not carry it. */
    public Optional<ShPostTypeDef> get(String name) {
        return Optional.ofNullable(catalogue().get(name));
    }

    /**
     * Every field name a catalogued type declares, nested ones included (SH607).
     *
     * <p>For {@code ShAgentBatchService.Declared}: a dry run of a document that provisions
     * {@code Page} and then writes one has to resolve the type this run <em>would</em> create, or
     * planning a conversion against a bare instance fails on the first post — SH153's problem,
     * reached from the conversion side rather than the blueprint side. Nested names are included
     * because {@code post.upsert}'s SH168 check reads Tab groups and Relator children too, and a
     * section's rows are exactly that.</p>
     *
     * @return the names, or empty when the catalogue does not carry {@code name}
     */
    public Set<String> fieldNames(String name) {
        return get(name)
                .map(def -> {
                    Set<String> names = new LinkedHashSet<>();
                    collectFieldNames(def.fields(), names);
                    return Collections.<String>unmodifiableSet(names);
                })
                .orElseGet(Set::of);
    }

    private static void collectFieldNames(List<ShPostTypeAttrDef> fields, Set<String> into) {
        if (fields == null) {
            return;
        }
        for (ShPostTypeAttrDef field : fields) {
            if (field.name() != null && !field.name().isBlank()) {
                into.add(field.name());
            }
            collectFieldNames(field.fields(), into);
        }
    }

    /**
     * Which of {@code names} this instance is missing, in the order asked.
     *
     * <p>Read-only, so a dry run answers from it and writes nothing — the same division
     * {@link com.viglet.shio.render.ShRenderPostTypes#existing()} draws for the render five, and for
     * the same reason: a plan that had to write in order to report is a plan that cannot run on a
     * read-only credential.</p>
     *
     * @throws UnknownVocabularyTypeException if a name is not in the catalogue
     */
    @Transactional(readOnly = true)
    public List<String> missing(List<String> names) {
        List<String> absent = new ArrayList<>();
        for (String name : requireKnown(names)) {
            if (shPostTypeAdminService.get(name).isEmpty()) {
                absent.add(name);
            }
        }
        return List.copyOf(absent);
    }

    /**
     * Creates whichever of {@code names} is absent and returns the ones it created.
     *
     * <p><b>Provision, never upsert</b> — SH152's rule, which {@code ShRenderPostTypes.ensure}
     * follows for the render five: a curator who added a field to their {@code SectionHero} keeps
     * it, and a conversion re-run against a site somebody has since edited changes no model. That
     * makes this idempotent, which is what lets {@code convert} emit the op unconditionally instead
     * of asking first.</p>
     *
     * @throws UnknownVocabularyTypeException if a name is not in the catalogue
     */
    @Transactional
    public List<String> ensure(List<String> names) {
        List<String> created = new ArrayList<>();
        for (String name : requireKnown(names)) {
            if (shPostTypeAdminService.provisionFromDef(catalogue().get(name))) {
                created.add(name);
            }
        }
        return List.copyOf(created);
    }

    /** Refuses the whole list before creating any of it — a half-applied model teaches nothing. */
    private List<String> requireKnown(List<String> names) {
        List<String> asked = names == null ? List.of() : names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        for (String name : asked) {
            if (!catalogue().containsKey(name)) {
                throw new UnknownVocabularyTypeException(name, names());
            }
        }
        return asked;
    }

    private Map<String, ShPostTypeDef> catalogue() {
        Map<String, ShPostTypeDef> loaded = catalogue;
        if (loaded == null) {
            synchronized (this) {
                loaded = catalogue;
                if (loaded == null) {
                    loaded = load();
                    catalogue = loaded;
                }
            }
        }
        return loaded;
    }

    private Map<String, ShPostTypeDef> load() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(CATALOG_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("The vocabulary catalogue at " + CATALOG_PATTERN
                    + " could not be listed. It is a shipped classpath resource, so this is a "
                    + "packaging failure rather than a configuration one.", e);
        }
        // Sorted by filename so `names()` is the same list wherever the jar was built: a classpath
        // scan's order is the filesystem's, and `allowed` is read by an agent diffing two runs (P5).
        List<Resource> ordered = new ArrayList<>(List.of(resources));
        ordered.sort(Comparator.comparing(resource -> String.valueOf(resource.getFilename())));
        Map<String, ShPostTypeDef> defs = new LinkedHashMap<>();
        for (Resource resource : ordered) {
            try (InputStream in = resource.getInputStream()) {
                ShPostTypeDef def = objectMapper.readValue(in, ShPostTypeDef.class);
                defs.put(def.name(), def);
            } catch (IOException e) {
                throw new IllegalStateException("The vocabulary definition "
                        + resource.getFilename() + " could not be read.", e);
            }
        }
        // `unmodifiableMap` rather than `Map.copyOf`: the insertion order above IS the sort, and
        // `Map.copyOf` does not keep it.
        return Collections.unmodifiableMap(defs);
    }

    /** A 400 naming the vocabulary this instance ships, with the nearest match (P6). */
    public static class UnknownVocabularyTypeException extends ShTeachingException {

        public UnknownVocabularyTypeException(String sent, List<String> allowed) {
            super(ShProblem.badRequest("unknown-vocabulary-type",
                            "No vocabulary post-type named '" + sent + "'")
                    .withDetail("`data.types` on render.provision names types from the SH95 "
                            + "vocabulary this instance ships. A model of your own is created with "
                            + "PUT /api/v2/post-type/{name}; this op only installs the shared one.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Name one of the listed types, or create your own with "
                            + "PUT /api/v2/post-type/" + sent + ".")
                    .withExample("{\"op\":\"render.provision\",\"data\":{\"types\":[\"Page\"]}}"));
        }
    }
}
