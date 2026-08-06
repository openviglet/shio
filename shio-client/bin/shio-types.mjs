#!/usr/bin/env node
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * shio-types — generate a TypeScript `.d.ts` from a Shio instance's post-types
 * (SH11). Fetches `/api/v2/cda/post-type` and writes typed `Post` interfaces so
 * page code gets autocomplete on `post.attrs.*`.
 *
 *   npx shio-types --url http://localhost:2710 --token <CDA token> --out lib/shio-types.d.ts
 *
 * Env fallbacks: SHIO_URL, SHIO_CDA_TOKEN.
 */
import { writeFile } from "node:fs/promises";
import { argv, env, exit, stdout } from "node:process";

import { ShioClient, generateShioTypes } from "../dist/shio-client.js";

const HELP = `shio-types — generate TypeScript types from Shio post-types

Usage:
  shio-types [options]

Options:
  --url <baseUrl>       Shio base URL        (env SHIO_URL, default http://localhost:2710)
  --token <token>       CDA token            (env SHIO_CDA_TOKEN)
  --out <file>          Output .d.ts path    (default shio-types.d.ts; "-" for stdout)
  --import-from <mod>   Module to import ShioPost from (default @viglet/shio-client)
  -h, --help            Show this help
`;

function parseArgs(args) {
  const opts = {};
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    switch (a) {
      case "-h":
      case "--help":
        opts.help = true;
        break;
      case "--url":
        opts.url = args[++i];
        break;
      case "--token":
        opts.token = args[++i];
        break;
      case "--out":
        opts.out = args[++i];
        break;
      case "--import-from":
        opts.importFrom = args[++i];
        break;
      default:
        fail(`Unknown argument: ${a}\n\n${HELP}`);
    }
  }
  return opts;
}

function fail(message) {
  process.stderr.write(`${message}\n`);
  exit(1);
}

async function main() {
  const opts = parseArgs(argv.slice(2));
  if (opts.help) {
    stdout.write(HELP);
    return;
  }

  const baseUrl = opts.url ?? env.SHIO_URL ?? "http://localhost:2710";
  const apiToken = opts.token ?? env.SHIO_CDA_TOKEN;
  const out = opts.out ?? "shio-types.d.ts";

  const client = new ShioClient({ baseUrl, apiToken });

  let schemas;
  try {
    schemas = await client.getPostTypes();
  } catch (err) {
    fail(`shio-types: failed to fetch post-types from ${baseUrl}\n  ${err?.message ?? err}`);
  }

  const source = generateShioTypes(schemas, {
    importFrom: opts.importFrom,
  });

  if (out === "-") {
    stdout.write(source);
    return;
  }

  await writeFile(out, source, "utf8");
  stdout.write(`shio-types: wrote ${schemas.length} post-type(s) to ${out}\n`);
}

main().catch((err) => fail(`shio-types: ${err?.stack ?? err}`));
