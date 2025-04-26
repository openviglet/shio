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
package com.viglet.shio.website.nashorn;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.website.cache.component.ShCacheJavascript;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShNashornEngineProcess {
	private static final Log logger = LogFactory.getLog(ShNashornEngineProcess.class);
	private static final ThreadLocal<HashMap<String, ScriptContext>> scriptContexts = new ThreadLocal<>();
	@Autowired
	private ScriptEngine scriptEngine;
	@Autowired
	private ShCacheJavascript shCacheJavascript;

	public void cleanup() {
		scriptContexts.remove();
	}

	public Object render(String labelForError, String javascript, String html, HttpServletRequest request,
			Map<String, Object> shContent) {
		if (logger.isDebugEnabled()) {
			logger.debug("javascript: " + javascript);
			logger.debug("html: " + html);
			logger.debug("shContent: " + shContent);
		}
		try {
			SimpleScriptContext ssc = new SimpleScriptContext();
			ssc.setBindings(scriptEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
			Bindings b = scriptEngine.getBindings(ScriptContext.GLOBAL_SCOPE);
			if (b != null) {
				for (Map.Entry<String, Object> e : b.entrySet()) {
					ssc.setAttribute(e.getKey(), e.getValue(), ScriptContext.ENGINE_SCOPE);
				}
			} else {
				if (logger.isDebugEnabled())
					logger.debug("Bindings is null");

			}
			ScriptContext sc = shObjectLib(ssc);
			sc.setAttribute("shContent", shContent, ScriptContext.ENGINE_SCOPE);
			sc.setAttribute("html", html, ScriptContext.ENGINE_SCOPE);
			sc.setAttribute("request", request, ScriptContext.ENGINE_SCOPE);

			Object render = scriptEngine.eval(javascript, sc);
			if (logger.isDebugEnabled())
				logger.debug("render: " + render);
			return render;

		} catch (ScriptException err) {
			regionError(labelForError, javascript, err);
		}
		return null;
	}

	private ScriptContext shObjectLib(SimpleScriptContext ssc) {
		StringBuilder shObjectJS;
		ScriptContext sc;
		if (scriptContexts.get() == null) {
			logger.debug("Creating TL");
			scriptContexts.set(new HashMap<>());
		}
		if ((sc = scriptContexts.get().get("shScript")) != null) {
			logger.debug("Reusing shScript");
		} else {
			logger.debug("Creating shScript");

			try {
				shObjectJS = shCacheJavascript.shObjectJSFactory();
				scriptEngine.eval(shObjectJS.toString(), ssc);
			} catch (ScriptException e) {
				logger.error(e);
			}
			HashMap<String, ScriptContext> elementMap = new HashMap<>();
			sc = ssc;
			elementMap.put("shScript", ssc);
			scriptContexts.set(elementMap);

		}
		return sc;
	}

	public void regionError(String regionAttr, String javascript, Throwable err) {
		if (err instanceof ScriptException exc) {
			String scriptStack = ExceptionUtils.getStackTrace(exc);
			int columnNumber = exc.getColumnNumber();
			int lineNumber = exc.getLineNumber();
			String fileName = exc.getFileName();
			String message = exc.getMessage();
			String[] javascriptLines = javascript.split("\\n");
			StringBuilder errorCode = new StringBuilder();
			int minlines = 0;
			if (lineNumber - 5 > minlines) {
				minlines = lineNumber - 5;
			}
			int maxlines = javascriptLines.length;

			if (lineNumber + 5 < maxlines)
				maxlines = lineNumber + 5;
			for (int x = minlines; x <= maxlines; x++) {
				errorCode.append(javascriptLines[x] + "\n");
				if (x == lineNumber - 1) {
					String errorPos = IntStream.range(0, columnNumber).mapToObj(i -> "-")
							.collect(Collectors.joining(StringUtils.EMPTY)) + "^";
					errorCode.append(errorPos + "\n");
				}

			}
			logger.error(String.format("Javascript Code of %s:%n %s", regionAttr, errorCode));
			logger.error(String.format("ScriptError: %s '%s' at: <%s>%d:%d%n%s", regionAttr, message, fileName,
					lineNumber, columnNumber, scriptStack));
		} else {
			logger.error((ExceptionUtils.getStackTrace(err)));
		}
	}
}
