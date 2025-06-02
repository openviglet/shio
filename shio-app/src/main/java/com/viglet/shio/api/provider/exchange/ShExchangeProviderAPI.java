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
package com.viglet.shio.api.provider.exchange;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.bean.provider.exchange.ShExchangeProviderInstanceBean;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.provider.exchange.ShExchangeProviderInstance;
import com.viglet.shio.persistence.model.provider.exchange.ShExchangeProviderVendor;
import com.viglet.shio.persistence.model.system.ShConfigVar;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.provider.exchange.ShExchangeProviderInstanceRepository;
import com.viglet.shio.persistence.repository.provider.exchange.ShExchangeProviderVendorRepository;
import com.viglet.shio.persistence.repository.system.ShConfigVarRepository;
import com.viglet.shio.property.ShConfigProperties;
import com.viglet.shio.provider.exchange.ShExchangeProvider;
import com.viglet.shio.provider.exchange.ShExchangeProviderFolder;
import com.viglet.shio.provider.exchange.ShExchangeProviderPost;
import com.viglet.shio.utils.ShConfigVarUtils;
import com.viglet.shio.utils.ShStaticFileUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/provider/exchange")
@Tag( name = "Exchange Provider", description = "Exchange Provider API")
@Slf4j
public class ShExchangeProviderAPI {

	private final ShConfigProperties shConfigProperties;
	private final ShFolderRepository shFolderRepository;
	private final ShStaticFileUtils shStaticFileUtils;
	private final ShConfigVarUtils shConfigVarUtils;
	private final ShExchangeProviderInstanceRepository shExchangeProviderInstanceRepository;
	private final ShExchangeProviderVendorRepository shExchangeProviderVendorRepository;
	private final ShConfigVarRepository shConfigVarRepository;

	private ShExchangeProvider shExchangeProvider;

	@Autowired
	public ShExchangeProviderAPI(ShConfigProperties shConfigProperties, ShFolderRepository shFolderRepository,
								 ShStaticFileUtils shStaticFileUtils, ShConfigVarUtils shConfigVarUtils,
								 ShExchangeProviderInstanceRepository shExchangeProviderInstanceRepository,
								 ShExchangeProviderVendorRepository shExchangeProviderVendorRepository,
								 ShConfigVarRepository shConfigVarRepository) {
		this.shConfigProperties = shConfigProperties;
		this.shFolderRepository = shFolderRepository;
		this.shStaticFileUtils = shStaticFileUtils;
		this.shConfigVarUtils = shConfigVarUtils;
		this.shExchangeProviderInstanceRepository = shExchangeProviderInstanceRepository;
		this.shExchangeProviderVendorRepository = shExchangeProviderVendorRepository;
		this.shConfigVarRepository = shConfigVarRepository;
	}

	@GetMapping("/vendor")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShExchangeProviderVendor> shExchangeProviderVendorListItem() {
		return shExchangeProviderVendorRepository.findAll();
	}

	@GetMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public List<ShExchangeProviderInstance> shExchangeProviderInstanceListItem() {
		return shExchangeProviderInstanceRepository.findAll();
	}

	@GetMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShExchangeProviderInstanceBean shExchangeProviderEdit(@PathVariable String id) {
		ShExchangeProviderInstance shExchangeProviderInstance = shExchangeProviderInstanceRepository.findById(id)
				.orElse(null);
		ShExchangeProviderInstanceBean shExchangeProviderInstanceBean = new ShExchangeProviderInstanceBean();
		if (shExchangeProviderInstance != null) {
			shExchangeProviderInstanceBean = new ShExchangeProviderInstanceBean();
			shExchangeProviderInstanceBean.setId(shExchangeProviderInstance.getId());
			shExchangeProviderInstanceBean.setName(shExchangeProviderInstance.getName());
			shExchangeProviderInstanceBean.setDescription(shExchangeProviderInstance.getDescription());
			shExchangeProviderInstanceBean.setVendor(shExchangeProviderInstance.getVendor());
			shExchangeProviderInstanceBean.setEnabled(shExchangeProviderInstance.getEnabled());

			String providerInstancePath = String.format(shConfigProperties.getExchange(),
					shExchangeProviderInstance.getId());

			List<ShConfigVar> shConfigVars = shConfigVarRepository.findByPath(providerInstancePath);

			for (ShConfigVar shConfigVar : shConfigVars) {
				shExchangeProviderInstanceBean.getProperties().put(shConfigVar.getName(), shConfigVar.getValue());
			}
		}

		return shExchangeProviderInstanceBean;
	}

