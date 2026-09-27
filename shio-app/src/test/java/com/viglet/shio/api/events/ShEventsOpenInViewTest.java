/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * SH1165 — the change stream is the one request open-in-view does not cover.
 *
 * <p>Asserted on the handler chain the dispatcher builds for each path, which is where the
 * exclusion either took or did not: a stream request whose chain carries the interceptor holds a
 * connection for as long as the stream is open, and ten of those were the whole default pool. The
 * control path proves the other half — that turning Boot's registration off did not take
 * open-in-view away from everything else, and that it is not registered twice.</p>
 */
@SpringBootTest
class ShEventsOpenInViewTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void sh1165_theStreamRequestHoldsNoEntityManager() throws Exception {
        assertThat(openInViewCount("/api/v2/events"))
                .as("SH1165: GET /api/v2/events must not run under open-in-view, or every open"
                        + " console tab holds a pooled connection for the stream's whole life")
                .isZero();
    }

    @Test
    void sh1165_everyOtherRequestKeepsOpenInViewExactlyOnce() throws Exception {
        assertThat(openInViewCount("/api/v2/site"))
                .as("SH1165: an ordinary console read must keep the open-in-view session it had")
                .isEqualTo(1);
    }

    private int openInViewCount(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        HandlerExecutionChain chain = handlerMapping.getHandler(request);
        assertThat(chain).as("no handler for %s", path).isNotNull();
        int count = 0;
        for (HandlerInterceptor interceptor : chain.getInterceptorList()) {
            if (interceptor instanceof WebRequestHandlerInterceptorAdapter adapter
                    && ReflectionTestUtils.getField(adapter, "requestInterceptor")
                            instanceof OpenEntityManagerInViewInterceptor) {
                count++;
            }
        }
        return count;
    }
}
