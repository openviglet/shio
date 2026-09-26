/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.pattern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.ShBlueprintProperties;
import com.viglet.shio.marketplace.ShMarketplaceInstaller;
import com.viglet.shio.utils.ShIoFailure;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1108 — a pattern file, pushed to the instance so the console can place it.
 *
 * <h2>The file, byte for byte, and nothing new that stores anything</h2>
 *
 * <p>A pattern is {@code shio/patterns/<name>.json} in the project (SH1040), and the console is a
 * browser that cannot read the project. So a push copies the file into the operator directory the
 * marketplace installs packages into ({@code shio.blueprint.path}), under {@code .patterns/} —
 * which the blueprint registry skips, since it holds no {@code blueprint.json}. Stored as sent:
 * the digest this answers is the digest of the file on disk, which is how the CLI tells a pushed
 * copy from one edited since.</p>
 *
 * <p>SH1108's design had the push go through the marketplace installer as a one-pattern package.
 * The installer downloads by catalogue id and accepts no upload, so that path would have been a new
 * upload endpoint plus a package wrapper the console would then have to unwrap. The directory, the
 * admin gate and the refusal without one are the installer's; the wrapper is not.</p>
 *
 * <h2>Validated the way the CLI reads it</h2>
 *
 * <p>{@code parsePattern} in {@code cli/src/patterns-store.mjs} is the format's reader, and a file
 * it would refuse is refused here too: an object, {@code formatVersion} 1, a non-empty
 * {@code sections} array whose every entry names a {@code type}, and a name matching the one in the
 * route. A pushed file the console could not place is a pushed file that lies about being one.</p>
 */
@Service
public class ShPatternStore {

    /** The subdirectory of {@code shio.blueprint.path}; the dot keeps it out of any package id. */
    static final String DIRECTORY = ".patterns";

    /** The format {@code cli/src/patterns-store.mjs} writes. */
    static final int FORMAT = 1;

    /** A pattern is a page's worth of sections, not an archive. */
    static final int MAX_BYTES = 256 * 1024;

    private static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private final ShBlueprintProperties blueprintProperties;
    private final ObjectMapper objectMapper;

    public ShPatternStore(ShBlueprintProperties blueprintProperties, ObjectMapper objectMapper) {
        this.blueprintProperties = blueprintProperties;
        this.objectMapper = objectMapper;
    }

    /** One pushed pattern, described: what the rail lists, and the digest a push can be compared by. */
    public record Summary(String name, String title, String from, List<String> sections,
                          List<String> params, String digest, String pushed) {
    }

    /** One pushed pattern, whole. */
    public record Stored(String name, String digest, String pushed, JsonNode pattern) {
    }

