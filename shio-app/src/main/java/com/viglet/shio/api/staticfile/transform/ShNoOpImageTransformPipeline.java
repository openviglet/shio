/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * The SH39 placeholder pipeline: it recognises that a transform was requested,
 * logs it, and returns {@link Optional#empty()} so the delivery path serves the
 * original bytes unchanged. This keeps the interception layer fully wired and
 * testable before the resize/crop engine exists.
 *
 * <p><strong>SH40</strong> supplies the real {@link ShImageTransformPipeline}
 * (resize/crop via imageio/Thumbnailator). When it does, annotate that
 * implementation {@code @Primary} (or remove this bean) so it wins injection.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Slf4j
public class ShNoOpImageTransformPipeline implements ShImageTransformPipeline {

    @Override
    public Optional<ShImageTransformResult> transform(Resource source, String sourceContentType,
                                                      ShImageTransformSpec spec) {
        log.debug("Image transform requested ({}) but no transform pipeline is implemented yet (SH40); "
                + "serving the original bytes", spec);
        return Optional.empty();
    }
}
