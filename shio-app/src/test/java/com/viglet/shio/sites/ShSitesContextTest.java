/*
 * Copyright (C) 2016-2021 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General License for more details.
 *
 * You should have received a copy of the GNU General License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.shio.sites;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.website.ShSitesContextURL;
import com.viglet.shio.website.ShSitesContextURLProcess;
import com.viglet.shio.website.cache.component.ShCachePage;
import com.viglet.shio.website.cache.component.ShCachePageBean;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSitesContextTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@TestConfiguration
	static class TestBeans {
		private static final ShSitesContextURLProcess SH_SITES_CONTEXT_URL_PROCESS_MOCK = Mockito
				.mock(ShSitesContextURLProcess.class);
		private static final ShCachePage SH_CACHE_PAGE_MOCK = Mockito.mock(ShCachePage.class);

		@Bean
		@Primary
		ShSitesContextURLProcess shSitesContextURLProcess() {
			return SH_SITES_CONTEXT_URL_PROCESS_MOCK;
		}

		@Bean
		@Primary
		ShCachePage shCachePage() {
			return SH_CACHE_PAGE_MOCK;
		}
	}

	@BeforeAll
	void setup() {
		ShCachePageBean shCachePageBean = new ShCachePageBean();
		shCachePageBean.setContentType("text/html");
		shCachePageBean.setBody("ok");

		when(TestBeans.SH_SITES_CONTEXT_URL_PROCESS_MOCK.getContextURL(any(), any())).thenAnswer(invocation -> {
			ShSitesContextURL shSitesContextURL = new ShSitesContextURL();
			shSitesContextURL.setRequest(invocation.getArgument(0));
			shSitesContextURL.setResponse(invocation.getArgument(1));
			shSitesContextURL.getInfo().setSiteId("test-site");
			shSitesContextURL.getInfo().setObjectId("test-object");
			shSitesContextURL.getInfo().setPageAllowGuestUser(true);
			shSitesContextURL.getInfo().setContextURLOriginal("/sites/viglet/default/en-us");
			return shSitesContextURL;
		});
		when(TestBeans.SH_CACHE_PAGE_MOCK.cache(any())).thenReturn(shCachePageBean);

		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

	@Test
	void sitesFullGeneric() throws Exception {
		mockMvc.perform(get("/sites/viglet/default/en-us")).andExpect(status().isOk());

	}

	@Test
	void sitesFullGenericHeaders() throws Exception {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add("x-sh-site", "viglet");
		httpHeaders.add("x-sh-nocache", "1");
		mockMvc.perform(get("/sites").headers(httpHeaders)).andExpect(status().isOk());

	}
}