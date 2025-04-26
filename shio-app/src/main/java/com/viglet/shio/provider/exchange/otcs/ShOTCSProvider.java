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
package com.viglet.shio.provider.exchange.otcs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.viglet.shio.provider.exchange.ShExchangeProvider;
import com.viglet.shio.provider.exchange.ShExchangeProviderBreadcrumbItem;
import com.viglet.shio.provider.exchange.ShExchangeProviderFolder;
import com.viglet.shio.provider.exchange.ShExchangeProviderPost;
import com.viglet.shio.provider.exchange.otcs.bean.folder.ShOTCSFolderBean;
import com.viglet.shio.provider.exchange.otcs.bean.object.ShOTCSObjectBean;
import com.viglet.shio.provider.exchange.otcs.bean.ticket.ShOTCSTicketBean;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;

/**
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 */
public class ShOTCSProvider implements ShExchangeProvider {

	private static final Log logger = LogFactory.getLog(ShOTCSProvider.class);
	private static final String OTCS_TICKET = "OTCSTicket";
	private static final String ROOT_FOLDER_ID = "2000";
	private static final String PROVIDER_NAME = "OTCS";
	private static final String URL_VAR = "URL";
	private static final String USERNAME_VAR = "USERNAME";
	private static final String PASSWORD_VAR = "PASSWORD";

	private ObjectMapper objectMapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	private HttpClient httpClient = HttpClientBuilder.create().build();

	private ResponseHandler<String> responseHandler = new BasicResponseHandler();

	private String baseURL = null;

	private String username = null;

	private String password = null;

	public void init(Map<String, String> variables) {
		this.baseURL = variables.get(URL_VAR);
		this.username = variables.get(USERNAME_VAR);
		this.password = variables.get(PASSWORD_VAR);
	}

	public ShExchangeProviderFolder getRootFolder() {

		return this.getFolder(ROOT_FOLDER_ID);
	}

	public ShExchangeProviderFolder getFolder(String id) {
		ShOTCSTicketBean shOTCSTicketBean = this.otcsAuth();
		ShExchangeProviderFolder shExchangeProviderFolder = new ShExchangeProviderFolder();
		if (shOTCSTicketBean != null) {
			ShOTCSFolderBean shOTCSFolderBean = null;

			try {
				HttpGet httpGet = new HttpGet(String.format("%s/api/v2/nodes/%s/nodes", this.baseURL, id));
				httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
				httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
				httpGet.setHeader(OTCS_TICKET, shOTCSTicketBean.getTicket());
				HttpResponse response = httpClient.execute(httpGet);
				shOTCSFolderBean = objectMapper.readValue(responseHandler.handleResponse(response),
						ShOTCSFolderBean.class);

			} catch (UnsupportedOperationException | IOException e) {
				logger.error(e);
			}

			ShExchangeProviderPost shExchangeProviderPost = this.getObject(id, true);

			shExchangeProviderFolder.setId(id);
			shExchangeProviderFolder.setName(shExchangeProviderPost.getTitle());
			shExchangeProviderFolder.setBreadcrumb(this.getOTCSBreadcrumb(id));
			shExchangeProviderFolder.setProviderName(PROVIDER_NAME);
			shExchangeProviderFolder.setParentId(shExchangeProviderPost.getParentId());

			if (shOTCSFolderBean != null) {
				shOTCSFolderBean.getResults().forEach(results -> {
					if (results.getData().getProperties().getTypeName().equals("Folder")) {

						String resultId = Integer.toString(results.getData().getProperties().getId());

						String resultName = results.getData().getProperties().getName();

						Date resultDate = results.getData().getProperties().getCreateDate();

						ShExchangeProviderFolder shExchangeProviderFolderChild = new ShExchangeProviderFolder();
						shExchangeProviderFolderChild.setId(resultId);
						shExchangeProviderFolderChild.setName(resultName);
						shExchangeProviderFolderChild.setDate(resultDate);

						shExchangeProviderFolder.getFolders().add(shExchangeProviderFolderChild);
					} else {

						String postId = Integer.toString(results.getData().getProperties().getId());

						String postTitle = results.getData().getProperties().getName();

						Date postDate = results.getData().getProperties().getCreateDate();

						String postType = results.getData().getProperties().getTypeName();

						ShExchangeProviderPost shExchangeProviderPostChild = new ShExchangeProviderPost();

						shExchangeProviderPostChild.setId(postId);
						shExchangeProviderPostChild.setTitle(postTitle);
						shExchangeProviderPostChild.setDate(postDate);
						shExchangeProviderPostChild.setType(postType);

						shExchangeProviderFolder.getPosts().add(shExchangeProviderPostChild);

					}
				});
			}
		}
		return shExchangeProviderFolder;
	}

