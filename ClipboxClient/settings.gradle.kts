// CI（GitHub Actions，境外 runner）直接用官方仓库：阿里云镜像在境外不稳定，早期仓库 I/O 失败会中止整次插件解析，官方源兜底不生效
// 本地与国内环境保留阿里云镜像优先以维持构建速度，官方源仍在后兜底
//
// 指向 Google Maven 的仓库必须按实际托管分组精确匹配，不能用 com.google.* 全量：
// KSP 插件 marker（com.google.devtools.ksp）与 protobuf、guava 等只存在于 Maven Central，
// 全量匹配会先把它们路由到 dl.google.com；Gradle 遇到任一仓库的非 404 错误会中止整次插件解析，
// 并报成 “plugin not found”，release CI 曾多次因此卡在 KSP（2026-11-18、2026-11-19）。
pluginManagement {
    val onCi = System.getenv("CI") == "true"
    repositories {
        if (!onCi) {
            maven("https://maven.aliyun.com/repository/google") {
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google\\.android\\..*")
                    includeGroupByRegex("com\\.google\\.(firebase|gms|mlkit|prefab|testing\\.platform|test\\.platform|androidbrowserhelper).*")
                }
            }
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android\\..*")
                includeGroupByRegex("com\\.google\\.(firebase|gms|mlkit|prefab|testing\\.platform|test\\.platform|androidbrowserhelper).*")
            }
        }
        mavenCentral()
        if (onCi) {
            // Maven Central 的 CDN 边缘偶发对 com.google.devtools.ksp 的 marker 返回 404（制品实际存在），
            // 而 Plugin Portal 把 KSP marker 302 回 Central，只有非 Fastly 的镜像能兜底。
            // 使用 Google 官方维护的 Maven Central 全量镜像；本地网络通常不可达，仅 CI 启用。
            maven("https://maven-central.storage-download.googleapis.com/maven2/") {
                name = "MavenCentralGcsMirror"
            }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val onCi = System.getenv("CI") == "true"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!onCi) {
            maven("https://maven.aliyun.com/repository/google") {
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google\\.android\\..*")
                    includeGroupByRegex("com\\.google\\.(firebase|gms|mlkit|prefab|testing\\.platform|test\\.platform|androidbrowserhelper).*")
                }
            }
            maven("https://maven.aliyun.com/repository/public")
        }
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android\\..*")
                includeGroupByRegex("com\\.google\\.(firebase|gms|mlkit|prefab|testing\\.platform|test\\.platform|androidbrowserhelper).*")
            }
        }
        mavenCentral()
        if (onCi) {
            // 与 pluginManagement 同源兜底：Central 边缘 404 时改从镜像取产物。
            maven("https://maven-central.storage-download.googleapis.com/maven2/") {
                name = "MavenCentralGcsMirror"
            }
        }
    }
}

rootProject.name = "Clipbox"

include(":composeApp")
include(":shared")
