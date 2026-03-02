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

package com.viglet.shio.api.user;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.utils.ShUtils;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestMethodOrder (MethodOrderer.MethodName.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShUserAPITest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	private String newUsername = "test";

	private Principal mockPrincipal;

	@Autowired
	private ShUserRepository shUserRepository;

	@Mock
	SecurityContext mockSecurityContext;

	@BeforeAll
	void setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		mockPrincipal = Mockito.mock(Principal.class);
		Mockito.when(mockPrincipal.getName()).thenReturn("admin");
	}

	@Test
	void shUserList() throws Exception {
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/api/v2/user").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).contentType("application/json");
		mockMvc.perform(requestBuilder).andExpect(status().isOk());
	}

	// @Test
	void shUserCurrent() throws Exception {
		Authentication authentication = mock(Authentication.class);
		SecurityContext securityContext = mock(SecurityContext.class);
		when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
		when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(mockPrincipal);
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/api/v2/user/current").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).contentType("application/json");

		mockMvc.perform(requestBuilder).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}

	@Test
	void shUserStructure() throws Exception {
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/api/v2/user/model").principal(mockPrincipal)
				.accept(MediaType.APPLICATION_JSON).contentType("application/json");

		mockMvc.perform(requestBuilder).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}

	@Test
	void stage01ShUserAdd() throws Exception {
		ShUser shUser = new ShUser();
		shUser.setUsername(newUsername);
		shUser.setEmail("test@test.com");
		shUser.setConfirmEmail("test@test.com");
		shUser.setEnabled(1);
		shUser.setFirstName("Test");
		shUser.setLastName("Test");
		shUser.setPassword("test");

		String requestBody = ShUtils.asJsonString(shUser);

		RequestBuilder requestBuilder = MockMvcRequestBuilders.post(String.format("/api/v2/user/%s", newUsername))
				.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(requestBody)
				.contentType("application/json");

		mockMvc.perform(requestBuilder).andExpect(status().isOk());
	}

	@Test
	void stage02ShUserGet() throws Exception {
		mockMvc.perform(get(String.format("/api/v2/user/%s", newUsername))).andExpect(status().isOk())
				.andExpect(content().contentType("application/json"));
	}

	@Test
	void stage03ShUserUpdate() throws Exception {
		ShUser shUser = shUserRepository.findByUsername(newUsername);
		shUser.setFirstName("Test2");

		String userRequestBody = ShUtils.asJsonString(shUser);

		RequestBuilder userRequestBuilder = MockMvcRequestBuilders.put(String.format("/api/v2/user/%s", newUsername))
				.principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(userRequestBody)
				.contentType("application/json");

		mockMvc.perform(userRequestBuilder).andExpect(status().isOk());
	}

	@Test
	void stage04ShUserDelete() throws Exception {
		mockMvc.perform(delete("/api/v2/user/" + newUsername)).andExpect(status().isOk());
	}
}