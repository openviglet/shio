/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.api.object.dto.ShCopyResult;
import com.viglet.shio.api.query.ShContentQuery;
import com.viglet.shio.api.acl.ShContentAclService.AclEntry;
import com.viglet.shio.persistence.model.acl.ShContentAcl;
import com.viglet.shio.persistence.model.object.ShObject;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Content-tree navigation and move/copy API, rebuilt for the unified post model
 * (the legacy {@code ShObjectAPI} was removed in the nuclear refactor). Backs the
 * React console's content browser: {@code /list}, {@code /path}, {@code /copyto},
 * {@code /moveto}.
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/object")
@Tag(name = "Object", description = "Content object navigation API")
public class ShObjectAPI {

    /**
     * The words this listing serves (SH1022).
     *
     * <p>Everything but {@code site} and {@code folder}: the folder is the {@code {id}} in the
     * path, and a second one in the query string would be two answers to one question. They are
     * refused rather than ignored, and the refusal says where they do work.</p>
     */
    private static final List<String> LIST_SERVED = ShContentQuery.KEYS.stream()
            .filter(key -> !List.of("site", "folder").contains(key)).toList();

    /** SH1024 — the listing's own parameter, outside the vocabulary: ask for the facet counts. */
    private static final List<String> LIST_OWN = List.of("facets");

    private static final String LIST_ELSEWHERE =
            "This listing's folder is the {id} in its path. To list across folders or sites, "
                    + "use GET /api/v2/agent/find, which takes `site` and `folder` as addresses.";

    private final ShObjectNavigationService shObjectNavigationService;
    private final ShObjectWriteService shObjectWriteService;
    private final ShContentAclService shContentAclService;
    /** SH1101 — how a copied post is named in the answer, the same way every agent read names it. */
    private final ShAddressResolver shAddressResolver;

    public ShObjectAPI(ShObjectNavigationService shObjectNavigationService,
                       ShObjectWriteService shObjectWriteService,
                       ShContentAclService shContentAclService,
                       ShAddressResolver shAddressResolver) {
        this.shAddressResolver = shAddressResolver;
        this.shObjectNavigationService = shObjectNavigationService;
        this.shObjectWriteService = shObjectWriteService;
        this.shContentAclService = shContentAclService;
    }

