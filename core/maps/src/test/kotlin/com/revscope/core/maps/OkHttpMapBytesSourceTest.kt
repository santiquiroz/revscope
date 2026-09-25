package com.revscope.core.maps

import com.revscope.core.common.net.RevScopeHttp
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class OkHttpMapBytesSourceTest {

    private class CapturingInterceptor : Interceptor {
        var captured: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            captured = chain.request()
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("pmtiles".toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }
    }

    @Test
    fun `la descarga se identifica con el User-Agent de RevScope`() = runTest {
        val interceptor = CapturingInterceptor()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val destination = File.createTempFile("map", ".pmtiles").apply { deleteOnExit() }

        OkHttpMapBytesSource(client).download("https://example.invalid/map.pmtiles", destination) { _, _ -> }

        assertEquals(RevScopeHttp.USER_AGENT, interceptor.captured?.header("User-Agent"))
        assertEquals("pmtiles", destination.readText())
    }
}
