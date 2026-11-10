package com.ytone.longcare.worker

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ytone.longcare.R
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class DownloadWorkerTest {
    private lateinit var context: Context
    private lateinit var downloadDir: File
    private lateinit var finalFile: File
    private val clients = mutableListOf<OkHttpClient>()
    private val fileName = "update-${UUID.randomUUID()}.apk"
    private val payload = "Downloaded bytes are passed to the system installer".toByteArray()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        downloadDir.mkdirs()
        finalFile = File(downloadDir, fileName)
        finalFile.writeText("previous complete download")
        mockkObject(DiagnosticEventTracker)
        every { DiagnosticEventTracker.currentUserId() } returns "test"
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        if (::downloadDir.isInitialized) {
            downloadDir.listFiles().orEmpty()
                .filter { it.name == fileName || it.name.startsWith(".$fileName.") }
                .forEach { it.delete() }
        }
        clients.forEach { client ->
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
        unmockkObject(DiagnosticEventTracker)
    }

    @Test
    fun `complete download is published without inspecting APK metadata`() = runTest {
        assertPublished(worker(body(payload, payload.size.toLong())).doWork())
    }

    @Test
    fun `unknown length response is published only after normal completion`() = runTest {
        assertPublished(worker(body(payload, -1L)).doWork())
    }

    @Test
    fun `declared length mismatch preserves the previous download`() = runTest {
        for (length in listOf(0L, payload.size - 1L, payload.size + 1L)) {
            val result = worker(body(payload, length)).doWork()

            assertEquals(
                ListenableWorker.Result.failure(workDataOf(
                    DownloadWorker.KEY_ERROR to context.getString(R.string.update_download_incomplete),
                )),
                result,
            )
            assertPreviousDownloadPreserved()
        }
    }

    @Test
    fun `empty responses are rejected with or without a declared length`() = runTest {
        for (length in listOf(0L, -1L)) {
            val result = worker(body(byteArrayOf(), length)).doWork()

            assertEquals(
                ListenableWorker.Result.failure(workDataOf(
                    DownloadWorker.KEY_ERROR to context.getString(R.string.update_empty_response),
                )),
                result,
            )
            assertPreviousDownloadPreserved()
        }
    }

    @Test
    fun `partial HTTP response cannot replace the previous download`() = runTest {
        val result = worker(body(payload, payload.size.toLong()), code = 206).doWork()

        assertEquals(
            ListenableWorker.Result.failure(workDataOf(
                DownloadWorker.KEY_ERROR to context.getString(R.string.update_download_incomplete),
            )),
            result,
        )
        assertPreviousDownloadPreserved()
    }

    @Test
    fun `interrupted stream retries without publishing partial bytes`() = runTest {
        val result = worker(body(payload, -1L, IOException("Connection interrupted"))).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertPreviousDownloadPreserved()
    }

    @Test
    fun `cancellation cleans temporary bytes and propagates cancellation`() = runTest {
        val error = runCatching {
            worker(body(payload, -1L, CancellationException("Download cancelled"))).doWork()
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertPreviousDownloadPreserved()
    }

    private fun assertPublished(result: ListenableWorker.Result) {
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(finalFile.absolutePath, output.getString(DownloadWorker.KEY_FILE_PATH))
        assertEquals(fileName, output.getString(DownloadWorker.KEY_FILE_NAME))
        assertEquals(payload.size.toLong(), output.getLong(DownloadWorker.KEY_FILE_SIZE, -1L))
        assertTrue(payload.contentEquals(finalFile.readBytes()))
        assertNoTemporaryFile()
    }

    private fun assertPreviousDownloadPreserved() {
        assertEquals("previous complete download", finalFile.readText())
        assertNoTemporaryFile()
    }

    private fun assertNoTemporaryFile() {
        assertFalse(downloadDir.listFiles().orEmpty().any { it.name.startsWith(".$fileName.") })
    }

    private fun body(bytes: ByteArray, length: Long, failureAtEnd: Exception? = null): ResponseBody {
        val source = object : ForwardingSource(Buffer().write(bytes)) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read == -1L && failureAtEnd != null) throw failureAtEnd
                return read
            }
        }.buffer()
        return object : ResponseBody() {
            override fun contentType() = "application/vnd.android.package-archive".toMediaType()
            override fun contentLength() = length
            override fun source() = source
        }
    }

    private fun worker(responseBody: ResponseBody, code: Int = 200): DownloadWorker {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Test response")
                .body(responseBody)
                .build()
        }.build().also { clients += it }
        val retrofit = Retrofit.Builder().baseUrl("https://updates.example.test/").client(client).build()
        return TestListenableWorkerBuilder<DownloadWorker>(context)
            .setInputData(workDataOf(
                DownloadWorker.KEY_URL to "https://updates.example.test/update.apk",
                DownloadWorker.KEY_FILE_NAME to fileName,
            ))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = DownloadWorker(appContext, workerParameters, retrofit, Dispatchers.Unconfined)
            })
            .build()
    }
}
