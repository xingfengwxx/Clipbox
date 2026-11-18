<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const steps = ['step1', 'step2', 'step3'] as const
</script>

<template>
  <section id="how" class="section" aria-labelledby="how-title">
    <div class="container">
      <div class="section__head">
        <h2 id="how-title" class="section__title">{{ t('how.title') }}</h2>
        <p class="section__subtitle">{{ t('how.subtitle') }}</p>
      </div>
      <ol class="steps">
        <li v-for="key in steps" :key="key" class="step">
          <h3 class="step__title">{{ t(`how.${key}.title`) }}</h3>
          <p class="step__desc">{{ t(`how.${key}.desc`) }}</p>
        </li>
      </ol>
    </div>
  </section>
</template>
