package com.viglet.shio.spring.security;

import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShRole;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShRoleRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Centralizes authority resolution for all authentication flows.
 * Resolves from user → groups → roles.
 *
 * @author Alexandre Oliveira
 */
@Component
public class ShAuthorityResolver {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final ShUserRepository shUserRepository;
    private final ShGroupRepository shGroupRepository;
    private final ShRoleRepository shRoleRepository;

    public ShAuthorityResolver(ShUserRepository shUserRepository,
                               ShGroupRepository shGroupRepository,
                               ShRoleRepository shRoleRepository) {
        this.shUserRepository = shUserRepository;
        this.shGroupRepository = shGroupRepository;
        this.shRoleRepository = shRoleRepository;
    }

    public void resolve(String username, Set<GrantedAuthority> authorities) {
        if (username != null) {
            resolveFromDatabase(username, authorities);
        }
    }

    private void resolveFromDatabase(String username, Set<GrantedAuthority> authorities) {
        ShUser shUser = shUserRepository.findByUsername(username);
        if (shUser != null) {
            List<ShUser> users = new ArrayList<>();
            users.add(shUser);
            Set<ShGroup> groups = shGroupRepository.findByShUsersIn(users);
            Set<ShRole> roles = shRoleRepository.findByShGroupsIn(groups);
            for (ShRole role : roles) {
                authorities.add(new SimpleGrantedAuthority(role.getName()));
            }
            for (ShGroup group : groups) {
                if ("Administrator".equals(group.getName())) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
                }
            }
        }
    }
}
