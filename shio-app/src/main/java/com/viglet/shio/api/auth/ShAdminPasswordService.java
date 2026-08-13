/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.property.ShAppProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * SH706 — the single owner of the local {@code admin} password.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>{@code ShUserOnStartup} seeds {@code admin} with a <b>null</b> password and the first-access
 * screen behind {@code POST /api/v2/setup} was the only writer. A fresh container therefore
 * started in a state no script could leave: every surface answers 401 and the remedy is a person
 * opening a browser. That is law P1 — a capability whose only interface is the console — and it is
 * the one instance of it that gates every other capability, because an agent cannot reach its
 * first correct call at all.</p>
 *
 * <h2>Single owner, and that is the point rather than tidiness</h2>
 *
 * <p>The setup endpoint and the headless bootstrap both write the same field, and the moment they
 * write it through two code paths they will eventually disagree about the minimum length, the
 * encoder, or the "only while unset" rule. This project has a name for that shape and a lint
 * pending for it (SH245, "one writer per verb"): four times the console's copy of a shared verb
 * was the careless one. So {@link ShSetupAPI} holds the HTTP contract and delegates the write.</p>
 *
 * <h2>The three properties that make it safe in a compose file</h2>
 *
 * <ol>
 *   <li><b>It never overwrites.</b> A password already configured — by the screen, or by an earlier
 *       boot — is kept, so a restart with the variable still present is a no-op. An operator who
 *       changed the password in the console does not get it reverted by their own deployment.</li>
 *   <li><b>It never fails startup.</b> Blank, too short, or already set: each logs and returns. A
 *       bootstrap that could halt boot would make a typo in an env var an outage.</li>
 *   <li><b>It runs outside the first-time gate</b> (see {@code ShOnStartup}), so an install seeded
 *       before the variable was supplied still becomes usable. Inside the gate it would help
 *       exactly the installs that do not need help.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Slf4j
@Service
public class ShAdminPasswordService {

    public static final String ADMIN = "admin";

    /** The same floor the first-access screen enforces, because they are one rule (SH706). */
    public static final int PASSWORD_MINIMUM_SIZE = 6;

    private final ShUserRepository shUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShAppProperties shAppProperties;

    public ShAdminPasswordService(ShUserRepository shUserRepository, PasswordEncoder passwordEncoder,
            ShAppProperties shAppProperties) {
        this.shUserRepository = shUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.shAppProperties = shAppProperties;
    }

    /**
     * {@code true} while the {@code admin} account exists with no usable password — which is to
     * say, while the first-access setup is still pending.
     *
     * <p>A missing {@code admin} row reads as <b>not</b> pending, matching what the endpoint has
     * always answered: an instance whose admin was deleted is a different problem, and reporting
     * it as "setup required" would offer a password write on a row that is not there.</p>
     */
    public boolean isSetupRequired() {
        ShUser admin = shUserRepository.findByUsername(ADMIN);
        return admin != null && !StringUtils.hasText(admin.getPassword());
    }

    /**
     * Encodes and stores the {@code admin} password.
     *
     * @param rawPassword the new password, trimmed before it is measured
     * @throws IllegalArgumentException when it is blank or shorter than
     *                                  {@value #PASSWORD_MINIMUM_SIZE} characters
     * @throws IllegalStateException    when there is no {@code admin} row to write to
     */
    public void setPassword(String rawPassword) {
        String password = rawPassword == null ? null : rawPassword.trim();
        if (!StringUtils.hasText(password) || password.length() < PASSWORD_MINIMUM_SIZE) {
            throw new IllegalArgumentException(
                    "The admin password must be at least " + PASSWORD_MINIMUM_SIZE + " characters.");
        }
        ShUser admin = shUserRepository.findByUsername(ADMIN);
        if (admin == null) {
            throw new IllegalStateException("There is no `admin` account on this instance to set a password on.");
        }
        admin.setPassword(passwordEncoder.encode(password));
        shUserRepository.saveAndFlush(admin);
    }

    /**
     * Applies {@code shio.admin.password} ({@code SHIO_ADMIN_PASSWORD}) so a container or CI deploy
     * starts with a usable admin login and the first-access screen is skipped.
     *
     * <p>Idempotent and boot-safe by construction: every reason not to write is a log line and a
     * return, never a throw. See the class note for why each of the three is a no-op rather than an
     * error.</p>
     */
    public void bootstrapFromConfiguration() {
        String configured = shAppProperties.getAdmin().getPassword();
        if (!StringUtils.hasText(configured)) {
            return;
        }
        if (!isSetupRequired()) {
            log.info("SHIO_ADMIN_PASSWORD ignored: the admin password is already configured. "
                    + "Change it in the console instead — a bootstrap that overwrote it would undo "
                    + "an operator's decision on every restart.");
            return;
        }
        if (configured.trim().length() < PASSWORD_MINIMUM_SIZE) {
            log.warn("SHIO_ADMIN_PASSWORD ignored: the admin password must be at least {} characters. "
                    + "Falling back to the first-access setup screen.", PASSWORD_MINIMUM_SIZE);
            return;
        }
        setPassword(configured);
        log.info("Admin password set from SHIO_ADMIN_PASSWORD — the first-access setup screen is skipped.");
    }
}
