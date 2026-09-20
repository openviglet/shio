/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import axios from "axios";

/**
 * SH1047 — the options every console read takes, which is one option.
 *
 * <h2>The race it closes</h2>
 *
 * <p>Nothing in the console could cancel a request. The content browser's page fetch is rebuilt on
 * every change of folder, sort or filter, and the previous one was left running — so a curator who
 * moves quickly can be shown a <b>slow earlier response over a fast later one</b>: the rows on
 * screen belong to a folder they already left, under a breadcrumb that says otherwise. Nothing
 * errors, and there is no way to tell by looking.</p>
 *
 * <h2>Why a type and not just a parameter</h2>
 *
 * <p>Sixty-two reads live across twenty-seven service classes, and threading an `AbortSignal`
 * through them one signature at a time is how half of them end up spelling it differently —
 * `abort`, `cancel`, a bare third positional. One named type means the lint can ask a single
 * question of every read, and a reader of any service knows what the last argument is without
 * looking it up.</p>
 *
 * <h2>Reads only, and that is a rule rather than an omission</h2>
 *
 * <p>Writes do not take this. Aborting a POST does not undo it — the request may already have
 * reached the server — so a cancelled write is a write whose <i>answer</i> was thrown away, which
 * is strictly worse than waiting for it. Cancellation belongs to the reads, where the only thing
 * lost is an answer nobody is going to look at.</p>
 */
export interface ShRead {
  /**
   * The caller's cancellation signal.
   *
   * <p>`useQuery` supplies one per query and aborts it when the key changes or the component
   * unmounts, which is exactly the moment the answer stopped being wanted — so a migrated page
   * passes `({ signal }) => service.read(..., { signal })` and gets the whole of this for free.</p>
   */
  signal?: AbortSignal;
}

/**
 * Whether a rejection is this side hanging up, rather than the server refusing (SH1047).
 *
 * <p>The half that makes cancellation safe to add, and the half it is a regression without. An
 * aborted request <b>rejects</b>, so every `catch(() => setItems([]))` in the console would read a
 * navigation as a failed load and empty the list the curator is walking into — the cure being
 * worse than the race. A cancelled read has no answer and needs no handling: the caller that
 * cancelled it is the one that stopped wanting it.</p>
 */
export function isCancelled(error: unknown): boolean {
  if (axios.isCancel(error)) return true;
  const name = (error as { name?: unknown } | null | undefined)?.name;
  // `AbortError` is what a bare `fetch` rejects with, and `CanceledError` is axios's own — both
  // reach a shared handler, and neither is something a curator should be told about.
  return name === "AbortError" || name === "CanceledError";
}
