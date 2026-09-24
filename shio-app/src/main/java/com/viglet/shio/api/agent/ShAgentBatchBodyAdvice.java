/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import com.viglet.shio.api.agent.dto.ShAgentBatch;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1083 — reads a batch body as it arrived and refuses an op field binding would drop.
 *
 * <p>An advice rather than a change to the handler: {@code POST /api/v2/agent/batch} keeps binding
 * {@link ShAgentBatch}, so SH430's interceptor still reads its body fields off the parameter type
 * and a body that does not parse is still Spring's own {@code unreadable-body}. Scoped to that one
 * target type, because other request bodies may rely on lenient binding and this should not decide
 * that for them. The bytes are read once and handed on unchanged.</p>
 */
@ControllerAdvice
public class ShAgentBatchBodyAdvice extends RequestBodyAdviceAdapter {

    private final ObjectMapper objectMapper;

    public ShAgentBatchBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return ShAgentBatch.class.equals(targetType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        byte[] bytes = inputMessage.getBody().readAllBytes();
        JsonNode body;
        try {
            body = objectMapper.readTree(bytes);
        } catch (JacksonException malformed) {
            // The exception the converter would have raised on the same bytes, so the caller gets
            // the unreadable-body answer it got before SH1083.
            throw new HttpMessageNotReadableException(malformed.getOriginalMessage(), malformed,
                    inputMessage);
        }
        if (body != null) {
            ShAgentOpFields.check(body.get("ops"));
        }
        HttpHeaders headers = inputMessage.getHeaders();
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}
