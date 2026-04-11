import { defineConfig } from "vite";
import { resolve } from "path";

export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, "src/index.ts"),
      name: "ShioReactSdk",
      formats: ["es", "umd"],
      fileName: (format) =>
        format === "umd" ? "shio-react-sdk.umd.cjs" : "shio-react-sdk.js",
    },
    rollupOptions: {
      external: ["react", "react/jsx-runtime"],
      output: {
        globals: { react: "React", "react/jsx-runtime": "jsxRuntime" },
      },
    },
    outDir: "dist",
    emptyOutDir: true,
  },
});
