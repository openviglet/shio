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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.dto.ShConsoleSurface;
import com.viglet.shio.utils.ShStableMap;

import tools.jackson.databind.ObjectMapper;

/**
 * The console's surfaces, read once, for every reader of them (SH978/SH979).
 *
 * <h2>Why a bean and not a constant</h2>
 *
 * <p>SH978 declared the surfaces in {@code console/surfaces.json} and one test read it. A
 * declaration with one reader is a second source of truth: the rail, the command palette, the
 * manifest and MCP would each go on describing the console their own way, which is the state the
 * declaration was written to end. So it is loaded here, once, and the four readers ask this.</p>
 *
 * <p>Read from the classpath at startup rather than parsed per request: it is a file that ships
 * with the build, so a caller paying to re-read it would be paying for nothing. The order is the
 * file's own — the console's rail order — because two readers printing the same list differently is
 * the kind of difference somebody spends an afternoon on.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShConsoleSurfaces {

    /** Where the declaration lives. The console reads this same file through its own import. */
    public static final String FILE = "/console/surfaces.json";

    private final List<ShConsoleSurface> surfaces;
    private final Map<String, ShConsoleSurface.Reason> reasons;
    private final List<ShConsoleSurface.ConsoleOnly> consoleOnly;

    public ShConsoleSurfaces(ObjectMapper objectMapper) {
        try (InputStream in = ShConsoleSurfaces.class.getResourceAsStream(FILE)) {
            if (in == null) {
                throw new IllegalStateException(FILE + " is not on the classpath — the console's "
                        + "surfaces are declared there and four readers need them");
            }
            Declaration declaration = objectMapper.readValue(in, Declaration.class);
            this.surfaces = List.copyOf(declaration.surfaces());
            // The order the file declares, kept: the reasons are printed as a set in a failure
            // message and read by a person comparing two runs (SH: byte stability).
            this.reasons = ShStableMap.copyOf(declaration.reasons());
            this.consoleOnly = List.copyOf(declaration.consoleOnly());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + FILE, e);
        }
    }

    /** Every surface, in the console's own order. */
    public List<ShConsoleSurface> surfaces() {
        return surfaces;
    }

    /**
     * The words an exception may use, and what each claims (SH980).
     *
     * <p>A closed set, declared in the document every reader holds rather than in one test: a
     * console-only task then adds a classified entry instead of arguing its P1 position in a
     * paragraph, which is what the gate cannot read.</p>
     */
    public Map<String, ShConsoleSurface.Reason> reasons() {
        return reasons;
    }

    /** What the console does that has no agent leg by nature, classified in the same words. */
    public List<ShConsoleSurface.ConsoleOnly> consoleOnly() {
        return consoleOnly;
    }

    /** The file's top level. The comment block it carries is for whoever opens it, not for this. */
    private record Declaration(List<ShConsoleSurface> surfaces,
                               Map<String, ShConsoleSurface.Reason> reasons,
                               List<ShConsoleSurface.ConsoleOnly> consoleOnly) {
    }
}
