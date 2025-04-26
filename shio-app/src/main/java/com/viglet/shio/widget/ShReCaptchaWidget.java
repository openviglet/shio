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
package com.viglet.shio.widget;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

import com.google.gson.Gson;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;

/**
 * reCAPTCHA Widget.
 *
 * @author Alexandre Oliveira
 * @since 0.3.0
 */
@Component
public class ShReCaptchaWidget extends ShDefaultWidget {

	private static final Log logger = LogFactory.getLog(ShReCaptchaWidget.class);

	@Override
	public void setTemplate() {
		this.template = "widget/recaptcha/recaptcha-widget";

	}

	@Override
	public String render(ShPostTypeAttr shPostTypeAttr, ShObjectImpl shObject) {
		String widgetSettings = shPostTypeAttr.getWidgetSettings();
		JSONObject settings = new JSONObject(widgetSettings);
		String siteKey = settings.getString("siteKey");
		final Context ctx = new Context();
		ctx.setVariable("shPostTypeAttr", shPostTypeAttr);
		ctx.setVariable("siteKey", siteKey);
		return templateEngine.process(this.template, ctx);
	}

	@Override
	public boolean validateForm(HttpServletRequest request, ShPostTypeAttr shPostTypeAttr) {
		String recap = request.getParameter("g-recaptcha-response");
		String widgetSettings = shPostTypeAttr.getWidgetSettings();
		JSONObject settings = new JSONObject(widgetSettings);
		String secretKey = settings.getString("secretKey");

		try {
			String urlGoogle = "https://www.google.com/recaptcha/api/siteverify?secret=%s&response=%s&remoteip=%s";

			// Send get request to Google reCaptcha server with secret key
			String urlFormatada = String.format(urlGoogle, secretKey, recap,
					(request.getRemoteAddr() != null ? request.getRemoteAddr() : "0.0.0.0"));
			URL url = new URL(urlFormatada);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			String outputString = StringUtils.EMPTY;
			try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
				outputString = reader.lines().collect(Collectors.joining(StringUtils.LF));
			}
			// Convert response into Object
			CaptchaResponse capRes = new Gson().fromJson(outputString, CaptchaResponse.class);
			return capRes.isSuccess();

		} catch (Exception e) {
			logger.error("validateFormException", e);
			return false;
		}
	}

	private class CaptchaResponse {
		private boolean success;
		private String[] errorCodes;

		@SuppressWarnings("unused")
		public boolean isSuccess() {
			return success;
		}

		@SuppressWarnings("unused")
		public void setSuccess(boolean success) {
			this.success = success;
		}

		@SuppressWarnings("unused")
		public String[] getErrorCodes() {
			return errorCodes;
		}

		@SuppressWarnings("unused")
		public void setErrorCodes(String[] errorCodes) {
			this.errorCodes = errorCodes;
		}

	}
}
