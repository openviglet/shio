import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import { registerVigTranslations } from "@viglet/viglet-design-system/i18n";

import enCommon from "./locales/en/common.json";
import enLogin from "./locales/en/login.json";
import enSidebar from "./locales/en/sidebar.json";
import enNav from "./locales/en/nav.json";
import enDialog from "./locales/en/dialog.json";
import enContent from "./locales/en/content.json";
import enAdmin from "./locales/en/admin.json";
import enGraphql from "./locales/en/graphql.json";
import enUniversalEditor from "./locales/en/universal-editor.json";
import enApiToken from "./locales/en/apiToken.json";
import enTenantAdmin from "./locales/en/tenantAdmin.json";
import enAuthProvider from "./locales/en/authProvider.json";
import enExchangeProvider from "./locales/en/exchangeProvider.json";
import enEmailConfig from "./locales/en/emailConfig.json";
import enActivity from "./locales/en/activity.json";
import enAcl from "./locales/en/acl.json";
import enSiteWebhook from "./locales/en/siteWebhook.json";
import enHome from "./locales/en/home.json";
import enStaticFile from "./locales/en/staticFile.json";
import enNote from "./locales/en/note.json";
import enInsights from "./locales/en/insights.json";
import enBlocks from "./locales/en/blocks.json";
import enReview from "./locales/en/review.json";
import enMarketplace from "./locales/en/marketplace.json";

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

const en = {
  ...enCommon, ...enLogin, ...enSidebar, ...enNav, ...enDialog,
  ...enContent, ...enAdmin, ...enGraphql, ...enUniversalEditor, ...enApiToken,
  ...enTenantAdmin, ...enAuthProvider, ...enExchangeProvider, ...enEmailConfig,
  ...enActivity, ...enAcl, ...enSiteWebhook, ...enHome, ...enStaticFile, ...enReview, ...enNote, ...enMarketplace,
  ...enInsights, ...enBlocks,
};

const pt = {
  ...ptCommon, ...ptLogin, ...ptSidebar, ...ptNav, ...ptDialog,
  ...ptContent, ...ptAdmin, ...ptGraphql, ...ptUniversalEditor, ...ptApiToken,
  ...ptTenantAdmin, ...ptAuthProvider, ...ptExchangeProvider, ...ptEmailConfig,
  ...ptActivity, ...ptAcl, ...ptSiteWebhook, ...ptHome, ...ptStaticFile, ...ptReview, ...ptNote, ...ptMarketplace,
  ...ptInsights, ...ptBlocks,
};

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      pt: { translation: pt },
    },
    fallbackLng: "en",
    interpolation: { escapeValue: false },
    detection: {
      order: ["navigator", "htmlTag", "localStorage", "cookie"],
      caches: ["localStorage"],
    },
  });

registerVigTranslations(i18n);

/**
 * SH1054 — the document says which language it is in.
 *
 * `index.html` is `lang="en"` and nothing changed it, so a console rendering Portuguese told a
 * screen reader it was English and every word was pronounced with the wrong rules — measured on
 * every route by the axe pass in `console-check.mjs`. Stamped from what i18next resolved, at start
 * and on every change; the detector reads `navigator` first, so this never feeds its own answer back.
 */
function stampLanguage(): void {
  const lng = i18n.resolvedLanguage ?? i18n.language;
  if (typeof document !== "undefined" && lng) document.documentElement.lang = lng;
}
stampLanguage();
i18n.on("languageChanged", stampLanguage);

export default i18n;