	@PostMapping
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShExchangeProviderInstanceBean shExchangeProviderInstanceAdd(
			@RequestBody ShExchangeProviderInstanceBean shExchangeProviderInstanceBean) {
		ShExchangeProviderInstance shExchangeProviderInstance = new ShExchangeProviderInstance();

		shExchangeProviderInstance.setName(shExchangeProviderInstanceBean.getName());
		shExchangeProviderInstance.setDescription(shExchangeProviderInstanceBean.getDescription());
		shExchangeProviderInstance.setVendor(shExchangeProviderInstanceBean.getVendor());
		shExchangeProviderInstance.setEnabled(shExchangeProviderInstanceBean.getEnabled());
		shExchangeProviderInstanceRepository.save(shExchangeProviderInstance);

		for (Entry<String, String> propertyEntry : shExchangeProviderInstanceBean.getProperties().entrySet()) {
			String providerInstancePath = String.format(shConfigProperties.getExchange(),
					shExchangeProviderInstance.getId());

			ShConfigVar shConfigVar = shConfigVarRepository.findByPathAndName(providerInstancePath,
					propertyEntry.getKey());

			if (shConfigVar != null) {
				shConfigVar.setValue(propertyEntry.getValue());
			} else {
				shConfigVar = new ShConfigVar();
				shConfigVar.setPath(providerInstancePath);
				shConfigVar.setName(propertyEntry.getKey());
				shConfigVar.setValue(propertyEntry.getValue());
			}
			shConfigVarRepository.saveAndFlush(shConfigVar);
		}

		shExchangeProviderInstanceBean.setId(shExchangeProviderInstance.getId());

		return shExchangeProviderInstanceBean;
	}

	@PutMapping("/{id}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShExchangeProviderInstanceBean shExchangeProviderInstanceUpdate(@PathVariable String id,
			@RequestBody ShExchangeProviderInstanceBean shExchangeProviderInstanceBean) {
		Optional<ShExchangeProviderInstance> shExchangeProviderInstanceOptional = shExchangeProviderInstanceRepository
				.findById(id);
		if (shExchangeProviderInstanceOptional.isPresent()) {
			ShExchangeProviderInstance shExchangeProviderInstanceEdit = shExchangeProviderInstanceOptional.get();
			shExchangeProviderInstanceEdit.setName(shExchangeProviderInstanceBean.getName());
			shExchangeProviderInstanceEdit.setDescription(shExchangeProviderInstanceBean.getDescription());
			shExchangeProviderInstanceEdit.setVendor(shExchangeProviderInstanceBean.getVendor());
			shExchangeProviderInstanceEdit.setEnabled(shExchangeProviderInstanceBean.getEnabled());
			shExchangeProviderInstanceRepository.save(shExchangeProviderInstanceEdit);

			for (Entry<String, String> propertyEntry : shExchangeProviderInstanceBean.getProperties().entrySet()) {
				String providerInstancePath = String.format(shConfigProperties.getExchange(),
						shExchangeProviderInstanceEdit.getId());

				ShConfigVar shConfigVar = shConfigVarRepository.findByPathAndName(providerInstancePath,
						propertyEntry.getKey());

				if (shConfigVar != null) {
					shConfigVar.setValue(propertyEntry.getValue());
				} else {
					shConfigVar = new ShConfigVar();
					shConfigVar.setPath(providerInstancePath);
					shConfigVar.setName(propertyEntry.getKey());
					shConfigVar.setValue(propertyEntry.getKey());
				}
				shConfigVarRepository.saveAndFlush(shConfigVar);
			}

			return shExchangeProviderInstanceBean;
		}

		return null;

	}

