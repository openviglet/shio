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
package com.viglet.shio.provider.exchange.otmm;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.http.MediaType;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.viglet.shio.provider.exchange.ShExchangeProvider;
import com.viglet.shio.provider.exchange.ShExchangeProviderBreadcrumbItem;
import com.viglet.shio.provider.exchange.ShExchangeProviderFolder;
import com.viglet.shio.provider.exchange.ShExchangeProviderPost;
import com.viglet.shio.provider.exchange.otmm.bean.assets.ShOTMMAssetDetailBean;
import com.viglet.shio.provider.exchange.otmm.bean.assets.ShOTMMAssetsBean;
import com.viglet.shio.provider.exchange.otmm.bean.folders.ShOTMMFolderBean;
import com.viglet.shio.provider.exchange.otmm.bean.folders.ShOTMMFolderDetailBean;
import com.viglet.shio.provider.exchange.otmm.bean.folders.ShOTMMFoldersBean;
import com.viglet.shio.provider.exchange.otmm.bean.sessions.ShOTMMSessionsBean;

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
public class ShOTMMProvider implements ShExchangeProvider {

	private static final Log logger = LogFactory.getLog(ShOTMMProvider.class);
	private static final String PROVIDER_NAME = "OTMM";
	private static final String ROOT_FOLDER_ID = "_root";
	private static final String ROOT_FOLDER_NAME = "OTMM Root Folder";
	private static final String URL_VAR = "URL";
	private static final String USERNAME_VAR = "USERNAME";
	private static final String PASSWORD_VAR = "PASSWORD";

	private ObjectMapper objectMapper = new ObjectMapper().rebuild()
			.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
			.build();