    /**
     * An object's children — child folders whole, posts one page at a time (SH165).
     *
     * <p>It used to return every child of a folder, so a folder with ten thousand posts was
     * one query, one response and one render; the CDA listing has been paged since SH1 and this
     * one never was. The parameters are the CDA's plus the two the console needs and a delivery
     * API does not: {@code sort}, because the browser's own sort would otherwise sort a single
     * page, and {@code q}, because its search box would otherwise search one.</p>
     *
     * <p>An unknown {@code sort} is a teaching {@code 400} rather than a fallback to the
     * default — see {@link ShUnknownListingSortException}.</p>
     *
     * <p><b>SH1022 — it now takes the whole content-query vocabulary</b>: {@code type},
     * {@code state}, {@code locale}, {@code modifiedSince}, {@code modifiedBefore},
     * {@code author}, {@code modifier} and {@code subtree}, each meaning what it means on
     * {@code /agent/find}. {@code site} and {@code folder} are the two it does not serve — the
     * folder is the {@code {id}} in the path, and naming a second one would be two answers to
     * one question.</p>
     *
     * @param params the request's own parameters, which is how an unknown one is refused at all:
     *               Spring binds what it recognises and silently drops the rest, so
     *               {@code ?modifedSince=} would otherwise return every row with a {@code 200}
     */
    @GetMapping("/{id}/list")
    public ResponseEntity<ShConsoleListing> list(@PathVariable String id,
            @RequestParam Map<String, String> params) {
        shContentAclService.requireRead(id);
        ShContentQuery query = ShContentQuery.of(params, LIST_SERVED, LIST_OWN, LIST_ELSEWHERE);
        // SH1024 — the facet counts are this listing's own word, and opt-in: a caller that draws
        // no filter bar pays nothing for them.
        boolean facets = "true".equalsIgnoreCase(params.get("facets"));
        return shObjectNavigationService.list(id, query, facets)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/path")
    public ResponseEntity<ShCdaPath> path(@PathVariable String id) {
        shContentAclService.requireRead(id);
        return shObjectNavigationService.path(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Duplicates objects into {@code destId}.
     *
     * <h2>Both ends are authorized (SH201)</h2>
     *
     * <p>The destination needs WRITE — that is where the new content lands. The sources
     * need <b>READ</b>, because a copy is a read of them: without that check, write on any
     * single folder was enough to lift content out of a subtree the caller was never
     * granted read on, and the copy is then theirs to keep.</p>
     */
    @PutMapping("/copyto/{destId}")
    public ShCopyResult copyTo(@PathVariable String destId, @RequestBody List<String> ids) {
        shContentAclService.requireWrite(destId);
        requireEach(ids, shContentAclService::requireRead);
        ShObjectWriteService.Copied copied = shObjectWriteService.copyTo(destId, ids);
        // SH1101 — the posts too, addressed the way every agent read addresses them, so the caller
        // that copied one can write about it next (the visual editor's Duplicate, first).
        return new ShCopyResult(copied.folders(), copied.posts().stream()
                .map(post -> new ShCopyResult.Post(post.getId(), post.getTitle(), post.getFurl(),
                        shAddressResolver.addressOf(post).toString()))
                .toList());
    }

    /**
     * Relocates objects. The mover is threaded through because a move is now an
     * audited content change rather than a silent re-parent (SH139).
     *
     * <h2>Both ends are authorized (SH201)</h2>
     *
     * <p>A move needs WRITE on the <b>sources</b> as well as the destination: from the
     * owner's point of view a move out of their subtree is a removal, and this endpoint
     * used to check only where the content was going. The agent path has checked both ends
     * since SH134/SH137, so this was the console being the looser of the two surfaces —
     * Block L's invariant read from the other end. The rule to keep is symmetrical: the two
     * surfaces enforce the same ACL, and whichever is stricter is the correct one.</p>
     */
    @PutMapping("/moveto/{destId}")
    public ShMoveOutcome moveTo(@PathVariable String destId, @RequestBody List<String> ids,
                                Principal principal) {
        shContentAclService.requireWrite(destId);
        requireEach(ids, shContentAclService::requireWrite);
        return shObjectWriteService.moveTo(destId, ids,
                principal != null ? principal.getName() : null);
    }

    /**
     * Applies a permission check to every id in the request body <em>before</em> the write
     * service runs, so a partially-authorized batch moves nothing at all rather than
     * relocating the ids that happened to be listed first.
     */
    private static void requireEach(List<String> ids, java.util.function.Consumer<String> check) {
        if (ids == null) {
            return;
        }
        ids.stream().filter(java.util.Objects::nonNull).forEach(check);
    }

    /** The explicit ACL grants set directly on an object (SH54); inherited grants are not expanded. */
    @GetMapping("/{id}/acl")
    public ResponseEntity<List<ShContentAcl>> getAcl(@PathVariable String id) {
        shContentAclService.requireRead(id);
        return ResponseEntity.ok(shContentAclService.getAcl(id));
    }

    /**
     * SH995 — what the signed-in user may do with this object: read, write, publish, and the sentence
     * to show instead of a Publish button when they may not.
     *
     * <p>Readable by anybody who may read the object, because that is exactly who the answer is
     * about. It exists so the console can disable a control rather than offer it and produce a 403 —
     * and it hands over the server's own wording, so the explanation a curator reads is the one the
     * refusal would have given.</p>
     */
    @GetMapping("/{id}/allowed")
    public ResponseEntity<ShContentAclService.Allowed> allowed(@PathVariable String id) {
        shContentAclService.requireRead(id);
        return ResponseEntity.ok(shContentAclService.allowed(id));
    }

    /**
     * SH1302 — who may read an object's delivered pages: {@code public}, or {@code members} with the
     * groups (none: any signed-in user). The object's own gate; one inherited from a folder above is
     * that folder's to show.
     */
    public record Visibility(String visibility, List<String> groups) {
    }

    /** SH1302 — the console's read of {@code folder.visibility} / {@code post.visibility}. */
    @GetMapping("/{id}/visibility")
    public ResponseEntity<Visibility> getVisibility(@PathVariable String id) {
        shContentAclService.requireRead(id);
        return ResponseEntity.ok(shContentAclService.ownGate(id)
                .map(groups -> new Visibility("members", groups))
                .orElse(new Visibility("public", List.of())));
    }

    /**
     * SH1302 — the console's write of the same gate the ops write, through the same service and under
     * the same WRITE guard, so the agent path and the console grant exactly the same thing.
     */
    @PutMapping("/{id}/visibility")
    public ResponseEntity<Visibility> putVisibility(@PathVariable String id, @RequestBody Visibility body) {
        shContentAclService.requireWrite(id);
        String spelled = body == null || body.visibility() == null ? ""
                : body.visibility().trim().toLowerCase(java.util.Locale.ROOT);
        if (!spelled.equals("public") && !spelled.equals("members")) {
            throw new InvalidVisibilityException(body == null ? null : body.visibility());
        }
        List<String> groups = body.groups() == null ? List.of()
                : body.groups().stream().filter(java.util.Objects::nonNull).map(String::trim)
                        .filter(group -> !group.isEmpty()).toList();
        boolean members = spelled.equals("members");
        String unknown = members ? shContentAclService.unknownGroup(groups) : null;
        if (unknown != null) {
            throw new UnknownGroupException(unknown);
        }
        shContentAclService.replaceGate(id, members, groups);
        return getVisibility(id);
    }

    /** SH1302 — a visibility that is not {@code public} or {@code members}. */
    public static class InvalidVisibilityException extends com.viglet.shio.api.error.ShTeachingException {
        public InvalidVisibilityException(String sent) {
            super(com.viglet.shio.api.error.ShProblem.badRequest("invalid-visibility",
                            "'" + sent + "' is not a visibility")
                    .withAllowed(List.of("public", "members"))
                    .withFix("Send {\"visibility\":\"members\",\"groups\":[\"Members\"]}, or \"public\"."));
        }
    }

    /** SH1302 — a members-only gate for a group the instance does not have. */
    public static class UnknownGroupException extends com.viglet.shio.api.error.ShTeachingException {
        public UnknownGroupException(String group) {
            super(com.viglet.shio.api.error.ShProblem.badRequest("unknown-member-group",
                            "No group named '" + group + "'")
                    .withDetail("A gate for nobody would hide the content from everyone but an administrator.")
                    .withFix("Create the group first (Administration > Groups), or leave groups empty to "
                            + "open it to any signed-in user."));
        }
    }

    /** Replace an object's ACL grants (SH54). Editing requires WRITE on the object (admins always pass). */
    @PutMapping("/{id}/acl")
    public ResponseEntity<List<ShContentAcl>> putAcl(@PathVariable String id, @RequestBody List<AclEntry> entries) {
        shContentAclService.requireWrite(id);
        return ResponseEntity.ok(shContentAclService.replaceAcl(id, entries));
    }

}
