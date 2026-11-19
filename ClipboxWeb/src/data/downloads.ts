export const VERSION = '1.0.0'

export type PlatformId = 'android' | 'ios' | 'macos' | 'windows' | 'linux'

export interface PlatformDownload {
  id: PlatformId
  available: boolean
  file?: string
  sizeBytes?: number
  sha256?: string
}

export interface DownloadManifest {
  schemaVersion: number
  version: string
  platforms: PlatformDownload[]
}

const PLATFORM_ORDER: PlatformId[] = ['android', 'ios', 'macos', 'windows', 'linux']

// 回退种子：仅在运行时清单加载失败时使用，数值可能滞后一个发布版本；正常路径以 downloads.json 为准。
export const seedPlatforms: PlatformDownload[] = [
  {
    id: 'android',
    available: true,
    file: 'Theca-v1.0.0-android-release.apk',
    sizeBytes: 16063967,
    sha256: 'a508a9f9d6991655b7be31d30ca6ae9e7a9b0305e3af8b106f14a2fcf432077c',
  },
  {
    id: 'ios',
    available: false,
  },
  {
    id: 'macos',
    available: true,
    file: 'Theca-v1.0.0-macos-arm64-release.dmg',
    sizeBytes: 83106427,
    sha256: 'c1270ec23b095993a7e1dcc2906e3dde44096172e98785c76133643006fea75a',
  },
  {
    id: 'windows',
    available: false,
  },
  {
    id: 'linux',
    available: false,
  },
]

function isPlatformId(value: unknown): value is PlatformId {
  return typeof value === 'string' && (PLATFORM_ORDER as string[]).includes(value)
}

function isValidPlatform(value: unknown): value is PlatformDownload {
  if (typeof value !== 'object' || value === null) return false
  const entry = value as Record<string, unknown>
  if (!isPlatformId(entry.id) || typeof entry.available !== 'boolean') return false
  if (!entry.available) return true
  // file 会直接拼进下载链接，只接受纯文件名，避免路径逃逸。
  return (
    typeof entry.file === 'string' &&
    /^Theca-[0-9A-Za-z._-]+$/.test(entry.file) &&
    typeof entry.sizeBytes === 'number' &&
    Number.isFinite(entry.sizeBytes) &&
    entry.sizeBytes > 0 &&
    typeof entry.sha256 === 'string' &&
    /^[0-9a-f]{64}$/.test(entry.sha256)
  )
}

function parseManifest(payload: unknown): DownloadManifest | null {
  if (typeof payload !== 'object' || payload === null) return null
  const raw = payload as Record<string, unknown>
  // 服务端与生成脚本都输出 schema 2；旧站点缓存可能仍是 schema 1，两者都接受
  if (raw.schemaVersion !== 1 && raw.schemaVersion !== 2) return null
  const schemaVersion = raw.schemaVersion === 2 ? 2 : 1
  if (typeof raw.version !== 'string' || !/^[0-9A-Za-z._-]+$/.test(raw.version)) return null
  if (!Array.isArray(raw.platforms) || raw.platforms.length !== PLATFORM_ORDER.length) return null
  const valid = raw.platforms.filter(isValidPlatform)
  if (valid.length !== PLATFORM_ORDER.length) return null
  const byId = new Map(valid.map((entry) => [entry.id, entry]))
  const ordered: PlatformDownload[] = []
  for (const id of PLATFORM_ORDER) {
    const entry = byId.get(id)
    if (!entry) return null
    ordered.push(entry)
  }
  return { schemaVersion, version: raw.version, platforms: ordered }
}

export async function loadDownloads(): Promise<DownloadManifest> {
  try {
    const response = await fetch('/downloads/downloads.json', { cache: 'no-store' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const manifest = parseManifest(await response.json())
    if (!manifest) throw new Error('清单字段无效')
    return manifest
  } catch (error) {
    console.warn('downloads.json 不可用，使用内置回退种子', error)
    return { schemaVersion: 1, version: VERSION, platforms: seedPlatforms }
  }
}

export function formatSize(bytes: number): string {
  const mb = bytes / (1024 * 1024)
  return `${mb.toFixed(1)} MB`
}
