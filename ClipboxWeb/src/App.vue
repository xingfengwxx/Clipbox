<script setup lang="ts">
import { watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import SiteHeader from './components/SiteHeader.vue'

const { t, locale } = useI18n()

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

  <SiteHeader />

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
      <div class="site-footer__group">
        <p class="site-footer__tagline">{{ t('footer.tagline') }}</p>
        <p class="mono">{{ t('footer.copyright') }}</p>
      </div>
      <div class="site-footer__group site-footer__group--end">
        <p class="mono">{{ t('footer.version') }}</p>
        <p class="mono site-footer__notice">{{ t('footer.notice') }}</p>
      </div>
    </div>
  </footer>
</template>
