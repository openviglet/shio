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
package com.viglet.shio.provider.exchange.blogger;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.viglet.shio.exchange.ShCloneExchange;
import com.viglet.shio.exchange.ShExchange;
import com.viglet.shio.exchange.ShExchangeData;
import com.viglet.shio.exchange.ShExchangeFilesDirs;
import com.viglet.shio.exchange.folder.ShFolderExchange;
import com.viglet.shio.exchange.post.ShPostExchange;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.plugin.ShImporterPlugin;
import com.viglet.shio.post.type.ShArticlePostType;
import com.viglet.shio.url.ShURLFormatter;
import com.viglet.shio.utils.ShUserUtils;

/**
 * Blogger Importer
 * 
 * @author Alexandre Oliveira
 * @since 0.3.4
 * 
 */

@Component
public class ShExchangeBloggerImport {
	private static final Logger logger = LogManager.getLogger(ShExchangeBloggerImport.class);
	@Autowired
	private ShUserUtils shUserUtils;
	@Autowired
	private ShCloneExchange shCloneExchange;

	private static final String HOME_NAME = "Home";

	private static final String SCHEMA_POST = "http://schemas.google.com/blogger/2008/kind#post";

	@Value("${shio.plugin.blogger:}")
	private String customClass;
	private boolean hasPlugin = false;

	public ShExchange shImportFromBlogger(MultipartFile multipartFile) {
		ShImporterPlugin shImporterPlugin = null;
		if (!StringUtils.isEmpty(customClass)) {

			try {
				shImporterPlugin = (ShImporterPlugin) Class.forName(customClass).getDeclaredConstructor().newInstance();
				hasPlugin = true;
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				logger.error(e.getMessage(), e);
			}
		}
		ShExchangeFilesDirs shExchangeFilesDirs = new ShExchangeFilesDirs();
		if (shExchangeFilesDirs.generate()) {

			try {
				multipartFile.transferTo(shExchangeFilesDirs.getExportJsonFile());

				SyndFeed feed = new SyndFeedInput().build(new XmlReader(shExchangeFilesDirs.getExportJsonFile()));
				ShExchangeData shExchangeData = createExchangeData(feed);

				String folderHomeId = null;

				for (ShFolderExchange shFolderExchange : shExchangeData.getShExchange().getFolders()) {
					if (shFolderExchange.getName().equals(HOME_NAME)) {
						folderHomeId = shFolderExchange.getId();
					}
				}

				List<SyndEntry> entries = feed.getEntries();

				List<ShPostExchange> posts = createPosts(shImporterPlugin, shExchangeData, folderHomeId, entries);

				shExchangeData.getShExchange().setPosts(posts);
				shCloneExchange.importFromShExchangeData(shExchangeData);

				shExchangeData.getShExchangeFilesDirs().deleteExport();
				return shExchangeData.getShExchange();
			} catch (IOException | IllegalArgumentException | FeedException | IllegalStateException e) {
				logger.error(e.getMessage(), e);
			}
		}
		return new ShExchange();
	}

	private ShExchangeData createExchangeData(SyndFeed feed) {
		ShSite shSite = new ShSite();
		shSite.setName(feed.getTitle().trim());
		shSite.setDate(feed.getPublishedDate());
		shSite.setOwner(shUserUtils.getCurrentUsername());
		shSite.setFurl(ShURLFormatter.format(shSite.getName()));

		return shCloneExchange.getDefaultTemplateToSite(shSite);
	}

	private List<ShPostExchange> createPosts(ShImporterPlugin shImporterPlugin, ShExchangeData shExchangeData,
			String folderHomeId, List<SyndEntry> entries) {
		List<ShPostExchange> posts = shExchangeData.getShExchange().getPosts();
		for (SyndEntry syndEntry : entries) {
			for (SyndCategory category : syndEntry.getCategories()) {
				createPost(shImporterPlugin, folderHomeId, posts, syndEntry, category);
			}
		}
		return posts;
	}

	private void createPost(ShImporterPlugin shImporterPlugin, String folderHomeId, List<ShPostExchange> posts,
			SyndEntry syndEntry, SyndCategory category) {
		if (category.getName().equals(SCHEMA_POST)) {
			ShArticlePostType article = new ShArticlePostType();
			article.setId(UUID.randomUUID().toString());
			article.setOwner(syndEntry.getAuthor().trim());
			article.setDate(syndEntry.getPublishedDate());
			article.setFurl(syndEntry.getTitle().trim());
			article.setFolder(folderHomeId);
			article.setTitle(syndEntry.getTitle().trim());
			if (syndEntry.getDescription() != null) {
				article.setDescription(syndEntry.getDescription().getValue().trim());
			}
			syndEntry.getContents().forEach(content -> article.setText(content.getValue().trim()));
			if (hasPlugin) {
				posts.add(shImporterPlugin.process(article.getShPostExchange()));
			} else {
				posts.add(article.getShPostExchange());
			}
		}
	}
}
