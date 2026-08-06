import { defineConfig } from "vite";
import { resolve } from "path";

// React and the model DSL stay external: this package is a vocabulary, not a bundle,
// and duplicating either into it would mean a site could end up with two Reacts or two
// copies of the serializer that decides whether a post-type is byte-identical.
export default defineConfig({
  build: {
    lib: {
      entry: resolve(import.meta.dirname, "src/index.ts"),
      name: "ShioSections",
      formats: ["es", "umd"],
      fileName: (format) =>
        format === "umd" ? "shio-sections.umd.cjs" : "shio-sections.js",
    },
    rollupOptions: {
      external: ["react", "react/jsx-runtime", "@viglet/shio-model"],
      output: {
        globals: {
          react: "React",
          "react/jsx-runtime": "jsxRuntime",
          "@viglet/shio-model": "ShioModel",
        },
      },
    },
    outDir: "dist",
    emptyOutDir: true,
  },
});
