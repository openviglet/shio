/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
package com.viglet.shio.website.nashorn;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jakarta.annotation.Resource;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Alexandre Oliveira
 */
@Configuration
public class ShNashornEngineConfiguration {
	private static final Log logger = LogFactory.getLog(ShNashornEngineConfiguration.class);

	private static final String NASHORN_CLASS = "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory";
	private static final String RHINO_CLASS = "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory";
	private static final String NASHORN_ENGINE = "nashorn";

	@Value("${shio.website.javascript.engine:nashorn}")
	private String defaultEngine;

	@Resource
	private ApplicationContext context;

	@Bean
	public ScriptEngine scriptEngine(ShNashornEngineBindings shBindings) {

		try {
			ScriptEngine engine = defaultEngine.equals(NASHORN_ENGINE) ? nashornEngine(shBindings) : rhinoEngine();

			Bindings bindings = engine.createBindings();

			bindings.put("shNavigationComponent", shBindings.getShNavigationComponent());
			bindings.put("shQueryComponent", shBindings.getShQueryComponent());
			bindings.put("shSearchComponent", shBindings.getShSearchComponent());
			bindings.put("shFormComponent", shBindings.getShFormComponent());
			bindings.put("shGetRelationComponent", shBindings.getShGetRelationComponent());
			bindings.put("shSitesFolderUtils", shBindings.getShSitesFolderUtils());
			bindings.put("shSitesObjectUtils", shBindings.getShSitesObjectUtils());
			bindings.put("shSitesPostUtils", shBindings.getShSitesPostUtils());

			engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);

			return engine;
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
				| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			logger.error("ShNashornEngineConfiguration Error:", e);
		}
		return null;
	}

	private ScriptEngine rhinoEngine() throws ClassNotFoundException, NoSuchMethodException, InstantiationException,
			IllegalAccessException, InvocationTargetException {
		ScriptEngine engine;
		Class<?> rhinoScriptEngineFactory = Class.forName(RHINO_CLASS);
		Method getScriptEngine = rhinoScriptEngineFactory.getDeclaredMethod("getScriptEngine");
		ScriptEngineFactory scriptEngineFactory = (ScriptEngineFactory) rhinoScriptEngineFactory
				.getDeclaredConstructor().newInstance();
		engine = (ScriptEngine) getScriptEngine.invoke(scriptEngineFactory);
		return engine;
	}

	private ScriptEngine nashornEngine(ShNashornEngineBindings shBindings) throws ClassNotFoundException,
			NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
		ScriptEngine engine;
		Class<?> nashornScriptEngineFactory = Class.forName(NASHORN_CLASS);
		Method getScriptEngine = nashornScriptEngineFactory.getDeclaredMethod("getScriptEngine", String[].class);
		ScriptEngineFactory scriptEngineFactory = (ScriptEngineFactory) nashornScriptEngineFactory
				.getDeclaredConstructor().newInstance();
		engine = (ScriptEngine) getScriptEngine.invoke(scriptEngineFactory,
				shBindings.getShWebsiteProperties().getNashornAsObject());
		return engine;
	}

}