	@DeleteMapping("/{id}")
	@Transactional
	public boolean shExchangeProviderInstanceDelete(@PathVariable String id) {
		Optional<ShExchangeProviderInstance> shExchangeProviderInstance = shExchangeProviderInstanceRepository
				.findById(id);
		if (shExchangeProviderInstance.isPresent()) {
			String providerInstancePath = String.format(shConfigProperties.getExchange(), id);
			shConfigVarRepository.deleteByPath(providerInstancePath);
			shExchangeProviderInstanceRepository.delete(id);
			return true;
		} else {
			return false;
		}
	}

	@GetMapping("/model")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShExchangeProviderInstanceBean shExchangeProviderInstanceStructure() {
		return new ShExchangeProviderInstanceBean();

	}

	@Operation(summary = "Sort Exchange Provider")
	@PutMapping("/sort")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public Map<String, Integer> shExchangeProviderInstanceSort(@RequestBody Map<String, Integer> objectOrder) {

		for (Entry<String, Integer> objectOrderItem : objectOrder.entrySet()) {
			int shObjectOrder = objectOrderItem.getValue();
			String shExchangeProviderId = objectOrderItem.getKey();
			Optional<ShExchangeProviderInstance> shExchangeProviderInstanceOptional = shExchangeProviderInstanceRepository
					.findById(shExchangeProviderId);
			if (shExchangeProviderInstanceOptional.isPresent()) {
				ShExchangeProviderInstance shExchangeProviderInstance = shExchangeProviderInstanceOptional.get();
				shExchangeProviderInstance.setPosition(shObjectOrder);
				shExchangeProviderInstanceRepository.save(shExchangeProviderInstance);

			}
		}
		return objectOrder;

	}

	private void initProvider(String providerInstanceId) {
		ShExchangeProviderInstance shExchangeProviderInstance = shExchangeProviderInstanceRepository
				.findById(providerInstanceId).orElse(null);
		if (shExchangeProviderInstance != null) {
			Map<String, String> variables = shConfigVarUtils
					.getVariablesFromPath(String.format(shConfigProperties.getExchange(), providerInstanceId));

			try {

				shExchangeProvider = (ShExchangeProvider) Class
						.forName(shExchangeProviderInstance.getVendor().getClassName()).getDeclaredConstructor()
						.newInstance();

			} catch (IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException
					| InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				log.error(e.getMessage(), e);
			}
			shExchangeProvider.init(variables);
		}
	}

	@PostMapping("/{providerInstanceId}/import/{providerItemId}/to/{folderId}")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShPostImpl shExchangeProviderImportItem(@PathVariable String folderId,
			@PathVariable String providerInstanceId, @PathVariable String providerItemId, Principal principal) {

		this.initProvider(providerInstanceId);

		ShExchangeProviderPost shExchangeProviderPost = shExchangeProvider.getObject(providerItemId, false);

		String fileName = shExchangeProviderPost.getTitle();
		InputStream is = shExchangeProvider.getDownload(providerItemId);

		try {

			MultipartFile file = new MockMultipartFile(fileName, IOUtils.toByteArray(is));

			ShFolder shFolder = shFolderRepository.findById(folderId).orElse(null);
			if (shFolder != null) {
				TikaConfig config = TikaConfig.getDefaultConfig();
				String mediaType = new Tika().detect(file.getInputStream());
				MimeType mimeType = config.getMimeRepository().forName(mediaType);
				String extension = mimeType.getExtension();
				if (!StringUtils.isEmpty(extension)) {
					String fileWithoutExtension = fileName.substring(0, fileName.lastIndexOf('.'));
                    fileName = String.format("%s%s", fileWithoutExtension, extension);
				}
				return shStaticFileUtils.createFilePost(file, fileName, shFolder, principal, true);
			}
		} catch (IOException | MimeTypeException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	@GetMapping("/{providerInstanceId}/{id}/list")
	@JsonView({ ShJsonView.ShJsonViewObject.class })
	public ShExchangeProviderFolder shExchangeProviderListItem(@PathVariable String providerInstanceId,
			@PathVariable String id) {
		this.initProvider(providerInstanceId);

		return id.equals("_root") ? shExchangeProvider.getRootFolder() : shExchangeProvider.getFolder(id);

	}
}
