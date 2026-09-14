/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { toast } from "@viglet/viglet-design-system"

/**
 * SH955 — errors are instructions, on both sides of the wall.
 *
 * P6 is a law about errors, not about the agent surface, and it held on one side only. The backend
 * writes RFC 9457 problem documents carrying `fix`, `allowed`, `didYouMean` and `at`, and calls a
 * 4xx that has none of them incomplete — while twenty-four console save paths caught the rejection
 * and toasted the word **Error**. The same refusal taught an agent what to do and told a curator
 * nothing at all.
 *
 * The console's axios interceptor handled CSRF capture and backend-offline reporting and nothing
 * else, so no code path read `application/problem+json`. This is that reader, and one reporter over
 * it, so a refusal reaches a person in the words the server chose.
 */

/** What the backend says when it refuses. Every field past `title` is optional and usually there. */
export interface Problem {
  /** The short name of the refusal — `ShProblem.of(slug, status, title)`. */
  title: string
  /** What went wrong, at length. The headline a person reads. */
  detail?: string
  /** What to do about it. The half that makes an error an instruction (P6). */
  fix?: string
  /** The values that would have been accepted. */
  allowed?: string[]
  /** The correction the server guessed at, for a value that was nearly right. */
  didYouMean?: string
  /** Which field it is about — `data.<name>` on a write. Lets a form pin the message. */
  at?: string
  status?: number
}

/** `data.title` → `title`, so a form can match it against its own field names. */
export function fieldOf(problem: Problem): string | undefined {
  if (!problem.at) return undefined
  return problem.at.startsWith("data.") ? problem.at.slice("data.".length) : problem.at
}

/**
 * Read a rejection as a problem document, falling back through what is actually there.
 *
 * An axios rejection buries the body in `response.data`. A network failure has no document at all,
 * and the rejection's own `message` is then the only true thing available — which is still better
 * than a fixed string, because "Network Error" and "Request failed" are different facts.
 */
export function problemOf(error: unknown): Problem {
  const response = (error as { response?: { data?: unknown; status?: number } })?.response
  const data = response?.data
  if (data && typeof data === "object") {
    const raw = data as Record<string, unknown>
    const title = [raw.title, raw.message].find((value) => typeof value === "string" && value)
    if (typeof title === "string") {
      const problem: Problem = { title }
      if (typeof raw.detail === "string" && raw.detail) problem.detail = raw.detail
      if (typeof raw.fix === "string" && raw.fix) problem.fix = raw.fix
      if (typeof raw.didYouMean === "string" && raw.didYouMean) problem.didYouMean = raw.didYouMean
      if (typeof raw.at === "string" && raw.at) problem.at = raw.at
      if (Array.isArray(raw.allowed)) {
        problem.allowed = raw.allowed.filter((v): v is string => typeof v === "string")
      }
      if (typeof response?.status === "number") problem.status = response.status
      return problem
    }
  }
  const message = (error as { message?: unknown })?.message
  const problem: Problem = { title: typeof message === "string" && message ? message : "" }
  if (typeof response?.status === "number") problem.status = response.status
  return problem
}

/** The one line a person reads first: the long form where there is one, else the short. */
export function headlineOf(problem: Problem, fallback: string): string {
  return problem.detail || problem.title || fallback
}

/**
 * Everything under the headline, in the order a reader needs it: what to do, then what would have
 * been accepted. `allowed` is capped — a list of forty post-type names is not a toast.
 */
export function adviceOf(problem: Problem, allowedLimit = 6): string | undefined {
  const parts: string[] = []
  if (problem.fix) parts.push(problem.fix)
  if (problem.didYouMean) parts.push(`Did you mean: ${problem.didYouMean}`)
  if (problem.allowed?.length) {
    const shown = problem.allowed.slice(0, allowedLimit).join(", ")
    const rest = problem.allowed.length - Math.min(allowedLimit, problem.allowed.length)
    parts.push(rest > 0 ? `Allowed: ${shown}, +${rest} more` : `Allowed: ${shown}`)
  }
  return parts.length ? parts.join(" · ") : undefined
}

/**
 * Report a rejection to the curator in the server's own words.
 *
 * The single replacement for a toast of the fixed `common.error` string. `fallback` is what a
 * caller would have shown anyway, used only where the rejection carries nothing — so this can
 * never say less than the line it replaced.
 */
export function reportProblem(error: unknown, fallback: string): Problem {
  const problem = problemOf(error)
  toast.error(headlineOf(problem, fallback), { description: adviceOf(problem) })
  return problem
}
