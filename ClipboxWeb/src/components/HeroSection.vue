<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { VERSION } from '../data/downloads'
import desktop500 from '../assets/shots/desktop-history-500.webp'
import desktop750 from '../assets/shots/desktop-history-750.webp'
import desktop1000 from '../assets/shots/desktop-history-1000.webp'
import phone216 from '../assets/shots/phone-history-216.webp'
import phone432 from '../assets/shots/phone-history-432.webp'

const { t } = useI18n()

const desktopSrcset = `${desktop500} 500w, ${desktop750} 750w, ${desktop1000} 1000w`
const desktopSizes = '(max-width: 768px) 60vw, (max-width: 1024px) 420px, 500px'
const phoneSrcset = `${phone216} 216w, ${phone432} 432w`
</script>

<template>
  <section id="top" class="hero">
    <div class="hero__inner container">
      <link
        rel="preload"
        as="image"
        :href="desktop1000"
        :imagesrcset="desktopSrcset"
        :imagesizes="desktopSizes"
      />
      <link rel="preload" as="image" :href="phone432" :imagesrcset="phoneSrcset" />

      <div class="hero__copy">
        <h1 class="hero__title">{{ t('hero.title') }}</h1>
        <p class="hero__subtitle">{{ t('hero.subtitle') }}</p>
        <div class="hero__actions">
          <a class="button-primary" href="#download">{{ t('hero.primary', { version: VERSION }) }}</a>
          <a class="button-secondary" href="#features">{{ t('hero.secondary') }}</a>
        </div>
        <p class="mono hero__meta">{{ t('hero.meta', { version: VERSION }) }}</p>
      </div>

      <figure class="relay">
        <figcaption class="mono relay__status">
          <span class="relay__dot" aria-hidden="true"></span>
          {{ t('hero.status') }}
        </figcaption>
        <div class="relay__frames">
          <div class="relay__desktop">
            <img
              :src="desktop1000"
              :srcset="desktopSrcset"
              :sizes="desktopSizes"
              width="1000"
              height="738"
              fetchpriority="high"
              :alt="t('hero.desktopAlt')"
            />
          </div>
          <span class="relay__hand" aria-hidden="true"></span>
          <div class="relay__phone">
            <img
              :src="phone432"
              :srcset="phoneSrcset"
              sizes="(max-width: 480px) 104px, (max-width: 900px) 132px, 180px"
              width="432"
              height="912"
              fetchpriority="high"
              :alt="t('hero.phoneAlt')"
            />
          </div>
        </div>
      </figure>
    </div>
  </section>
</template>
