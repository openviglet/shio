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
package com.viglet.shio.workflow;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.auth.ShGroup;
import com.viglet.shio.persistence.model.auth.ShUser;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.workflow.ShWorkflowTask;
import com.viglet.shio.persistence.repository.auth.ShGroupRepository;
import com.viglet.shio.persistence.repository.auth.ShUserRepository;
import com.viglet.shio.persistence.repository.workflow.ShWorkflowTaskRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Alexandre Oliveira
 */
@Slf4j
@Component
public class ShWorkflow {

	private final JavaMailSender javaMailSender;
	private final ShWorkflowTaskRepository shWorkflowTaskRepository;
	private final ShUserRepository shUserRepository;
	private final ShGroupRepository shGroupRepository;

	public ShWorkflow(JavaMailSender javaMailSender, ShWorkflowTaskRepository shWorkflowTaskRepository,
					  ShUserRepository shUserRepository, ShGroupRepository shGroupRepository) {
		this.javaMailSender = javaMailSender;
		this.shWorkflowTaskRepository = shWorkflowTaskRepository;
		this.shUserRepository = shUserRepository;
		this.shGroupRepository = shGroupRepository;
	}

	public void requestWorkFlow(ShObject shObject, Principal principal) {
		if (shObject instanceof ShPostImpl shPostImpl && shWorkflowTaskRepository.countByShObject(shObject) == 0) {
			ShWorkflowTask shWorkflowTask = new ShWorkflowTask();
			shWorkflowTask.setDate(new Date());
			shWorkflowTask.setTitle("Request to Publish");
			shWorkflowTask.setShObject(shObject);
			shWorkflowTask.setRequester(principal.getName());
			shWorkflowTask.setRequested(shPostImpl.getShPostType().getWorkflowPublishEntity());

			shWorkflowTaskRepository.save(shWorkflowTask);

			this.sendWorkflowEmail(shWorkflowTask);
		}

	}

	public String sendWorkflowEmail(ShWorkflowTask shWorkflowTask) {
		try {

			String title = StringUtils.EMPTY;
			if (shWorkflowTask.getShObject() instanceof ShPostImpl shPostImpl) {
				title = shPostImpl.getTitle();
			} else if (shWorkflowTask.getShObject() instanceof ShFolder shFolder) {
				title = shFolder.getName();
			}

			List<ShGroup> shGroups = new ArrayList<>();
			ShGroup shGroup = shGroupRepository.findByName(shWorkflowTask.getRequested());
			shGroups.add(shGroup);
			Set<ShUser> shUsers = shUserRepository.findByShGroupsIn(shGroups);

			for (ShUser shUser : shUsers) {
				SimpleMailMessage msg = new SimpleMailMessage();
				msg.setTo(shUser.getEmail());

				msg.setSubject("New publish request");
				msg.setText(String.format("Hi %s,%n There a new publish request for content: %s",
						shUser.getFirstName(), title));

				javaMailSender.send(msg);
			}
		} catch (Exception e) {
			if (log.isDebugEnabled()) {
				log.debug("Test Connection Email failed");
			}
		}

		return null;

	}
}
