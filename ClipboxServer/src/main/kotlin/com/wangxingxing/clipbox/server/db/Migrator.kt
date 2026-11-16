/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 自研顺序迁移器：按版本号执行 resources/migrations 下的 SQL，并在同一事务内记录版本。
 *               注意：V001 自身创建 schema_version，迁移器只在读取已应用版本时探测该表，不使用 SchemaUtils。
 */
package com.wangxingxing.clipbox.server.db

import java.sql.Connection
import javax.sql.DataSource

class MigrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

private data class Migration(val version: Int, val resource: String)

private val MIGRATIONS = listOf(
    Migration(version = 1, resource = "migrations/V001__init.sql"),
)

class Migrator(private val dataSource: DataSource) {

    fun migrate(): Int {
        dataSource.connection.use { connection ->
            var currentVersion = readAppliedVersions(connection).maxOrNull() ?: 0
            MIGRATIONS.sortedBy { it.version }.forEach { migration ->
                if (migration.version <= currentVersion) return@forEach
                apply(connection, migration)
                currentVersion = migration.version
            }
            return currentVersion
        }
    }

    private fun apply(connection: Connection, migration: Migration) {
        val script = readScript(migration.resource)
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                script.split(';')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { statement.execute(it) }
            }
            connection.prepareStatement(
                "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)",
            ).use { insert ->
                insert.setInt(1, migration.version)
                insert.setLong(2, System.currentTimeMillis())
                insert.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw MigrationException("迁移 V${migration.version}（${migration.resource}）执行失败", e)
        } finally {
            connection.autoCommit = true
        }
    }

    private fun readAppliedVersions(connection: Connection): List<Int> {
        if (!schemaVersionTableExists(connection)) return emptyList()
        return connection.createStatement().use { statement ->
            statement.executeQuery("SELECT version FROM schema_version").use { rows ->
                buildList {
                    while (rows.next()) add(rows.getInt("version"))
                }
            }
        }
    }

    // SQLite 没有 information_schema，直接查 sqlite_master 判断版本表是否已由迁移创建。
    private fun schemaVersionTableExists(connection: Connection): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'",
        ).use { statement ->
            statement.executeQuery().use { rows -> rows.next() }
        }

    private fun readScript(resource: String): String =
        Migrator::class.java.classLoader.getResourceAsStream(resource)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw MigrationException("找不到迁移脚本：$resource")
}
