/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */

// Client
export { ShioClient, createShioClient } from "./client";

// Errors
export { ShioHttpError } from "./errors";

// Types
export type {
  ShioClientConfig,
  ShioRequestOptions,
  ShioSite,
  ShioFolder,
  ShioPostSummary,
  ShioPost,
  ShioListing,
  ShioPath,
  ShioQueryParams,
  ShioPageParams,
} from "./types";
