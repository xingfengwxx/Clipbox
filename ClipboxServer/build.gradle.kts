import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.shadow)
}

group = "com.wangxingxing.clipbox"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.wangxingxing.clipbox.server.ApplicationKt")
}

// 容器只复制单个可执行 JAR（服务端技术架构 §11.1）；ServiceLoader 元数据（JDBC 驱动、日志实现）
// 必须合并，而 Shadow 9 默认的 EXCLUDE 策略会丢弃待合并的 service 文件，因此显式改为 INCLUDE。
tasks.shadowJar {
    archiveFileName.set("clipbox-server.jar")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    // Netty 各模块各带一份版本清单，INCLUDE 策略下会重复 14 次；追加合并保证版本探测完整。
    append("META-INF/io.netty.versions.properties")
}

tasks.test {
    useJUnitPlatform()
}
