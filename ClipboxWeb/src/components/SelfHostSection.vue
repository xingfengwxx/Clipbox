<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const steps = ['step1', 'step2', 'step3', 'step4'] as const
const requirements = ['docker', 'resources', 'port', 'domain'] as const

// 片段与仓库 docker-compose.yml 对应段落逐行一致；改部署文件时同步这里与 downloads 示例。
const commandsSnippet = `cp theca.env.example .env
mkdir -p data && sudo chown -R 10001:10001 data
docker compose up -d --build
curl http://<SERVER_IP>:8080/healthz`

const composeSnippet = `services:
  clipbox:
    build:
      context: .
      dockerfile: ClipboxServer/Dockerfile
    ports:
      - "8080:8080"
    environment:
      DATA_DIR: /data
      ADMIN_TOKEN: "\${ADMIN_TOKEN}"
      PUBLIC_BASE_URL: "\${PUBLIC_BASE_URL}"
    volumes:
      - ./data:/data
      - ./downloads:/app/downloads:ro
    read_only: true
    tmpfs:
      - /tmp:exec
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"`

const envSnippet = `ADMIN_TOKEN=<REPLACE_WITH_OPENSSL_RAND_BASE64_48_OUTPUT>
PUBLIC_BASE_URL=http://<SERVER_IP>:8080`
</script>

<template>
  <section id="selfhost" class="section" aria-labelledby="selfhost-title">
    <div class="container">
      <div class="section__head">
        <h2 id="selfhost-title" class="section__title">{{ t('selfhost.title') }}</h2>
        <p class="section__subtitle">{{ t('selfhost.subtitle') }}</p>
      </div>

      <div class="selfhost">
        <div class="selfhost__intro">
          <p class="selfhost__model">{{ t('selfhost.model') }}</p>
          <p class="mono selfhost__binary">{{ t('selfhost.noBinary') }}</p>

          <h3 class="selfhost__subtitle">{{ t('selfhost.req.title') }}</h3>
          <ul class="selfhost__req">
            <li v-for="key in requirements" :key="key">{{ t(`selfhost.req.${key}`) }}</li>
          </ul>

          <ol class="selfhost__steps">
            <li v-for="key in steps" :key="key">
              <p class="selfhost__step-title">{{ t(`selfhost.${key}.title`) }}</p>
              <p class="selfhost__step-desc">{{ t(`selfhost.${key}.desc`) }}</p>
            </li>
          </ol>
        </div>

        <div class="selfhost__refs">
          <h3>{{ t('selfhost.commandsTitle') }}</h3>
          <pre class="code"><code>{{ commandsSnippet }}</code></pre>

          <h3>{{ t('selfhost.composeTitle') }}</h3>
          <pre class="code"><code>{{ composeSnippet }}</code></pre>

          <h3>{{ t('selfhost.envTitle') }}</h3>
          <pre class="code"><code>{{ envSnippet }}</code></pre>

          <div class="selfhost__downloads">
            <a
              class="button-secondary"
              href="/downloads/theca-docker-compose.example.yml"
              download
            >
              {{ t('selfhost.composeDownload') }}
            </a>
            <a class="button-secondary" href="/downloads/theca.env.example" download>
              {{ t('selfhost.envDownload') }}
            </a>
          </div>
        </div>
      </div>

      <p class="selfhost__warning">{{ t('selfhost.warning') }}</p>
    </div>
  </section>
</template>
