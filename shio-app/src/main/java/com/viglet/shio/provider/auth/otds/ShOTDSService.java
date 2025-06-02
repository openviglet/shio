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
package com.viglet.shio.provider.auth.otds;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.viglet.shio.provider.auth.otds.bean.ShOTDSAuthenticationResponseBean;
import com.viglet.shio.provider.auth.otds.bean.ShOTDSCredencialBean;
import com.viglet.shio.provider.auth.otds.bean.ShOTDSTicketRequestBean;
import com.viglet.shio.provider.auth.otds.bean.ShOTDSUserResponseBean;
import com.viglet.shio.provider.auth.otds.bean.ShOTDSValuesBean;
import com.viglet.shio.bean.provider.auth.ShAuthProviderInstanceBean;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.provider.auth.ShAuthProviderService;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
public class ShOTDSService {

	public static final String HOST = "HOST";
	public static final String PORT = "PORT";
	public static final String RESOURCE_ID = "RESOURCE_ID";
	public static final String SECRET_KEY = "SECRET_KEY";
	public static final String PARTITION = "PARTITION";
	public static final String DOMAIN = "DOMAIN";
	public static final String MEMBERSHIP_FILTER = "MEMBERSHIP_FILTER";
	public static final String USERNAME = "USERNAME";
	public static final String USER_FILTER = "USER_FILTER";
	public static final String USER_SCOPE = "USER_SCOPE";
	public static final String USER_DN = "USER_DN";
	public static final String GROUP_FILTER = "GROUP_FILTER";
	public static final String GROUP_SCOPE = "GROUP_SCOPE";
	public static final String GROUP_DN = "GROUP_DN";
	public static final String OTDS_TICKET = "OTDSTicket";

	private final ShAuthProviderService shAuthProviderService;

	private ShOTDSProviderInstanceBean otdsInstance = null;

	private final HttpClient httpClient = HttpClientBuilder.create().build();

	private final ObjectMapper objectMapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private final ResponseHandler<String> responseHandler = new BasicResponseHandler();

	private static final Log logger = LogFactory.getLog(ShOTDSService.class);

	private String otdsTicket = null;

	@Autowired
	public ShOTDSService(ShAuthProviderService shAuthProviderService) {
		this.shAuthProviderService = shAuthProviderService;
	}

	public void init(String providerId) {
		otdsInstance = this.getInstance(providerId);
	}

	public boolean isAuthorizedUser(String username, String password) {	
		ShOTDSCredencialBean shOTDSCredencialBean = new ShOTDSCredencialBean();
		shOTDSCredencialBean.setUserName(username);
		shOTDSCredencialBean.setPassword(password);

		try {
			String jsonString = objectMapper.writeValueAsString(shOTDSCredencialBean);

			StringEntity entity = new StringEntity(jsonString);

			HttpPost httpPost = new HttpPost(String.format("http://%s:%d/otdsws/rest/authentication/credentials",
					otdsInstance.getHost(), otdsInstance.getPort()));
			httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
			httpPost.setEntity(entity);

			HttpResponse response = httpClient.execute(httpPost);
			String responseString = responseHandler.handleResponse(response);
			ShOTDSAuthenticationResponseBean authenticationResponse = objectMapper.readValue(responseString,
					ShOTDSAuthenticationResponseBean.class);

			if (response.getStatusLine().getStatusCode() == 200
					&& this.validateTicket(authenticationResponse.getTicket())) {

				otdsTicket = authenticationResponse.getTicket();
				return true;
			}
		} catch (HttpResponseException e) {
			logger.error("authenticate HttpResponseException.");
		} catch (UnsupportedOperationException e) {
			logger.error("authenticate UnsupportedOperationException: ", e);
		} catch (IOException e) {
			logger.error("authenticate IOException: ", e);
		}
		return false;
	}

