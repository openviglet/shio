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
package com.viglet.shio.api.workflow;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.workflow.ShWorkflowTask;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.workflow.ShWorkflowTaskRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/workflow")
@Tag( name = "Workflow", description = "Workflow API")
public class ShWorkflowAPI {


	private final ShWorkflowTaskRepository shWorkflowTaskRepository;
	private final ShUserRepository shUserRepository;
	private final ShGroupRepository shGroupRepository;
	private final ShPostRepository shPostRepository;

	ShWorkflowAPI(ShWorkflowTaskRepository shWorkflowTaskRepository, ShUserRepository shUserRepository,
				  ShGroupRepository shGroupRepository, ShPostRepository shPostRepository) {
		this.shWorkflowTaskRepository = shWorkflowTaskRepository;
		this.shUserRepository = shUserRepository;
		this.shGroupRepository = shGroupRepository;
		this.shPostRepository = shPostRepository;
	}

	@GetMapping("/task")
	public Set<ShWorkflowTask> shWorkflowTasksGet(Principal principal) {
		ShUser shUser = shUserRepository.findByUsername(principal.getName());
		List<ShUser> shUsers = new ArrayList<>();
		List<String> requesters = new ArrayList<>();

		shUsers.add(shUser);
		
		shGroupRepository.findByShUsersIn(shUsers).forEach(shGroup -> requesters.add(shGroup.getName()));
		
		Set<ShWorkflowTask> shWorkflowTasks = shWorkflowTaskRepository.findByRequestedIn(requesters);
		
		shWorkflowTasks.forEach(shWorkflowTask -> 
			shPostRepository.findByIdFull(shWorkflowTask.getShObject().getId()).ifPresent(shWorkflowTask::setShObject));	
		
		return shWorkflowTasks;
	}
}
