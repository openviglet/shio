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

package com.viglet.shio.api.widget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

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
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.utils.ShUtils;
import com.viglet.shio.widget.ShSystemWidget;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestMethodOrder (MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShWidgetAPITest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	private Principal mockPrincipal;

	private String newWidgetId = "9be0d9a6-1b98-43af-9e19-00bf71a05908";
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeAll
	void setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		mockPrincipal = Mockito.mock(Principal.class);
		Mockito.when(mockPrincipal.getName()).thenReturn("admin");
	}

	@Test
	void shWidgetList() throws Exception {
		mockMvc.perform(get("/api/v2/widget")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	@Test
	void stage01ShWidgetAdd() throws Exception {
		ShWidget shWidget = new ShWidget();

		shWidget.setName(ShSystemWidget.TEXT);
		shWidget.setDescription("Test Widget");
		shWidget.setClassName("com.viglet.shio.widget.ShTestWidget");
		shWidget.setImplementationCode("template/widget/test.html");
		shWidget.setType("TEXT,TEXTAREA");

		String requestBody = ShUtils.asJsonString(shWidget);

		RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/api/v2/widget").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).content(requestBody).contentType("application/json");

		ResultActions resultActions = mockMvc.perform(requestBuilder).andExpect(status().isOk());
		JsonNode resultNode = objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
		newWidgetId = resultNode.path("id").asString(newWidgetId);
	}

	@Test
	void stage02ShWidgetUpdate() throws Exception {
		ShWidget shWidget = new ShWidget();

		shWidget.setId(newWidgetId);
		shWidget.setName(ShSystemWidget.TEXT);
		shWidget.setDescription("Changed Test Widget");
		shWidget.setClassName("com.viglet.shio.widget.ShTestWidget");
		shWidget.setImplementationCode("template/widget/test.html");
		shWidget.setType("TEXT,TEXTAREA");

		String requestBody = ShUtils.asJsonString(shWidget);

		RequestBuilder requestBuilder = MockMvcRequestBuilders.put("/api/v2/widget/" + newWidgetId)
				.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(requestBody)
				.contentType("application/json");

		mockMvc.perform(requestBuilder).andExpect(status().isOk());
	}

	@Test
	void stage03ShWidgetEdit() throws Exception {
		mockMvc.perform(get("/api/v2/widget/" + newWidgetId)).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}
	
	@Test
	void stage04ShWidgetDelete() throws Exception {
		mockMvc.perform(delete("/api/v2/widget/" + newWidgetId)).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}
}