import { defineConfig } from "vite";
import { resolve } from "path";

export default defineConfig({
  build: {
    lib: {
      entry: resolve(import.meta.dirname, "src/index.ts"),
      name: "ShioReactSdk",
      formats: ["es", "umd"],
      fileName: (format) =>
        format === "umd" ? "shio-react-sdk.umd.cjs" : "shio-react-sdk.js",
    },
    rollupOptions: {
      // @viglet/shio-client is a declared dependency — keep it external so it
      // isn't duplicated into the bundle.
      external: ["react", "react/jsx-runtime", "@viglet/shio-client"],
      output: {
        globals: {
          react: "React",
          "react/jsx-runtime": "jsxRuntime",
          "@viglet/shio-client": "ShioClient",
        },
      },
    },
    outDir: "dist",
    emptyOutDir: true,
  },
});
