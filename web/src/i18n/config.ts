import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import en from './locales/en.json'
import zh from './locales/zh.json'

/**
 * Initializes i18next for the browser app. Language preference is restored from
 * localStorage first; new visitors default to Chinese so localized API labels
 * render with the product's primary language.
 */
i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      zh: { translation: zh },
    },
    fallbackLng: 'zh',
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['localStorage'],
      lookupLocalStorage: 'skillhubLng',
      caches: ['localStorage'],
    },
  })

export default i18n
