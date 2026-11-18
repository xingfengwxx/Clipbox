export const VERSION = '1.0.0'

export type PlatformId = 'android' | 'ios' | 'macos' | 'windows' | 'linux'

export interface PlatformDownload {
  id: PlatformId
  available: boolean
  file?: string
  sizeBytes?: number
  sha256?: string
}

// 大小与哈希来自 downloads/ 实测产物（2026-11-18，T-REL-15 配对改造后重建）；重新打包后必须同步更新，页面不臆造数值。
export const platforms: PlatformDownload[] = [
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

export function formatSize(bytes: number): string {
  const mb = bytes / (1024 * 1024)
  return `${mb.toFixed(1)} MB`
}
