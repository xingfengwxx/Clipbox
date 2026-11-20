<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { VERSION } from '../data/downloads'

const { t } = useI18n()

const faqs = [1, 2, 3, 4, 5] as const
</script>

<template>
  <section id="faq" class="section" aria-labelledby="faq-title">
    <div class="container">
      <div class="section__head">
        <h2 id="faq-title" class="section__title">{{ t('faq.title') }}</h2>
      </div>
      <div class="faq">
        <details v-for="n in faqs" :key="n" class="faq__item">
          <summary class="faq__q">
            {{ t(`faq.q${n}`) }}
            <span class="faq__marker" aria-hidden="true"></span>
          </summary>
          <p class="faq__a">{{ t(`faq.a${n}`, { version: VERSION }) }}</p>
        </details>
      </div>
    </div>
  </section>
</template>
