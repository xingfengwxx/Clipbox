import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'
import en from './locales/en'

export const SUPPORTED_LOCALES = ['zh-CN', 'zh-TW', 'en'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

export const LOCALE_STORAGE_KEY = 'theca.lang'

export function isLocale(value: string | null | undefined): value is Locale {
  return value !== null && value !== undefined && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

function readStoredLocale(): Locale | null {
  try {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
    return isLocale(stored) ? stored : null
  } catch {
    return null
  }
}

function persistLocale(code: Locale): void {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, code)
  } catch {
    // 隐私模式或存储被禁用时只影响记忆，不影响本次会话
  }
}

// 简中是产品默认语言；繁中只在中文圈地区跟随系统，其余一律回退简中。
function detectLocaleFromNavigator(): Locale {
  const language = navigator.language
  if (/^zh-(TW|HK|MO)/i.test(language)) return 'zh-TW'
  if (/^en(-|$)/i.test(language)) return 'en'
  return 'zh-CN'
}

// ?lang 只覆盖本次访问；显式切换才写入 localStorage，分享出去的链接不会改掉访客偏好。
export function resolveInitialLocale(): Locale {
  const fromQuery = new URLSearchParams(window.location.search).get('lang')
  if (isLocale(fromQuery)) return fromQuery
  const stored = readStoredLocale()
  if (stored) return stored
  return detectLocaleFromNavigator()
}

export const i18n = createI18n({
  legacy: false,
  locale: resolveInitialLocale(),
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
  persistLocale(code)
}
