/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.lang.reflect.RecordComponent;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SH430 — <b>an unrecognised query parameter on a mutating agent endpoint is a teaching 400,
 * not silence.</b>
 *
 * <h2>The defect this was written from</h2>
 *
 * <p>{@code POST /api/v2/agent/batch?dryRun=true} created a site, two folders and three posts.
 * The response said {@code "dryRun": false} and {@code "changed": 6}. Nothing refused and nothing
 * warned; the write was reported as a write, which is the only tell and arrives after the fact.</p>
 *
 * <p>{@code ids}, {@code verify}, {@code digest} and {@code format} are {@code @RequestParam} on
 * that same endpoint; {@code dryRun} is a field on the body. Spring ignores an unrecognised query
 * parameter, so the spelling that looks exactly like the four flags that <em>are</em> query
 * parameters is the one that silently disarms the rehearsal. P7 is the law — draft-default,
 * dry-run, explicit publish — and a dry run that quietly commits is worse than no dry run,
 * because the caller has been told the opposite of what happened and will trust the next one.</p>
 *
 * <h2>Why a refusal and not a second spelling</h2>
 *
 * <p>Accepting {@code ?dryRun=} as a synonym would make the convention ambiguous instead of
 * merely undiscoverable, and SH365 is the precedent: one flag, one meaning. The refusal teaches
 * the body field instead (P6), which is the answer the caller actually needs.</p>
 *
 * <h2>Why an interceptor, and why the allow-list is derived</h2>
 *
 * <p>SH338's rule: when a review finds a law is being broken, the instrument that finds every
 * <em>other</em> break lands before the fixes. A per-endpoint check would have covered
 * {@code /batch} and {@code /apply} — the two the review named — and said nothing about the next
 * mutating endpoint somebody adds. So the allowed set is read off the handler itself: every
 * {@code @RequestParam} it declares, and nothing else. A new parameter is covered the moment it
 * is declared, and a new endpoint is covered before it is written.</p>
 *
 * <h2>The declared scope, and the two things deliberately outside it</h2>
 *
 * <p><b>Mutating</b> handlers only ({@code POST}/{@code PUT}/{@code PATCH}/{@code DELETE}). On a
 * read, an ignored parameter costs the caller a section it asked for and its own code notices —
 * SH320 decided exactly that for {@code ?include=}, and tightening reads here would refuse the
 * cache-busting and tracing parameters browsers and proxies add to a {@code GET} for reasons that
 * have nothing to do with this contract.</p>
 *
 * <p>{@code POST /mcp} is outside too, and not by omission: a malformed MCP message must come back
 * as JSON-RPC, so answering an MCP client with an HTTP problem body would hand it an error it has
 * no handler for ({@code ShMcpAPI}'s own reasoning for taking its body as a {@code String}).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShAgentQueryParams implements HandlerInterceptor {

    /** Where the interceptor is mounted — the agent surface, whose contract this enforces. */
    public static final String PATH_PATTERN = "/api/v2/agent/**";

    /**
     * P7's name for a rehearsal, and the reason this class exists.
     *
     * <p>Named explicitly because it is the one parameter whose silent ignore is a <b>write</b>,
     * and because a caller sending it to an endpoint that has no such body field is asking for
     * an <em>endpoint</em> rather than a spelling.</p>
     */
    private static final String DRY_RUN = "dryRun";

    /**
     * Where a document's rehearsal lives, for the write whose {@code dryRun} is not a body field
     * at all.
     *
     * <p>One entry, and it stays one: every other write on this surface carries {@code dryRun} in
     * its own body, which the derivation above already names. A desired-state document is the
     * exception — its rehearsal is the same body sent to a <em>different</em> endpoint (SH82), so
     * no allow-list of parameter names can answer the caller and the pair has to be stated. Keyed
     * by the body type rather than by path, so a moved endpoint cannot leave this pointing at
     * nothing.</p>
     */
    private static final Map<Class<?>, String> REHEARSAL =
            Map.of(ShAgentDesiredState.class, "POST /api/v2/agent/plan");

    /**
     * Spring Security reads {@code _csrf} as a request parameter when a caller cannot set the
     * header. It is the framework's, not this contract's, so refusing it would turn a working
     * CSRF form post into a 400 about a parameter the endpoint never declared.
     */
    private static final Set<String> FRAMEWORK = Set.of("_csrf");

    private static final Set<HttpMethod> MUTATING =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !MUTATING.contains(HttpMethod.valueOf(request.getMethod()))) {
            return true;
        }
        Set<String> sent = names(request.getQueryString());
        if (sent.isEmpty()) {
            return true;
        }
        Declared declared = declared(handlerMethod);
        if (declared.lenient()) {
            return true;
        }
        for (String name : sent) {
            if (!declared.query().contains(name) && !FRAMEWORK.contains(name)) {
                throw new UnknownParameterException(name, request.getMethod(),
                        request.getRequestURI(), declared);
            }
        }
        return true;
    }

    /**
     * The parameter <em>names</em> in the query string, in the order sent.
     *
     * <p>Read from {@link HttpServletRequest#getQueryString()} rather than
     * {@code getParameterMap()} on purpose: on a form-encoded request the parameter map is
     * populated by <b>consuming the body</b>, which would leave the handler's
     * {@code @RequestBody} empty. The query string is also the only half of that map this rule
     * is about.</p>
     */
    static Set<String> names(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String pair : queryString.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String raw = equals < 0 ? pair : pair.substring(0, equals);
            names.add(URLDecoder.decode(raw, StandardCharsets.UTF_8));
        }
        return names;
    }

    /** What this handler declares: its query parameters, and the field names of its body. */
    static Declared declared(HandlerMethod handlerMethod) {
        Set<String> query = new LinkedHashSet<>();
        Set<String> body = new LinkedHashSet<>();
        Class<?> bodyType = null;
        boolean lenient = false;
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
            if (requestParam != null) {
                // A Map-typed @RequestParam binds *every* parameter by design, so the handler
                // has already said it accepts anything and this rule has nothing to enforce.
                if (Map.class.isAssignableFrom(parameter.getParameterType())) {
                    lenient = true;
                    continue;
                }
                query.add(nameOf(requestParam, parameter));
                continue;
            }
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                bodyType = parameter.getParameterType();
                body.addAll(fields(bodyType));
            }
        }
        return new Declared(query, body, lenient, bodyType);
    }

    private static String nameOf(RequestParam annotation, MethodParameter parameter) {
        if (!annotation.name().isBlank()) {
            return annotation.name();
        }
        if (!annotation.value().isBlank()) {
            return annotation.value();
        }
        String declared = parameter.getParameterName();
        return declared != null ? declared : "";
    }

    /**
     * The body's field names — record components, which is what every request body on this
     * surface is (SH81's {@code ShAgentBatch}, SH82's {@code ShAgentDesiredState}, SH93's
     * {@code ShBlueprintRequest}). Derived rather than listed, for the SH338 reason above.
     */
    private static Set<String> fields(Class<?> type) {
        RecordComponent[] components = type == null ? null : type.getRecordComponents();
        if (components == null) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (RecordComponent component : components) {
            names.add(component.getName());
        }
        return names;
    }

    /**
     * What a handler accepts.
     *
     * @param query    the {@code @RequestParam} names it declares — the closed set
     * @param body     its request body's field names, so a refusal can name where the parameter
     *                 actually belongs
     * @param lenient  the handler binds every parameter itself, so there is nothing to enforce
     * @param bodyType the body's type, which is what {@link #REHEARSAL} is keyed by
     */
    record Declared(Set<String> query, Set<String> body, boolean lenient, Class<?> bodyType) {

        /** The rehearsal endpoint for this body, when the parameter sent was {@code dryRun}. */
        Optional<String> rehearsal(String sent) {
            return DRY_RUN.equalsIgnoreCase(sent) && !body.contains(DRY_RUN)
                    ? Optional.ofNullable(REHEARSAL.get(bodyType))
                    : Optional.empty();
        }
    }

    /**
     * A 400 that says where the parameter belongs, or that it does not exist (SH80/P6).
     *
     * <p>Three shapes, because a caller who sent {@code ?dryRun=true} to {@code /batch}, one who
     * sent it to {@code /apply}, and one who mistyped {@code ?verif=true} need three different
     * sentences — and the first of the three is the one whose absence let a rehearsal commit.</p>
     */
    public static class UnknownParameterException extends ShTeachingException {

        /** Stable across all three shapes: a caller branches on the slug, not on the sentence. */
        public static final String SLUG = "unknown-parameter";

        UnknownParameterException(String sent, String verb, String path, Declared declared) {
            super(problem(sent, verb, path, declared));
        }

        private static ShProblem problem(String sent, String verb, String path,
                                         Declared declared) {
            // Sorted: the allow-list is an answer a caller reads, and two identical failures
            // must produce byte-identical bodies (P5).
            List<String> allowed = List.copyOf(new TreeSet<>(declared.query()));
            if (declared.body().contains(sent)) {
                return ShProblem
                        .badRequest(SLUG,
                                "`" + sent + "` is a body field here, not a query parameter")
                        .withDetail("`" + sent + "` arrived in the query string, where this "
                                + "endpoint does not read it — an unrecognised query parameter is "
                                + "ignored, so the request would have run as if you had not sent "
                                + "it at all. It is a field on the request body.")
                        .withAllowed(allowed)
                        .withFix("Move `" + sent + "` into the JSON body and retry.")
                        .withExample(verb + " " + path + " {\"" + sent + "\": true, ...}");
            }
            Optional<String> rehearsal = declared.rehearsal(sent);
            if (rehearsal.isPresent()) {
                return ShProblem
                        .badRequest(SLUG,
                                "`" + sent + "` is not how this endpoint rehearses")
                        .withDetail("This endpoint has no `" + sent + "`, in the query string or "
                                + "in the body — and an unrecognised query parameter is ignored, "
                                + "so the call would have written for real. `" + rehearsal.get()
                                + "` takes the same document and never writes.")
                        .withAllowed(allowed)
                        .withFix("Send the same body to `" + rehearsal.get() + "` instead.")
                        .withExample(rehearsal.get());
            }
            return ShProblem
                    .badRequest(SLUG,
                            "`" + sent + "` is not a parameter of " + verb + " " + path)
                    .withDetail("An unrecognised query parameter is ignored rather than applied, "
                            + "so a request that carries one does something other than what it "
                            + "says. On a call that writes, that is worth refusing.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix(allowed.isEmpty()
                            ? "Remove `" + sent + "` — this endpoint takes no query parameters."
                            : "Remove `" + sent + "`, or use one of the parameters it declares.")
                    .withExample(verb + " " + path);
        }
    }

    /** Named for the interceptor registry, so a config dump and a stack trace agree. */
    @Override
    public String toString() {
        return "shAgentQueryParams";
    }
}
