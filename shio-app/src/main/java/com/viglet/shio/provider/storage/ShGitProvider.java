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

package com.viglet.shio.provider.storage;

import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.viglet.shio.exchange.post.ShPostExchange;
import com.viglet.shio.exchange.post.ShPostExport;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.property.ShGitProperties;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.utils.ShStaticFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Component
public class ShGitProvider {
	static final Logger logger = LogManager.getLogger(ShGitProvider.class.getName());
	private static final String GIT_SOURCE_BASE = File.separator + "store" + File.separator + "git";
	private static final File USER_DIR = new File(System.getProperty("user.dir"));
	private Git git;

	@Autowired
	private ShPostUtils shPostUtils;

	@Autowired
	private ShStaticFileUtils shStaticFileUtils;

	@Autowired
	private ShPostExport shPostExport;

	@Autowired
	private ShGitProperties shGitProperties;

	public ShGitProvider() {
		super();
	}

	public void cloneRepository() {
		CloneCommand cloneCommand = null;
		File gitDirectory = new File(USER_DIR.getAbsolutePath().concat(GIT_SOURCE_BASE));
		if (!gitDirectory.exists()) {
			gitDirectory.mkdirs();

			cloneCommand = Git.cloneRepository();
			cloneCommand.setURI(shGitProperties.getUrl());
			cloneCommand.setCredentialsProvider(
					new UsernamePasswordCredentialsProvider(shGitProperties.getToken(), StringUtils.EMPTY));
			cloneCommand.setDirectory(gitDirectory);
			try {
				cloneCommand.call();
			} catch (GitAPIException e) {
				logger.error(e);
			}
		}
	}

	public void pushToRepo() throws JGitInternalException, GitAPIException {
		PushCommand pc = git.push();
		pc.setCredentialsProvider(
				new UsernamePasswordCredentialsProvider(shGitProperties.getToken(), StringUtils.EMPTY)).setForce(true)
				.setPushAll();
		try {
			Iterator<PushResult> it = pc.call().iterator();

			if (logger.isDebugEnabled() && it.hasNext())
				logger.debug(it.next().getMessages());
		} catch (InvalidRemoteException e) {
			logger.error(e);
		}
	}

	public void init() {
		File gitDirectory = new File(USER_DIR.getAbsolutePath().concat(GIT_SOURCE_BASE));
		logger.info("Opening a git repo at '{}'", GIT_SOURCE_BASE);
		try (Repository localRepo = new FileRepository(
				new File(gitDirectory.getAbsolutePath().concat(File.separator + ".git")))) {
			if (!localRepo.getDirectory().exists()) {
				logger.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
				localRepo.create();
			}
			git = new Git(localRepo);
		} catch (Exception e) {
			logger.error(e);
		}
	}

	public void move(String shObjectId) throws IOException {
		String shObjectFileName = "<<DEFINE>";
		String newShObjectFileName = "<<DEFINE>";
		git.rm().addFilepattern(shObjectFileName);
		git.add().addFilepattern(newShObjectFileName);
		try {
			git.commit()
					.setMessage(
							"Move shObject " + shObjectId + " from " + shObjectFileName + " to " + newShObjectFileName)
					.call();
		} catch (GitAPIException e) {
			throw new IOException(e);
		}
	}

	public void move(String folderPath, String newFolderPath) throws IOException {
		git.rm().addFilepattern(folderPath.substring(1));
		git.add().addFilepattern(newFolderPath.substring(1));
		try {
			git.commit().setMessage("Move folder " + folderPath + " to " + newFolderPath).call();
		} catch (GitAPIException e) {
			throw new IOException(e);
		}
	}

