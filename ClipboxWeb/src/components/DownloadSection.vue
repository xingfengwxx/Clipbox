<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  VERSION,
  formatSize,
  loadDownloads,
  seedPlatforms,
  type PlatformDownload,
  type PlatformId,
} from '../data/downloads'

const { t } = useI18n()
const version = ref(VERSION)
const platforms = ref<PlatformDownload[]>(seedPlatforms)

onMounted(async () => {
  const manifest = await loadDownloads()
  version.value = manifest.version
  platforms.value = manifest.platforms
})

function buttonLabel(id: PlatformId): string {
  return t(`download.${id}Button`)
}
</script>

<template>
  <section id="download" class="section" aria-labelledby="download-title">
    <div class="container">
      <div class="section__head">
        <h2 id="download-title" class="section__title">{{ t('download.title') }}</h2>
        <p class="section__subtitle">{{ t('download.subtitle') }}</p>
      </div>

      <ul class="dl-list">
        <li v-for="p in platforms" :key="p.id" class="dl-row">
          <div class="dl-row__main">
            <h3 class="dl-row__name">
              {{ t(`download.platform.${p.id}`) }}
              <span class="mono dl-row__status" :class="p.available ? 'is-ok' : 'is-off'">
                <span v-if="p.available" class="dl-row__dot" aria-hidden="true"></span>
                {{ p.available ? t('download.available') : t('download.unavailable') }}
              </span>
            </h3>
            <p class="dl-row__note">{{ t(`download.${p.id}.note`) }}</p>
            <template v-if="p.available">
              <p class="mono dl-row__meta">
                {{ t('download.versionLabel') }} v{{ version }} ·
                {{ t('download.sizeLabel') }} {{ formatSize(p.sizeBytes ?? 0) }}
              </p>
              <p class="mono dl-row__sha">{{ t('download.shaLabel') }}<br />{{ p.sha256 }}</p>
            </template>
          </div>
          <div v-if="p.available" class="dl-row__action">
            <a class="button-primary" :href="`/downloads/${p.file}`" download>
              {{ buttonLabel(p.id) }}
            </a>
          </div>
        </li>
      </ul>

      <div class="dl-foot">
        <a class="mono dl-foot__checksums" href="/downloads/SHA256SUMS.txt" download>
          {{ t('download.checksums') }}
        </a>
        <p class="dl-foot__note">{{ t('download.checksumsNote') }}</p>
      </div>
      <p class="dl-warning">{{ t('download.warning') }}</p>
    </div>
  </section>
</template>
