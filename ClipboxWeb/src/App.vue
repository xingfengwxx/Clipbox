<script setup lang="ts">
import { watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, setLocale, type Locale } from './i18n'

const { t, locale } = useI18n()

const localeOptions: Locale[] = [...SUPPORTED_LOCALES]

// 语言、标题与描述随 locale 同步，切换语言不刷新页面。
watchEffect(() => {
  document.documentElement.lang = locale.value
  document.title = t('meta.title')
  const description = document.querySelector('meta[name="description"]')
  if (description) description.setAttribute('content', t('meta.description'))
})
</script>

<template>
  <a class="skip-link" href="#main">{{ t('a11y.skip') }}</a>

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
        <a href="#features">{{ t('nav.features') }}</a>
        <a href="#how">{{ t('nav.how') }}</a>
        <a href="#download">{{ t('nav.download') }}</a>
        <a href="#selfhost">{{ t('nav.selfhost') }}</a>
        <a href="#faq">{{ t('nav.faq') }}</a>
      </nav>

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

  <main id="main">
    <section id="top" class="hero container">
      <h1 class="hero__title">{{ t('hero.title') }}</h1>
      <p class="hero__subtitle">{{ t('hero.subtitle') }}</p>
      <div class="hero__actions">
        <a class="button-primary" href="#download">{{ t('hero.primary') }}</a>
        <a class="button-secondary" href="#features">{{ t('hero.secondary') }}</a>
      </div>
      <p class="mono hero__meta">{{ t('hero.meta') }}</p>
    </section>
  </main>

  <footer class="site-footer">
    <div class="site-footer__inner container">
      <p class="site-footer__tagline">{{ t('footer.tagline') }}</p>
      <p class="mono">{{ t('footer.version') }}</p>
      <p class="mono site-footer__notice">{{ t('footer.notice') }}</p>
    </div>
  </footer>
</template>
