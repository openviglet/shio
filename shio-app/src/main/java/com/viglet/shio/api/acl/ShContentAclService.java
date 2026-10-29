/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.acl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.acl.ShAclPermission;
import com.viglet.shio.persistence.model.acl.ShAclPrincipalType;
import com.viglet.shio.persistence.model.acl.ShContentAcl;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.acl.ShContentAclRepository;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShRoleRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

/**
 * Content-level access control (SH54). Resolves whether the current console user
 * may READ/WRITE a folder/site/post, honouring:
 *
 * <ul>
 *   <li><b>Folder-tree inheritance</b> — the <i>nearest</i> object up the chain
 *       (the object itself, then its folder, then ancestor folders) that carries
 *       any ACL entry decides; levels above it are not consulted.</li>
 *   <li><b>Default-allow</b> — if no object up the whole chain has any ACL, access
 *       is open (so existing installs are unaffected until an ACL is set).</li>
 *   <li><b>Admin bypass</b> — a {@code ROLE_ADMIN} principal always passes, so an
 *       ACL can never lock the administrator out.</li>
 *   <li><b>No security context</b> — internal/system calls (no authentication) are
 *       allowed, matching the rest of the service layer.</li>
 * </ul>
 *
 * Enforcement lives on the console authoring boundary (navigation + folder/post
 * writes); CDA delivery is already scoped per token by SH2.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShContentAclService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final ShContentAclRepository shContentAclRepository;
    private final ShObjectRepository shObjectRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShUserRepository shUserRepository;
    private final ShGroupRepository shGroupRepository;
    private final ShRoleRepository shRoleRepository;

    public ShContentAclService(ShContentAclRepository shContentAclRepository,
                               ShObjectRepository shObjectRepository,
                               ShFolderRepository shFolderRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShUserRepository shUserRepository,
                               ShGroupRepository shGroupRepository,
                               ShRoleRepository shRoleRepository) {
        this.shContentAclRepository = shContentAclRepository;
        this.shObjectRepository = shObjectRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shUserRepository = shUserRepository;
        this.shGroupRepository = shGroupRepository;
        this.shRoleRepository = shRoleRepository;
    }

    @Transactional(readOnly = true)
    public boolean canRead(String objectId) {
        return hasPermission(objectId, ShAclPermission.READ, null);
    }

    /**
     * SH1077 — {@link #canRead(String)}, with the lookups that cannot change inside one pass
     * answered from {@code memo}.
     *
     * <p>The decision is the same method's. What the memo holds is the <b>inputs</b>: where an
     * object's folder chain starts, each folder's parent, the ACL rows on each level, and a user's
     * principals. So the answer cannot drift from the one a REST read gets. The change stream asks
     * the same question for every entry on every open stream, and before this each ask re-read all
     * of it.</p>
     *
     * @param memo from {@link #readMemo()}, scoped to one pass and one tenant by the caller. A memo
     *             that outlives the pass would keep answering after an ACL changed.
     */
    @Transactional(readOnly = true)
    public boolean canRead(String objectId, ReadMemo memo) {
        return hasPermission(objectId, ShAclPermission.READ, memo);
    }

    /** SH1077 — an empty memo for {@link #canRead(String, ReadMemo)}. */
    public ReadMemo readMemo() {
        return new ReadMemo();
    }

    /**
     * SH1077 — what one pass has already read, keyed so that nothing in it depends on who asked
     * except the principals, which are keyed by username.
     */
    public static final class ReadMemo {
        private final Map<String, Optional<String>> startFolders = new HashMap<>();
        private final Map<String, Optional<String>> parents = new HashMap<>();
        private final Map<String, List<ShContentAcl>> acls = new HashMap<>();
        private final Map<String, Principals> principals = new HashMap<>();

        private ReadMemo() {
        }
    }

    @Transactional(readOnly = true)
    public boolean canWrite(String objectId) {
        return hasPermission(objectId, ShAclPermission.WRITE, null);
    }

    /** SH995 — whether the current user may change what visitors see. */
    @Transactional(readOnly = true)
    public boolean canPublish(String objectId) {
        return hasPermission(objectId, ShAclPermission.PUBLISH, null);
    }

    /**
     * SH995 — what the current user may do with this object, and why not, in the server's words.
     *
     * <p>The console asks this to decide whether Publish is a button or an explanation. It carries
     * the <b>sentence</b> rather than a flag the console phrases itself: the refusal a curator would
     * have got from the server is the one they should read before making the request, or the product
     * has two answers to one question and only one of them is enforced.</p>
     *
     * @param publishHint {@code null} when they may publish — a hint beside a permitted action is
     *                    something a reader has to work out is not about them
     */
    public record Allowed(boolean read, boolean write, boolean publish, String publishHint) {
    }

    @Transactional(readOnly = true)
    public Allowed allowed(String objectId) {
        boolean publish = canPublish(objectId);
        return new Allowed(canRead(objectId), canWrite(objectId), publish,
                publish ? null : PUBLISH_DENIED);
    }

    /** One sentence, used by the refusal and by the console that avoids provoking it. */
    private static final String PUBLISH_DENIED =
            "the draft is saved, and somebody granted PUBLISH here can put it live";

    /** Throw {@link AccessDeniedException} (→ 403) unless the current user may read it. */
    /**
     * SH225 — annotated because this is the <b>entry point</b>, not because `canRead` is.
     *
     * <p>These reach `canRead`/`canWrite` by self-invocation, which never crosses the proxy — so
     * the annotation on those methods never applied and the check ran in whatever transaction the
     * caller happened to have. Annotating here is what makes one exist; the inner annotation is
     * decoration, kept because the methods are also called directly.</p>
     */
    @Transactional(readOnly = true)
    public void requireRead(String objectId) {
        if (!canRead(objectId)) {
            throw new AccessDeniedException("No read permission on " + objectId);
        }
    }

    /** Throw {@link AccessDeniedException} (→ 403) unless the current user may write it. */
    /** SH225 — see {@link #requireRead}: the annotation belongs on the entry point. */
    @Transactional(readOnly = true)
    public void requireWrite(String objectId) {
        if (!canWrite(objectId)) {
            throw new AccessDeniedException("No write permission on " + objectId);
        }
    }

    /**
     * SH995 — throw unless the current user may put this object in front of visitors.
     *
     * <p>The message says what to do about it, because the person who reads it is a curator being
     * told no by a product that used to say yes: the draft is saved either way, and somebody with
     * PUBLISH on this branch of the tree is who finishes it.</p>
     */
    @Transactional(readOnly = true)
    public void requirePublish(String objectId) {
        if (!canPublish(objectId)) {
            throw new AccessDeniedException("No publish permission on " + objectId
                    + " — " + PUBLISH_DENIED);
        }
    }

    private boolean hasPermission(String objectId, ShAclPermission required, ReadMemo memo) {
        if (objectId == null) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return true; // internal/system call — not user-gated
        }
        if (isAdmin(auth)) {
            return true;
        }
        for (String level : aclChain(objectId, memo)) {
            List<ShContentAcl> acls = memo == null
                    ? shContentAclRepository.findByObjectId(level)
                    : memo.acls.computeIfAbsent(level, shContentAclRepository::findByObjectId);
            if (!acls.isEmpty()) {
                Principals who = memo == null
                        ? currentPrincipals(auth.getName())
                        : memo.principals.computeIfAbsent(auth.getName(), this::currentPrincipals);
                return acls.stream().anyMatch(a -> matches(a, who) && a.getPermission().satisfies(required));
            }
        }
        return true; // no ACL anywhere up the chain → default-allow
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    private static boolean matches(ShContentAcl acl, Principals who) {
        return switch (acl.getPrincipalType()) {
            case USER -> who.username().equals(acl.getPrincipalName());
            case GROUP -> who.groups().contains(acl.getPrincipalName());
            case ROLE -> who.roles().contains(acl.getPrincipalName());
        };
    }

    /** The object's id followed by its folder and that folder's ancestors, nearest first. */
    /**
     * The object's id followed by its folder and that folder's ancestors, nearest first.
     *
     * <h2>Climbed by id, not by association (SH225)</h2>
     *
     * <p>This used to walk {@code folder.getParentFolder()}, which is lazy — so a permission
     * decision needed an open persistence context and got one only because
     * {@code spring.jpa.open-in-view} defaults to true. Outside a request it answered a
     * {@code LazyInitializationException} instead of yes or no, and <b>the code that has to work is
     * the code that denies</b>.</p>
     *
     * <p>An id-keyed query per level costs the same round trips the association walk did — it was
     * lazy, so each hop was already a query — and it takes the SH22 join hazard out of a security
     * check as well. A chain is a handful of levels deep, and a deeper one is a content model
     * problem rather than a reason to keep the graph walk.</p>
     *
     * <p>The visited set is not just deduplication: a folder cycle would otherwise spin here
     * forever, in the one place an instance cannot afford to.</p>
     */
    private List<String> aclChain(String objectId, ReadMemo memo) {
        List<String> chain = new ArrayList<>();
        chain.add(objectId);
        // `visited` is separate from `chain` on purpose, and the first draft of SH225 conflated
        // them: for a folder, `startFolderId` returns the object's own id, which `chain` already
        // holds — so a single contains() guard doing double duty as the cycle brake stopped the
        // walk before its first hop, and every folder silently stopped inheriting its parent's
        // ACL. Deduplication and termination are two questions; `visited.add` answers only the
        // second, and a folder cycle is the one loop an authorization check cannot afford.
        Set<String> visited = new LinkedHashSet<>();
        for (String folderId = memo == null
                        ? startFolderId(objectId)
                        : memo.startFolders.computeIfAbsent(objectId,
                                id -> Optional.ofNullable(startFolderId(id))).orElse(null);
                folderId != null && visited.add(folderId);
                folderId = (memo == null
                        ? shFolderRepository.findParentIdOf(folderId)
                        : memo.parents.computeIfAbsent(folderId, shFolderRepository::findParentIdOf))
                        .orElse(null)) {
            if (!chain.contains(folderId)) {
                chain.add(folderId);
            }
        }
        return chain;
    }

    /**
     * Where the ancestor walk starts: the object itself if a folder, else a post's folder (SH225).
     *
     * <p>Returns an <b>id</b> rather than an entity, so nothing downstream can be tempted back onto
     * the lazy graph. The post's folder is read through its own id projection for the same reason —
     * {@code post.getShFolder()} is an association, and reading it here would put the session
     * dependency back one line below where it was removed.</p>
     */
    private String startFolderId(String objectId) {
        // ShLiveObject-exempt: this decides whose grants apply, not what a curator may see. A
        // trashed folder still has an ACL — the restore that brings it back is authorized by it —
        // and dropping it here would not deny the read, it would fall through to the post branch
        // below and walk the wrong chain (SH226).
        ShObject object = shObjectRepository.findById(objectId).orElse(null);
        if (object instanceof ShFolder folder) {
            return folder.getId();
        }
        if (object != null) {
            return null; // a site or post-type — no folder chain
        }
        return shPostUnifiedRepository.findFolderIdOf(objectId).orElse(null);
    }

    private Principals currentPrincipals(String username) {
        ShUser user = shUserRepository.findByUsername(username);
        if (user == null) {
            return new Principals(username, Set.of(), Set.of());
        }
        Set<ShGroup> groups = shGroupRepository.findByShUsersIn(List.of(user));
        Set<String> groupNames = groups.stream().map(ShGroup::getName).collect(Collectors.toSet());
        Set<String> roleNames = shRoleRepository.findByShGroupsIn(groups).stream()
                .map(r -> r.getName()).collect(Collectors.toSet());
        return new Principals(username, groupNames, roleNames);
    }

    // --- ACL management ---

    @Transactional(readOnly = true)
    public List<ShContentAcl> getAcl(String objectId) {
        return shContentAclRepository.findByObjectId(objectId);
    }

    /** Replace all ACL entries on an object with the supplied set (empty = open it back up). */
    @Transactional
    public List<ShContentAcl> replaceAcl(String objectId, List<AclEntry> entries) {
        shContentAclRepository.deleteByObjectId(objectId);
        List<ShContentAcl> saved = new ArrayList<>();
        if (entries != null) {
            for (AclEntry e : entries) {
                if (e == null || e.principalType() == null || e.principalName() == null
                        || e.principalName().isBlank() || e.permission() == null) {
                    continue;
                }
                ShContentAcl acl = new ShContentAcl();
                acl.setObjectId(objectId);
                acl.setPrincipalType(e.principalType());
                acl.setPrincipalName(e.principalName().trim());
                acl.setPermission(e.permission());
                saved.add(shContentAclRepository.save(acl));
            }
        }
        return saved;
    }

    /**
     * SH1277 — a declared grant list ({@code [{"group": "Editors", "permission": "WRITE"}]}) as entries.
     *
     * <p>Here rather than in the batch that reads it, because the vocabulary of principals and
     * permissions is this class's: a caller that spelled {@code ShAclPermission} itself would be a
     * second place deciding access (SH650's lint counts exactly that).</p>
     *
     * @return the entries, in declaration order
     * @throws MalformedGrantException naming the first grant that is not one principal and one permission
     */
    public static List<AclEntry> parseGrants(tools.jackson.databind.JsonNode list) {
        List<AclEntry> grants = new ArrayList<>();
        for (tools.jackson.databind.JsonNode grant : list) {
            List<ShAclPrincipalType> named = new ArrayList<>();
            for (ShAclPrincipalType type : ShAclPrincipalType.values()) {
                if (grant.hasNonNull(type.name().toLowerCase(java.util.Locale.ROOT))) {
                    named.add(type);
                }
            }
            String spelled = grant.path("permission").asString("").trim().toUpperCase(java.util.Locale.ROOT);
            ShAclPermission permission = java.util.Arrays.stream(ShAclPermission.values())
                    .filter(each -> each.name().equals(spelled)).findFirst().orElse(null);
            if (named.size() != 1 || permission == null) {
                throw new MalformedGrantException(grant.toString());
            }
            ShAclPrincipalType type = named.getFirst();
            grants.add(new AclEntry(type,
                    grant.path(type.name().toLowerCase(java.util.Locale.ROOT)).asString().trim(), permission));
        }
        return grants;
    }

    /** SH1277 — whether an object's own grants are exactly these, order aside. */
    @Transactional(readOnly = true)
    public boolean hasExactly(String objectId, List<AclEntry> wanted) {
        Set<String> have = shContentAclRepository.findByObjectId(objectId).stream()
                .map(acl -> acl.getPrincipalType() + "|" + acl.getPrincipalName() + "|" + acl.getPermission())
                .collect(Collectors.toSet());
        Set<String> want = wanted.stream()
                .map(grant -> grant.principalType() + "|" + grant.principalName() + "|" + grant.permission())
                .collect(Collectors.toSet());
        return have.equals(want);
    }

    /** A declared grant that is not one principal and one permission (SH1277). */
    public static class MalformedGrantException extends IllegalArgumentException {
        public MalformedGrantException(String grant) {
            super(grant);
        }
    }

    /**
     * SH1277 — the first grant naming a user or group this instance does not have, or null.
     *
     * <p>A grant to nobody is not a harmless no-op: the folder then has an ACL that matches no one,
     * so it stops being editable by everyone but an administrator, and the declaring package never
     * hears why. A role has no name lookup here and is not checked; an unknown role fails the same
     * closed way, which is the safe direction.</p>
     */
    @Transactional(readOnly = true)
    public AclEntry unknownPrincipal(List<AclEntry> entries) {
        for (AclEntry entry : entries) {
            boolean known = switch (entry.principalType()) {
                case USER -> shUserRepository.findByUsername(entry.principalName()) != null;
                case GROUP -> shGroupRepository.findByName(entry.principalName()) != null;
                case ROLE -> true;
            };
            if (!known) {
                return entry;
            }
        }
        return null;
    }

    /** The current user's resolved principal identifiers. */
    private record Principals(String username, Set<String> groups, Set<String> roles) {
    }

    /** One grant in a {@link #replaceAcl} payload. */
    public record AclEntry(ShAclPrincipalType principalType, String principalName, ShAclPermission permission) {
    }
}
