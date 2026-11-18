<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const features = ['cross', 'offline', 'share', 'tools'] as const
</script>

<template>
  <section id="features" class="section" aria-labelledby="features-title">
    <div class="container">
      <div class="section__head">
        <h2 id="features-title" class="section__title">{{ t('features.title') }}</h2>
        <p class="section__subtitle">{{ t('features.subtitle') }}</p>
      </div>
      <ul class="hairline-grid features">
        <li v-for="key in features" :key="key" class="feature">
          <h3 class="feature__title">{{ t(`features.${key}.title`) }}</h3>
          <p class="feature__desc">{{ t(`features.${key}.desc`) }}</p>
        </li>
      </ul>
    </div>
  </section>
</template>
