/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.dto.ShAgentConsoleCost;
import com.viglet.shio.utils.ShIoFailure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1112 — what the console costs to load, read from the build this instance serves.
 *
 * <p>SH1043 gave the console a number and two readers, a {@code pnpm} script and a build gate, and
 * neither is reachable from an instance: an agent that changed a console file needed this
 * checkout, its toolchain and a build to learn what it cost. The instance already serves the
 * artefact — {@code index.html} and its chunks under the static root — so this is the script's
 * reading made where the files are: the entry module, every {@code modulepreload}, every
 * stylesheet, each measured raw and gzipped at level 9.</p>
 *
 * <p><b>The same reading, not a second opinion.</b> {@code shio-react/scripts/console-size.mjs}
 * is the definition; the three patterns below are its three, and a build that declares no entry or
 * no preload is reported as unmeasurable rather than as a total of what was left, which is the
 * script's own refusal. {@code src/test/resources/console-cost/} is a built console both readers
 * measure, and both are held to the one {@code expected.json} beside it.</p>
 *
 * <p>Computed once and kept: the static root does not change while the instance runs.</p>
 */
@Service
public class ShConsoleCost {

    private static final Pattern ENTRY = Pattern.compile("<script[^>]*type=\"module\"[^>]*src=\"([^\"]+)\"");
    private static final Pattern PRELOAD = Pattern.compile("<link[^>]*rel=\"modulepreload\"[^>]*href=\"([^\"]+)\"");
    private static final Pattern STYLE = Pattern.compile("<link[^>]*rel=\"stylesheet\"[^>]*href=\"([^\"]+)\"");

    /** Where Spring Boot serves the console from; the Vite build writes here. */
    static final String STATIC_ROOT = "classpath:public/";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String root;
    private volatile ShAgentConsoleCost measured;

    @Autowired
    public ShConsoleCost(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this(resourceLoader, objectMapper, STATIC_ROOT);
    }

    ShConsoleCost(ResourceLoader resourceLoader, ObjectMapper objectMapper, String root) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.root = root.endsWith("/") ? root : root + "/";
    }

    /** The eager load of the console this instance serves, or {@code null} when it serves none. */
    public ShAgentConsoleCost cost() {
        ShAgentConsoleCost known = measured;
        if (known == null) {
            known = measure();
            measured = known;
        }
        return known.unavailable() != null && known.unavailable().isEmpty() ? null : known;
    }

    private ShAgentConsoleCost measure() {
        Resource index = resourceLoader.getResource(root + "index.html");
        if (!index.exists()) {
            // A backend run with -Dskip.npm=true serves no console, which is not a finding: the
            // empty reason is what `cost()` turns into an absent section.
            return ShAgentConsoleCost.unavailable("");
        }
        try {
            String html = new String(read(index), StandardCharsets.UTF_8);
            List<String> entries = all(ENTRY, html);
            List<String> preloads = all(PRELOAD, html);
            if (entries.isEmpty() || preloads.isEmpty()) {
                return ShAgentConsoleCost.unavailable(entries.isEmpty()
                        ? "the built index.html has no <script type=\"module\">, so there is no entry to measure"
                        : "the built index.html declares no <link rel=\"modulepreload\">, so only the entry "
                                + "is known and a total of it would be a fraction reported as the whole");
            }
            TreeSet<String> eager = new TreeSet<>(entries);
            eager.addAll(preloads);
            eager.addAll(all(STYLE, html));
            List<ShAgentConsoleCost.Asset> assets = new ArrayList<>();
            long raw = 0;
            long gzip = 0;
            for (String href : eager) {
                byte[] bytes = read(resourceLoader.getResource(root + href.replaceFirst("^/", "")));
                long zipped = gzipSize(bytes);
                assets.add(new ShAgentConsoleCost.Asset(href, bytes.length, zipped));
                raw += bytes.length;
                gzip += zipped;
            }
            return new ShAgentConsoleCost(raw, gzip, ceiling(), List.copyOf(assets), null);
        } catch (IOException unreadable) {
            return ShAgentConsoleCost.unavailable("the built console could not be read: " + ShIoFailure.describe(unreadable));
        }
    }

    /** The eager ceilings the build shipped beside itself, or {@code null} for a build that did not. */
    private ShAgentConsoleCost.Ceiling ceiling() throws IOException {
        Resource budget = resourceLoader.getResource(root + "console-size-budget.json");
        if (!budget.exists()) {
            return null;
        }
        JsonNode eager = objectMapper.readTree(read(budget)).path("eager");
        return eager.path("raw").isNumber() && eager.path("gzip").isNumber()
                ? new ShAgentConsoleCost.Ceiling(eager.path("raw").longValue(), eager.path("gzip").longValue())
                : null;
    }

    private static List<String> all(Pattern pattern, String html) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static byte[] read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Gzipped at level 9, the script's {@code createGzip({ level: 9 })}. */
    static long gzipSize(byte[] bytes) throws IOException {
        CountingStream counted = new CountingStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(counted) {
            {
                def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }) {
            gzip.write(bytes);
        }
        return counted.count;
    }

    private static final class CountingStream extends OutputStream {

        private long count;

        @Override
        public void write(int b) {
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            count += len;
        }
    }
}
