import { defineConfig } from "vite";
import { resolve } from "path";

// Zero runtime dependencies → nothing is marked external; the bundle is
// self-contained for every target (browser, Node, edge, bundlers).
export default defineConfig({
  build: {
    lib: {
      entry: resolve(import.meta.dirname, "src/index.ts"),
      name: "ShioClient",
      formats: ["es", "umd"],
      fileName: (format) =>
        format === "umd" ? "shio-client.umd.cjs" : "shio-client.js",
    },
    outDir: "dist",
    emptyOutDir: true,
  },
});
