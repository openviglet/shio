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
import enConfig from "./locales/en/config.json";
import enGraphql from "./locales/en/graphql.json";
import enUniversalEditor from "./locales/en/universal-editor.json";

import ptCommon from "./locales/pt/common.json";
import ptLogin from "./locales/pt/login.json";
import ptSidebar from "./locales/pt/sidebar.json";
import ptNav from "./locales/pt/nav.json";
import ptDialog from "./locales/pt/dialog.json";
import ptContent from "./locales/pt/content.json";
import ptAdmin from "./locales/pt/admin.json";
import ptConfig from "./locales/pt/config.json";
import ptGraphql from "./locales/pt/graphql.json";
import ptUniversalEditor from "./locales/pt/universal-editor.json";

const en = {
  ...enCommon, ...enLogin, ...enSidebar, ...enNav, ...enDialog,
  ...enContent, ...enAdmin, ...enConfig, ...enGraphql, ...enUniversalEditor,
};

const pt = {
  ...ptCommon, ...ptLogin, ...ptSidebar, ...ptNav, ...ptDialog,
  ...ptContent, ...ptAdmin, ...ptConfig, ...ptGraphql, ...ptUniversalEditor,
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
