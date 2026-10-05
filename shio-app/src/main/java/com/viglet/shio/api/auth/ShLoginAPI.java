package com.viglet.shio.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.bean.ShCurrentUser;
import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShRole;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShRoleRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/login")
@Tag(name = "Login", description = "Session-based authentication")
public class ShLoginAPI {

    private static final String ADMINISTRATOR = "Administrator";

    private final AuthenticationManager authenticationManager;
    private final ShUserRepository shUserRepository;
    private final ShGroupRepository shGroupRepository;
    private final ShRoleRepository shRoleRepository;
    private final ShSignInRecorder shSignInRecorder;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public ShLoginAPI(AuthenticationManager authenticationManager,
            ShUserRepository shUserRepository,
            ShGroupRepository shGroupRepository,
            ShRoleRepository shRoleRepository,
            ShSignInRecorder shSignInRecorder) {
        this.shSignInRecorder = shSignInRecorder;
        this.authenticationManager = authenticationManager;
        this.shUserRepository = shUserRepository;
        this.shGroupRepository = shGroupRepository;
        this.shRoleRepository = shRoleRepository;
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping
    public ResponseEntity<ShCurrentUser> login(@RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // SH1238 — the console's sign-in, which is what the users list's Last sign-in reports.
        shSignInRecorder.record(request.username(), java.time.Instant.now());

        ShUser shUser = shUserRepository.findByUsername(request.username());
        boolean isAdmin = false;
        List<ShUser> users = new ArrayList<>();
        users.add(shUser);
        Set<ShGroup> groups = shGroupRepository.findByShUsersIn(users);
        if (groups != null) {
            for (ShGroup shGroup : groups) {
                if (shGroup.getName().equals(ADMINISTRATOR)) {
                    isAdmin = true;
                    break;
                }
            }
        }

        ShCurrentUser currentUser = new ShCurrentUser();
        currentUser.setUsername(shUser.getUsername());
        currentUser.setFirstName(shUser.getFirstName());
        currentUser.setLastName(shUser.getLastName());
        currentUser.setEmail(shUser.getEmail());
        currentUser.setAdmin(isAdmin);
        currentUser.setPrivileges(resolvePrivileges(groups));

        return ResponseEntity.ok(currentUser);
    }

    private List<String> resolvePrivileges(Set<ShGroup> groups) {
        var privileges = new LinkedHashSet<String>();
        for (var group : groups) {
            for (ShRole role : shRoleRepository.findByShGroupsIn(Set.of(group))) {
                privileges.add(role.getName());
            }
        }
        return new ArrayList<>(privileges);
    }
}
