export const VERSION = '1.0.0'

export type PlatformId = 'android' | 'ios' | 'macos' | 'windows' | 'linux'

export interface PlatformDownload {
  id: PlatformId
  available: boolean
  file?: string
  sizeBytes?: number
  sha256?: string
}

// 大小与哈希来自 downloads/ 实测产物（2026-11-18）；重新打包后必须同步更新，页面不臆造数值。
export const platforms: PlatformDownload[] = [
  {
    id: 'android',
    available: true,
    file: 'Theca-v1.0.0-android-release.apk',
    sizeBytes: 16030091,
    sha256: 'a8a3324bf8e0aff61d4aef6017ba5ede372c70d856a752868f7cd3a15ddb7f55',
  },
  {
    id: 'ios',
    available: false,
  },
  {
    id: 'macos',
    available: true,
    file: 'Theca-v1.0.0-macos-arm64-release.dmg',
    sizeBytes: 83019219,
    sha256: '9a840108438b9f19b828f4d5cb38df0d6803780e8dc34710b59db6ea138167d3',
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
