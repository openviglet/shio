/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */

/**
 * Thrown when the CDA responds with a non-OK status (other than the 404s that
 * single-resource getters translate to `null`).
 */
export class ShioHttpError extends Error {
  readonly status: number;
  readonly url: string;
  readonly body?: string;

  constructor(status: number, url: string, body?: string) {
    super(`Shio CDA ${status} for ${url}`);
    this.name = "ShioHttpError";
    this.status = status;
    this.url = url;
    this.body = body;
    // Restore prototype chain for transpiled-to-ES5 consumers.
    Object.setPrototypeOf(this, ShioHttpError.prototype);
  }
}
