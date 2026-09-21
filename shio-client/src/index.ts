/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */

// Client
export { ShioClient, createShioClient } from "./client";

// Errors
export { ShioHttpError } from "./errors";

// Code generation (SH11)
export { generateShioTypes } from "./codegen";
export type { GenerateTypesOptions } from "./codegen";

// Types
export type {
  ShioClientConfig,
  ShioRequestOptions,
  ShioSite,
  // SH1123 — the languages a site offers, which a switcher is built from.
  ShioSiteLocales,
  ShioFolder,
  ShioPostSummary,
  ShioPost,
  ShioListing,
  ShioPath,
  ShioQueryParams,
  ShioPageParams,
  ShioFieldSchema,
  ShioPostTypeSchema,
  // SH1016 — a body is an array of typed blocks, and this is what narrows one.
  ShioBlock,
  ShioBlockMark,
} from "./types";

// SH1016 — values, not types: the registered list and the reader that answers `null` for a
// field that is not blocks rather than making a consumer cast and find out by rendering nothing.
export { SHIO_BLOCK_TYPES, shioBlocks } from "./types";
