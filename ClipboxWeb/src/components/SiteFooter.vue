<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { VERSION } from '../data/downloads'

const { t } = useI18n()
</script>

<template>
  <footer class="site-footer">
    <div class="site-footer__inner container">
      <div class="site-footer__group">
        <p class="site-footer__tagline">{{ t('footer.tagline') }}</p>
        <p class="mono">{{ t('footer.copyright') }}</p>
      </div>
      <div class="site-footer__group site-footer__group--end">
        <p class="mono">{{ t('footer.version', { version: VERSION }) }}</p>
        <p class="mono site-footer__notice">{{ t('footer.notice') }}</p>
      </div>
    </div>
  </footer>
</template>
