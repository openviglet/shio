/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;

/**
 * @author Alexandre Oliveira
 */
@Component
public class ShFolderUtils {
	@Autowired
	private ShFolderRepository shFolderRepository;
	private static final String FILE_SOURCE_BASE = File.separator + "store" + File.separator + "file_source";
	private static final String USER_DIR = "user.dir";
	private File userDir = new File(System.getProperty(USER_DIR));

	public ShFolder getParentFolder(String shFolderId) {
		Optional<ShFolder> shFolder = shFolderRepository.findById(shFolderId);
		return shFolder.isPresent() ? shFolder.get().getParentFolder() : null;
	}

	public File filePath(ShFolder shFolder, String fileName) {
		File file = null;
		File directoryPath = dirPath(shFolder);

		if (directoryPath != null)
			file = new File(directoryPath.getAbsolutePath().concat(File.separator + fileName));
		return file;
	}

	public ShFolder getParentFolder(ShObjectImpl shObject) {
		if (shObject instanceof ShPostImpl shPostImpl) {
			return shPostImpl.getShFolder();
		} else if (shObject instanceof ShFolder shFolder) {
			return shFolder.getParentFolder();
		}
		return null;
	}

	public List<ShFolder> breadcrumb(ShFolder shFolder) {
		if (shFolder != null) {
			boolean rootFolder = false;
			List<ShFolder> folderBreadcrumb = new ArrayList<>();
			folderBreadcrumb.add(shFolder);
			ShFolder parentFolder = shFolder.getParentFolder();
			while (parentFolder != null && !rootFolder) {
				folderBreadcrumb.add(parentFolder);
				if (isRootFolder(parentFolder)) {
					rootFolder = true;
				} else {
					parentFolder = parentFolder.getParentFolder();
				}
			}

			Collections.reverse(folderBreadcrumb);
			return folderBreadcrumb;
		} else {
			return Collections.emptyList();
		}
	}

	public String folderPath(ShFolder shFolder, boolean usingFurl, boolean addHomeFolder) {
		return this.folderPath(shFolder, "/", usingFurl, addHomeFolder);
	}

	public String folderPath(ShFolder shFolder, String separator, boolean usingFurl, boolean addHomeFolder) {
		if (shFolder != null) {
			boolean rootFolder = false;
			List<String> pathContexts = new ArrayList<>();
			this.getFolderNameFromPath(shFolder, usingFurl, addHomeFolder, pathContexts);
			this.getParentPath(usingFurl, rootFolder, pathContexts, shFolder.getParentFolder());
			return this.pathBuilder(separator, pathContexts);
		} else {
			return separator;
		}

	}

	private void getParentPath(boolean usingFurl, boolean rootFolder, List<String> pathContexts,
			ShFolder parentFolder) {
		while (parentFolder != null && !rootFolder) {
			if (isRootFolder(parentFolder)) {
				rootFolder = true;
				if (!parentFolder.getName().equalsIgnoreCase("home"))
					this.addFolderToPath(usingFurl, pathContexts, parentFolder);

			} else {
				this.addFolderToPath(usingFurl, pathContexts, parentFolder);
				parentFolder = parentFolder.getParentFolder();
			}
		}
	}

	private void addFolderToPath(boolean usingFurl, List<String> pathContexts, ShFolder parentFolder) {
		if (usingFurl)
			pathContexts.add(parentFolder.getFurl());
		else
			pathContexts.add(parentFolder.getName());
	}

	private boolean isRootFolder(ShFolder parentFolder) {
		return (parentFolder.getRootFolder() == (byte) 1) || (parentFolder.getParentFolder() == null);
	}

	private void getFolderNameFromPath(ShFolder shFolder, boolean usingFurl, boolean addHomeFolder,
			List<String> pathContexts) {
		if (!(shFolder.getFurl().equals("home") && shFolder.getRootFolder() == (byte) 1 && !addHomeFolder)) {
			addFolderToPath(usingFurl, pathContexts, shFolder);

		}
	}

