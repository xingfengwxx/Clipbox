<script setup lang="ts">
import { watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import SiteHeader from './components/SiteHeader.vue'
import HeroSection from './components/HeroSection.vue'
import FeatureGrid from './components/FeatureGrid.vue'
import HowItWorks from './components/HowItWorks.vue'
import PrivacySection from './components/PrivacySection.vue'
import FaqSection from './components/FaqSection.vue'
import SiteFooter from './components/SiteFooter.vue'

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
    <HeroSection />
    <FeatureGrid />
    <HowItWorks />
    <PrivacySection />
    <FaqSection />
  </main>

  <SiteFooter />
</template>
