<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, setLocale, type Locale } from '../i18n'

const { t, locale } = useI18n()

const localeOptions: Locale[] = [...SUPPORTED_LOCALES]

const links = [
  { id: 'features', key: 'nav.features' },
  { id: 'how', key: 'nav.how' },
  { id: 'download', key: 'nav.download' },
  { id: 'selfhost', key: 'nav.selfhost' },
  { id: 'faq', key: 'nav.faq' },
] as const

const menu = ref<HTMLDetailsElement | null>(null)

// 窄屏菜单用原生 details，不做依赖 JS 的弹层；锚点跳转后手动收起，以免面板盖住目标区块。
function closeMenu() {
  if (menu.value) menu.value.open = false
}
</script>

<template>
  <header class="site-header">
    <div class="site-header__inner container">
      <a class="brand" href="#top" aria-label="字匣 Theca">
        <svg class="brand__mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
          <path
            class="brand__mark-ink"
            d="M15.2 5H6a3 3 0 0 0-3 3v16a3 3 0 0 0 3 3h20a3 3 0 0 0 3-3V15.2h-2V25H5V7h10.2V5Z"
          />
          <path class="brand__mark-line" d="M16 16V25M5 16H27" fill="none" stroke-width="1.6" />
          <rect class="brand__mark-accent" x="17" y="1" width="11" height="11" rx="1.5" />
        </svg>
        <span class="brand__zh">字匣</span>
        <span class="brand__en">Theca</span>
      </a>

      <nav class="site-nav" :aria-label="t('nav.label')">
        <a v-for="link in links" :key="link.id" :href="`#${link.id}`">{{ t(link.key) }}</a>
      </nav>

      <details ref="menu" class="menu">
        <summary class="menu__toggle">{{ t('nav.menu') }}</summary>
        <nav class="menu__panel" :aria-label="t('nav.label')">
          <a
            v-for="link in links"
            :key="link.id"
            :href="`#${link.id}`"
            @click="closeMenu"
          >
            {{ t(link.key) }}
          </a>
        </nav>
      </details>

      <div class="lang-switch" role="group" :aria-label="t('lang.group')">
        <button
          v-for="code in localeOptions"
          :key="code"
          type="button"
          :aria-pressed="locale === code"
          @click="setLocale(code)"
        >
          {{ t(`lang.${code}`) }}
        </button>
      </div>

      <a class="header-cta" href="#download">{{ t('nav.cta') }}</a>
    </div>
  </header>
</template>
