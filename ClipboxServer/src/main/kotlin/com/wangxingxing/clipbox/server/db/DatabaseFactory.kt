/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : SQLite 连接池、Exposed 事务入口与健康探测；写事务由 JVM Mutex 串行化。
 *               注意：写事务内不得嵌套获取同一 Mutex，也不得做网络发送、大对象序列化或等待外部 I/O。
 */
package com.wangxingxing.clipbox.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource

class DatabaseFactory private constructor(
    private val dataSource: HikariDataSource,
    private val database: Database,
) : AutoCloseable {

    private val writeMutex = Mutex()

    /**
     * 执行迁移并返回当前已应用的最大版本。
     * 迁移失败会抛出 [MigrationException]，由启动入口终止进程，避免半启动。
     */
    fun migrate(): Int = Migrator(dataSource).migrate()

    /**
     * 串行化写事务。序号分配与数据写入必须共用同一个事务，不能先分配再另开事务。
     */
    suspend fun <T> writeTransaction(block: JdbcTransaction.() -> T): T =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                suspendTransaction(database) { block() }
            }
        }

    /**
     * 读事务不获取写锁，依靠 SQLite WAL 与连接池并发读取。
     */
    suspend fun <T> readTransaction(block: JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) { block() }
        }

    suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT 1").use { result -> result.next() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        dataSource.close()
    }

    companion object {
        private const val MAX_POOL_SIZE = 4

        fun open(dataDir: Path): DatabaseFactory {
            val databaseFile = dataDir.resolve("clipbox.db")
            // PRAGMA 按连接生效，交给 SQLiteDataSource 保证池中每条连接配置一致。
            val sqliteConfig = SQLiteConfig().apply {
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
                enforceForeignKeys(true)
                setBusyTimeout(5_000)
                setCacheSize(-16_000)
                setTempStore(SQLiteConfig.TempStore.MEMORY)
            }
            val sqliteDataSource = SQLiteDataSource(sqliteConfig).apply {
                url = "jdbc:sqlite:$databaseFile"
            }
            val hikariConfig = HikariConfig().apply {
                dataSource = sqliteDataSource
                maximumPoolSize = MAX_POOL_SIZE
                minimumIdle = 1
                poolName = "clipbox-sqlite"
            }
            val hikariDataSource = HikariDataSource(hikariConfig)
            return DatabaseFactory(hikariDataSource, Database.connect(hikariDataSource))
        }
    }
}
