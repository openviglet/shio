/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.memory.dto.ShAgentMemory;
import com.viglet.shio.api.agent.memory.dto.ShAgentNote;
import com.viglet.shio.api.agent.memory.dto.ShAgentNoteRequest;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.agent.ShAgentMemoryEntry;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.agent.ShAgentMemoryRepository;

/**
 * The instance memory (SH114) — read, upsert, forget.
 *
 * <h2>What makes session N+1 cheaper than session N</h2>
 *
 * <p>Everything else in the token-economy block makes a single session cheaper.
 * This is the only piece that makes the <em>next</em> one cheaper, and it does it
 * by turning a conclusion into a fact the context pack already carries. The read
 * path is therefore free: an agent that reads the context pack — which it does
 * first, always — has already read the memory.</p>
 *
 * <h2>Scoping follows the site allow-list, not the memory table</h2>
 *
 * <p>A note scoped to a site the caller cannot read is not returned. Notes name
 * their site by <em>name</em> rather than id, so they survive an export/import
 * (SH68) the way an SH79 address does, and the filter is therefore a name
 * comparison against {@link ShAgentSiteAccess#readable()} rather than a join. A
 * note whose site has since been deleted or renamed is retained but hidden —
 * dropping the row on a rename would silently discard a human's instruction, and
 * showing it would leak a name outside the allow-list.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShAgentMemoryService {

    /** Slug grammar: lowercase, digits, dash, dot — the same alphabet a file name uses. */
    private static final String KEY_PATTERN = "[a-z0-9][a-z0-9.-]*";

    private final ShAgentMemoryRepository repository;
    private final ShAgentSiteAccess siteAccess;
    private final ShAgentProperties properties;

    public ShAgentMemoryService(ShAgentMemoryRepository repository,
                                ShAgentSiteAccess siteAccess,
                                ShAgentProperties properties) {
        this.repository = repository;
        this.siteAccess = siteAccess;
        this.properties = properties;
    }

    /**
     * Every readable note, instance-wide ones first.
     *
     * @param site  restrict to one site's notes plus the instance-wide ones —
     *              the shape a session working on one site wants
     * @param limit rows to return; the {@code found} count always describes the
     *              full set so a cut is visible
     */
    public ShAgentMemory memory(String site, Integer limit) {
        List<ShAgentNote> all = notes(site);
        int cap = resolveLimit(limit);
        return new ShAgentMemory(all.size(), all.size() > cap ? all.subList(0, cap) : all);
    }

    /**
     * The readable notes as a flat, deterministically ordered list — the form the
     * context pack (SH77) and the {@code AGENTS.md} renderer (SH115) consume.
     */
    public List<ShAgentNote> notes(String site) {
        Set<String> readable = readableScopes(site);
        List<ShAgentNote> notes = new ArrayList<>();
        for (ShAgentMemoryEntry row : repository.findAllByOrderByScopeAscMemoryKeyAsc()) {
            String scope = nullSafe(row.getScope());
            if (!scope.isEmpty() && !readable.contains(scope.toLowerCase(Locale.ROOT))) {
                continue;
            }
            notes.add(new ShAgentNote(scope.isEmpty() ? null : scope, row.getMemoryKey(),
                    row.getNote(), row.getAuthor(),
                    row.getModifiedDate() != null ? row.getModifiedDate() : row.getCreatedDate()));
        }
        // Instance-wide first: they are the rules that hold everywhere, and an agent
        // reading top-to-bottom should meet them before the per-site exceptions.
        // Stable within each group by (scope, key) — the repository's ordering.
        List<ShAgentNote> ordered = new ArrayList<>(notes.stream().filter(n -> n.scope() == null).toList());
        ordered.addAll(notes.stream().filter(n -> n.scope() != null).toList());
        return List.copyOf(ordered);
    }

    /**
     * Write one note, idempotently by {@code (scope, key)}.
     *
     * @param author the principal, recorded and reported — see the DTO note on why
     *               a caller may not supply it
     */
    @Transactional
    public ShAgentNote remember(ShAgentNoteRequest request, String author) {
        String key = requireKey(request == null ? null : request.key());
        String scope = requireScope(request.scope());
        String note = requireNote(request.note(), key);

        Instant now = Instant.now();
        ShAgentMemoryEntry row = repository.findByScopeAndMemoryKey(scope, key)
                .orElseGet(ShAgentMemoryEntry::new);
        if (row.getCreatedDate() == null) {
            row.setCreatedDate(now);
            row.setScope(scope);
            row.setMemoryKey(key);
        }
        row.setNote(note);
        row.setAuthor(author);
        row.setModifiedDate(now);
        repository.save(row);
        return new ShAgentNote(scope.isEmpty() ? null : scope, key, note, author, now);
    }

    /**
     * One site's own notes — <b>not</b> the instance-wide ones, and not filtered by the SH2
     * allow-list (SH161).
     *
     * <p>The travelling set. An SH68 export carries this and nothing else, because the
     * instance-wide notes describe the <em>installation</em> — "copy is authored in files
     * here", "this deployment publishes on Fridays" — and importing another instance's house
     * rules alongside somebody's blog is the kind of surprise P7 exists to prevent.</p>
     *
     * <p>No allow-list filter because the caller has already been authorized for the site by
     * the export endpoint, and a partial export is worse than none: it would produce a package
     * that looks complete and silently drops what the reader could not see.</p>
     */
    public List<ShAgentNote> scopedNotes(String siteName) {
        if (siteName == null || siteName.isBlank()) {
            return List.of();
        }
        List<ShAgentNote> notes = new ArrayList<>();
        for (ShAgentMemoryEntry row : repository.findAllByOrderByScopeAscMemoryKeyAsc()) {
            if (siteName.equalsIgnoreCase(nullSafe(row.getScope()))) {
                notes.add(new ShAgentNote(row.getScope(), row.getMemoryKey(), row.getNote(),
                        row.getAuthor(),
                        row.getModifiedDate() != null ? row.getModifiedDate() : row.getCreatedDate()));
            }
        }
        return List.copyOf(notes);
    }

    /**
     * Re-file a package's notes against the site they arrived with (SH161).
     *
     * <p>Called on import, where the site may well have been <em>renamed</em> — a clone
     * uniquifies the name, and a tenant provision suffixes it. A note names its site by name,
     * so re-scoping is the whole job; carrying the exported scope through verbatim would file
     * every note against a site that does not exist here, which is indistinguishable from
     * losing them.</p>
     *
     * <p>The original {@code author} is preserved and the timestamps are set to now, and that
     * split is deliberate. Attribution is the load-bearing half — SH114 keeps it precisely so a
     * human's instruction and an agent's inference stay distinguishable — while the dates
     * describe this instance's copy, which really did arrive now.</p>
     *
     * <p>Idempotent by {@code (scope, key)} like every other write here, so re-importing a
     * package updates the notes rather than duplicating them. Validation is skipped on purpose:
     * these values were validated when they were first written, and refusing a whole site
     * import over a note that predates a tightened key rule would be the wrong trade.</p>
     *
     * @return how many notes were written
     */
    @Transactional
    public int importNotes(String siteName, List<ShAgentNote> notes) {
        if (siteName == null || siteName.isBlank() || notes == null || notes.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        int written = 0;
        for (ShAgentNote note : notes) {
            String key = note.key() == null ? "" : note.key().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || note.note() == null || note.note().isBlank()) {
                continue;
            }
            ShAgentMemoryEntry row = repository.findByScopeAndMemoryKey(siteName, key)
                    .orElseGet(ShAgentMemoryEntry::new);
            if (row.getCreatedDate() == null) {
                row.setCreatedDate(now);
                row.setScope(siteName);
                row.setMemoryKey(key);
            }
            row.setNote(note.note());
            row.setAuthor(note.author());
            row.setModifiedDate(now);
            repository.save(row);
            written++;
        }
        return written;
    }

    /**
     * Forget one note.
     *
     * @return {@code true} when a row was removed; {@code false} when there was
     *         nothing to remove — a delete is idempotent, and a 404 for "already
     *         gone" would make a retry look like a failure
     */
    @Transactional
    public boolean forget(String scope, String key) {
        String normalizedKey = requireKey(key);
        String normalizedScope = requireScope(scope);
        Optional<ShAgentMemoryEntry> row =
                repository.findByScopeAndMemoryKey(normalizedScope, normalizedKey);
        row.ifPresent(repository::delete);
        return row.isPresent();
    }

    /**
     * Site names this credential may see, lower-cased for comparison. Includes the
     * selector's site when one is named, so {@code ?site=} narrows rather than
     * widens: an unreadable selector still raises the teaching 404.
     */
    private Set<String> readableScopes(String site) {
        List<ShSite> sites = site == null || site.isBlank()
                ? siteAccess.readable() : siteAccess.readable(site);
        Set<String> scopes = new LinkedHashSet<>();
        for (ShSite candidate : sites) {
            scopes.add(nullSafe(candidate.getName()).toLowerCase(Locale.ROOT));
        }
        return scopes;
    }

    private int resolveLimit(Integer limit) {
        ShAgentProperties.Memory config = properties.getMemory();
        if (limit == null || limit <= 0) {
            return config.getDefaultLimit();
        }
        return Math.min(limit, config.getMaxLimit());
    }

    /** Blank scope = instance-wide; a named one must be a readable site. */
    private String requireScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return ShAgentMemoryEntry.INSTANCE_SCOPE;
        }
        // Resolves through the allow-list, so an unreadable site is a teaching 404
        // rather than a note filed against a name nobody will ever read back.
        return siteAccess.require(scope.trim()).getName();
    }

    private String requireKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches(KEY_PATTERN)
                || normalized.length() > properties.getMemory().getMaxKeyLength()) {
            throw new InvalidNoteException("key", normalized.isEmpty() ? "(none)" : key,
                    "A key is a slug: lowercase letters, digits, dot and dash, up to "
                            + properties.getMemory().getMaxKeyLength() + " characters.",
                    "Use a stable slug so re-stating the same convention rewrites one note "
                            + "instead of adding another.",
                    "{\"key\":\"blog.post-type\",\"note\":\"This site's blog uses Article, not Post.\"}");
        }
        return normalized;
    }

    private String requireNote(String note, String key) {
        String normalized = note == null ? "" : note.trim();
        int max = properties.getMemory().getMaxNoteLength();
        if (normalized.isEmpty()) {
            throw new InvalidNoteException("note", "(empty)",
                    "A note must say something; an empty one is a delete.",
                    "Send the instruction, or DELETE /api/v2/agent/memory?key=" + key + " to forget it.",
                    "{\"key\":\"" + key + "\",\"note\":\"Copy is authored in files, never via the API.\"}");
        }
        if (normalized.length() > max) {
            throw new InvalidNoteException("note", normalized.length() + " characters",
                    "A note is an instruction, not a document — " + max + " characters at most.",
                    "Split it into several keyed notes, or put the long form in content and "
                            + "let the note point at its address.",
                    "{\"key\":\"" + key + "\",\"note\":\"<one sentence>\"}");
        }
        return normalized;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** A 400 that says which field, why, and what a good one looks like (SH80). */
    public static class InvalidNoteException extends ShTeachingException {

        public InvalidNoteException(String field, String sent, String detail, String fix, String example) {
            super(ShProblem.badRequest("invalid-memory-" + field,
                            "Invalid memory " + field + " '" + sent + "'")
                    .withDetail(detail)
                    .withFix(fix)
                    .withExample(example));
        }
    }
}
