/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.onstartup;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.auth.ShAdminPasswordService;
import com.viglet.shio.onstartup.provider.auth.ShAuthProviderInstanceOnStartup;
import com.viglet.shio.onstartup.provider.auth.ShAuthProviderVendorOnStartup;
import com.viglet.shio.onstartup.provider.exchange.ShExchangeProviderVendorOnStartup;
import com.viglet.shio.onstartup.system.ShConfigVarOnStartup;
import com.viglet.shio.onstartup.system.ShLocaleOnStartup;
import com.viglet.shio.onstartup.user.ShGroupOnStartup;
import com.viglet.shio.onstartup.user.ShUserOnStartup;
import com.viglet.shio.onstartup.widget.ShWidgetOnStartup;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;
import com.viglet.shio.property.ShConfigProperties;

@Component
@Slf4j
public class ShOnStartup implements ApplicationRunner {
	private final ShConfigProperties shConfigProperties;
	private final ShConfigVarRepository shConfigVarRepository;
	private final ShLocaleOnStartup shLocaleOnStartup;
	private final ShWidgetOnStartup shWidgetOnStartup;
	private final ShConfigVarOnStartup shConfigVarOnStartup;
	private final ShGroupOnStartup shGroupOnStartup;
	private final ShUserOnStartup shUserOnStartup;
	private final ShAuthProviderVendorOnStartup shAuthProviderVendorOnStartup;
	private final ShAuthProviderInstanceOnStartup shAuthProviderInstanceOnStartup;
	private final ShExchangeProviderVendorOnStartup shExchangeProviderVendorOnStartup;
	private final ShAdminPasswordService shAdminPasswordService;

	public ShOnStartup(ShConfigProperties shConfigProperties,
					   ShConfigVarRepository shConfigVarRepository,
					   ShLocaleOnStartup shLocaleOnStartup,
					   ShWidgetOnStartup shWidgetOnStartup,
					   ShConfigVarOnStartup shConfigVarOnStartup,
					   ShGroupOnStartup shGroupOnStartup,
					   ShUserOnStartup shUserOnStartup,
					   ShAuthProviderVendorOnStartup shAuthProviderVendorOnStartup,
					   ShAuthProviderInstanceOnStartup shAuthProviderInstanceOnStartup,
					   ShExchangeProviderVendorOnStartup shExchangeProviderVendorOnStartup,
					   ShAdminPasswordService shAdminPasswordService) {
		this.shAdminPasswordService = shAdminPasswordService;
		this.shConfigProperties = shConfigProperties;
		this.shConfigVarRepository = shConfigVarRepository;
		this.shLocaleOnStartup = shLocaleOnStartup;
		this.shWidgetOnStartup = shWidgetOnStartup;
		this.shConfigVarOnStartup = shConfigVarOnStartup;
		this.shGroupOnStartup = shGroupOnStartup;
		this.shUserOnStartup = shUserOnStartup;
		this.shAuthProviderVendorOnStartup = shAuthProviderVendorOnStartup;
		this.shAuthProviderInstanceOnStartup = shAuthProviderInstanceOnStartup;
		this.shExchangeProviderVendorOnStartup = shExchangeProviderVendorOnStartup;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arg0) throws Exception {

		// SH325 — the widget catalogue is seeded on *every* startup, outside the first-time gate.
		//
		// It is the one seed that must be: the catalogue is closed and code-owned (a widget is a
		// Java class plus a React component, so a curator never adds one), and a release that adds
		// a widget has no other way to reach an instance that already exists. SH252's
		// `Ace Editor - CSS` is what proved it — inside this gate, POST /api/v2/render/post-types
		// answered 500 on every install older than the feature, and SH289's `reconcile`, the verb
		// written to repair exactly that, failed identically.
		//
		// Safe because `createDefaultRows` is per-widget find-or-create and touches nothing that
		// exists. The other seeds stay inside the gate deliberately: a locale, a group, a user and
		// a config var are all things an operator legitimately edits or deletes, so re-creating
		// them on boot would undo a decision.
		List<String> widgets = shWidgetOnStartup.createDefaultRows();
		if (!widgets.isEmpty()) {
			log.info("Widget catalog: created {}", widgets);
		}

		// SH753 — every resolver assumes the catalogue is unique by name and nothing enforces it.
		// The readers are all tolerant now (findFirstByName), which fixed the crash and would have
		// made the duplication invisible: picking the first row never mentions there were five. One
		// grouping over a closed sixteen-row catalogue, once per boot, is what keeps it visible.
		Map<String, Long> duplicated = shWidgetOnStartup.duplicateNames();
		if (!duplicated.isEmpty()) {
			log.warn("SH753: the widget catalog holds duplicate names {} — every resolver picks the "
					+ "first row, so a field may be backed by a widget nobody chose. De-duplicating "
					+ "means repointing the post-type attributes on the losing rows, which is a "
					+ "migration rather than something this seed may do.", duplicated);
		}

		if (!shConfigVarRepository.existsByPathAndName(shConfigProperties.getSystem(),
				ShConfigVarOnStartup.FIRST_TIME_NAME)) {

			log.info("First Time Configuration ...");

			shLocaleOnStartup.createDefaultRows();
			shGroupOnStartup.createDefaultRows();
			shUserOnStartup.createDefaultRows();
			shAuthProviderVendorOnStartup.createDefaultRows();
			shAuthProviderInstanceOnStartup.createDefaultRows();
			shExchangeProviderVendorOnStartup.createDefaultRows();
			shConfigVarOnStartup.createDefaultRows();

			log.info("Configuration finished.");
		}

		// SH706 — headless admin bootstrap (SHIO_ADMIN_PASSWORD), OUTSIDE the first-time gate on
		// purpose. Inside it, this would help exactly the installs that do not need help: an
		// instance seeded before the variable was supplied, or one whose setup screen was never
		// completed, is the case an automated deploy actually meets. It is a no-op once a password
		// exists, so a restart with the variable still in the compose file never overwrites one.
		shAdminPasswordService.bootstrapFromConfiguration();

	}

}
