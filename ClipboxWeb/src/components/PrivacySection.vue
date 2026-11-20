<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { VERSION } from '../data/downloads'

const { t } = useI18n()

const points = ['account', 'token', 'logs', 'sdk'] as const
const notDoing = ['push', 'bgClipboard', 'favorites', 'tray', 'e2ee', 'transparent'] as const
</script>

<template>
  <section id="privacy" class="section" aria-labelledby="privacy-title">
    <div class="container">
      <div class="section__head">
        <h2 id="privacy-title" class="section__title">{{ t('privacy.title') }}</h2>
        <p class="section__subtitle">{{ t('privacy.subtitle') }}</p>
      </div>

      <ul class="hairline-grid privacy">
        <li v-for="key in points" :key="key" class="privacy__item">
          <h3 class="privacy__title">{{ t(`privacy.${key}.title`) }}</h3>
          <p class="privacy__desc">{{ t(`privacy.${key}.desc`) }}</p>
        </li>
        <li class="privacy__item privacy__item--wide privacy__item--warn">
          <h3 class="privacy__title">{{ t('privacy.plain.title') }}</h3>
          <p class="privacy__desc">{{ t('privacy.plain.desc', { version: VERSION }) }}</p>
        </li>
      </ul>

      <div class="not-doing">
        <h3 class="not-doing__title">{{ t('privacy.notDoing.title', { version: VERSION }) }}</h3>
        <ul class="not-doing__list">
          <li v-for="key in notDoing" :key="key">{{ t(`privacy.notDoing.${key}`) }}</li>
        </ul>
      </div>
    </div>
  </section>
</template>
