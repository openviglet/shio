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
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShUserRepository shUserRepository;
    private final ShGroupRepository shGroupRepository;
    private final ShRoleRepository shRoleRepository;

    public ShContentAclService(ShContentAclRepository shContentAclRepository,
                               ShObjectRepository shObjectRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShUserRepository shUserRepository,
                               ShGroupRepository shGroupRepository,
                               ShRoleRepository shRoleRepository) {
        this.shContentAclRepository = shContentAclRepository;
        this.shObjectRepository = shObjectRepository;
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
    public void requireRead(String objectId) {
        if (!canRead(objectId)) {
            throw new AccessDeniedException("No read permission on " + objectId);
        }
    }

    /** Throw {@link AccessDeniedException} (→ 403) unless the current user may write it. */
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
    private List<String> aclChain(String objectId) {
        List<String> chain = new ArrayList<>();
        chain.add(objectId);
        for (ShFolder f = startFolder(objectId); f != null; f = f.getParentFolder()) {
            if (!chain.contains(f.getId())) {
                chain.add(f.getId());
            }
        }
        return chain;
    }

    /** The folder whose ancestor chain we walk: the object itself if a folder, else a post's folder. */
    private ShFolder startFolder(String objectId) {
        ShObject object = shObjectRepository.findById(objectId).orElse(null);
        if (object instanceof ShFolder folder) {
            return folder;
        }
        if (object != null) {
            return null; // a site or post-type — no folder chain
        }
        return shPostUnifiedRepository.findById(objectId).stream()
                .findFirst()
                .map(ShPostUnified::getShFolder)
                .orElse(null);
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
