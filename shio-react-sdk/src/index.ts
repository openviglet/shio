// Provider
export { ShioProvider, useShioClient } from "./provider";
export type { ShioProviderProps } from "./provider";

// Client (re-exported from the core)
export { ShioClient, createShioClient } from "@viglet/shio-client";
export { ShioHttpError } from "@viglet/shio-client";

// Hooks
export type { ShioAsyncState } from "./hooks/use-async";
export { useShioSites, useShioSite } from "./hooks/use-shio-sites";
export { useShioChildren } from "./hooks/use-shio-children";
export { useShioBreadcrumb } from "./hooks/use-shio-breadcrumb";
export { useShioPost, useShioPostByUrl } from "./hooks/use-shio-post";
export { useShioQuery } from "./hooks/use-shio-query";

// Render components
export { ShioRichText } from "./components/shio-rich-text";
export type { ShioRichTextProps } from "./components/shio-rich-text";
export { ShioImage } from "./components/shio-image";
export type { ShioImageProps } from "./components/shio-image";
export { ShioRelator } from "./components/shio-relator";
export type { ShioRelatorProps } from "./components/shio-relator";
export { ShioField } from "./components/shio-field";
export type { ShioFieldProps, ShioFieldKind } from "./components/shio-field";

// Types (re-exported from the core)
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
} from "@viglet/shio-client";
