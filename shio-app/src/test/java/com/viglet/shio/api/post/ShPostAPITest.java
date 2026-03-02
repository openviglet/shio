/*
 * Copyright (C) 2016-2020 the original author or authors. 
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
package com.viglet.shio.api.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeAttrRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShUtils;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestMethodOrder (MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShPostAPITest {
	private final Logger log = LoggerFactory.getLogger(this.getClass());
	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private ShFolderRepository shFolderRepository;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ShPostTypeAttrRepository shPostTypeAttrRepository;
	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShSiteRepository shSiteRepository;

	private MockMvc mockMvc;

	private Principal mockPrincipal;

	private String newPostId = "553923c7-fda4-4a91-9700-eb9a549bb522";
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeAll
	void setup() {
		log.debug("PostAPITest Setup");
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		mockPrincipal = Mockito.mock(Principal.class);
		Mockito.when(mockPrincipal.getName()).thenReturn("admin");
		this.ensureBaselineSite();
	}

	private void ensureBaselineSite() {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		if (shSite == null) {
			shSite = new ShSite();
			shSite.setName("Viglet");
			shSite.setDescription("Default Site");
			shSite.setUrl("http://example.com");
			shSite.setOwner("admin");
			shSite.setFurl("viglet");
			shSite.setDate(new Date().toInstant());
			shSiteRepository.save(shSite);
		}
		ShFolder shFolder = shFolderRepository.findByShSiteAndName(shSite, "Home");
		if (shFolder == null) {
			ShFolder homeFolder = new ShFolder();
			homeFolder.setName("Home");
			homeFolder.setRootFolder((byte) 1);
			homeFolder.setPosition(1);
			homeFolder.setShSite(shSite);
			homeFolder.setOwner("admin");
			homeFolder.setDate(new Date().toInstant());
			shFolderRepository.save(homeFolder);
		}
	}

	@Test
	void shPostList() throws Exception {
		mockMvc.perform(get("/api/v2/post")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	@Test
	void shPostAttrModel() throws Exception {
		mockMvc.perform(get("/api/v2/post/attr/model")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	@Test
	void stage01ShPostAdd() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		ShFolder shFolder = shFolderRepository.findByShSiteAndName(shSite, "Home");
		ShPostType shPostType = shPostTypeRepository.findByName(ShSystemPostType.ARTICLE);

		// Post
		ShPost shPost = new ShPost();
		shPost.setShFolder(shFolder);
		shPost.setShPostType(shPostType);
		shPost.setShSite(shSite);
		// Title Field
		ShPostAttr shPostAttrTitle = new ShPostAttr();
		shPostAttrTitle.setStrValue("Test Title Value");
		shPostAttrTitle.setType(1);
		shPostAttrTitle.setShPostTypeAttr(
				shPostTypeAttrRepository.findByShPostTypeAndName(shPostType, ShSystemPostTypeAttr.TITLE));

		shPost.getShPostAttrsNonDraft().add(shPostAttrTitle);

		// Description Field
		ShPostAttr shPostAttrDescription = new ShPostAttr();
		shPostAttrDescription.setStrValue("Test Description Value");
		shPostAttrDescription.setType(1);
		shPostAttrDescription.setShPostTypeAttr(
				shPostTypeAttrRepository.findByShPostTypeAndName(shPostType, ShSystemPostTypeAttr.DESCRIPTION));

		shPost.getShPostAttrsNonDraft().add(shPostAttrDescription);

		String postRequestBody = ShUtils.asJsonString(shPost);

		RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.post("/api/v2/post").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).content(postRequestBody).contentType("application/json");

		ResultActions resultActions = mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
		JsonNode resultNode = objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
		newPostId = resultNode.path("id").asText(newPostId);
	}

	@Test
	void stage02ShPostEdit() throws Exception {
		mockMvc.perform(get("/api/v2/post/" + newPostId)).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}

	@Test
	void stage03ShPostUpdate() throws Exception {

		ShPost shPost = shPostRepository.findById(newPostId).orElseThrow();

		for (ShPostAttr shPostAttr : shPost.getShPostAttrsNonDraft()) {
			shPostAttr.setStrValue("Test Value was changed");
		}

		String postRequestBody = ShUtils.asJsonStringAndView(shPost, ShJsonView.ShJsonViewPost.class);

		RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.put("/api/v2/post/" + newPostId)
				.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(postRequestBody)
				.contentType("application/json");

		mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
	}

	@Test
	void stage04ShPostDelete() throws Exception {

		RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.delete("/api/v2/post/" + newPostId)
				.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).contentType("application/json");

		mockMvc.perform(folderRequestBuilder).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}
}