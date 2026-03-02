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

package com.viglet.shio.api.post.type;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.Calendar;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShUtils;
import com.viglet.shio.widget.ShSystemWidget;

@SpringBootTest
@TestMethodOrder (MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTypeAPITest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private ShWidgetRepository shWidgetRepository;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;

	private MockMvc mockMvc;

	private Principal mockPrincipal;

	private String newPostTypeId = "f0a9cc2f-f283-4e05-a7c6-758bb3b81b76";
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeAll
	void setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		mockPrincipal = Mockito.mock(Principal.class);
		Mockito.when(mockPrincipal.getName()).thenReturn("admin");
	}

	@Test
	void shPostTypeList() throws Exception {
		mockMvc.perform(get("/api/v2/post/type")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	@Test
	void shPostTypeStructure() throws Exception {
		mockMvc.perform(get("/api/v2/post/type/model")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	@Test
	void stage01ShPostTypeAdd() throws Exception {

		ShWidget shWidgetText = shWidgetRepository.findByName(ShSystemWidget.TEXT);

		ShPostType shPostType = new ShPostType();
		shPostType.setName("PT-TEST");
		shPostType.setTitle("Test");
		shPostType.setDate(Calendar.getInstance().toInstant());
		shPostType.setDescription("Test Post Type");
		shPostType.setSystem((byte) 0);

		ShPostTypeAttr shPostTypeAttr = new ShPostTypeAttr();
		shPostTypeAttr.setName(ShSystemPostTypeAttr.TEXT);
		shPostTypeAttr.setLabel("Test");
		shPostTypeAttr.setDescription("Test");
		shPostTypeAttr.setIsSummary((byte) 0);
		shPostTypeAttr.setIsTitle((byte) 1);
		shPostTypeAttr.setOrdinal(1);
		shPostTypeAttr.setRequired((byte) 1);
		shPostTypeAttr.setShWidget(shWidgetText);

		shPostType.getShPostTypeAttrs().add(shPostTypeAttr);

		String postTypeRequestBody = ShUtils.asJsonStringAndView(shPostType, ShJsonView.ShJsonViewPostType.class);

		RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.post("/api/v2/post/type").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).content(postTypeRequestBody)
				.contentType("application/json");

		ResultActions resultActions = mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
		JsonNode resultNode = objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
		newPostTypeId = resultNode.path("id").asText(newPostTypeId);
	}

	@Test
	void stage02ShPostTypeGet() throws Exception {
		mockMvc.perform(get("/api/v2/post/type/" + newPostTypeId)).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}

	@Test
	void stage03ShPostTypePostStructure() throws Exception {
		mockMvc.perform(get("/api/v2/post/type/" + newPostTypeId + "/post/model")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}

	@Test
	void stage04ShPostTypeByNamePostStructure() throws Exception {
		mockMvc.perform(get("/api/v2/post/type/name/PT-TEST/post/model")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}

	@Test
	void stage05ShPostTypeAttrAdd() throws Exception {
		ShWidget shWidgetTextArea = shWidgetRepository.findByName(ShSystemWidget.TEXT_AREA);

		ShPostTypeAttr shPostTypeAttr = new ShPostTypeAttr();
		shPostTypeAttr.setName(ShSystemPostTypeAttr.DESCRIPTION);
		shPostTypeAttr.setLabel("Test Area");
		shPostTypeAttr.setDescription("Test");
		shPostTypeAttr.setIsSummary((byte) 0);
		shPostTypeAttr.setIsTitle((byte) 1);
		shPostTypeAttr.setOrdinal(1);
		shPostTypeAttr.setRequired((byte) 1);
		shPostTypeAttr.setShWidget(shWidgetTextArea);

		String postTypeAttrRequestBody = ShUtils.asJsonStringAndView(shPostTypeAttr,
				ShJsonView.ShJsonViewPostType.class);

		RequestBuilder folderRequestBuilder = MockMvcRequestBuilders
				.post("/api/v2/post/type/" + newPostTypeId + "/attr").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).content(postTypeAttrRequestBody)
				.contentType("application/json");

		mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
	}

	@Test
	void stage06ShPostTypeUpdate() throws Exception {
		ShPostType shPostType = shPostTypeRepository.findById(newPostTypeId).orElseThrow();

		shPostType.setName("PT-TEST");
		shPostType.setTitle("Test was Changed");
		shPostType.setDate(Calendar.getInstance().toInstant());
		shPostType.setDescription("Test Post Type was Changed");
		shPostType.setSystem((byte) 0);
		String postTypeRequestBody = ShUtils.asJsonStringAndView(shPostType, ShJsonView.ShJsonViewPostType.class);

		RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.put("/api/v2/post/type/" + newPostTypeId)
				.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(postTypeRequestBody)
				.contentType("application/json");

		mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
	}

	@Test
	void stage07ShPostTypeDelete() throws Exception {
		mockMvc.perform(delete("/api/v2/post/type/" + newPostTypeId)).andExpect(status().isOk());
	}
}