    /** Every pushed pattern, by name; empty where the instance has no directory to hold one. */
    public List<Summary> list() {
        if (!blueprintProperties.hasPath()) {
            return List.of();
        }
        Path directory = directory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Summary> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                String name = file.getFileName().toString().replaceFirst("\\.json$", "");
                read(name).ifPresent(stored -> out.add(summaryOf(stored)));
            }
        } catch (IOException unreadable) {
            throw new UnreadableException(ShIoFailure.describe(unreadable));
        }
        return List.copyOf(out);
    }

    /** One pushed pattern, or empty. A file that no longer parses is absent, not a 500. */
    public Optional<Stored> read(String name) {
        if (!validName(name) || !blueprintProperties.hasPath()) {
            return Optional.empty();
        }
        Path file = directory().resolve(name + ".json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            JsonNode pattern = objectMapper.readTree(bytes);
            return Optional.of(new Stored(name, digest(bytes),
                    Files.getLastModifiedTime(file).toInstant().toString(), pattern));
        } catch (IOException | JacksonException unreadable) {
            return Optional.empty();
        }
    }

    /** Stores {@code body} as {@code name}, replacing what was there; answers what is stored now. */
    public Stored push(String name, byte[] body) {
        if (!validName(name)) {
            throw new InvalidPatternException(name, "the name is lowercase letters, digits and hyphens, "
                    + "the rule `shio patterns save` enforces");
        }
        if (body == null || body.length == 0 || body.length > MAX_BYTES) {
            throw new InvalidPatternException(name, "the body is empty or larger than "
                    + (MAX_BYTES / 1024) + " KB");
        }
        validate(name, body);
        if (!blueprintProperties.hasPath()) {
            throw new ShMarketplaceInstaller.NoBlueprintPathException();
        }
        Path directory = directory();
        try {
            Files.createDirectories(directory);
            Path staged = Files.createTempFile(directory, name, ".tmp");
            Files.write(staged, body);
            Files.move(staged, directory.resolve(name + ".json"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UnreadableException(ShIoFailure.describe(unwritable));
        }
        return read(name).orElseThrow(() -> new UnreadableException("the stored file did not read back"));
    }

    private void validate(String name, byte[] body) {
        JsonNode pattern;
        try {
            pattern = objectMapper.readTree(body);
        } catch (JacksonException malformed) {
            throw new InvalidPatternException(name, "it is not valid JSON");
        }
        if (pattern == null || !pattern.isObject()) {
            throw new InvalidPatternException(name, "a pattern is a JSON object with a `sections` array");
        }
        if (!pattern.path("formatVersion").isInt() || pattern.path("formatVersion").intValue() != FORMAT) {
            throw new InvalidPatternException(name, "formatVersion must be " + FORMAT
                    + ", the format `shio patterns save` writes");
        }
        if (!name.equals(pattern.path("name").stringValue(null))) {
            throw new InvalidPatternException(name, "the file's `name` must be the name it is pushed as");
        }
        JsonNode sections = pattern.path("sections");
        if (!sections.isArray() || sections.isEmpty()) {
            throw new InvalidPatternException(name, "`sections` is missing or empty, so there is nothing "
                    + "to place");
        }
        for (int index = 0; index < sections.size(); index++) {
            String type = sections.get(index).path("type").stringValue(null);
            if (type == null || type.isBlank()) {
                throw new InvalidPatternException(name, "section " + index + " has no `type`");
            }
        }
    }

    private Summary summaryOf(Stored stored) {
        JsonNode pattern = stored.pattern();
        List<String> types = new ArrayList<>();
        pattern.path("sections").forEach(section -> types.add(section.path("type").stringValue("")));
        List<String> params = new ArrayList<>();
        for (String param : pattern.path("params").propertyNames()) {
            params.add(param);
        }
        return new Summary(stored.name(), pattern.path("title").stringValue(null),
                pattern.path("from").stringValue(null), List.copyOf(types), List.copyOf(params),
                stored.digest(), stored.pushed());
    }

    private Path directory() {
        return Path.of(blueprintProperties.getPath().trim()).normalize().resolve(DIRECTORY);
    }

    static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** {@code sha256:<hex>} over the bytes, which is what the CLI computes over its file. */
    static String digest(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A body the CLI's own reader would refuse, refused here with the same reason. */
    public static class InvalidPatternException extends ShTeachingException {

        public InvalidPatternException(String name, String reason) {
            super(ShProblem.of("invalid-pattern", HttpStatus.BAD_REQUEST,
                            "This is not a pattern the console could place")
                    .withDetail("Pattern '" + name + "': " + reason + ".")
                    .withFix("Push the file `shio patterns save` wrote, unedited in shape: "
                            + "shio patterns push " + (validName(name) ? name : "<name>"))
                    .withExample("{\"formatVersion\":1,\"name\":\"pricing-block\","
                            + "\"sections\":[{\"type\":\"SectionHero\",\"data\":{}}]}"));
        }
    }

    /** The directory exists and could not be read or written. */
    public static class UnreadableException extends ShTeachingException {

        public UnreadableException(String reason) {
            super(ShProblem.of("patterns-unreadable", HttpStatus.INTERNAL_SERVER_ERROR,
                            "The pattern directory could not be read or written")
                    .withDetail(reason + ".")
                    .withFix("Make " + DIRECTORY + " under shio.blueprint.path writable by this "
                            + "instance."));
        }
    }
}
