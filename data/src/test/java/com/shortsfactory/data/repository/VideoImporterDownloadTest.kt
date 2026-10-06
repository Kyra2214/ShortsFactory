package com.shortsfactory.data.repository

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VideoImporterDownloadTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var files: File
    private lateinit var importer: VideoImporter

    private class FakeContext(private val files: File, private val cache: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = files
        override fun getCacheDir(): File = cache
    }

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        files = tmp.newFolder("files")
        importer = VideoImporter(FakeContext(files, tmp.newFolder("cache")) as Context)
    }

    @After fun tearDown() { server.shutdown() }

    private fun url(path: String = "/v") = server.url(path).toString()
    private fun download(u: String) = runBlocking { importer.downloadFromUrl(u) }
    private fun partFiles() = files.listFiles { f -> f.name.endsWith(".part") }.orEmpty()
    private fun key(u: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(u.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(24)
    private fun body(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test fun `html e rejeitado`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html></html>"))
        val r = download(url())
        assertTrue(r is VideoImporter.ImportResult.Failure)
        assertTrue((r as VideoImporter.ImportResult.Failure).message.contains("HTML"))
    }

    @Test fun `corpo vazio e rejeitado`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody(""))
        val r = download(url())
        assertTrue((r as VideoImporter.ImportResult.Failure).message.contains("vazio"))
        assertEquals(0, partFiles().size)
    }

    @Test fun `download completo usa content-type e promove o parcial`() {
        val data = body(50_000)
        server.enqueue(MockResponse().setHeader("Content-Type", "video/webm").setHeader("ETag", "\"v1\"").setBody(Buffer().write(data)))
        val r = download(url("/arquivo"))
        val path = (r as VideoImporter.ImportResult.Success).localPath
        assertTrue(path.endsWith(".webm"))
        assertEquals(data.size.toLong(), File(path).length())
        assertEquals(0, partFiles().size)
        assertEquals(0, files.listFiles { f -> f.name.endsWith(".meta") }.orEmpty().size)
    }

    @Test fun `acima do limite e rejeitado antes de gravar`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setHeader("Content-Length", "9000000000").setBody("x"))
        val r = download(url())
        assertTrue((r as VideoImporter.ImportResult.Failure).message.contains("limite"))
        assertEquals(0, partFiles().size)
    }

    @Test fun `acesso negado nao e contornado`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val r = download(url())
        assertTrue((r as VideoImporter.ImportResult.Failure).message.contains("403"))
    }

    @Test fun `queda no meio mantem parcial e retomada usa range e if-range`() {
        val data = body(200_000)
        val u = url("/longo")
        server.enqueue(
            MockResponse().setHeader("Content-Type", "video/mp4").setHeader("ETag", "\"v1\"")
                .setBody(Buffer().write(data)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        assertTrue(download(u) is VideoImporter.ImportResult.Failure)
        server.takeRequest()
        val part = partFiles().single()
        val offset = part.length().toInt()
        assertTrue(offset in 1 until data.size)

        server.enqueue(
            MockResponse().setResponseCode(206).setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes $offset-${data.size - 1}/${data.size}")
                .setBody(Buffer().write(data, offset, data.size - offset))
        )
        val r = download(u)
        val req = server.takeRequest()
        assertEquals("bytes=$offset-", req.getHeader("Range"))
        assertEquals("\"v1\"", req.getHeader("If-Range"))
        val path = (r as VideoImporter.ImportResult.Success).localPath
        assertTrue(File(path).readBytes().contentEquals(data))
        assertEquals(0, partFiles().size)
    }

    @Test fun `content-range incompativel e rejeitado`() {
        val u = url("/x")
        File(files, "video_${key(u)}.part").writeBytes(body(100))
        File(files, "video_${key(u)}.part.meta").writeText("\"v1\"")
        server.enqueue(
            MockResponse().setResponseCode(206).setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-199/200").setBody(Buffer().write(body(200)))
        )
        val r = download(u)
        assertTrue((r as VideoImporter.ImportResult.Failure).message.contains("incompatível"))
    }

    @Test fun `parcial sem validador recomeca do zero`() {
        val u = url("/y")
        File(files, "video_${key(u)}.part").writeBytes(body(100))
        val data = body(1_000)
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody(Buffer().write(data)))
        val r = download(u)
        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals(data.size.toLong(), File((r as VideoImporter.ImportResult.Success).localPath).length())
    }

    @Test fun `416 descarta o parcial`() {
        val u = url("/z")
        val part = File(files, "video_${key(u)}.part").apply { writeBytes(body(100)) }
        File(files, "video_${key(u)}.part.meta").writeText("\"v1\"")
        server.enqueue(MockResponse().setResponseCode(416))
        assertTrue(download(u) is VideoImporter.ImportResult.Failure)
        assertFalse(part.exists())
    }
}