	public boolean validateTicket(String ticket) {

		if (otdsInstance != null) {
			ShOTDSTicketRequestBean shOTDSTicketRequestBean = new ShOTDSTicketRequestBean();
			shOTDSTicketRequestBean.setSourceResourceId(otdsInstance.getResourceId());
			shOTDSTicketRequestBean.setSecureSecret(otdsInstance.getSecretKey());
			shOTDSTicketRequestBean.setTicket(ticket);

			try {
				String jsonString = objectMapper.writeValueAsString(shOTDSTicketRequestBean);

				StringEntity entity = new StringEntity(jsonString);

				HttpPost httpPost = new HttpPost(
						String.format("http://%s:%d/otdsws/rest/authentication/resource/validation",
								otdsInstance.getHost(), otdsInstance.getPort()));
				httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
				httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
				httpPost.setEntity(entity);

				HttpResponse response = httpClient.execute(httpPost);

				if (response.getStatusLine().getStatusCode() == 200) {
					return true;
				}
			} catch (HttpResponseException e) {
				logger.error("validateTicket HttpResponseException.");
			} catch (UnsupportedOperationException e) {
				logger.error("validateTicket UnsupportedOperationException: ", e);
			} catch (IOException e) {
				logger.error("validateTicket IOException: ", e);
			}
		}
		return false;
	}

	public ShOTDSUserResponseBean getUser(String userName) {

		ShOTDSUserResponseBean userResponse = new ShOTDSUserResponseBean();

		HttpGet httpGet = new HttpGet(String.format("http://%s:%d/otdsws/rest/users/%s", otdsInstance.getHost(),
				otdsInstance.getPort(), userName));
		httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
		httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
		httpGet.setHeader(OTDS_TICKET, otdsTicket);
		try {
			HttpResponse response = httpClient.execute(httpGet);

			String responseString = responseHandler.handleResponse(response);
			userResponse = objectMapper.readValue(responseString, ShOTDSUserResponseBean.class);

		} catch (HttpResponseException e) {
			logger.error("getUser HttpResponseException.");
		} catch (UnsupportedOperationException e) {
			logger.error("getUser UnsupportedOperationException: ", e);
		} catch (IOException e) {
			logger.error("getUser IOException: ", e);
		}
		return userResponse;
	}

	public ShUser getCurrentUser() {
		ShUser shUser = new ShUser();
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof AnonymousAuthenticationToken)) {
			String currentUserName = authentication.getName();

			shUser = this.getShUser(currentUserName);
		}
		return shUser;
	}

	public ShUser getShUser(String username) {

		ShUser shUser = new ShUser();

		ShOTDSUserResponseBean userResponse = this.getUser(username);

		if (userResponse != null) {
			shUser.setUsername(username);
			for (ShOTDSValuesBean value : userResponse.getValues()) {
				if (value.getName().equals("givenName")) {
					shUser.setFirstName((String) value.getValues()[0]);

				}
				if (value.getName().equals("sn")) {
					shUser.setLastName((String) value.getValues()[0]);

				}
			}
		}
		return shUser;
	}

	public ShOTDSProviderInstanceBean getInstance(String providerId) {
		ShAuthProviderInstanceBean shAuthProviderInstanceBean = shAuthProviderService
				.getShAuthProviderInstanceBean(providerId);

		ShOTDSProviderInstanceBean instance = new ShOTDSProviderInstanceBean();
		instance.setName(shAuthProviderInstanceBean.getName());
		instance.setDescription(shAuthProviderInstanceBean.getDescription());
		instance.setEnabled(shAuthProviderInstanceBean.getEnabled());
		instance.setPosition(shAuthProviderInstanceBean.getPosition());
		instance.setVendor(shAuthProviderInstanceBean.getVendor());

		Map<String, String> properties = shAuthProviderInstanceBean.getProperties();

		instance.setHost(properties.get(ShOTDSService.HOST));
		instance.setPort(Integer.parseInt(properties.get(ShOTDSService.PORT)));

		instance.setResourceId(properties.get(ShOTDSService.RESOURCE_ID));
		instance.setSecretKey(properties.get(ShOTDSService.SECRET_KEY));

		instance.setPartition(properties.get(ShOTDSService.PARTITION));
		instance.setDomain(properties.get(ShOTDSService.DOMAIN));

		instance.setMembershipFilter(properties.get(ShOTDSService.MEMBERSHIP_FILTER));
		instance.setUsername(properties.get(ShOTDSService.USERNAME));

		instance.setUserDN(properties.get(ShOTDSService.USER_DN));
		instance.setUserFilter(properties.get(ShOTDSService.USER_FILTER));
		instance.setUserScope(properties.get(ShOTDSService.USER_SCOPE));

		instance.setGroupDN(properties.get(ShOTDSService.GROUP_DN));
		instance.setGroupFilter(properties.get(ShOTDSService.GROUP_FILTER));
		instance.setGroupScope(properties.get(ShOTDSService.GROUP_SCOPE));

		return instance;

	}
}
