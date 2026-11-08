/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.web.multipart.MultipartFile;

/**
 * A finished upload session's bytes, as the {@link MultipartFile} {@code upload} already takes
 * (SH1355).
 *
 * <p>An adapter rather than a second write path: the upload policy, the quota, the File post and
 * the digest stamp all stay in {@link ShStaticFileService#upload}, so a file that arrived in chunks
 * becomes exactly the asset it would have become in one request. The stream is read from disk, so a
 * video is never held in heap.</p>
 */
final class ShStagedUpload implements MultipartFile {

    private final Path data;
    private final String fileName;
    private final String contentType;

    ShStagedUpload(Path data, String fileName, String contentType) {
        this.data = data;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return fileName;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
    }

    @Override
    public long getSize() {
        try {
            return Files.size(data);
        } catch (IOException e) {
            // The session's own file, read a moment ago by finish's digest: unreadable now is a
            // failure, not an empty file, and an empty asset must not stand in for it.
            throw new java.io.UncheckedIOException("Upload session bytes vanished: " + data, e);
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(data);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(data);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.copy(data, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
