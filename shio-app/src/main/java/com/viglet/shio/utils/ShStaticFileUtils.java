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
package com.viglet.shio.utils;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeAttrRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShStaticFileUtils {
	private static final Log logger = LogFactory.getLog(ShStaticFileUtils.class);
	@Autowired
	private ShFolderUtils shFolderUtils;
	@Autowired
	private ShPostTypeRepository shPostTypeRepository;
	@Autowired
	private ShPostRepository shPostRepository;
	@Autowired
	private ShPostTypeAttrRepository shPostTypeAttrRepository;
	@Autowired
	private ShPostAttrRepository shPostAttrRepository;
	@Autowired
	private ShHistoryUtils shHistoryUtils;

	private static final String FILE_SOURCE_BASE = File.separator + "store" + File.separator + "file_source";
	private static final String TEMP_DIR = File.separator + "store" + File.separator + "tmp";
	private static final String FILE_SOURCE_BASE_AS_LINK = "/store/file_source";
	private static final String USER_DIR = "user.dir";
	private File userDir = new File(System.getProperty(USER_DIR));


	public boolean fileExists(ShFolder shFolder, String fileName) {
		return shPostRepository.existsByShFolderAndTitle(shFolder, fileName);
	}

	public File filePath(ShPostImpl shPost) {
		File file = null;
		if (shPost.getShPostType().getName().equals(ShSystemPostType.FILE)) {
			File directoryPath = shFolderUtils.dirPath(shPost.getShFolder());

			if (directoryPath != null)
				file = new File(directoryPath.getAbsolutePath().concat(File.separator + shPost.getTitle()));
		}
		return file;
	}

	public File filePath(String url) {
		File file = null;
		String fileRelativePath = url.replace("/", File.separator);

		if (userDir.exists() && userDir.isDirectory()) {
			String fileSource = userDir.getAbsolutePath().concat(FILE_SOURCE_BASE);
			file = new File(fileSource.concat(fileRelativePath));
		}
		return file;
	}

	public File getFileSource() {
		File file = null;

		if (userDir.exists() && userDir.isDirectory()) {
			String fileSource = userDir.getAbsolutePath().concat(FILE_SOURCE_BASE);
			file = new File(fileSource);
		}
		return file;
	}

	public File getTmpDir() {
		File file = null;

		if (userDir.exists() && userDir.isDirectory()) {
			String fileSource = userDir.getAbsolutePath().concat(TEMP_DIR);
			file = new File(fileSource);
		}
		return file;

	}
	
	public String getFileSourceBase(boolean isLink) {
		return isLink ? FILE_SOURCE_BASE_AS_LINK : FILE_SOURCE_BASE;
	}

	public ShPost createFilePost(MultipartFile file, String fileName, ShFolder shFolder, Principal principal,
			boolean createPost) {
		File directoryPath = shFolderUtils.dirPath(shFolder);
		ShPost shPost = new ShPost();
		if (directoryPath != null) {
			if (!directoryPath.exists())
				directoryPath.mkdirs();

			try {

				String destFile = directoryPath.getAbsolutePath().concat("/" + fileName);

				file.transferTo(new File(destFile));

				if (createPost) {
					// Post File
					ShPostType shPostType = shPostTypeRepository.findByName(ShSystemPostType.FILE);

					shPost.setDate(new Date());
					shPost.setShPostType(shPostType);
					shPost.setSummary(null);
					shPost.setTitle(fileName);
					shPost.setShFolder(shFolder);
					shPost.setPublished(true);
					shPostRepository.save(shPost);

					ShPostTypeAttr shPostTypeAttr = shPostTypeAttrRepository.findByShPostTypeAndName(shPostType,
							ShSystemPostTypeAttr.FILE);

					ShPostAttr shPostAttr = new ShPostAttr();
					shPostAttr.setShPost(shPost);
					shPostAttr.setShPostTypeAttr(shPostTypeAttr);
					shPostAttr.setStrValue(shPost.getTitle());
					shPostAttr.setType(1);

					shPostAttrRepository.save(shPostAttr);

					shHistoryUtils.commit(shPost, principal, ShHistoryUtils.CREATE);
				} else {
					shPost.setTitle(fileName);
				}
			} catch (IOException e) {
				logger.error("shStaticFileUploadException", e);
			}
		}
		return shPost;
	}
}
