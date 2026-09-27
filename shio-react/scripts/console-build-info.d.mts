/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1126 — types for the one module both the build and the test import.
 *
 * <p>The implementation stays `.mjs` because `node --test` runs the suites with no build step, and
 * `vite.config.ts` is type-checked by `tsc -b`, so the two meet here. A hand-written declaration
 * rather than moving the module to TypeScript: a `.ts` file would need compiling before a test could
 * import it, which is the build-before-you-can-measure problem this task exists to remove.</p>
 */

/** Emitted beside `index.html`, so an instance serving the build can answer the same question. */
export declare const BUILD_INFO: string;

/** The inputs whose bytes decide what the bundle contains, relative to `shio-react/`. */
export declare const SOURCE_INPUTS: readonly string[];

/** `sha256:<hex>` over the console's sources, as a build and a test both compute it. */
export declare function sourceDigest(root: string): string;

/** What a build says about itself, or `null` when it says nothing. */
export declare function readBuildInfo(dist: string): Record<string, unknown> | null;

/** Whether the build in `dist` was made from the tree at `root`, and why not when it was not. */
export declare function buildProvenance(dist: string, root: string): {
  measurable: boolean;
  why: string;
  info: Record<string, unknown> | null;
};
