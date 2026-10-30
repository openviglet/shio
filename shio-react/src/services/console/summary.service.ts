/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import axios from "axios";
import type { ShRead } from "@/lib/read-options";
import type { ShHistory } from "@/models/history/history.model";

/**
 * SH1050 — what the console's home asks for, in one answer.
 *
 * <p>Home used to fire four requests on mount and take one property of two of them: every site and
 * every post type, transferred whole, for their length. `/api/v2/console/summary` answers the same
 * screen with count queries, and carries the activity rows so there is no second read for those
 * either.</p>
 */

/** What is waiting for a person, each number with a page behind it. */
export interface ShAttention {
  reviewSessions: number;
  reviewChanges: number;
  scheduledSoon: number;
  openNotes: number;
}

/** One agent run, as the dashboard names it — the intent is what makes it worth a row. */
export interface ShRun {
  session: string;
  intent: string | null;
  agent: string | null;
  changes: number;
  pending: number;
  at: string | null;
}

/** Whether the instance is failing at anything, from the in-memory window since boot. */
export interface ShDiagnostics {
  incidents: number;
  unpersisted: number;
}

/** SH1336 — a page that fails verify's content or routes check, and the first reason why. */
export interface ShFailing {
  address: string;
  id: string | null;
  rule: string;
  message: string;
}

/** SH1336 — error findings across every readable site, and the first few pages they are on. */
export interface ShChecks {
  errors: number;
  pages: ShFailing[];
}

/** The dashboard's whole answer. */
export interface ShConsoleSummary {
  sites: number;
  postTypes: number;
  version: string;
  attention: ShAttention;
  runs: ShRun[];
  diagnostics: ShDiagnostics;
  activity: ShHistory[];
  checks?: ShChecks;
}

export class ShConsoleSummaryService {
  async get(read: ShRead = {}): Promise<ShConsoleSummary> {
    const response = await axios.get<ShConsoleSummary>("/v2/console/summary",
      { signal: read.signal });
    return response.data;
  }
}