	public void newItem(String shObjectId) {
		ShPost shPost = shPostUtils.getShPostFromObjectId(shObjectId);
		File source = shStaticFileUtils.filePath(shPost);
		File gitSource = new File(git.getRepository().getDirectory().getParent());
		String objectGitDirectory = shObjectId.substring(0, 2) + File.separator + shObjectId.substring(2, 4)
				+ File.separator + shObjectId.substring(4, 6);

		File dest = new File(gitSource.getAbsolutePath().concat(File.separator + objectGitDirectory));
		if (!dest.exists()) {
			dest.mkdirs();
		}
		String staticFileRelative = objectGitDirectory.concat(File.separator + shObjectId);
		String jsonFileRelative = objectGitDirectory.concat(File.separator + shObjectId + ".json");

		File staticFile = new File(gitSource.getAbsolutePath().concat(File.separator + staticFileRelative));
		File jsonFile = new File(gitSource.getAbsolutePath().concat(File.separator + jsonFileRelative));

		try {
			FileUtils.copyFile(source, staticFile);
		} catch (IOException e) {
			logger.error(e);
		}

		ShPostExchange shPostExchange = shPostExport.exportShPostDraft(shPost);
		ObjectMapper mapper = new ObjectMapper();
		try {

			mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, shPostExchange);
		} catch (Exception mapperException) {
			logger.error("exportObject, MapperObject", mapperException);
		}
		try {
			git.add().addFilepattern(staticFileRelative).call();
			git.add().addFilepattern(jsonFileRelative).call();
			git.commit().setMessage("Added the Object " + source.getName() + " (" + shObjectId + ")").call();
			logger.info("Add new item '{}'", source.getAbsolutePath());
		} catch (GitAPIException e) {
			logger.error("Failed to add+commit {} to Git", source.getAbsolutePath(), e);
		}
	}

	public String checkpoint(String commitMessage) {
		String shObjectFileName = "store/file_source/Viglet/_static_files/css/viglet.css";
		try {
			List<DiffEntry> gitDiff = git.diff().call();
			boolean modified = gitDiff.parallelStream()
					.anyMatch(diffEntry -> diffEntry.getNewPath().equals(shObjectFileName));
			if (modified) {
				logger.debug("Changes found for pattern '{}': {}", shObjectFileName, gitDiff);
				DirCache added = git.add().addFilepattern(shObjectFileName).call();
				logger.debug("{} changes are about to be commited", added.getEntryCount());
				git.commit().setMessage(commitMessage).call();
			} else {
				logger.debug("No changes found {}", shObjectFileName);
			}
		} catch (GitAPIException e) {
			logger.error("Failed to add+commit {} to Git", shObjectFileName, e);
		}
		return null;
	}

	public synchronized String get(String revId) throws IOException {
		RevCommit stash = null;
		String shObjectFileName = "<<DEFINE>>";
		try {
			List<DiffEntry> gitDiff = git.diff().setPathFilter(PathFilter.create(shObjectFileName)).call();
			boolean modified = !gitDiff.isEmpty();
			if (modified) {
				stash = git.stashCreate().call();
				Collection<RevCommit> stashes = git.stashList().call();
				logger.debug("Created stash : {}, stash size : {}", stash, stashes.size());
			}
			ObjectId head = git.getRepository().resolve(Constants.HEAD);
			git.checkout().setStartPoint(revId).addPath(shObjectFileName).call();
			if (head != null) {
				git.checkout().setStartPoint(head.getName()).addPath(shObjectFileName).call();
			}
			if (modified && stash != null) {
				ObjectId applied = git.stashApply().setStashRef(stash.getName()).call();
				ObjectId dropped = git.stashDrop().setStashRef(0).call();
				Collection<RevCommit> stashes = git.stashList().call();
				logger.debug("Stash applied as : {}, and dropped : {}, stash size: {}", applied, dropped,
						stashes.size());
			}
		} catch (GitAPIException e) {
			logger.error("Failed to return shObject from revision \"{}\"", revId, e);
		}
		return null;
	}

	public List<String> revisionHistory() {
		List<String> history = Lists.newArrayList();
		String shObjectFileName = "<<DEFINE>>";
		logger.debug("Listing history for {}:", shObjectFileName);
		try {
			Iterable<RevCommit> logs = git.log().addPath(shObjectFileName).call();
			for (RevCommit log : logs) {
				history.add("History Item");
				logger.debug(" - ({},{},{})", log.getName(), log.getCommitTime(), log.getFullMessage());
			}
		} catch (NoHeadException e) {
			logger.warn("No Head found for {}, {}", shObjectFileName, e.getMessage());
		} catch (GitAPIException e) {
			logger.error("Failed to get logs for {}", shObjectFileName, e);
		}
		return history;
	}

	public String setShObjectRevision(String revId) throws IOException {
		String revisionShObject = get(revId);
		if (revisionShObject != null) {
			// Save
		}
		return revisionShObject;
	}

	public void close() {
		git.getRepository().close();
	}

	protected Git getGit() {
		return git;
	}

	void setGit(Git git) {
		this.git = git;
	}

}
