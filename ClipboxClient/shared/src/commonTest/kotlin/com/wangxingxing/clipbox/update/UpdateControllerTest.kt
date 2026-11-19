/**
 * author : 王星星
 * date : 2026-11-19 11:20
 * email : 1099420259@qq.com
 * description : 更新控制器测试；覆盖自动检查每进程一次、开关跳过、静默失败、红点、下载 / 校验 / 安装状态迁移与重试
 */
package com.wangxingxing.clipbox.update

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OFFICIAL_URL = "http://official.local:8080"
private const val ZERO_SHA = "0000000000000000000000000000000000000000000000000000000000000000"

class UpdateControllerTest {

    private fun api(
        requests: MutableList<HttpRequestData> = mutableListOf(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): UpdateApi {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return UpdateApi(OFFICIAL_URL, engine)
    }

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
    )

    private fun updateBody(version: String = "1.1.0", androidAvailable: Boolean = true): String {
        val android = if (androidAvailable) {
            """
            {
              "id": "android",
              "available": true,
              "file": "Theca-v1.1.0-android-release.apk",
              "sizeBytes": 2048,
              "sha256": "$ZERO_SHA",
              "downloadUrl": "http://203.0.113.10:8080/downloads/Theca-v1.1.0-android-release.apk"
            }
            """.trimIndent()
        } else {
            """{"id": "android", "available": false}"""
        }
        return """
            {
              "version": "$version",
              "publishedAt": "2026-11-19T08:00:00Z",
              "notes": {"zh-CN": "简中", "zh-TW": "繁中", "en": "English"},
              "platforms": [
                $android,
                {"id": "ios", "available": false},
                {"id": "macos", "available": false},
                {"id": "windows", "available": false},
                {"id": "linux", "available": false}
              ]
            }
        """.trimIndent()
    }

    private fun controller(
        scope: CoroutineScope,
        api: UpdateApi,
        installer: UpdateInstaller? = FakeInstaller(),
        currentVersion: String = "1.0.0",
        autoCheckEnabled: Boolean = true,
    ) = UpdateController(
        scope = scope,
        currentVersion = currentVersion,
        installer = installer,
        createApi = { api },
        autoCheckEnabled = { autoCheckEnabled },
    )

    private suspend fun readyController(controller: UpdateController) {
        controller.checkManually().join()
        controller.startDownload()?.join()
    }

    @Test
    fun autoCheckRunsOncePerProcess() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val controller = controller(this, api(requests) { jsonResponse(updateBody()) })

        controller.onAppStart()?.join()

        assertEquals(1, requests.size)
        assertIs<UpdateUiState.Available>(controller.state.value)
        assertTrue(controller.hasUnseenUpdate.value)

