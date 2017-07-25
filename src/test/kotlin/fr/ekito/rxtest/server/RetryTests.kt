package fr.ekito.rxtest.server

import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import java.util.concurrent.TimeUnit

/**
 * Experimenting retries :
 * - on http error
 * - on rx timeout
 * - on okhttp timeout
 */
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RetryTests {

    val logger = LoggerFactory.getLogger(TimeoutTests::class.java)

    @Value("\${local.server.port}")
    lateinit var port: Integer

    val baseUrl by lazy { "http://localhost:$port" }

    lateinit var httpClient: OkHttpClient

    @Before
    fun setup() {
        httpClient = HttpClient.httpClient()
    }

    /**
     * Will retry on http error
     */
    @Test
    fun retryOnHttpError() {
        // Setup custom HttpClient timeout
        val ws = HttpClient.ws(baseUrl, HttpClient.httpClient())

        val test = ws.failService()
                .retry { count, error ->
                    logger.error("retry on error ? $error")
                    count <= ServerApplication.MAX_FAILS
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .test()

        /*
        017-07-25 12:04:06.213  INFO 2757 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 12:04:06.216  INFO 2757 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 12:04:06.279  INFO 2757 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:50085/failService http/1.1
        2017-07-25 12:04:06.332  INFO 2757 --- [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring FrameworkServlet 'dispatcherServlet'
        2017-07-25 12:04:06.332  INFO 2757 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization started
        2017-07-25 12:04:06.342  INFO 2757 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 10 ms
        2017-07-25 12:04:06.364  WARN 2757 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : fail - retry : 2 ----> wait 1 sec
        2017-07-25 12:04:07.446  INFO 2757 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- 400  http://localhost:50085/failService (1166ms, unknown-length body)
        2017-07-25 12:04:07.449 ERROR 2757 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? retrofit2.adapter.rxjava2.HttpException: HTTP 400
        2017-07-25 12:04:07.450  INFO 2757 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:50085/failService http/1.1
        2017-07-25 12:04:07.456  WARN 2757 --- [o-auto-1-exec-2] f.ekito.rxtest.server.ServerApplication  : fail - retry : 1 ----> wait 2 sec
        2017-07-25 12:04:09.458  INFO 2757 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- 400  http://localhost:50085/failService (2008ms, unknown-length body)
        2017-07-25 12:04:09.459 ERROR 2757 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? retrofit2.adapter.rxjava2.HttpException: HTTP 400
        2017-07-25 12:04:09.459  INFO 2757 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:50085/failService http/1.1
        2017-07-25 12:04:09.461  WARN 2757 --- [o-auto-1-exec-3] f.ekito.rxtest.server.ServerApplication  : fail - retry : 0 ----> wait 3 sec
        2017-07-25 12:04:12.465  INFO 2757 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- 400  http://localhost:50085/failService (3006ms, unknown-length body)
        2017-07-25 12:04:12.466 ERROR 2757 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? retrofit2.adapter.rxjava2.HttpException: HTTP 400
        2017-07-25 12:04:12.466  INFO 2757 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:50085/failService http/1.1
        2017-07-25 12:04:12.468  WARN 2757 --- [o-auto-1-exec-4] f.ekito.rxtest.server.ServerApplication  : ok - no retry
        2017-07-25 12:04:12.469  INFO 2757 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- 200  http://localhost:50085/failService (2ms, unknown-length body)
         */

        test.awaitTerminalEvent()
        test.assertValue {
            logger.info("receivedValue = $it")
            it.message == "OK"
        }
        test.assertNoErrors()
    }

    /**
     * Will retry on Rx timeout (1 sec)
     */
    @Test
    fun retryOnRxTimeout() {
        // Setup custom HttpClient timeout
        val ws = HttpClient.ws(baseUrl, HttpClient.httpClient())

        val test = ws.failService()
                .timeout(1, TimeUnit.SECONDS) // if an http error & retry times
                .retry { count, error ->
                    logger.error("retry on error ? $error")
                    count <= ServerApplication.MAX_FAILS
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .test()
        /*
        2017-07-25 12:11:18.910  INFO 2792 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 12:11:18.912  INFO 2792 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 12:11:18.916  INFO 2792 --- [ionThreadPool-5] okhttp3.OkHttpClient                     : --> GET http://localhost:50221/failService http/1.1
        2017-07-25 12:11:18.920  WARN 2792 --- [o-auto-1-exec-9] f.ekito.rxtest.server.ServerApplication  : fail - retry : 2 ----> wait 1 sec
        2017-07-25 12:11:19.918 ERROR 2792 --- [ionThreadPool-6] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? java.util.concurrent.TimeoutException
        2017-07-25 12:11:19.918  INFO 2792 --- [ionThreadPool-5] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.io.IOException: Canceled
        2017-07-25 12:11:19.919  INFO 2792 --- [ionThreadPool-5] okhttp3.OkHttpClient                     : --> GET http://localhost:50221/failService http/1.1
        2017-07-25 12:11:19.923  WARN 2792 --- [-auto-1-exec-10] f.ekito.rxtest.server.ServerApplication  : fail - retry : 1 ----> wait 2 sec
        2017-07-25 12:11:20.921 ERROR 2792 --- [ionThreadPool-7] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? java.util.concurrent.TimeoutException
        2017-07-25 12:11:20.921  INFO 2792 --- [ionThreadPool-5] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.io.IOException: Canceled
        2017-07-25 12:11:20.921  INFO 2792 --- [ionThreadPool-5] okhttp3.OkHttpClient                     : --> GET http://localhost:50221/failService http/1.1
        2017-07-25 12:11:20.923  WARN 2792 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : fail - retry : 0 ----> wait 3 sec
        2017-07-25 12:11:21.925 ERROR 2792 --- [ionThreadPool-8] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? java.util.concurrent.TimeoutException
        2017-07-25 12:11:21.925  INFO 2792 --- [ionThreadPool-5] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.io.IOException: Canceled
        2017-07-25 12:11:21.925  INFO 2792 --- [ionThreadPool-5] okhttp3.OkHttpClient                     : --> GET http://localhost:50221/failService http/1.1
        2017-07-25 12:11:21.927  WARN 2792 --- [o-auto-1-exec-2] f.ekito.rxtest.server.ServerApplication  : ok - no retry
        2017-07-25 12:11:21.928  INFO 2792 --- [ionThreadPool-5] okhttp3.OkHttpClient                     : <-- 200  http://localhost:50221/failService (2ms, unknown-length body)
        2017-07-25 12:11:21.929  INFO 2792 --- [           main] fr.ekito.rxtest.server.TimeoutTests      : receivedValue = MessageData(message=OK)
         */

        test.awaitTerminalEvent()
        test.assertValue {
            logger.info("receivedValue = $it")
            it.message == "OK"
        }
        test.assertNoErrors()
    }

    /**
     * Will retry on okhttp timeout
     */
    @Test
    fun retryOnOkHttpTimeout() {
        // Setup custom HttpClient timeout
        val ws = HttpClient.ws(baseUrl, HttpClient.httpClient(timeout = 1L))

        val test = ws.failService()
                .retry { count, error ->
                    logger.error("retry on error ? $error ### $count")
                    count <= ServerApplication.MAX_FAILS
                } // if an http error & retry times
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .test()
        // ### httpClient - level BASIC & longService 6 seconds

        /*
        2017-07-25 12:02:39.190  INFO 2753 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 12:02:39.193  INFO 2753 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 1 seconds
        2017-07-25 12:02:39.259  INFO 2753 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:50064/failService http/1.1
        2017-07-25 12:02:39.311  INFO 2753 --- [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring FrameworkServlet 'dispatcherServlet'
        2017-07-25 12:02:39.311  INFO 2753 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization started
        2017-07-25 12:02:39.322  INFO 2753 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 11 ms
        2017-07-25 12:02:39.344  WARN 2753 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : fail - retry : 2 ----> wait 1 sec
        2017-07-25 12:02:40.280  INFO 2753 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.net.SocketTimeoutException: timeout
        2017-07-25 12:02:40.281 ERROR 2753 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? java.net.SocketTimeoutException: timeout ### 1
        2017-07-25 12:02:40.281  INFO 2753 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:50064/failService http/1.1
        2017-07-25 12:02:40.284  WARN 2753 --- [o-auto-1-exec-2] f.ekito.rxtest.server.ServerApplication  : fail - retry : 1 ----> wait 2 sec
        2017-07-25 12:02:41.287  INFO 2753 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.net.SocketTimeoutException: timeout
        2017-07-25 12:02:41.288 ERROR 2753 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? java.net.SocketTimeoutException: timeout ### 2
        2017-07-25 12:02:41.289  INFO 2753 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:50064/failService http/1.1
        2017-07-25 12:02:41.292  WARN 2753 --- [o-auto-1-exec-3] f.ekito.rxtest.server.ServerApplication  : fail - retry : 0 ----> wait 3 sec
        2017-07-25 12:02:42.293  INFO 2753 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.net.SocketTimeoutException: timeout
        2017-07-25 12:02:42.294 ERROR 2753 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : retry on error ? java.net.SocketTimeoutException: timeout ### 3
        2017-07-25 12:02:42.294  INFO 2753 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:50064/failService http/1.1
        2017-07-25 12:02:42.296  WARN 2753 --- [o-auto-1-exec-4] f.ekito.rxtest.server.ServerApplication  : ok - no retry
        2017-07-25 12:02:42.301  INFO 2753 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- 200  http://localhost:50064/failService (7ms, unknown-length body)
         */

        test.awaitTerminalEvent()
        test.assertValue { it.message == "OK" }
        test.assertNoErrors()
    }
}
