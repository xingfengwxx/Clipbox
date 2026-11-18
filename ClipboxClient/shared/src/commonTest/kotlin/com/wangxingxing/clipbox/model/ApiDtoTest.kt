/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : REST 与 WebSocket DTO 契约测试；字段名必须与 api.md 一致，墓碑与未知字段不得导致反序列化失败
 */
package com.wangxingxing.clipbox.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val activeClipId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
    private val deletedClipId = "01J8W6Z3K4M5N6P7Q8R9S0T1V2"

    @Test
    fun decodesPairResponseAndIgnoresUnknownFields() {
        val payload = """
            {
              "deviceId": "$deviceId",
              "token": "test-token",
              "deviceName": "Pixel 8",
              "futureField": {"nested": true}
            }
        """.trimIndent()

        val dto = json.decodeFromString<PairResponse>(payload)

        assertEquals(deviceId, dto.deviceId)
        assertEquals("test-token", dto.token)
        assertEquals("Pixel 8", dto.deviceName)
    }

    @Test
    fun decodesSyncResponseWithTombstoneAndUnknownFields() {
        val payload = """
            {
              "items": [
                {
                  "id": "$activeClipId",
                  "content": "hello",
                  "sourceDeviceId": "$deviceId",
                  "sourceDeviceName": "Pixel 8",
                  "pinned": false,
                  "createdAt": 1730000000000,
                  "serverSeq": 41,
                  "deleted": false,
                  "futureField": "ignored"
                },
                {
                  "id": "$deletedClipId",
                  "serverSeq": 42,
                  "deleted": true
                }
              ],
              "nextSince": 42,
              "hasMore": false,
              "resyncRequired": false,
              "minAvailableSeq": 1,
              "serverMaxSeq": 42,
              "futureEnvelope": [1, 2, 3]
            }
        """.trimIndent()

        val dto = json.decodeFromString<SyncResponse>(payload)

        assertEquals(2, dto.items.size)
        val active = dto.items[0]
        assertEquals(activeClipId, active.id)
        assertEquals("hello", active.content)
        assertEquals(deviceId, active.sourceDeviceId)
        assertEquals("Pixel 8", active.sourceDeviceName)
        assertFalse(active.pinned!!)
        assertEquals(1730000000000L, active.createdAt)
        assertEquals(41L, active.serverSeq)
        assertFalse(active.deleted)

        val tombstone = dto.items[1]
        assertEquals(deletedClipId, tombstone.id)
        assertEquals(42L, tombstone.serverSeq)
        assertTrue(tombstone.deleted)
        assertNull(tombstone.content)
        assertNull(tombstone.sourceDeviceId)
        assertNull(tombstone.sourceDeviceName)
        assertNull(tombstone.pinned)
        assertNull(tombstone.createdAt)

        assertEquals(42L, dto.nextSince)
        assertFalse(dto.hasMore)
        assertFalse(dto.resyncRequired)
        assertEquals(1L, dto.minAvailableSeq)
        assertEquals(42L, dto.serverMaxSeq)
    }

    @Test
    fun decodesPairingCodeResponseAndEncodesBootstrapRequest() {
        val pairingCode = json.decodeFromString<PairingCodeResponse>(
            """
                {
                  "code": "654321",
                  "expiresAt": 1730000600000,
                  "futureField": true
                }
            """.trimIndent()
        )
        assertEquals("654321", pairingCode.code)
        assertEquals(1730000600000L, pairingCode.expiresAt)

        val bootstrap = json.parseToJsonElement(
            json.encodeToString(BootstrapRequest(deviceName = "Pixel 8"))
        ).jsonObject
        assertEquals("Pixel 8", bootstrap["deviceName"]?.jsonPrimitive?.content)
    }

    @Test
    fun decodesWriteAndShareResponses() {
        val create = json.decodeFromString<CreateClipResponse>(
            """
                {
                  "id": "$activeClipId",
                  "serverSeq": 41,
                  "createdAt": 1730000000000,
                  "pinned": false,
                  "deleted": false
                }
            """.trimIndent()
        )
        assertEquals(activeClipId, create.id)
        assertEquals(41L, create.serverSeq)
        assertEquals(1730000000000L, create.createdAt)
        assertFalse(create.pinned)
        assertFalse(create.deleted)

        val patch = json.decodeFromString<PatchPinnedResponse>(
            """
                {
                  "id": "$activeClipId",
                  "pinned": true,
                  "serverSeq": 42,
                  "deleted": false
                }
            """.trimIndent()
        )
        assertTrue(patch.pinned)
        assertEquals(42L, patch.serverSeq)

        val delete = json.decodeFromString<DeleteClipResponse>(
            """
                {
                  "id": "$activeClipId",
                  "deleted": true,
                  "serverSeq": 43
                }
            """.trimIndent()
        )
        assertTrue(delete.deleted)
        assertEquals(43L, delete.serverSeq)

        val share = json.decodeFromString<CreateShareResponse>(
            """
                {
                  "token": "test-share-token",
                  "url": "https://share.test/s/test-share-token",
                  "expiresAt": 1730086400000
                }
            """.trimIndent()
        )
        assertEquals("test-share-token", share.token)
        assertEquals("https://share.test/s/test-share-token", share.url)
        assertEquals(1730086400000L, share.expiresAt)
    }

    @Test
    fun encodesRequestsWithContractFieldNames() {
        val pair = json.parseToJsonElement(
            json.encodeToString(PairRequest(code = "123456", deviceName = "Pixel 8"))
        ).jsonObject
        assertEquals("123456", pair["code"]?.jsonPrimitive?.content)
        assertEquals("Pixel 8", pair["deviceName"]?.jsonPrimitive?.content)

        val createClip = json.parseToJsonElement(
            json.encodeToString(
                CreateClipRequest(id = activeClipId, content = "hello", createdAt = 1730000000000)
            )
        ).jsonObject
        assertEquals(activeClipId, createClip["id"]?.jsonPrimitive?.content)
        assertEquals("hello", createClip["content"]?.jsonPrimitive?.content)
        assertEquals(1730000000000L, createClip["createdAt"]?.jsonPrimitive?.long)

        val patch = json.parseToJsonElement(
            json.encodeToString(PatchPinnedRequest(pinned = true))
        ).jsonObject
        assertTrue(patch["pinned"]?.jsonPrimitive?.boolean == true)

        val share = json.parseToJsonElement(
            json.encodeToString(
                CreateShareRequest(clipId = activeClipId, ttlSeconds = 86_400, oneTime = true)
            )
        ).jsonObject
        assertEquals(activeClipId, share["clipId"]?.jsonPrimitive?.content)
        assertEquals(86_400L, share["ttlSeconds"]?.jsonPrimitive?.long)
        assertTrue(share["oneTime"]?.jsonPrimitive?.boolean == true)

        val auth = json.parseToJsonElement(
            json.encodeToString(WsAuthRequest.auth("test-token"))
        ).jsonObject
        assertEquals(WsMessageType.AUTH, auth["type"]?.jsonPrimitive?.content)
        assertEquals("test-token", auth["token"]?.jsonPrimitive?.content)
    }

    @Test
    fun decodesErrorEnvelopeAndMapsErrorCode() {
        val payload = """
            {
              "error": {
                "code": "PAYLOAD_TOO_LARGE",
                "message": "content exceeds 65536 UTF-8 bytes",
                "futureField": true
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<ApiErrorEnvelope>(payload)

        assertEquals("PAYLOAD_TOO_LARGE", dto.error.code)
        assertEquals(
            ApiErrorCode.PAYLOAD_TOO_LARGE,
            ApiErrorCode.fromWire(dto.error.code)
        )
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromWire("FUTURE_CODE"))
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromWire(null))
    }

    @Test
    fun decodesWebSocketFrames() {
        val authOk = json.decodeFromString<WsAuthOk>(
            """
                {
                  "type": "auth.ok",
                  "deviceId": "$deviceId",
                  "serverSeq": 42
                }
            """.trimIndent()
        )
        assertEquals(WsMessageType.AUTH_OK, authOk.type)
        assertEquals(deviceId, authOk.deviceId)
        assertEquals(42L, authOk.serverSeq)

        val created = json.decodeFromString<WsClipEvent>(
            """
                {
                  "type": "clip.created",
                  "seq": 43,
                  "data": {
                    "id": "$activeClipId",
                    "content": "hello",
                    "sourceDeviceId": "$deviceId",
                    "sourceDeviceName": "Pixel 8",
                    "pinned": false,
                    "createdAt": 1730000000000,
                    "serverSeq": 43,
                    "deleted": false
                  }
                }
            """.trimIndent()
        )
        assertEquals(WsMessageType.CLIP_CREATED, created.type)
        assertEquals(43L, created.seq)
        assertEquals("hello", created.data.content)

        val deleted = json.decodeFromString<WsClipEvent>(
            """
                {
                  "type": "clip.deleted",
                  "seq": 45,
                  "data": {
                    "id": "$deletedClipId",
                    "serverSeq": 45,
                    "deleted": true
                  }
                }
            """.trimIndent()
        )
        assertEquals(WsMessageType.CLIP_DELETED, deleted.type)
        assertEquals(45L, deleted.seq)
        assertTrue(deleted.data.deleted)
        assertNull(deleted.data.content)

        val error = json.decodeFromString<WsErrorFrame>(
            """
                {
                  "type": "error",
                  "error": {
                    "code": "UNAUTHORIZED",
                    "message": "invalid token"
                  }
                }
            """.trimIndent()
        )
        assertEquals(WsMessageType.ERROR, error.type)
        assertEquals(ApiErrorCode.UNAUTHORIZED, ApiErrorCode.fromWire(error.error.code))
    }
}