	private int timeout = 5;
	private RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout * 1000)
			.setConnectionRequestTimeout(timeout * 1000).setSocketTimeout(timeout * 1000).build();
	private CookieStore httpCookieStore = new BasicCookieStore();

	private HttpClient httpClient = null;

	private ResponseHandler<String> responseHandler = new BasicResponseHandler();

	private String baseURL = null;

	private String username = null;

	private String password = null;

	public void init(Map<String, String> variables) {
		this.baseURL = variables.get(URL_VAR);
		this.username = variables.get(USERNAME_VAR);
		this.password = variables.get(PASSWORD_VAR);
		this.otmmAuth();
	}

	public ShExchangeProviderFolder getRootFolder() {

		ShOTMMFoldersBean shOTMMFoldersBean = null;
		try {
			HttpGet httpGet = new HttpGet(String.format("%s/otmmapi/v5/folders/rootfolders", this.baseURL));
			httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
			HttpResponse response = httpClient.execute(httpGet);

			shOTMMFoldersBean = objectMapper.readValue(responseHandler.handleResponse(response),
					ShOTMMFoldersBean.class);
		} catch (UnsupportedOperationException e) {
			logger.error("getRootFolder UnsupportedOperationException: ", e);
		} catch (IOException e) {
			logger.error("getRootFolder IOException: ", e);
		}

		ShExchangeProviderFolder shExchangeProviderFolder = new ShExchangeProviderFolder();

		shExchangeProviderFolder.setId(ROOT_FOLDER_ID);
		shExchangeProviderFolder.setName(ROOT_FOLDER_NAME);
		shExchangeProviderFolder.setBreadcrumb(this.getOTMMBreadcrumb(ROOT_FOLDER_ID));
		shExchangeProviderFolder.setProviderName(PROVIDER_NAME);
		shExchangeProviderFolder.setParentId(null);

		this.folderChildren(shOTMMFoldersBean, shExchangeProviderFolder);
		return shExchangeProviderFolder;
	}

	private void folderChildren(ShOTMMFoldersBean shOTMMFoldersBean,
			ShExchangeProviderFolder shExchangeProviderFolder) {
		if (shOTMMFoldersBean != null && shOTMMFoldersBean.getFoldersResource() != null
				&& shOTMMFoldersBean.getFoldersResource().getFolderList() != null) {

			shOTMMFoldersBean.getFoldersResource().getFolderList().forEach(folder -> {
				String resultId = folder.getAssetId();

				String resultName = folder.getName();

				Date resultDate = folder.getDateLastUpdated();

				ShExchangeProviderFolder shExchangeProviderFolderChild = new ShExchangeProviderFolder();
				shExchangeProviderFolderChild.setId(resultId);
				shExchangeProviderFolderChild.setName(resultName);
				shExchangeProviderFolderChild.setDate(resultDate);

				shExchangeProviderFolder.getFolders().add(shExchangeProviderFolderChild);
			});
		}
	}

	public ShExchangeProviderFolder getFolder(String id) {
		ShExchangeProviderFolder shExchangeProviderFolder = new ShExchangeProviderFolder();

		ShExchangeProviderPost shExchangeProviderPost = this.getObject(id, true);
		shExchangeProviderFolder.setId(id);
		shExchangeProviderFolder.setName(shExchangeProviderPost.getTitle());
		shExchangeProviderFolder.setBreadcrumb(this.getOTMMBreadcrumb(id));
		shExchangeProviderFolder.setProviderName(PROVIDER_NAME);
		ShOTMMFoldersBean shOTMMFoldersBean = this.getOTMMFolderParents(id);
		if (shOTMMFoldersBean != null) {
			List<ShOTMMFolderBean> parentFolderList = shOTMMFoldersBean.getFoldersResource().getFolderList();
			if (!parentFolderList.isEmpty())
				shExchangeProviderFolder.setParentId(parentFolderList.get(0).getAssetId());
		} else {
			shExchangeProviderFolder.setParentId(null);
		}
		this.getOTMMFolders(id, shExchangeProviderFolder);

		this.getOTMMAssets(id, shExchangeProviderFolder);

		return shExchangeProviderFolder;
	}

	public ShOTMMFoldersBean getOTMMFolderParents(String id) {
		return restAPIFolderGet(id, "%s/otmmapi/v5/folders/%s/parents");
	}

	private ShOTMMFoldersBean restAPIFolderGet(String id, String restAPI) {
		ShOTMMFoldersBean shOTMMFoldersBean = null;
		try {

			HttpGet httpGet = new HttpGet(String.format(restAPI, this.baseURL, id));
			httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
			HttpResponse response = httpClient.execute(httpGet);
			if (response.getStatusLine().getStatusCode() == 200)
				shOTMMFoldersBean = objectMapper.readValue(responseHandler.handleResponse(response),
						ShOTMMFoldersBean.class);
		} catch (UnsupportedOperationException | IOException e) {
			logger.error(e);
		}
		return shOTMMFoldersBean;
	}

	private ShOTMMFoldersBean getOTMMAssetParents(String id) {
		return restAPIFolderGet(id, "%s/otmmapi/v5/assets/%s/parents");
	}

	private void getOTMMFolders(String id, ShExchangeProviderFolder shExchangeProviderFolder) {
		this.folderChildren(restAPIFolderGet(id, "%s/otmmapi/v5/folders/%s/folders"), shExchangeProviderFolder);
	}

	private void getOTMMAssets(String id, ShExchangeProviderFolder shExchangeProviderFolder) {

		ShOTMMAssetsBean shOTMMAssetsBean = null;
		try {
			HttpGet httpGet = new HttpGet(String.format("%s/otmmapi/v5/folders/%s/assets", this.baseURL, id));
			httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
			HttpResponse response = httpClient.execute(httpGet);
			shOTMMAssetsBean = objectMapper.readValue(responseHandler.handleResponse(response), ShOTMMAssetsBean.class);
		} catch (UnsupportedOperationException | IOException e) {
			logger.error(e);
		}
		if (shOTMMAssetsBean != null && shOTMMAssetsBean.getAssetsResource() != null
				&& shOTMMAssetsBean.getAssetsResource().getAssetList() != null) {

			shOTMMAssetsBean.getAssetsResource().getAssetList().forEach(asset -> {
				String postId = asset.getAssetId();

				String postTitle = asset.getName();

				Date postDate = asset.getDateLastUpdated();

				String postType = asset.getContentType();

				ShExchangeProviderPost shExchangeProviderPostChild = new ShExchangeProviderPost();

				shExchangeProviderPostChild.setId(postId);
				shExchangeProviderPostChild.setTitle(postTitle);
				shExchangeProviderPostChild.setDate(postDate);
				shExchangeProviderPostChild.setType(postType);

				shExchangeProviderFolder.getPosts().add(shExchangeProviderPostChild);
			});
		}
	}

	public ShExchangeProviderPost getObject(String id, boolean isFolder) {

		if (isFolder) {
			return this.folderObject(id);
		} else {
			return this.assetObject(id);
		}
	}

	private ShExchangeProviderPost assetObject(String id) {
		ShOTMMAssetDetailBean shOTMMAssetDetailBean = null;
		shOTMMAssetDetailBean = this.getAssetFromOTMM(id, shOTMMAssetDetailBean);

		ShExchangeProviderPost shExchangeProviderPost = new ShExchangeProviderPost();
		shExchangeProviderPost.setId(id);
		if (shOTMMAssetDetailBean != null)
			shExchangeProviderPost.setTitle(shOTMMAssetDetailBean.getAssetResource().getAsset().getName());
		ShOTMMFoldersBean shOTMMFoldersBean = this.getOTMMAssetParents(id);
		if (shOTMMFoldersBean != null) {
			List<ShOTMMFolderBean> parentFolderList = shOTMMFoldersBean.getFoldersResource().getFolderList();
			if (!parentFolderList.isEmpty())
				shExchangeProviderPost.setParentId(parentFolderList.get(0).getAssetId());
		} else {
			shExchangeProviderPost.setParentId(null);
		}
		return shExchangeProviderPost;
	}

	private ShExchangeProviderPost folderObject(String id) {
		ShOTMMFolderDetailBean shOTMMFolderDetailBean = null;
		shOTMMFolderDetailBean = this.getFolderFromOTMM(id, shOTMMFolderDetailBean);

		ShExchangeProviderPost shExchangeProviderPost = new ShExchangeProviderPost();
		shExchangeProviderPost.setId(id);
		if (shOTMMFolderDetailBean != null) {
			shExchangeProviderPost.setTitle(shOTMMFolderDetailBean.getFolderResource().getFolder().getName());
		}
		ShOTMMFoldersBean shOTMMFoldersBean = this.getOTMMFolderParents(id);
		if (shOTMMFoldersBean != null) {
			List<ShOTMMFolderBean> parentFolderList = shOTMMFoldersBean.getFoldersResource().getFolderList();
			if (!parentFolderList.isEmpty()) {
				shExchangeProviderPost.setParentId(parentFolderList.get(0).getAssetId());
			}
		} else {
			shExchangeProviderPost.setParentId(null);
		}

		return shExchangeProviderPost;
	}

	private ShOTMMAssetDetailBean getAssetFromOTMM(String id, ShOTMMAssetDetailBean shOTMMAssetDetailBean) {
		try {
			HttpGet httpGet = new HttpGet(String.format("%s/otmmapi/v5/assets/%s", this.baseURL, id));
			httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
			HttpResponse response = httpClient.execute(httpGet);
			shOTMMAssetDetailBean = objectMapper.readValue(responseHandler.handleResponse(response),
					ShOTMMAssetDetailBean.class);

		} catch (UnsupportedOperationException | IOException e) {
			logger.error(e);
		}
		return shOTMMAssetDetailBean;
	}

	private ShOTMMFolderDetailBean getFolderFromOTMM(String id, ShOTMMFolderDetailBean shOTMMFolderDetailBean) {
		try {
			HttpGet httpGet = new HttpGet(String.format("%s/otmmapi/v5/folders/%s", this.baseURL, id));
			httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
			HttpResponse response = httpClient.execute(httpGet);
			shOTMMFolderDetailBean = objectMapper.readValue(responseHandler.handleResponse(response),
					ShOTMMFolderDetailBean.class);
		} catch (UnsupportedOperationException | IOException e) {
			logger.error(e);
		}
		return shOTMMFolderDetailBean;
	}

	private ShOTMMSessionsBean otmmAuth() {
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(10000);
		cm.setDefaultMaxPerRoute(10000);
		cm.setValidateAfterInactivity(3000);
		httpClient = HttpClientBuilder.create().setDefaultRequestConfig(config).setDefaultCookieStore(httpCookieStore)
				.setConnectionManager(cm).build();
		List<NameValuePair> form = new ArrayList<>();
		form.add(new BasicNameValuePair("username", this.username));
		form.add(new BasicNameValuePair("password", this.password));
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(form, Consts.UTF_8);

		HttpPost httpPost = new HttpPost(String.format("%s/otmmapi/v5/sessions", this.baseURL));
		httpPost.setEntity(entity);
		httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());

		ShOTMMSessionsBean shOTMMSessionsBean = null;

		try {
			HttpResponse response = httpClient.execute(httpPost);

			shOTMMSessionsBean = objectMapper.readValue(responseHandler.handleResponse(response),
					ShOTMMSessionsBean.class);

		} catch (UnsupportedOperationException | IOException e) {
			logger.error(e);
		}
		return shOTMMSessionsBean;
	}

	public InputStream getDownload(String id) {

		InputStream inputStream = null;

		try {
			HttpGet httpGet = new HttpGet(String.format("%s/otmmapi/v5/assets/%s/contents", this.baseURL, id));
			httpGet.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
			httpGet.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
			HttpResponse response = httpClient.execute(httpGet);
			inputStream = response.getEntity().getContent();

		} catch (UnsupportedOperationException | IOException e) {
			logger.error(e);
		}

		return inputStream;

	}

	private List<ShExchangeProviderBreadcrumbItem> getOTMMBreadcrumb(String id) {
		ArrayList<ShExchangeProviderBreadcrumbItem> breadcrumb = new ArrayList<>();

		this.getOTMMParentBreadcrumbItem(id, breadcrumb);

		return breadcrumb;
	}

	private void getOTMMParentBreadcrumbItem(String id, ArrayList<ShExchangeProviderBreadcrumbItem> breadcrumb) {
		if (!StringUtils.isBlank(id) && !id.equals(ROOT_FOLDER_ID)) {
			ShExchangeProviderPost shExchangeProviderPost = this.getObject(id, true);

			ShExchangeProviderBreadcrumbItem shExchangeProviderBreadcrumbItem = new ShExchangeProviderBreadcrumbItem();
			shExchangeProviderBreadcrumbItem.setId(shExchangeProviderPost.getId());
			shExchangeProviderBreadcrumbItem.setTitle(shExchangeProviderPost.getTitle());

			this.getOTMMParentBreadcrumbItem(shExchangeProviderPost.getParentId(), breadcrumb);
			breadcrumb.add(shExchangeProviderBreadcrumbItem);
		} else {
			ShExchangeProviderBreadcrumbItem shExchangeProviderBreadcrumbItem = new ShExchangeProviderBreadcrumbItem();
			shExchangeProviderBreadcrumbItem.setId(ROOT_FOLDER_ID);
			shExchangeProviderBreadcrumbItem.setTitle(ROOT_FOLDER_NAME);
			breadcrumb.add(shExchangeProviderBreadcrumbItem);
		}
	}
}
