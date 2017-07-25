package fr.ekito.rxtest.server

import fr.ekito.rxtest.server.ThreadUtil.threadName
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

/**
 * Simple Http Test with Rx
 */
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimpleTests {

    val logger = LoggerFactory.getLogger(TimeoutTests::class.java)

    @Value("\${local.server.port}")
    lateinit var port: Integer

    val baseUrl by lazy { "http://localhost:$port" }

    lateinit var httpClient: OkHttpClient

    @Before
    fun setup() {
        httpClient = HttpClient.httpClient()
    }

    @Test
    fun simpleGet() {
        val tester = Single.create<Boolean> { emitter ->
            val response = makeASimpleHTTPGet()
            if (response.isSuccessful) {
                emitter.onSuccess(true)
            } else {
                emitter.onError(IllegalStateException("Http error with ${response.code()}"))
            }
        }.test()

        // all is on main thread

        tester.awaitTerminalEvent()

        logger.warn(threadName() + "test asserts !")
        tester.assertNoErrors()
        tester.assertResult(true)
    }

    private fun makeASimpleHTTPGet(): Response {
        val request = Request.Builder()
                .url("$baseUrl/ping")
                .get()
                .build()
        logger.info("executing $request")
        val response = httpClient.newCall(request).execute()
        return response
    }
}
