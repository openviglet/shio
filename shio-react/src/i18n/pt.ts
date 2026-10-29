/*
 * SH1286 — the console's Portuguese strings, in one module that `index.ts` imports only when the
 * reader's language is Portuguese.
 *
 * Every namespace of both languages used to be in the eager bundle, so an English reader downloaded
 * the Portuguese copy of every screen before the first row of content. English stays eager because it
 * is the fallback; this file is behind an `import()`, so Vite gives it a chunk of its own.
 *
 * Kept out of `locales/pt/`, because the tests read every file in that folder as one JSON bundle.
 */
import ptCommon from "./locales/pt/common.json";
import ptLogin from "./locales/pt/login.json";
import ptSidebar from "./locales/pt/sidebar.json";
import ptNav from "./locales/pt/nav.json";
import ptDialog from "./locales/pt/dialog.json";
import ptContent from "./locales/pt/content.json";
import ptAdmin from "./locales/pt/admin.json";
import ptGraphql from "./locales/pt/graphql.json";
import ptUniversalEditor from "./locales/pt/universal-editor.json";
import ptApiToken from "./locales/pt/apiToken.json";
import ptTenantAdmin from "./locales/pt/tenantAdmin.json";
import ptAuthProvider from "./locales/pt/authProvider.json";
import ptExchangeProvider from "./locales/pt/exchangeProvider.json";
import ptEmailConfig from "./locales/pt/emailConfig.json";
import ptActivity from "./locales/pt/activity.json";
import ptAcl from "./locales/pt/acl.json";
import ptSiteWebhook from "./locales/pt/siteWebhook.json";
import ptHome from "./locales/pt/home.json";
import ptStaticFile from "./locales/pt/staticFile.json";
import ptNote from "./locales/pt/note.json";
import ptInsights from "./locales/pt/insights.json";
import ptBlocks from "./locales/pt/blocks.json";
import ptReview from "./locales/pt/review.json";
import ptMarketplace from "./locales/pt/marketplace.json";

const pt = {
  ...ptCommon, ...ptLogin, ...ptSidebar, ...ptNav, ...ptDialog,
  ...ptContent, ...ptAdmin, ...ptGraphql, ...ptUniversalEditor, ...ptApiToken,
  ...ptTenantAdmin, ...ptAuthProvider, ...ptExchangeProvider, ...ptEmailConfig,
  ...ptActivity, ...ptAcl, ...ptSiteWebhook, ...ptHome, ...ptStaticFile, ...ptReview, ...ptNote, ...ptMarketplace,
  ...ptInsights, ...ptBlocks,
};

export default pt;
