/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.benchmark;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.ShH2ResultCache;
import com.viglet.shio.api.agent.ShTokenBudget;

/**
 * The measuring tape for SH112 — one recorded run of one canonical task.
 *
 * <h2>What is counted, and why each one</h2>
 *
 * <ul>
 *   <li><b>Calls.</b> A round trip is not free even when its payload is small: it is a
 *       turn, and a turn is latency plus a model invocation. This is the number the
 *       "≈6× fewer tool calls" claim is about.</li>
 *   <li><b>Tokens.</b> Request <em>and</em> response. Counting only responses would
 *       make a chatty writer look cheap — a batch of ten posts sends most of its cost
 *       upward — and the whole point of comparing two paths is that they move the cost
 *       around rather than only shrinking it.</li>
 *   <li><b>Wall clock.</b> Recorded, never asserted. A CI runner's timing is noise at
 *       this scale, and a benchmark that fails on noise is a benchmark people delete.
 *       It is here because a change that halves the tokens and decuples the latency is
 *       still a regression, and the number is the only way to notice.</li>
 * </ul>
 *
 * <h2>Why it measures bodies rather than reading the cost header</h2>
 *
 * <p>{@code X-Shio-Est-Tokens} (SH124) covers the agent and delivery namespaces. The
 * baseline path this class exists to compare against is mostly <em>console</em>
 * endpoints, which carry no such header — so measuring one path through the header and
 * the other through its body would compare two different rulers. Both sides are counted
 * the same way, with the same 4-chars-per-token estimate the header itself uses.</p>
 *
 * @author Alexandre Oliveira
 */
public final class ShAgentBenchmark {

    private final MockMvc mockMvc;
    private final String task;
    private final String path;
    private final List<Step> steps = new ArrayList<>();
    private final long startedNanos = System.nanoTime();

    public ShAgentBenchmark(MockMvc mockMvc, String task, String path) {
        this.mockMvc = mockMvc;
        this.task = task;
        this.path = path;
    }

    /**
     * Performs one call and records what it cost.
     *
     * @param label      what the agent was doing, for the report
     * @param request    the call
     * @param sentBody   the request body, or {@code null} — passed separately because a
     *                   {@code MockHttpServletRequestBuilder} does not expose it, and a
     *                   write's cost is mostly what it sent
     */
    public MvcResult call(String label, MockHttpServletRequestBuilder request, String sentBody)
            throws Exception {
        // SH1327 — a read task repeats reads with nothing written between them, and H2 would
        // recall them from its result cache; the wall clock is the calls' own.
        ShH2ResultCache.forget(dataSource());
        MvcResult result = mockMvc.perform(request).andReturn();
        int requestTokens = ShTokenBudget.estimate(result.getRequest().getRequestURI())
                + ShTokenBudget.estimate(nullSafe(result.getRequest().getQueryString()))
                + ShTokenBudget.estimate(nullSafe(sentBody));
        int responseTokens = ShTokenBudget.estimateChars(
                result.getResponse().getContentAsByteArray().length);
        steps.add(new Step(label, requestTokens, responseTokens,
                result.getResponse().getStatus()));
        if (Boolean.getBoolean("shio.benchmark.trace") && result.getResponse().getStatus() >= 400) {
            System.out.println("TRACE " + label + " -> " + result.getResponse().getStatus()
                    + " " + result.getResponse().getContentAsString());
        }
        return result;
    }

    public MvcResult call(String label, MockHttpServletRequestBuilder request) throws Exception {
        return call(label, request, null);
    }

    public Run finish() {
        return new Run(task, path, List.copyOf(steps),
                (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    /** The database behind the {@code MockMvc}'s context, which is the one its calls read. */
    private DataSource dataSource() {
        WebApplicationContext context = mockMvc.getDispatcherServlet().getWebApplicationContext();
        if (context == null) {
            throw new IllegalStateException("SH1327: the MockMvc has no application context, so "
                    + "its calls cannot be timed with H2's result cache off");
        }
        return context.getBean(DataSource.class);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** One recorded call. */
    public record Step(String label, int requestTokens, int responseTokens, int status) {

        public int tokens() {
            return requestTokens + responseTokens;
        }
    }

    /**
     * One task, done one way.
     *
     * @param path {@code agent} or {@code naive} — the two ways of reaching the same
     *             end state, which is what makes the ratio meaningful rather than a
     *             number with no denominator
     */
    public record Run(String task, String path, List<Step> steps, long millis) {

        public int calls() {
            return steps.size();
        }

        public int tokens() {
            return steps.stream().mapToInt(Step::tokens).sum();
        }

        /** Fails loudly rather than reporting a cheap run that was really a 500. */
        public void requireAllSucceeded() {
            List<Step> broken = steps.stream().filter(step -> step.status() >= 400).toList();
            if (!broken.isEmpty()) {
                throw new IllegalStateException("Benchmark task '" + task + "' (" + path
                        + ") had failing calls, so its cost is meaningless: " + broken);
            }
        }

        /** A row of the printed table. */
        public String row() {
            return String.format("| %-24s | %-5s | %5d | %7d | %6d |",
                    task, path, calls(), tokens(), millis);
        }

        public String json() {
            StringBuilder out = new StringBuilder();
            out.append("{\"task\":\"").append(task).append("\",\"path\":\"").append(path)
                    .append("\",\"calls\":").append(calls())
                    .append(",\"tokens\":").append(tokens())
                    .append(",\"millis\":").append(millis)
                    .append(",\"steps\":[");
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                if (i > 0) {
                    out.append(',');
                }
                out.append("{\"label\":\"").append(step.label())
                        .append("\",\"tokens\":").append(step.tokens())
                        .append(",\"status\":").append(step.status()).append('}');
            }
            return out.append("]}").toString();
        }
    }

    /** The report artifact: a JSON document CI can archive per release. */
    public static byte[] report(List<Run> runs) {
        StringBuilder out = new StringBuilder("{\"runs\":[");
        for (int i = 0; i < runs.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(runs.get(i).json());
        }
        return out.append("]}").toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String table(List<Run> runs) {
        StringBuilder out = new StringBuilder();
        out.append("| task                     | path  | calls |  tokens | millis |\n");
        out.append("|--------------------------|-------|-------|---------|--------|\n");
        runs.forEach(run -> out.append(run.row()).append('\n'));
        return out.toString();
    }
}