	public ShExchangeProviderPost getObject(String id, boolean isFolder) {
		ShOTCSTicketBean sOTCSTicketBean = this.otcsAuth();
		ShExchangeProviderPost shExchangeProviderPost = new ShExchangeProviderPost();
		if (sOTCSTicketBean != null) {
			ShOTCSObjectBean shOTCSObjetBean = null;
			try {
				HttpGet httpGet = new HttpGet(String.format("%s/api/v2/nodes/%s", this.baseURL, id));
				httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
				httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
				httpGet.setHeader(OTCS_TICKET, sOTCSTicketBean.getTicket());
				HttpResponse response = httpClient.execute(httpGet);
				shOTCSObjetBean = objectMapper.readValue(responseHandler.handleResponse(response),
						ShOTCSObjectBean.class);
			} catch (UnsupportedOperationException | IOException e) {
				logger.error(e);
			}

			if (shOTCSObjetBean != null) {
				shExchangeProviderPost.setId(id);
				shExchangeProviderPost.setTitle(shOTCSObjetBean.getResults().getData().getProperties().getName());
				shExchangeProviderPost.setParentId(
						Integer.toString(shOTCSObjetBean.getResults().getData().getProperties().getParentId()));
			}
		}
		return shExchangeProviderPost;
	}

	private ShOTCSTicketBean otcsAuth() {
		List<NameValuePair> form = new ArrayList<>();
		form.add(new BasicNameValuePair("username", this.username));
		form.add(new BasicNameValuePair("password", this.password));
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);

		HttpPost httpPost = new HttpPost(String.format("%s/api/v1/auth", this.baseURL));
		httpPost.setEntity(entity);
		httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());

		ShOTCSTicketBean sOTCSTicketBean = null;

		try {
			HttpResponse response = httpClient.execute(httpPost);

			sOTCSTicketBean = objectMapper.readValue(responseHandler.handleResponse(response), ShOTCSTicketBean.class);

		} catch (UnsupportedOperationException | IOException e) {
			logger.error(e);
		}
		return sOTCSTicketBean;
	}

	public InputStream getDownload(String id) {
		ShOTCSTicketBean shOTCSTicketBean = this.otcsAuth();
		InputStream inputStream = null;
		if (shOTCSTicketBean != null) {
			try {
				HttpGet httpGet = new HttpGet(String.format("%s/api/v2/nodes/%s/content", this.baseURL, id));
				httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
				httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
				httpGet.setHeader(OTCS_TICKET, shOTCSTicketBean.getTicket());
				HttpResponse response = httpClient.execute(httpGet);
				inputStream = response.getEntity().getContent();
			} catch (UnsupportedOperationException | IOException e) {
				logger.error(e);
			}
		}

		return inputStream;

	}

	private List<ShExchangeProviderBreadcrumbItem> getOTCSBreadcrumb(String id) {
		ArrayList<ShExchangeProviderBreadcrumbItem> breadcrumb = new ArrayList<>();

		this.getOTCSParentBreadcrumbItem(id, breadcrumb);

		return breadcrumb;
	}

	private void getOTCSParentBreadcrumbItem(String id, ArrayList<ShExchangeProviderBreadcrumbItem> breadcrumb) {
		if (!StringUtils.isBlank(id) && isNumber(id) && Integer.parseInt(id) > 0) {
			ShExchangeProviderPost shExchangeProviderPost = this.getObject(id, true);

			ShExchangeProviderBreadcrumbItem shExchangeProviderBreadcrumbItem = new ShExchangeProviderBreadcrumbItem();
			shExchangeProviderBreadcrumbItem.setId(shExchangeProviderPost.getId());
			shExchangeProviderBreadcrumbItem.setTitle(shExchangeProviderPost.getTitle());

			this.getOTCSParentBreadcrumbItem(shExchangeProviderPost.getParentId(), breadcrumb);
			breadcrumb.add(shExchangeProviderBreadcrumbItem);
		}
	}
	
	private boolean isNumber(String s) {
	    boolean isNumber = true;
	    for (char c : s.toCharArray()) {
	        isNumber = isNumber && Character.isDigit(c);
	    }
	    return isNumber;
	}
}
