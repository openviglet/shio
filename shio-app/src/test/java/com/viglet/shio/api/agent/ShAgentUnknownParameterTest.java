/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;

/**
 * SH430 — <b>{@code POST /agent/batch?dryRun=true} wrote for real, and said {@code "dryRun": false}
 * while doing it.</b>
 *
 * <h2>The run this was written from</h2>
 *
 * <p>One call created a site, two folders and three posts. The response reported six changes and
 * {@code dryRun} off; nothing refused, nothing warned. {@code ids}, {@code verify}, {@code digest}
 * and {@code format} are query parameters on that endpoint and {@code dryRun} is a body field, so
 * the spelling that looks exactly like the four flags that <em>are</em> query parameters is the one
 * Spring drops on the floor — and the caller learns it from the write.</p>
 *
 * <p>P7 is the law the failure breaks. A dry run that quietly commits is worse than no dry run,
 * because the caller has been told the opposite of what happened and will trust the next one; and
 * it is the flag most likely to be sent by someone who has not read the reference, which is who a
 * rehearsal is for.</p>
 *
 * <h2>Why the last test is the important one</h2>
 *
 * <p>The first four assert the three sentences a caller can get. The last one asserts the
 * <em>instrument</em>: every mutating handler on this surface, derived from the controller rather
 * than listed, refuses a parameter it does not declare. SH338's rule is that when a law is found
 * broken, the thing that finds every other break lands before the fixes — a per-endpoint check
 * would have covered the two endpoints this task names and nothing about the next one somebody
 * adds.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentUnknownParameterTest {

    private static final String BATCH = "/api/v2/agent/batch";
    private static final String APPLY = "/api/v2/agent/apply";

    /** One op against a site that does not exist: enough to bind, never enough to write. */
    private static final String ONE_OP = """
            {"ops":[{"op":"post.publish","address":"post:no-such-site/no-such-page"}]}""";

    private static final String DOCUMENT = """
            {"site":"no-such-site","folders":[],"posts":[]}""";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShAgentAPI shAgentAPI;

    private MockMvc mockMvc;
    private Principal principal;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn("admin");
    }

    @Test
    @DisplayName("SH430: POST /agent/batch?dryRun=true is a 400 naming the body field, not a write")
    void aQueryStringDryRunIsRefused() throws Exception {
        String body = refusal(post(BATCH).queryParam("dryRun", "true"), ONE_OP);

        assertThat(body)
                .as("""
                        the refusal has to say where dryRun belongs, because the caller's next \
                        move is to send it correctly — and it has to say why silence was not an \
                        option: an ignored parameter means the request did something other than \
                        what it says.""")
                .contains("errors/" + ShAgentQueryParams.UnknownParameterException.SLUG)
                .contains("body field")
                .contains("dryRun");
        assertThat(body)
                .as("the four flags that are query parameters here are the allow-list a caller "
                        + "reads next")
                .contains("digest", "format", "ids", "verify");
    }

    @Test
    @DisplayName("SH430: the four declared flags still bind — the refusal is not a tightening")
    void theDeclaredFlagsStillBind() throws Exception {
        MvcResult result = mockMvc.perform(post(BATCH)
                        .queryParam("ids", "true").queryParam("verify", "true")
                        .queryParam("digest", "5").queryParam("format", "terse")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(ONE_OP))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .as("""
                        every parameter here is declared, so whatever this call answers it must \
                        not be the unknown-parameter refusal. The outcome itself is deliberately \
                        not asserted — ShMinimalRequestBodyTest's reasoning: pinning a business \
                        result here would break this class every time an unrelated rule changed.""")
                .doesNotContain(ShAgentQueryParams.UnknownParameterException.SLUG);
    }

    @Test
    @DisplayName("SH430: a mistyped flag is refused with the nearest declared spelling")
    void aMistypedFlagNamesTheNearest() throws Exception {
        String body = refusal(post(BATCH).queryParam("verif", "true"), ONE_OP);

        assertThat(body)
                .as("SH80's didYouMean: `verif` is one edit from a parameter that bounds a whole "
                        + "lint pass, and SH320 already refuses `verify=yes` for the same reason")
                .contains("\"didYouMean\":\"verify\"");
    }

    @Test
    @DisplayName("SH430: /agent/apply has no dryRun at all, so the refusal names /agent/plan")
    void applyIsPointedAtTheRehearsal() throws Exception {
        String body = refusal(post(APPLY).queryParam("dryRun", "true"), DOCUMENT);

        assertThat(body)
                .as("""
                        a desired-state document rehearses by going somewhere else (SH82), which \
                        no allow-list of parameter names can say — and "not a parameter here" \
                        would leave the caller with a rehearsal it cannot find.""")
                .contains("/api/v2/agent/plan");
    }

    @Test
    @DisplayName("SH430: a read is deliberately out of scope — an ignored parameter there costs a section")
    void readsAreNotTightened() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v2/agent/manifest")
                        .queryParam("nope", "1").principal(principal))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("""
                        SH320 decided this for `include`: on a read a typo costs the caller a \
                        section it asked for and its own code notices. Tightening reads would \
                        also refuse the cache-busting and tracing parameters a browser or proxy \
                        adds for reasons that have nothing to do with this contract.""")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("SH430: every mutating handler on the agent surface refuses what it does not declare")
    void everyMutatingHandlerIsCovered() {
        ShAgentQueryParams interceptor = new ShAgentQueryParams();
        List<String> covered = new ArrayList<>();
        List<String> uncovered = new ArrayList<>();

        for (Method method : mutatingHandlers()) {
            String verb = verbOf(method);
            MockHttpServletRequest request = new MockHttpServletRequest(verb, "/api/v2/agent/probe");
            request.setQueryString("shio430NotAParameter=1");
            Throwable failure = catchThrowable(() -> interceptor.preHandle(request,
                    new MockHttpServletResponse(), new HandlerMethod(shAgentAPI, method)));
            if (failure instanceof ShAgentQueryParams.UnknownParameterException) {
                covered.add(verb + " " + method.getName());
            } else {
                uncovered.add(verb + " " + method.getName() + " → " + failure);
            }
        }

        System.out.printf("%n[SH430] %d mutating agent handlers, all refusing an undeclared "
                + "parameter: %s%n%n", covered.size(), new TreeSet<>(covered));

        assertThat(covered)
                .as("derived from the controller's own mapping annotations, never listed — an "
                        + "empty set would make this guard report zero forever, which is the "
                        + "failure every lint here is written against")
                .hasSizeGreaterThanOrEqualTo(5);
        assertThat(uncovered)
                .as("""
                        a mutating endpoint that ignores an unrecognised query parameter does \
                        something other than what the caller asked for, and the caller finds out \
                        from the write. That is the SH430 defect; this is the list that must stay \
                        empty as the surface grows.""")
                .isEmpty();
    }

    /** Every {@code POST}/{@code PUT}/{@code PATCH}/{@code DELETE} handler on {@link ShAgentAPI}. */
    private static List<Method> mutatingHandlers() {
        return Arrays.stream(ShAgentAPI.class.getDeclaredMethods())
                .filter(method -> !verbOf(method).isEmpty())
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .toList();
    }

    /** The handler's mutating verb, or {@code ""} when it is a read or not a handler at all. */
    private static String verbOf(Method method) {
        RequestMapping mapping =
                AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return "";
        }
        for (RequestMethod verb : mapping.method()) {
            if (verb != RequestMethod.GET && verb != RequestMethod.HEAD
                    && verb != RequestMethod.OPTIONS) {
                return verb.name();
            }
        }
        return "";
    }

    /** Performs the call, asserts it was refused with a 400, and returns the problem body. */
    private String refusal(MockHttpServletRequestBuilder request, String body) throws Exception {
        MvcResult result = mockMvc.perform(request.principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("the handler never ran, which is the only way a rehearsal that was going to "
                        + "write can be stopped")
                .isEqualTo(400);
        return result.getResponse().getContentAsString();
    }
}
