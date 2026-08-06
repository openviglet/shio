/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * The injectable IO context every command receives. Production wires the real
 * Node primitives; tests pass fakes (a scripted fetch, a tmp-dir fs, a string
 * sink for stdout) so commands run without a live server or disk.
 */
import * as nodeFs from "node:fs/promises";

export function defaultContext(overrides = {}) {
  return {
    env: process.env,
    cwd: process.cwd(),
    stdin: process.stdin,
    stdout: process.stdout,
    stderr: process.stderr,
    fetch: globalThis.fetch,
    fs: nodeFs,
    // Dynamic module loader for the SH64 compile step; injectable so tests can
    // supply a fake loader for `*.ts`/`*.mjs` DSL sources without touching disk.
    import: (url) => import(url),
    ...overrides,
  };
}

/** Resolve the console (Basic-auth) connection options for pull/push/deploy. */
export function resolveAdmin(flags, env) {
  return {
    baseUrl: flags.url ?? env.SHIO_URL ?? "http://localhost:2710",
    user: flags.user ?? env.SHIO_USER,
    password: flags.password ?? env.SHIO_PASSWORD,
  };
}

/** Resolve the CDA (token) connection options for types/dev. */
export function resolveCda(flags, env) {
  return {
    baseUrl: flags.url ?? env.SHIO_URL ?? "http://localhost:2710",
    apiToken: flags.token ?? env.SHIO_CDA_TOKEN,
  };
}
