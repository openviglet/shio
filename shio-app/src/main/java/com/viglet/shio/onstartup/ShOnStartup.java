/*
 * Copyright (C) 2016-2020 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.onstartup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.viglet.shio.onstartup.post.type.ShPostTypeOnStartup;
import com.viglet.shio.onstartup.provider.auth.ShAuthProviderInstanceOnStartup;
import com.viglet.shio.onstartup.provider.auth.ShAuthProviderVendorOnStartup;
import com.viglet.shio.onstartup.provider.exchange.ShExchangeProviderInstanceOnStartup;
import com.viglet.shio.onstartup.provider.exchange.ShExchangeProviderVendorOnStartup;
import com.viglet.shio.onstartup.site.ShSiteOnStartup;
import com.viglet.shio.onstartup.system.ShConfigVarOnStartup;
import com.viglet.shio.onstartup.system.ShLocaleOnStartup;
import com.viglet.shio.onstartup.user.ShGroupOnStartup;
import com.viglet.shio.onstartup.user.ShUserOnStartup;
import com.viglet.shio.onstartup.widget.ShWidgetOnStartup;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;
import com.viglet.shio.property.ShConfigProperties;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShOnStartup implements ApplicationRunner {
	private static final Logger logger = LogManager.getLogger(ShOnStartup.class);
	@Autowired
	private ShConfigProperties shConfigProperties;
	@Autowired
	private ShConfigVarRepository shConfigVarRepository;
	@Autowired
	private ShLocaleOnStartup shLocaleOnStartup;
	@Autowired
	private ShWidgetOnStartup shWidgetOnStartup;
	@Autowired
	private ShPostTypeOnStartup shPostTypeOnStartup;
	@Autowired
	private ShConfigVarOnStartup shConfigVarOnStartup;
	@Autowired
	private ShSiteOnStartup shSiteOnStartup;
	@Autowired
	private ShGroupOnStartup shGroupOnStartup;
	@Autowired
	private ShUserOnStartup shUserOnStartup;
	@Autowired
	private ShAuthProviderVendorOnStartup shAuthProviderVendorOnStartup;
	@Autowired
	private ShAuthProviderInstanceOnStartup shAuthProviderInstanceOnStartup;
	@Autowired
	private ShExchangeProviderVendorOnStartup shExchangeProviderVendorOnStartup;
	@Autowired
	private ShExchangeProviderInstanceOnStartup shExchangeProviderInstanceOnStartup;

	@Override
	public void run(ApplicationArguments arg0) throws Exception {

		if (!shConfigVarRepository.existsByPathAndName(shConfigProperties.getSystem(),
				ShConfigVarOnStartup.FIRST_TIME_NAME)) {

			logger.info("First Time Configuration ...");

			shLocaleOnStartup.createDefaultRows();
			shWidgetOnStartup.createDefaultRows();
			shPostTypeOnStartup.createDefaultRows();
			shGroupOnStartup.createDefaultRows();
		//	shUserOnStartup.createDefaultRows();
			shAuthProviderVendorOnStartup.createDefaultRows();
			shAuthProviderInstanceOnStartup.createDefaultRows();
			shExchangeProviderVendorOnStartup.createDefaultRows();
			shExchangeProviderInstanceOnStartup.createDefaultRows();
			shConfigVarOnStartup.createDefaultRows();
		//	shSiteOnStartup.createDefaultRows();

			logger.info("Configuration finished.");
		}

	}

}
