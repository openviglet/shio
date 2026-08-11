import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { globalIgnores } from 'eslint/config'

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
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
  },
])
