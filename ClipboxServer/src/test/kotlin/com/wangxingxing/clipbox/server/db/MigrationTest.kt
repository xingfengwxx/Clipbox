/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 迁移测试：空库建表、重复执行不重复记录版本、关闭重开后仍保持同一版本、V2 升级保留旧码。
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
            assertEquals(3, factory.migrate())
        }

        withDatabase { connection ->
            assertEquals(
                setOf("schema_version", "devices", "clips", "shares", "meta", "device_pairing_codes"),
                connection.tableNames(),
            )
            assertEquals(
                setOf(
                    "idx_clips_server_seq",
                    "idx_clips_created_at",
                    "idx_clips_retention",
                    "idx_dpc_expires",
                    "idx_shares_expires",
                ),
                connection.indexNames(),
            )
            assertEquals(listOf(1, 2, 3), connection.appliedVersions())
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
        val secondAppliedAt = withDatabase { connection -> connection.appliedAt(2) }

        DatabaseFactory.open(tempDir).use { factory ->
            assertEquals(3, factory.migrate())
        }

        withDatabase { connection ->
            assertEquals(listOf(1, 2, 3), connection.appliedVersions())
            assertEquals(firstAppliedAt, connection.appliedAt(1))
            assertEquals(secondAppliedAt, connection.appliedAt(2))
            assertEquals(6, connection.tableNames().size)
        }
    }

    @Test
    fun `upgrading a v2 database keeps pairing codes and allows anonymous issuers`() {
        seedVersion2Database()

        DatabaseFactory.open(tempDir).use { factory ->
            assertEquals(3, factory.migrate())
        }

        withDatabase { connection ->
            assertEquals(listOf(1, 2, 3), connection.appliedVersions())
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT issuer_id, code_hash, used_at FROM device_pairing_codes",
                ).use { rows ->
                    assertTrue(rows.next(), "V2 的旧码必须保留")
                    assertEquals(ISSUER_ID, rows.getString("issuer_id"))
                    assertEquals("hash-482915", rows.getString("code_hash"))
                    assertEquals(123L, rows.getLong("used_at"))
                    assertTrue(!rows.next(), "旧数据只应保留一行")
                }
            }
            // 开外键校验后仍能写入匿名 issuer，证明 V003 已移除对 devices 的引用。
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.executeUpdate(
                    "INSERT INTO device_pairing_codes(issuer_id, code_hash, created_at, expires_at) " +
                        "VALUES ('anonymous', 'hash-anonymous', 2000, 3000)",
                )
            }
        }
    }

    private fun seedVersion2Database() {
        DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("clipbox.db")}").use { connection ->
            connection.createStatement().use { statement ->
                scriptOf("migrations/V001__init.sql").forEach(statement::execute)
                statement.execute(
                    "INSERT INTO devices(id, name, token_hash, created_at) " +
                        "VALUES ('$ISSUER_ID', 'Host', 'token-hash', 1000)",
                )
                scriptOf("migrations/V002__device_pairing_codes.sql").forEach(statement::execute)
                statement.execute(
                    "INSERT INTO device_pairing_codes(device_id, code_hash, created_at, expires_at, used_at) " +
                        "VALUES ('$ISSUER_ID', 'hash-482915', 1000, 2000, 123)",
                )
                statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (1, 1000)")
                statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (2, 1000)")
            }
        }
    }

    private fun scriptOf(resource: String): List<String> {
        val script = requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) {
            "缺少迁移脚本：$resource"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return script.split(';').map { it.trim() }.filter { it.isNotEmpty() }
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

    companion object {
        private const val ISSUER_ID = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    }
}
