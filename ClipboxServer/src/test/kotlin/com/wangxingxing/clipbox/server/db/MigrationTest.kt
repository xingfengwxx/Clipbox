/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 迁移测试：空库建表、重复执行不重复记录版本、关闭重开后仍保持同一版本。
 *               注意：全部使用 @TempDir，避免污染仓库与共享数据库。
 */
package com.wangxingxing.clipbox.server.db

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class MigrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `empty database is migrated with schema and meta defaults`() {
        DatabaseFactory.open(tempDir).use { factory ->
            assertEquals(1, factory.migrate())
        }

        withDatabase { connection ->
            assertEquals(
                setOf("schema_version", "devices", "clips", "pairing_codes", "shares", "meta"),
                connection.tableNames(),
            )
            assertEquals(
                setOf(
                    "idx_clips_server_seq",
                    "idx_clips_created_at",
                    "idx_clips_retention",
                    "idx_pairing_expires",
                    "idx_shares_expires",
                ),
                connection.indexNames(),
            )
            assertEquals(listOf(1), connection.appliedVersions())
            assertEquals("0", connection.metaValue("server_seq"))
            assertEquals("0", connection.metaValue("sync_floor"))
        }
    }

    @Test
    fun `migration is idempotent and survives restart`() {
        DatabaseFactory.open(tempDir).use { factory ->
            factory.migrate()
        }
        val firstAppliedAt = withDatabase { connection -> connection.appliedAt(1) }

        DatabaseFactory.open(tempDir).use { factory ->
            assertEquals(1, factory.migrate())
        }

        withDatabase { connection ->
            assertEquals(listOf(1), connection.appliedVersions())
            assertEquals(firstAppliedAt, connection.appliedAt(1))
            assertEquals(6, connection.tableNames().size)
        }
    }

    private fun <T> withDatabase(block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("clipbox.db")}").use(block)

    private fun Connection.tableNames(): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
                buildSet {
                    while (rows.next()) add(rows.getString("name"))
                }
            }
        }

    private fun Connection.indexNames(): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%'").use { rows ->
                buildSet {
                    while (rows.next()) add(rows.getString("name"))
                }
            }
        }

    private fun Connection.appliedVersions(): List<Int> =
        createStatement().use { statement ->
            statement.executeQuery("SELECT version FROM schema_version ORDER BY version").use { rows ->
                buildList {
                    while (rows.next()) add(rows.getInt("version"))
                }
            }
        }

    private fun Connection.appliedAt(version: Int): Long =
        prepareStatement("SELECT applied_at FROM schema_version WHERE version = ?").use { statement ->
            statement.setInt(1, version)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next())
                rows.getLong("applied_at")
            }
        }

    private fun Connection.metaValue(key: String): String? =
        prepareStatement("SELECT value FROM meta WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getString("value") else null
            }
        }
}
