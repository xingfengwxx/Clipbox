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
    file: 'Theca-1.0.0-android.apk',
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
    file: 'Theca-1.0.0-macos-arm64.dmg',
    sizeBytes: 83276402,
    sha256: '75b91ae0d07c9fab4e26a0cec338cbc36879cbb19233aaa6c0f3943bff90fd47',
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