        assertNull(controller.onAppStart())
        assertEquals(1, requests.size)
    }

    @Test
    fun disabledSwitchSkipsAutoCheckButKeepsManualCheck() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val controller = controller(
            scope = this,
            api = api(requests) { jsonResponse(updateBody()) },
            autoCheckEnabled = false,
        )

        controller.onAppStart()?.join()

        assertEquals(0, requests.size)
        assertEquals(UpdateUiState.Idle, controller.state.value)

        controller.checkManually().join()

        assertEquals(1, requests.size)
        assertIs<UpdateUiState.Available>(controller.state.value)
    }

    @Test
    fun autoCheckFailureIsSilent() = runTest {
        val controller = controller(
            scope = this,
            api = api { jsonResponse("""{"error":{"code":"SERVICE_UNAVAILABLE"}}""", HttpStatusCode.ServiceUnavailable) },
        )

        controller.onAppStart()?.join()

        assertEquals(UpdateUiState.Idle, controller.state.value)
        assertFalse(controller.hasUnseenUpdate.value)
    }

    @Test
    fun manualCheckFailureExposesReason() = runTest {
        val unavailable = controller(
            scope = this,
            api = api { jsonResponse("""{"error":{"code":"SERVICE_UNAVAILABLE"}}""", HttpStatusCode.ServiceUnavailable) },
        )
        unavailable.checkManually().join()
        assertEquals(
            UpdateError.SERVICE_UNAVAILABLE,
            assertIs<UpdateUiState.Failed>(unavailable.state.value).error,
        )

        val offline = controller(scope = this, api = api { throw IOException("offline") })
        offline.checkManually().join()
        assertEquals(UpdateError.OFFLINE, assertIs<UpdateUiState.Failed>(offline.state.value).error)
    }

    @Test
    fun malformedRemoteVersionFailsManualCheckButStaysSilentForAuto() = runTest {
        val manual = controller(scope = this, api = api { jsonResponse(updateBody(version = "latest")) })
        manual.checkManually().join()
        assertEquals(UpdateError.INVALID_RESPONSE, assertIs<UpdateUiState.Failed>(manual.state.value).error)
        assertFalse(manual.hasUnseenUpdate.value)

        val automatic = controller(scope = this, api = api { jsonResponse(updateBody(version = "latest")) })
        automatic.onAppStart()?.join()
        assertEquals(UpdateUiState.Idle, automatic.state.value)
    }

    @Test
    fun openingDialogClearsRedDotWithoutLosingResult() = runTest {
        val controller = controller(scope = this, api = api { jsonResponse(updateBody()) })
        controller.checkManually().join()
        val checked = controller.state.value
        assertTrue(controller.hasUnseenUpdate.value)

        controller.onDialogOpened()

        assertFalse(controller.hasUnseenUpdate.value)
        assertEquals(checked, controller.state.value)
    }

    @Test
    fun sameVersionIsLatest() = runTest {
        val controller = controller(
            scope = this,
            api = api { jsonResponse(updateBody(version = "1.0.0")) },
            currentVersion = "1.0.0",
        )
        controller.checkManually().join()

        assertIs<UpdateUiState.Latest>(controller.state.value)
        assertFalse(controller.hasUnseenUpdate.value)
    }

    @Test
    fun missingPlatformArtifactIsUnsupportedWithRedDot() = runTest {
        val noInstaller = controller(scope = this, api = api { jsonResponse(updateBody()) }, installer = null)
        noInstaller.checkManually().join()
        assertIs<UpdateUiState.Unsupported>(noInstaller.state.value)
        assertTrue(noInstaller.hasUnseenUpdate.value)

        val unavailableArtifact = controller(
            scope = this,
            api = api { jsonResponse(updateBody(androidAvailable = false)) },
        )
        unavailableArtifact.checkManually().join()
        assertIs<UpdateUiState.Unsupported>(unavailableArtifact.state.value)
    }

    @Test
    fun downloadMovesThroughDownloadingAndVerifyingToReady() = runTest {
        val installer = FakeInstaller()
        val controller = controller(scope = this, api = api { jsonResponse(updateBody()) }, installer = installer)
        val downloadStates = mutableListOf<UpdateUiState>()
        val verifyStates = mutableListOf<UpdateUiState>()
        installer.onDownloadProgress = { downloadStates += controller.state.value }
        installer.onVerify = { verifyStates += controller.state.value }

        controller.checkManually().join()
        assertIs<UpdateUiState.Available>(controller.state.value)

        controller.startDownload()?.join()

        assertIs<UpdateUiState.Ready>(controller.state.value)
        assertEquals(1, installer.downloadCalls)
        assertEquals(1, installer.verifyCalls)
        assertTrue(downloadStates.isNotEmpty() && downloadStates.all { it is UpdateUiState.Downloading })
        assertTrue(verifyStates.isNotEmpty() && verifyStates.all { it is UpdateUiState.Verifying })
        val progress = downloadStates.filterIsInstance<UpdateUiState.Downloading>().last()
        assertEquals(100L, progress.downloadedBytes)
        assertEquals(100L, progress.totalBytes)
    }

    @Test
    fun downloadFailureAndChecksumMismatchEndInFailed() = runTest {
        val downloadFailure = controller(
            scope = this,
            api = api { jsonResponse(updateBody()) },
            installer = FakeInstaller(downloadSucceeds = false),
        )
        downloadFailure.checkManually().join()
        downloadFailure.startDownload()?.join()
        assertEquals(
            UpdateError.DOWNLOAD_FAILED,
            assertIs<UpdateUiState.Failed>(downloadFailure.state.value).error,
        )

        val checksumFailure = controller(
            scope = this,
            api = api { jsonResponse(updateBody()) },
            installer = FakeInstaller(verifySucceeds = false),
        )
        checksumFailure.checkManually().join()
        checksumFailure.startDownload()?.join()
        assertEquals(
            UpdateError.CHECKSUM_MISMATCH,
            assertIs<UpdateUiState.Failed>(checksumFailure.state.value).error,
        )
    }

    @Test
    fun retryAfterDownloadFailureRestartsDownload() = runTest {
        val installer = FakeInstaller(downloadSucceeds = false)
        val controller = controller(scope = this, api = api { jsonResponse(updateBody()) }, installer = installer)
        controller.checkManually().join()
        controller.startDownload()?.join()
        assertIs<UpdateUiState.Failed>(controller.state.value)

        installer.downloadSucceeds = true
        controller.retry()?.join()

        assertIs<UpdateUiState.Ready>(controller.state.value)
        assertEquals(2, installer.downloadCalls)
    }

    @Test
    fun installStartsForReadyAndClearsState() = runTest {
        val installer = FakeInstaller()
        val controller = controller(scope = this, api = api { jsonResponse(updateBody()) }, installer = installer)
        readyController(controller)

        assertEquals(UpdateInstallResult.Started, controller.installNow())

        assertEquals(UpdateUiState.Idle, controller.state.value)
        assertEquals(1, installer.installCalls)
    }

    @Test
    fun permissionRequiredKeepsReadyState() = runTest {
        val installer = FakeInstaller(installResult = UpdateInstallResult.PermissionRequired)
        val controller = controller(scope = this, api = api { jsonResponse(updateBody()) }, installer = installer)
        readyController(controller)

        assertEquals(UpdateInstallResult.PermissionRequired, controller.installNow())

        assertIs<UpdateUiState.Ready>(controller.state.value)
    }

    private class FakeInstaller(
        override val platformId: UpdatePlatformId = UpdatePlatformId.ANDROID,
        var downloadSucceeds: Boolean = true,
        var verifySucceeds: Boolean = true,
        var installResult: UpdateInstallResult = UpdateInstallResult.Started,
    ) : UpdateInstaller {

        var downloadCalls = 0
        var verifyCalls = 0
        var installCalls = 0
        var onDownloadProgress: (() -> Unit)? = null
        var onVerify: (() -> Unit)? = null

        override suspend fun download(
            platform: UpdatePlatform,
            onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        ): Boolean {
            downloadCalls++
            onDownloadProgress?.invoke()
            onProgress(50L, 100L)
            onDownloadProgress?.invoke()
            onProgress(100L, 100L)
            onDownloadProgress?.invoke()
            return downloadSucceeds
        }

        override suspend fun verify(expectedSha256: String): Boolean {
            verifyCalls++
            onVerify?.invoke()
            return verifySucceeds
        }

        override suspend fun install(): UpdateInstallResult {
            installCalls++
            return installResult
        }

        override suspend fun cancel() = Unit

        override suspend fun cleanup() = Unit
    }
}
