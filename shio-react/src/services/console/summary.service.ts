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

/** The dashboard's whole answer. */
export interface ShConsoleSummary {
  sites: number;
  postTypes: number;
  version: string;
  attention: ShAttention;
  activity: ShHistory[];
}

export class ShConsoleSummaryService {
  async get(read: ShRead = {}): Promise<ShConsoleSummary> {
    const response = await axios.get<ShConsoleSummary>("/v2/console/summary",
      { signal: read.signal });
    return response.data;
  }
}
