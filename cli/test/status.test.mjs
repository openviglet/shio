/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { owed, render } from "../src/commands/status.mjs";

/**
 * SH1050 — `shio status`, and the one thing a report like this gets wrong.
 *
 * ## Why the renderer is a pure function
 *
 * Everything this command does is read one endpoint and print it, so the endpoint is asserted where
 * it is built (`ShConsoleSummaryAPITest`) and the printing is asserted here. What is worth holding
 * is not the wording but the **omissions**: a row is drawn only when its number is not zero, and a
 * report that prints three zeroes to say nothing is wrong is one people stop reading — which is the
 * same failure as not printing it.
 */
const FULL = {
  version: "2026.3.2",
  sites: 3,
  postTypes: 12,
  attention: { reviewSessions: 2, reviewChanges: 17, scheduledSoon: 1, openNotes: 0 },
  runs: [
    { session: "s1", intent: "add a pricing page", agent: "claude", changes: 9, pending: 4 },
    { session: "s2", intent: null, agent: null, changes: 3, pending: 0 },
  ],
  diagnostics: { incidents: 1, unpersisted: 0 },
  activity: [{ date: "2026-09-20T20:00:00Z", action: "PUBLISH", description: "Pricing" }],
};

const QUIET = {
  version: "2026.3.2",
  sites: 1,
  postTypes: 4,
  attention: { reviewSessions: 0, reviewChanges: 0, scheduledSoon: 0, openNotes: 0 },
  runs: [],
  diagnostics: { incidents: 0, unpersisted: 0 },
  activity: [],
};

test("SH1050: a number that is zero gets no row, and nothing waiting says so once", () => {
  const busy = render("http://localhost:2710", FULL);
  const quiet = render("http://localhost:2710", QUIET);

  assert.match(busy, /review {4}2 session\(s\), 17 change\(s\)/u);
  assert.match(busy, /scheduled 1 in the next 7 days/u);
  assert.doesNotMatch(busy, /notes/u,
    "SH1050: no notes are open, so there is no notes row. A report that lists every category "
    + "whether or not it has anything in it is one whose reader stops scanning it.");

  assert.match(quiet, /nothing waiting/u,
    "SH1050: an instance with nothing owed must say so in one line. Silence there reads as a "
    + "command that failed to fetch, which is the opposite of the news.");
  assert.doesNotMatch(quiet, /waiting:/u);
  assert.doesNotMatch(quiet, /incidents/u,
    "SH1050: zero incidents is not a finding. The log starts empty at boot, so printing the zero "
    + "would be announcing that nothing has happened yet.");
  assert.doesNotMatch(quiet, /runs:/u);
});

test("SH1050: a run is named by what it said it was doing", () => {
  const out = render("http://x", FULL);

  assert.match(out, /add a pricing page {2}\(9 change\(s\), 4 waiting\)/u,
    "SH1050: the intent is the row. A session id says an agent was here; the sentence it declared "
    + "is what a curator needs before deciding whether to read the diff.");
  assert.match(out, /^ {4}s2 {2}\(3 change\(s\)\)$/mu,
    "SH1050: a run with no intent falls back to the agent, then to the session id — the last is "
    + "always there, so a row is never blank. And no `waiting` clause when nothing is waiting: "
    + "'the agent ran and you have dealt with it' is its own fact.");
});

test("SH1050: the incident count says since when", () => {
  const out = render("http://x", FULL);

  assert.match(out, /incidents: 1 since this process started/u,
    "SH1050: the incident log is an in-memory window that starts empty at boot, so a bare count "
    + "would read as a total. `0` meaning 'nothing ever' and `0` meaning 'nothing yet' are "
    + "different answers about an instance that may have been restarted to hide the first one.");

  const unwritten = render("http://x", {
    ...FULL, diagnostics: { incidents: 0, unpersisted: 3 },
  });
  assert.match(unwritten, /3 that could not be written down/u,
    "SH1050: if the database was the thing failing, the record of the failure is exactly what "
    + "could not be stored — so a quiet feed with unpersisted incidents must not print as quiet.");
});

test("SH1050: `owed` is what decides the section, and it is not a truthiness check", () => {
  assert.equal(owed(undefined), 0, "a summary from an older instance owes nothing, not NaN");
  assert.equal(owed({}), 0);
  assert.equal(owed({ reviewSessions: 0, scheduledSoon: 0, openNotes: 0 }), 0);
  assert.equal(owed({ reviewSessions: 2, scheduledSoon: 1, openNotes: 4 }), 7);
  assert.equal(owed({ openNotes: 1 }), 1,
    "SH1050: one open note is something waiting. A check that required all three to be present "
    + "would report a clear instance on the day only one thing was owed.");
});

test("SH1050: the instance is named, so a report cannot be about the wrong one", () => {
  const out = render("https://shio.example.com", FULL);

  assert.match(out, /^https:\/\/shio\.example\.com — Shio 2026\.3\.2$/mu,
    "SH1050: the first line carries the base URL and the version. A status report with neither is "
    + "one somebody reads against the instance they meant rather than the one it came from.");
  assert.match(render("http://x", { ...FULL, version: null }), /Shio unknown/u,
    "SH1050: an instance that does not report a version says `unknown` rather than `Shio null`");
});
