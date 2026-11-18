/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 仓储基础读写测试：五个仓储各写入一行并读回，校验列映射与 0/1 布尔转换。
 */
package com.wangxingxing.clipbox.server.db

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir

class RepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `repositories read back the rows they write`() = runBlocking {
        DatabaseFactory.open(tempDir).use { factory ->
            factory.migrate()

            factory.writeTransaction {
                DeviceRepository.insert(
                    DeviceRow(
                        id = "device-1",
                        name = "Pixel 8",
                        tokenHash = "device-hash",
                        createdAt = 1_000,
                        lastSeenAt = null,
                    ),
                )
                DeviceRepository.touchLastSeen("device-1", 1_500)
                ClipRepository.insert(
                    ClipRow(
                        id = "clip-1",
                        content = "hello",
                        sourceDeviceId = "device-1",
                        pinned = true,
                        createdAt = 2_000,
                        deletedAt = null,
                        serverSeq = 1,
                    ),
                )
                PairingRepository.upsertCode(
                    deviceId = "device-1",
                    codeHash = "pairing-hash",
                    createdAt = 3_000,
                    expiresAt = 3_600,
                )
                PairingRepository.markUsedIfUnused("pairing-hash", usedAt = 3_300)
                ShareRepository.insert(
                    ShareRow(
                        tokenHash = "share-hash",
                        clipId = "clip-1",
                        content = "hello",
                        createdAt = 4_000,
                        expiresAt = 4_600,
                        oneTime = true,
                        openedAt = null,
                    ),
                )
                MetaRepository.put("custom-key", "custom-value")
            }

            factory.readTransaction {
                val device = assertNotNull(DeviceRepository.findById("device-1"))
                assertEquals("Pixel 8", device.name)
                assertEquals(1_500, device.lastSeenAt)
                assertNotNull(DeviceRepository.findByTokenHash("device-hash"))

                val clip = assertNotNull(ClipRepository.findById("clip-1"))
                assertEquals("device-1", clip.sourceDeviceId)
                assertEquals(true, clip.pinned)
                assertNull(clip.deletedAt)
                assertEquals(1, clip.serverSeq)

                val pairing = assertNotNull(PairingRepository.findByCodeHash("pairing-hash"))
                assertEquals("device-1", pairing.deviceId)
                assertEquals(3_300, pairing.usedAt)

                val share = assertNotNull(ShareRepository.findByTokenHash("share-hash"))
                assertEquals("clip-1", share.clipId)
                assertEquals(true, share.oneTime)
                assertNull(share.openedAt)

                assertEquals("custom-value", MetaRepository.get("custom-key"))
            }
        }
    }

    @Test
    fun `foreign keys are enforced on pooled connections`() = runBlocking {
        DatabaseFactory.open(tempDir).use { factory ->
            factory.migrate()

            assertFails {
                factory.writeTransaction {
                    ClipRepository.insert(
                        ClipRow(
                            id = "clip-orphan",
                            content = "orphan",
                            sourceDeviceId = "missing-device",
                            pinned = false,
                            createdAt = 1,
                            deletedAt = null,
                            serverSeq = 1,
                        ),
                    )
                }
            }
        }
        Unit
    }
}
