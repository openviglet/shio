/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.onstartup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.onstartup.provider.auth.ShAuthProviderInstanceOnStartup;
import com.viglet.shio.onstartup.provider.auth.ShAuthProviderVendorOnStartup;
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

	public ShOnStartup(ShConfigProperties shConfigProperties,
					   ShConfigVarRepository shConfigVarRepository,
					   ShLocaleOnStartup shLocaleOnStartup,
					   ShWidgetOnStartup shWidgetOnStartup,
					   ShConfigVarOnStartup shConfigVarOnStartup,
					   ShGroupOnStartup shGroupOnStartup,
					   ShUserOnStartup shUserOnStartup,
					   ShAuthProviderVendorOnStartup shAuthProviderVendorOnStartup,
					   ShAuthProviderInstanceOnStartup shAuthProviderInstanceOnStartup) {
		this.shConfigProperties = shConfigProperties;
		this.shConfigVarRepository = shConfigVarRepository;
		this.shLocaleOnStartup = shLocaleOnStartup;
		this.shWidgetOnStartup = shWidgetOnStartup;
		this.shConfigVarOnStartup = shConfigVarOnStartup;
		this.shGroupOnStartup = shGroupOnStartup;
		this.shUserOnStartup = shUserOnStartup;
		this.shAuthProviderVendorOnStartup = shAuthProviderVendorOnStartup;
		this.shAuthProviderInstanceOnStartup = shAuthProviderInstanceOnStartup;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arg0) throws Exception {

		if (!shConfigVarRepository.existsByPathAndName(shConfigProperties.getSystem(),
				ShConfigVarOnStartup.FIRST_TIME_NAME)) {

			log.info("First Time Configuration ...");

			shLocaleOnStartup.createDefaultRows();
			shWidgetOnStartup.createDefaultRows();
			shGroupOnStartup.createDefaultRows();
			shUserOnStartup.createDefaultRows();
			shAuthProviderVendorOnStartup.createDefaultRows();
			shAuthProviderInstanceOnStartup.createDefaultRows();
			shConfigVarOnStartup.createDefaultRows();

			log.info("Configuration finished.");
		}

	}

}
