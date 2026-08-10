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
import java.util.LinkedHashSet;
import java.util.List;
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
        return hasPermission(objectId, ShAclPermission.READ);
    }

    @Transactional(readOnly = true)
    public boolean canWrite(String objectId) {
        return hasPermission(objectId, ShAclPermission.WRITE);
    }

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

    private boolean hasPermission(String objectId, ShAclPermission required) {
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
        for (String level : aclChain(objectId)) {
            List<ShContentAcl> acls = shContentAclRepository.findByObjectId(level);
            if (!acls.isEmpty()) {
                Principals who = currentPrincipals(auth.getName());
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
    private List<String> aclChain(String objectId) {
        List<String> chain = new ArrayList<>();
        chain.add(objectId);
        // `visited` is separate from `chain` on purpose, and the first draft of SH225 conflated
        // them: for a folder, `startFolderId` returns the object's own id, which `chain` already
        // holds — so a single contains() guard doing double duty as the cycle brake stopped the
        // walk before its first hop, and every folder silently stopped inheriting its parent's
        // ACL. Deduplication and termination are two questions; `visited.add` answers only the
        // second, and a folder cycle is the one loop an authorization check cannot afford.
        Set<String> visited = new LinkedHashSet<>();
        for (String folderId = startFolderId(objectId);
                folderId != null && visited.add(folderId);
                folderId = shFolderRepository.findParentIdOf(folderId).orElse(null)) {
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

    /** The current user's resolved principal identifiers. */
    private record Principals(String username, Set<String> groups, Set<String> roles) {
    }

    /** One grant in a {@link #replaceAcl} payload. */
    public record AclEntry(ShAclPrincipalType principalType, String principalName, ShAclPermission permission) {
    }
}
