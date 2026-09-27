import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import { globalIgnores } from 'eslint/config'

/*
 * SH1054 — the accessibility rules a source scan can hold at edit time, beside the axe pass
 * `console-check.mjs` runs on the rendered page. Measured before enabling: 29 of the 34
 * recommended rules already passed across the console, and the rest found 17 findings in ten places.
 * One was a real gap (a post-type field could be expanded only with a pointer) and got a button;
 * the others are pointer shortcuts that duplicate a keyboard path, or autofocus inside a dialog, and
 * each says so at its own line.
 */
const jsxA11yRecommended = {
  plugins: { 'jsx-a11y': jsxA11y },
  rules: jsxA11y.flatConfigs.recommended.rules,
}

/*
 * SH665 — the react-hooks rules, declared here rather than taken from its preset.
 *
 * `eslint-plugin-react-hooks@7.1.1` still spells `plugins: ["react-hooks"]` in every one of its
 * flat configs, and ESLint 10 rejects an array there *before reading a rule* — so extending the
 * preset made `eslint` exit 2 on every file, including ones nobody had touched. It read as a
 * config error rather than a finding, which is why it was walked past.
 *
 * Declared and not pinned: 7.1.1 is the newest stable, and the only releases past it are canaries.
 * The rules come from the preset, so this tracks the plugin's own recommendation; only the
 * registration is ours. When a release ships the object form, this collapses back to `extends`.
 */
const reactHooksRecommended = {
  plugins: { 'react-hooks': reactHooks },
  rules: reactHooks.configs['recommended-latest'].rules,
}

export default tseslint.config([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooksRecommended,
      reactRefresh.configs.vite,
      jsxA11yRecommended,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
  },
])
