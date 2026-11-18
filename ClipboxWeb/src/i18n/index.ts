import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'
import en from './locales/en'

export const SUPPORTED_LOCALES = ['zh-CN', 'zh-TW', 'en'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

export const LOCALE_STORAGE_KEY = 'theca.lang'

// 简中是产品默认语言；繁中只在中文圈地区跟随系统，其余一律回退简中。
export function detectLocale(): Locale {
  const language = navigator.language
  if (/^zh-(TW|HK|MO)/i.test(language)) return 'zh-TW'
  if (/^en(-|$)/i.test(language)) return 'en'
  return 'zh-CN'
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'zh-TW': zhTW,
    en,
  },
})

export function setLocale(code: Locale): void {
  i18n.global.locale.value = code
  document.documentElement.lang = code
}
