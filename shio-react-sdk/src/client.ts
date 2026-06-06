/**
 * The HTTP client now lives in the framework-agnostic core package. This module
 * re-exports it so existing `import { ShioClient } from "@viglet/shio-react-sdk"`
 * keeps working — React is one consumer of the core, not a parallel client.
 */
export { ShioClient, createShioClient } from "@viglet/shio-client";
