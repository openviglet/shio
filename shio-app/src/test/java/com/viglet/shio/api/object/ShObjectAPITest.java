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

package com.viglet.shio.api.object;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@TestInstance(Lifecycle.PER_CLASS)
class ShObjectAPITest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private ShSiteRepository shSiteRepository;
	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ShPostTypeAttrRepository shPostTypeAttrRepository;
	@Autowired
	private ShFolderRepository shFolderRepository;
	
	private MockMvc mockMvc;
	
	private Principal mockPrincipal;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeAll
	void setup() {
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
			homeFolder.setFurl("home");
			homeFolder.setRootFolder((byte) 1);
			homeFolder.setPosition(1);
			homeFolder.setShSite(shSite);
			homeFolder.setOwner("admin");
			homeFolder.setDate(new Date().toInstant());
			shFolder = shFolderRepository.save(homeFolder);
		} else if (shFolder.getFurl() == null || shFolder.getFurl().isEmpty()) {
			shFolder.setFurl("home");
			shFolderRepository.save(shFolder);
		}
		this.ensureBaselinePost(shSite, shFolder);
	}

	private void ensureBaselinePost(ShSite shSite, ShFolder shFolder) {
		ShPost shPost = shPostRepository.findByShFolderAndFurl(shFolder, "index");
		if (shPost != null) {
			return;
		}
		ShPostType shPostType = shPostTypeRepository.findByName(ShSystemPostType.ARTICLE);
		if (shPostType == null) {
			return;
		}
		ShPost baselinePost = new ShPost();
		baselinePost.setShFolder(shFolder);
		baselinePost.setShSite(shSite);
		baselinePost.setShPostType(shPostType);
		baselinePost.setOwner("admin");
		baselinePost.setDate(new Date().toInstant());
		baselinePost.setFurl("index");
		baselinePost.setTitle("Index");
		baselinePost.setSummary("Index");

		ShPostAttr titleAttr = new ShPostAttr();
		titleAttr.setStrValue("Index");
		titleAttr.setType(1);
		titleAttr.setShPostTypeAttr(
				shPostTypeAttrRepository.findByShPostTypeAndName(shPostType, ShSystemPostTypeAttr.TITLE));
		titleAttr.setShPost(baselinePost);
		baselinePost.getShPostAttrsNonDraft().add(titleAttr);

		ShPostAttr descriptionAttr = new ShPostAttr();
		descriptionAttr.setStrValue("Index");
		descriptionAttr.setType(1);
		descriptionAttr.setShPostTypeAttr(
				shPostTypeAttrRepository.findByShPostTypeAndName(shPostType, ShSystemPostTypeAttr.DESCRIPTION));
		descriptionAttr.setShPost(baselinePost);
		baselinePost.getShPostAttrsNonDraft().add(descriptionAttr);

		shPostRepository.save(baselinePost);
	}

	// shObjectMoveTo
	
	@Test
	void shObjectMoveToFolder() throws Exception {

		ShSite shSite = shSiteRepository.findByName("Viglet");

		String newFolderId = UUID.randomUUID().toString();
		ShFolder shFolder = new ShFolder();
		shFolder.setName("Unit Test Move to Folder");
		shFolder.setPosition(2);
		shFolder.setRootFolder((byte) 1);
		shFolder.setShSite(shSite);
		String folderRequestBody = ShUtils.asJsonString(shFolder);

		RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.post("/api/v2/folder").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).content(folderRequestBody)
				.contentType("application/json");
		
		ResultActions folderResult = mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
		JsonNode folderNode = objectMapper.readTree(folderResult.andReturn().getResponse().getContentAsString());
		newFolderId = folderNode.path("id").asText(newFolderId);
		
		List<String> objectIds = new ArrayList<String>();
		
		objectIds.add(newFolderId);
		
		String objectIdsRequestBody = ShUtils.asJsonString(objectIds);
		ShFolder shFolderHome = shFolderRepository.findByShSiteAndName(shSite, "Home");
		RequestBuilder moveRequestBuilder = MockMvcRequestBuilders.put("/api/v2/object/moveto/" + shFolderHome.getId())
				.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(objectIdsRequestBody)
				.contentType("application/json");

		mockMvc.perform(moveRequestBuilder).andExpect(status().isOk());
	}

	@Test
	void shObjectMoveToSite() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		ShFolder shFolderHome = shFolderRepository.findByShSiteAndName(shSite, "Home");
		ShFolder shParentFolder = shFolderRepository.findById(shFolderHome.getId()).get();

		String newFolderId = UUID.randomUUID().toString();
		ShFolder shFolder = new ShFolder();
		shFolder.setName("Unit Test Move to Site");
		shFolder.setPosition(2);
		shFolder.setRootFolder((byte) 0);
		shFolder.setParentFolder(shParentFolder);
		String folderRequestBody = ShUtils.asJsonString(shFolder);

		RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.post("/api/v2/folder").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).content(folderRequestBody)
				.contentType("application/json");
		
		ResultActions folderResult = mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
		JsonNode folderNode = objectMapper.readTree(folderResult.andReturn().getResponse().getContentAsString());
		newFolderId = folderNode.path("id").asText(newFolderId);
		
		List<String> objectIds = new ArrayList<String>();
		
		objectIds.add(newFolderId);
		
		String objectIdsRequestBody = ShUtils.asJsonString(objectIds);
		
		RequestBuilder moveRequestBuilder = MockMvcRequestBuilders.put("/api/v2/object/moveto/" + shSite.getId())
				.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(objectIdsRequestBody)
				.contentType("application/json");

		mockMvc.perform(moveRequestBuilder).andExpect(status().isOk());
	}
	
	// shObjectCopyTo
	
		@Test
		void shObjectCopyToFolder() throws Exception {
			ShSite shSite = shSiteRepository.findByName("Viglet");
			ShFolder shFolderHome = shFolderRepository.findByShSiteAndName(shSite, "Home");
			

			String newFolderId = UUID.randomUUID().toString();
			ShFolder shFolder = new ShFolder();
			shFolder.setName("Unit Test Copy to Folder");
			shFolder.setPosition(2);
			shFolder.setRootFolder((byte) 1);
			shFolder.setShSite(shSite);
			String folderRequestBody = ShUtils.asJsonString(shFolder);

			RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.post("/api/v2/folder").principal(mockPrincipal)
					.accept(MediaType.APPLICATION_JSON).content(folderRequestBody)
					.contentType("application/json");
			
			ResultActions folderResult = mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
			JsonNode folderNode = objectMapper.readTree(folderResult.andReturn().getResponse().getContentAsString());
			newFolderId = folderNode.path("id").asText(newFolderId);
			
			List<String> objectIds = new ArrayList<String>();
			
			objectIds.add(newFolderId);
			
			String objectIdsRequestBody = ShUtils.asJsonString(objectIds);
			
			RequestBuilder moveRequestBuilder = MockMvcRequestBuilders.put("/api/v2/object/copyto/" + shFolderHome.getId())
					.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(objectIdsRequestBody)
					.contentType("application/json");

			mockMvc.perform(moveRequestBuilder).andExpect(status().isOk());
		}

		@Test
		void shObjectCopyToSite() throws Exception {
			ShSite shSite = shSiteRepository.findByName("Viglet");
			ShFolder shFolderHome = shFolderRepository.findByShSiteAndName(shSite, "Home");
			ShFolder shParentFolder = shFolderRepository.findById(shFolderHome.getId()).get();

			String newFolderId = UUID.randomUUID().toString();
			ShFolder shFolder = new ShFolder();
			shFolder.setName("Unit Test Copy to Site");
			shFolder.setPosition(2);
			shFolder.setRootFolder((byte) 0);
			shFolder.setParentFolder(shParentFolder);
			String folderRequestBody = ShUtils.asJsonString(shFolder);

			RequestBuilder folderRequestBuilder = MockMvcRequestBuilders.post("/api/v2/folder").principal(mockPrincipal)
					.accept(MediaType.APPLICATION_JSON).content(folderRequestBody)
					.contentType("application/json");
			
			ResultActions folderResult = mockMvc.perform(folderRequestBuilder).andExpect(status().isOk());
			JsonNode folderNode = objectMapper.readTree(folderResult.andReturn().getResponse().getContentAsString());
			newFolderId = folderNode.path("id").asText(newFolderId);
			
			List<String> objectIds = new ArrayList<String>();
			
			objectIds.add(newFolderId);
			
			String objectIdsRequestBody = ShUtils.asJsonString(objectIds);
			
			RequestBuilder moveRequestBuilder = MockMvcRequestBuilders.put("/api/v2/object/copyto/" + shSite.getId())
					.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(objectIdsRequestBody)
					.contentType("application/json");

			mockMvc.perform(moveRequestBuilder).andExpect(status().isOk());
		}
		
	// shObjectPreview

	@Test
	void shObjectPreviewSite() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		mockMvc.perform(get("/api/v2/object/" + shSite.getId() + "/preview")).andExpect(status().is3xxRedirection());

	}

	@Test
	void shObjectPreviewFolder() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		ShFolder shFolderHome = shFolderRepository.findByShSiteAndName(shSite, "Home");
		mockMvc.perform(get("/api/v2/object/" + shFolderHome.getId() + "/preview")).andExpect(status().is3xxRedirection());

	}

	@Test
	void shObjectPreviewPost() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		ShFolder shFolderHome = shFolderRepository.findByShSiteAndName(shSite, "Home");
		ShPost shPost = shPostRepository.findByShFolderAndFurl(shFolderHome, "index");
		mockMvc.perform(get("/api/v2/object/" + shPost.getId() + "/preview")).andExpect(status().is3xxRedirection());

	}

	// shObjectListItem

	@ParameterizedTest
	@ValueSource(strings = {"/list", "/list/Text", "/path"})
	void shObjectListItemSite(String path) throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		mockMvc.perform(get("/api/v2/object/" + shSite.getId() + path)).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	@Test
	void shObjectListItemFolder() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		ShFolder shFolderHome = shFolderRepository.findByShSiteAndName(shSite, "Home");
		mockMvc.perform(get("/api/v2/object/" + shFolderHome.getId() + "/list")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	// shObjectListItem

	@Test
	void shFolderListByPostTypeSite() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		mockMvc.perform(get("/api/v2/object/" + shSite.getId() + "/list/Text")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	// shObjectPath

	@Test
	void shObjectPathSite() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		mockMvc.perform(get("/api/v2/object/" + shSite.getId() + "/path")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

	@Test
	void shObjectPathPost() throws Exception {
		ShSite shSite = shSiteRepository.findByName("Viglet");
		ShFolder shFolderHome = shFolderRepository.findByShSiteAndName(shSite, "Home");
		ShPost shPost = shPostRepository.findByShFolderAndFurl(shFolderHome, "index");
		mockMvc.perform(get("/api/v2/object/" + shPost.getId() + "/path")).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));

	}

}