import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import { registerVigTranslations } from "@viglet/viglet-design-system";

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

const en = {
  ...enCommon, ...enLogin, ...enSidebar, ...enNav, ...enDialog,
  ...enContent, ...enAdmin, ...enGraphql, ...enUniversalEditor, ...enApiToken,
  ...enTenantAdmin, ...enAuthProvider, ...enExchangeProvider, ...enEmailConfig,
  ...enActivity,
};

const pt = {
  ...ptCommon, ...ptLogin, ...ptSidebar, ...ptNav, ...ptDialog,
  ...ptContent, ...ptAdmin, ...ptGraphql, ...ptUniversalEditor, ...ptApiToken,
  ...ptTenantAdmin, ...ptAuthProvider, ...ptExchangeProvider, ...ptEmailConfig,
  ...ptActivity,
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

export default i18n;