	private String pathBuilder(String separator, List<String> pathContexts) {
		StringBuilder path = new StringBuilder();

		pathContexts.forEach(context -> path.insert(0, context + separator));
		path.insert(0, separator);
		return path.toString();
	}

	public String directoryPath(ShFolder shFolder, String separator) {
		if (shFolder != null) {
			boolean rootFolder = false;
			List<String> pathContexts = new ArrayList<>();
			pathContexts.add(shFolder.getName());
			ShFolder parentFolder = shFolder.getParentFolder();
			while (parentFolder != null && !rootFolder) {
				pathContexts.add(parentFolder.getName());
				if (isRootFolder(parentFolder)) {
					rootFolder = true;
				} else {
					parentFolder = parentFolder.getParentFolder();
				}
			}

			return pathBuilder(separator, pathContexts);
		} else {
			return separator;
		}

	}


	public File dirPath(ShFolder shFolder) {
		File directoryPath = null;
		ShSite shSite = getSite(shFolder);
		String folderPath = directoryPath(shFolder, File.separator);
		String folderPathFile = FILE_SOURCE_BASE.concat(File.separator + shSite.getName() + folderPath);
		if (userDir.exists() && userDir.isDirectory())
			directoryPath = new File(userDir.getAbsolutePath().concat(folderPathFile));
		return directoryPath;
	}
	public ShSite getSite(ShFolder shFolder) {
		ShSite shSite = null;
		if (shFolder != null) {
			boolean rootFolder = false;
			if (isRootFolder(shFolder)) {
				shSite = shFolder.getShSite();
			} else {
				ShFolder parentFolder = shFolder.getParentFolder();
				while (parentFolder != null && !rootFolder) {
					if (isRootFolder(parentFolder)) {
						rootFolder = true;
						shSite = parentFolder.getShSite();
					} else {
						parentFolder = parentFolder.getParentFolder();
					}
				}
			}
		}
		return shSite;
	}

	public ShFolder folderFromPath(ShSite shSite, String folderPath) {
		return this.folderFromPath(shSite, folderPath, "/");
	}

	public ShFolder folderFromPath(ShSite shSite, String folderPath, String separator) {
		ShFolder currentFolder = null;
		String[] contexts = folderPath.split(separator);
		if (contexts.length == 0) {
			// Root Folder (Home)
			currentFolder = shFolderRepository.findByShSiteAndFurl(shSite, "home");
		} else {
			for (int i = 1; i < contexts.length; i++) {
				if (i == 1) {
					// When is null folder, because is rootFolder and it contains shSite attribute
					currentFolder = shFolderRepository.findByShSiteAndFurl(shSite, contexts[i]);
					if (currentFolder == null) {

						// Is not Root Folder, will try use the Home Folder
						currentFolder = shFolderRepository.findByShSiteAndFurl(shSite, "home");
						// Now will try access the first Folder non Root
						currentFolder = shFolderRepository.findByParentFolderAndFurl(currentFolder, contexts[i]);
					}
				} else {
					currentFolder = shFolderRepository.findByParentFolderAndFurl(currentFolder, contexts[i]);
				}

			}
		}
		return currentFolder;
	}

	public ShFolder copy(ShFolder shFolder, ShObjectImpl shObjectDest) {
		ShFolder shFolderCopy = new ShFolder();
		if (shObjectDest instanceof ShFolder shFolderDest) {
			shFolderCopy.setParentFolder(shFolderDest);
			shFolderCopy.setShSite(null);
			shFolderCopy.setRootFolder((byte) 0);
		} else if (shObjectDest instanceof ShSite shSiteDest) {
			shFolderCopy.setParentFolder(null);
			shFolderCopy.setShSite(shSiteDest);
			shFolderCopy.setRootFolder((byte) 1);
		} else {
			return null;
		}
		shFolderCopy.setDate(new Date());
		shFolderCopy.setName(shFolder.getName());
		shFolderCopy.setFurl(shFolder.getFurl());
		shFolderRepository.save(shFolderCopy);

		return shFolderCopy;
	}

}
