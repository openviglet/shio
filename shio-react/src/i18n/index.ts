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


const en = {
  ...enCommon, ...enLogin, ...enSidebar, ...enNav, ...enDialog,
  ...enContent, ...enAdmin, ...enGraphql, ...enUniversalEditor, ...enApiToken,
  ...enTenantAdmin, ...enAuthProvider, ...enExchangeProvider, ...enEmailConfig,
  ...enActivity, ...enAcl, ...enSiteWebhook, ...enHome, ...enStaticFile, ...enReview, ...enNote, ...enMarketplace,
  ...enInsights, ...enBlocks,
};

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    // SH1286 — English only, because it is the fallback; Portuguese arrives with `ensureLanguage`.
    resources: {
      en: { translation: en },
    },
    partialBundledLanguages: true,
    fallbackLng: "en",
    interpolation: { escapeValue: false },
    // A bundle added after the first render re-renders what read it, so a mid-session switch to
    // Portuguese replaces the English fallback on screen rather than waiting for a navigation.
    react: { bindI18nStore: "added" },
    detection: {
      order: ["navigator", "htmlTag", "localStorage", "cookie"],
      caches: ["localStorage"],
    },
  });

registerVigTranslations(i18n);

/**
 * SH1286 — the reader's own language, loaded only when it is not English.
 *
 * Portuguese is a chunk of its own (`./pt`), so an English reader no longer downloads it. Merged
 * with overwrite, so Shio's strings win over the design system's for the same key, which is the
 * precedence the eager bundle had when Shio's resources were initialised first.
 */
let portuguese: Promise<void> | null = null;
export function ensureLanguage(lng: string | undefined): Promise<void> {
  if (!lng?.toLowerCase().startsWith("pt")) return Promise.resolve();
  portuguese ??= import("./pt").then(async (module) => {
    i18n.addResourceBundle("pt", "translation", module.default, true, true);
    // SH1306 — `init` resolved against a store holding only English, so `resolvedLanguage` is still
    // `en`, and adding a bundle never recomputes it. Changing to the language already detected does,
    // and fires the `languageChanged` that `<html lang>` and every Intl formatter downstream read.
    await i18n.changeLanguage(i18n.language);
  });
  return portuguese;
}

/** Resolves once the detected language's strings are in, so the first paint is in that language. */
export const i18nReady: Promise<void> = ensureLanguage(i18n.language);
i18n.on("languageChanged", (lng) => {
  void ensureLanguage(lng);
});

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
