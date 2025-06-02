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
package com.viglet.shio.api.stock;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.stock.beans.ShSPhotoBean;
import com.viglet.shio.stock.beans.ShSPhotoPreviewBean;
import com.viglet.shio.utils.ShStaticFileUtils;
import com.viglet.shio.website.utils.ShSitesPostUtils;

import de.androidpit.colorthief.ColorThief;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
@RestController
@RequestMapping("/api/v2/stock/photos")
@Tag( name = "Photos", description = "Photos")
@Slf4j
public class ShSPhotoAPI {
	private final ShFolderRepository shFolderRepository;
	private final ShPostRepository shPostRepository;
	private final ShStaticFileUtils shStaticFileUtils;
	private final ShSitesPostUtils shSitesPostUtils;

	@Autowired
	public ShSPhotoAPI(ShFolderRepository shFolderRepository, ShPostRepository shPostRepository,
					   ShStaticFileUtils shStaticFileUtils, ShSitesPostUtils shSitesPostUtils) {
		this.shFolderRepository = shFolderRepository;
		this.shPostRepository = shPostRepository;
		this.shStaticFileUtils = shStaticFileUtils;
		this.shSitesPostUtils = shSitesPostUtils;
	}

	@GetMapping
	public List<ShSPhotoBean> getPhotos(){
		List<ShSPhotoBean> shSPhotoBeans = new ArrayList<>();
		shFolderRepository.findById("39cef32e-e754-4f1a-991c-c45a1ecebe7c")
				.ifPresent(shFolder -> shPostRepository.findByShFolderTiny(shFolder.getId()).forEach(
						shPostTinyBean -> shPostRepository.findById(shPostTinyBean.getId()).ifPresent(shPost -> {
							try {
								File fileImage = shStaticFileUtils.filePath(shPost);
								String imageURL = "http://localhost:2710" + shSitesPostUtils.generatePostLink(shPost);
								BufferedImage image = ImageIO.read(FileUtils.openInputStream(fileImage));
								int height = image.getHeight();
								int width = image.getWidth();

								int[] rgbArray = ColorThief.getColor(image);
								String rgb = String.format("rgb(%d,%d,%d)", rgbArray[0], rgbArray[1], rgbArray[2]);

								if (log.isDebugEnabled())
									log.debug("{} {}", imageURL, rgb);

								ShSPhotoBean shSPhotoBean = new ShSPhotoBean();

								shSPhotoBean.setName(shPost.getTitle());
								shSPhotoBean.setDate(shPost.getDate());
								shSPhotoBean.setPreviewXXS(new ShSPhotoPreviewBean(imageURL, width, height));
								shSPhotoBean.setPreviewXS(new ShSPhotoPreviewBean(imageURL, width, height));
								shSPhotoBean.setPreviewS(new ShSPhotoPreviewBean(imageURL, width, height));
								shSPhotoBean.setPreviewM(new ShSPhotoPreviewBean(imageURL, width, height));
								shSPhotoBean.setPreviewL(new ShSPhotoPreviewBean(imageURL, width, height));
								shSPhotoBean.setPreviewXL(new ShSPhotoPreviewBean(imageURL, width, height));
								shSPhotoBean.setRaw(new ShSPhotoPreviewBean(imageURL, width, height));
								shSPhotoBean.setDominantColor(rgb);
								shSPhotoBeans.add(shSPhotoBean);

							} catch (JSONException | IOException e) {
								log.error(e.getMessage(), e);
							}
						})));

		return shSPhotoBeans;
	}
}
