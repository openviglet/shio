/*
 * Copyright (C) 2016-2018 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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

package com.viglet.shio.api.site;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.utils.ShUtils;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestMethodOrder (MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteAPITest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	private String sampleSiteId = "c5bdee96-6feb-4894-9daf-3aab6cdd5087";

	private String newSiteId  = "2761f115-198d-4cd8-9566-7d5671764444";

	private Principal mockPrincipal;
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	@BeforeAll
	void setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		mockPrincipal = Mockito.mock(Principal.class);
		Mockito.when(mockPrincipal.getName()).thenReturn("admin");
	}

	@Test
	void shSiteList()  throws Exception {	
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/api/v2/site").principal(mockPrincipal);

		mockMvc.perform(requestBuilder).andExpect(status().isOk());
	}
	
	@Test
	void shSiteStructure()  throws Exception {		
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/api/v2/site/model").principal(mockPrincipal);

		mockMvc.perform(requestBuilder).andExpect(status().isOk());
	}
	
	@Test
	void shSiteRootFolder() throws Exception {
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/api/v2/site/" + sampleSiteId + "/folder").principal(mockPrincipal);

		mockMvc.perform(requestBuilder).andExpect(status().isOk());
		
	}
	
	@Test
	void shSiteEdit() throws Exception {
		mockMvc.perform(get("/api/v2/site/" + sampleSiteId)).andExpect(status().isOk());

	}

	@Test
	void shSiteExport() throws Exception {
		mockMvc.perform(get("/api/v2/site/" + sampleSiteId + "/export")).andExpect(status().isOk());

	}

	@Test
	void stage01ShSiteAdd() throws Exception {
		ShSite shSite = new ShSite();
		shSite.setDescription("Test Site");
		shSite.setName(UUID.randomUUID().toString());
		shSite.setUrl("http://example.com");

		String requestBody = ShUtils.asJsonString(shSite);

		RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/api/v2/site").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).content(requestBody).contentType("application/json")
				.header("X-Requested-With", "XMLHttpRequest");

		ResultActions resultActions = mockMvc.perform(requestBuilder).andExpect(status().isOk());
		JsonNode resultNode = objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
		newSiteId = resultNode.path("id").asText(newSiteId);

	}

	@Test
	void stage02ShSiteUpdate() throws Exception {
		ShSite shSite = new ShSite();
		shSite.setId(newSiteId);
		shSite.setDescription("Test Site2");
		shSite.setName("Test2");
		shSite.setUrl("http://www2.example.com");

		String requestBody = ShUtils.asJsonString(shSite);

		RequestBuilder requestBuilder = MockMvcRequestBuilders.put("/api/v2/site/" + newSiteId).principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).content(requestBody).contentType("application/json")
				.header("X-Requested-With", "XMLHttpRequest");

		mockMvc.perform(requestBuilder).andExpect(status().isOk());

	}
	
	@Test
	void stage03ShSiteDelete() throws Exception {
		mockMvc.perform(delete("/api/v2/site/" + newSiteId)).andExpect(status().isOk());

	}

}