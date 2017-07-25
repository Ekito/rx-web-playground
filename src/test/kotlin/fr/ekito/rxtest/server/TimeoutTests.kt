package fr.ekito.rxtest.server

import fr.ekito.rxtest.server.ThreadUtil.threadName
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Experimenting timeouts with okhttp,retrofit & rx
 * Cleanly closing request/resouces without causing any side error (connection reset ...)
 */
@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TimeoutTests {

    val logger = LoggerFactory.getLogger(TimeoutTests::class.java)

    @Value("\${local.server.port}")
    lateinit var port: Integer

    val baseUrl by lazy { "http://localhost:$port" }

    lateinit var httpClient: OkHttpClient


    val short_timeout = 1L // sec

    @Before
    fun setup() {
        httpClient = HttpClient.httpClient()
        logger.warn("longService >> $short_timeout SECONDS")
    }

    /**
     * Just OkHttp timeout
     */
    @Test
    fun okhttpTimeout() {
        httpClient = HttpClient.httpClient(timeout = short_timeout)

        val test = Single.create<Boolean> { s: SingleEmitter<Boolean> ->
            val request = Request.Builder()
                    .url("$baseUrl/longService")
                    .get()
                    .build()

            logger.info(threadName() + "executing $request")

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call?, e: IOException?) {
                    logger.info(threadName() + "onFailure $call $e")
                    e?.let { s.onError(e) }
                }

                override fun onResponse(call: Call?, response: Response?) {
                    logger.info(threadName() + "onResponse $call $response")
                    if (response != null && response.isSuccessful) {
                        s.onSuccess(true)
                    } else {
                        s.onError(IllegalStateException("${response?.code()}"))
                    }
                }
            })
        }.subscribeOn(Schedulers.computation())
                .test()

        /*
        2017-07-25 14:36:24.326  INFO 3382 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 14:36:24.328  WARN 3382 --- [           main] fr.ekito.rxtest.server.TimeoutTests      : longService >> 1 SECONDS
        2017-07-25 14:36:24.328  INFO 3382 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 1 seconds
        2017-07-25 14:36:24.366  INFO 3382 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : [RxComputationThreadPool-1] executing Request{method=GET, url=http://localhost:52022/longService, tag=null}
        2017-07-25 14:36:24.374  INFO 3382 --- [lhost:52022/...] okhttp3.OkHttpClient                     : --> GET http://localhost:52022/longService http/1.1
        2017-07-25 14:36:24.430  INFO 3382 --- [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring FrameworkServlet 'dispatcherServlet'
        2017-07-25 14:36:24.430  INFO 3382 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization started
        2017-07-25 14:36:24.440  INFO 3382 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 10 ms
        2017-07-25 14:36:24.465  WARN 3382 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : wait for 30000 ms
        2017-07-25 14:36:25.399  INFO 3382 --- [lhost:52022/...] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.net.SocketTimeoutException: timeout
        2017-07-25 14:36:25.399  INFO 3382 --- [lhost:52022/...] fr.ekito.rxtest.server.TimeoutTests      : [OkHttp http://localhost:52022/...] onFailure okhttp3.RealCall@2f893584 java.net.SocketTimeoutException: timeout
         */

        test.awaitTerminalEvent()
        test.assertError { it is SocketTimeoutException }
    }

    /**
     * Blocking okhttp - dirty interrupted by Rx
     */
    @Test
    fun blockIngOkHttpDirtyRxTimeout() {
        val test = Single.create<Boolean> { s: SingleEmitter<Boolean> ->
            val request = Request.Builder()
                    .url("$baseUrl/longService")
                    .get()
                    .build()

            logger.info(threadName() + "executing $request")
            val response = httpClient.newCall(request).execute()
            logger.info(threadName() + "got $response")
            if (response != null && response.isSuccessful) {
                s.onSuccess(true)
            } else {
                s.onError(IllegalStateException("${response?.code()}"))
            }
        }.timeout(short_timeout, TimeUnit.SECONDS) // does nothing on main thread - isgnal is on computation/io
                .subscribeOn(Schedulers.computation())
                .test()

        /*
        017-07-25 14:37:02.486  INFO 3389 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 14:37:02.489  WARN 3389 --- [           main] fr.ekito.rxtest.server.TimeoutTests      : longService >> 1 SECONDS
        2017-07-25 14:37:02.533  INFO 3389 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : [RxComputationThreadPool-1] executing Request{method=GET, url=http://localhost:52034/longService, tag=null}
        2017-07-25 14:37:02.538  INFO 3389 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:52034/longService http/1.1
        2017-07-25 14:37:02.596  INFO 3389 --- [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring FrameworkServlet 'dispatcherServlet'
        2017-07-25 14:37:02.596  INFO 3389 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization started
        2017-07-25 14:37:02.608  INFO 3389 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 12 ms
        2017-07-25 14:37:02.631  WARN 3389 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : wait for 30000 ms
        2017-07-25 14:37:03.527  INFO 3389 --- [       Thread-3] ConfigServletWebServerApplicationContext : Closing org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext@4dd6fd0a: startup date [Tue Jul 25 14:37:00 CEST 2017]; root of context hierarchy
        io.reactivex.exceptions.UndeliverableException: java.net.SocketException: Connection reset
            at io.reactivex.plugins.RxJavaPlugins.onError(RxJavaPlugins.java:349)
            at io.reactivex.internal.operators.single.SingleCreate$Emitter.onError(SingleCreate.java:97)
            at io.reactivex.internal.operators.single.SingleCreate.subscribeActual(SingleCreate.java:42)
            at io.reactivex.Single.subscribe(Single.java:2703)
            at io.reactivex.internal.operators.single.SingleTimeout.subscribeActual(SingleTimeout.java:55)
            at io.reactivex.Single.subscribe(Single.java:2703)
            at io.reactivex.internal.operators.single.SingleSubscribeOn$SubscribeOnObserver.run(SingleSubscribeOn.java:89)
            at io.reactivex.internal.schedulers.ScheduledDirectTask.call(ScheduledDirectTask.java:38)
            at io.reactivex.internal.schedulers.ScheduledDirectTask.call(ScheduledDirectTask.java:26)
            at java.util.concurrent.FutureTask.run(FutureTask.java:266)
            at java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.access$201(ScheduledThreadPoolExecutor.java:180)
            at java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:293)
            at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
            at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
            at java.lang.Thread.run(Thread.java:748)
         */

        test.awaitTerminalEvent()
        test.assertError { it is java.util.concurrent.TimeoutException }
    }

    /**
     * okhttp - interrupted by Rx
     * No handle of cancelled request
     */
    @Test
    fun okhttpDirtyTimeout() {
        httpClient = HttpClient.httpClient()

        val test = Single.create<Boolean> { s: SingleEmitter<Boolean> ->
            val request = Request.Builder()
                    .url("$baseUrl/longService")
                    .get()
                    .build()

            logger.info(threadName() + "executing $request")

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call?, e: IOException?) {
                    logger.info(threadName() + "onFailure $call $e")
                    e?.let { s.onError(e) }
                }

                override fun onResponse(call: Call?, response: Response?) {
                    logger.info(threadName() + "onResponse $call $response")
                    if (response != null && response.isSuccessful) {
                        s.onSuccess(true)
                    } else {
                        s.onError(IllegalStateException("${response?.code()}"))
                    }
                }
            })
        }.timeout(short_timeout, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.computation())
                .test()

        /*

        2017-07-25 14:37:42.217  INFO 3393 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 14:37:42.219  WARN 3393 --- [           main] fr.ekito.rxtest.server.TimeoutTests      : longService >> 1 SECONDS
        2017-07-25 14:37:42.220  INFO 3393 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 14:37:42.261  INFO 3393 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : [RxComputationThreadPool-1] executing Request{method=GET, url=http://localhost:52053/longService, tag=null}
        2017-07-25 14:37:42.267  INFO 3393 --- [lhost:52053/...] okhttp3.OkHttpClient                     : --> GET http://localhost:52053/longService http/1.1
        2017-07-25 14:37:42.323  INFO 3393 --- [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring FrameworkServlet 'dispatcherServlet'
        2017-07-25 14:37:42.323  INFO 3393 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization started
        2017-07-25 14:37:42.334  INFO 3393 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 11 ms
        2017-07-25 14:37:42.359  WARN 3393 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : wait for 30000 ms
        2017-07-25 14:37:43.254  INFO 3393 --- [       Thread-3] ConfigServletWebServerApplicationContext : Closing org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext@4dd6fd0a: startup date [Tue Jul 25 14:37:39 CEST 2017]; root of context hierarchy

        2017-07-25 14:37:45.486  INFO 3393 --- [lhost:52053/...] fr.ekito.rxtest.server.TimeoutTests      : [OkHttp http://localhost:52053/...] onFailure okhttp3.RealCall@5a188765 java.net.SocketException: Connection resetio.reactivex.exceptions.UndeliverableException: java.net.SocketException: Connection reset
            at io.reactivex.plugins.RxJavaPlugins.onError(RxJavaPlugins.java:349)
            at io.reactivex.internal.operators.single.SingleCreate$Emitter.onError(SingleCreate.java:97)
            at fr.ekito.rxtest.server.TimeoutTests$okhttpDirtyTimeout$test$1$1.onFailure(TimeoutTests.kt:171)
            at okhttp3.RealCall$AsyncCall.execute(RealCall.java:148)
            at okhttp3.internal.NamedRunnable.run(NamedRunnable.java:32)
            at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
            at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
            at java.lang.Thread.run(Thread.java:748)
        Caused by: java.net.SocketException: Connection reset

         */

        test.awaitTerminalEvent()
        test.assertError { it is TimeoutException }
    }

    /**
     * okhttp - interrupted by Rx
     */
    @Test
    fun okhttpCleanRxTimeout() {
        val TAG = "MY_REQ"
        val test = Single.create<Boolean>({ s ->
            val request = Request.Builder()
                    .url("$baseUrl/longService")
                    .get()
                    .tag(TAG)
                    .build()

            logger.info(threadName() + "executing $request")

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call?, e: IOException?) {
                    logger.error(threadName() + "onFailure ${e?.message}")
                    e?.let { s.onError(e) }
                }

                override fun onResponse(call: Call?, response: Response?) {
                    logger.info(threadName() + "onResponse $call $response")
                    if (response != null && response.isSuccessful) {
                        s.onSuccess(true)
                    } else {
                        s.onError(IllegalStateException("${response?.code()}"))
                    }
                }
            })

            s.setCancellable {
                val runningCall = httpClient.dispatcher().runningCalls().filter { c -> c.request().tag() == TAG }.firstOrNull()
                logger.info(threadName() + "cancelling runningCall $runningCall ?")
                runningCall?.cancel()

                val queuedCall = httpClient.dispatcher().queuedCalls().filter { c -> c.request().tag() == TAG }.firstOrNull()
                logger.info(threadName() + "cancelling queuedCall $queuedCall ?")
                queuedCall?.cancel()
            }
        })
                .timeout(short_timeout, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .test()

        /*
        2017-07-25 14:39:02.524  INFO 3396 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 14:39:02.529  WARN 3396 --- [           main] fr.ekito.rxtest.server.TimeoutTests      : longService >> 1 SECONDS
        2017-07-25 14:39:02.577  INFO 3396 --- [ionThreadPool-1] fr.ekito.rxtest.server.TimeoutTests      : [RxComputationThreadPool-1] executing Request{method=GET, url=http://localhost:52065/longService, tag=MY_REQ}
        2017-07-25 14:39:02.586  INFO 3396 --- [lhost:52065/...] okhttp3.OkHttpClient                     : --> GET http://localhost:52065/longService http/1.1
        2017-07-25 14:39:02.655  INFO 3396 --- [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring FrameworkServlet 'dispatcherServlet'
        2017-07-25 14:39:02.655  INFO 3396 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization started
        2017-07-25 14:39:02.669  INFO 3396 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 14 ms
        2017-07-25 14:39:02.697  WARN 3396 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : wait for 30000 ms
        2017-07-25 14:39:03.578  INFO 3396 --- [ionThreadPool-2] fr.ekito.rxtest.server.TimeoutTests      : [RxComputationThreadPool-2] cancelling runningCall okhttp3.RealCall@f8194fa ?
        2017-07-25 14:39:03.579  INFO 3396 --- [ionThreadPool-2] fr.ekito.rxtest.server.TimeoutTests      : [RxComputationThreadPool-2] cancelling queuedCall null ?
        2017-07-25 14:39:03.580  INFO 3396 --- [lhost:52065/...] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.io.IOException: Canceled
        2017-07-25 14:39:03.580 ERROR 3396 --- [lhost:52065/...] fr.ekito.rxtest.server.TimeoutTests      : [OkHttp http://localhost:52065/...] onFailure Canceled
        io.reactivex.exceptions.UndeliverableException: java.io.IOException: Canceled
            at io.reactivex.plugins.RxJavaPlugins.onError(RxJavaPlugins.java:349)
            at io.reactivex.internal.operators.single.SingleCreate$Emitter.onError(SingleCreate.java:97)
            at fr.ekito.rxtest.server.TimeoutTests$okhttpCleanRxTimeout$test$1$1.onFailure(TimeoutTests.kt:236)
            at okhttp3.RealCall$AsyncCall.execute(RealCall.java:148)
            at okhttp3.internal.NamedRunnable.run(NamedRunnable.java:32)
            at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
            at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
            at java.lang.Thread.run(Thread.java:748)
        Caused by: java.io.IOException: Canceled
         */

        test.awaitTerminalEvent()
        test.assertError { it is TimeoutException }
    }

    /**
     * Retrofit cleanly Interrupted by okhttp
     */
    @Test
    fun retrofitOkHttpTimeout() {
        httpClient = HttpClient.httpClient(timeout = short_timeout)
        val ws = HttpClient.ws(baseUrl, httpClient)

        val test = ws.longService()
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .test()
        /*
        2017-07-25 14:40:14.450  INFO 3399 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 14:40:14.453  WARN 3399 --- [           main] fr.ekito.rxtest.server.TimeoutTests      : longService >> 1 SECONDS
        2017-07-25 14:40:14.454  INFO 3399 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 1 seconds
        2017-07-25 14:40:14.536  INFO 3399 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:52081/longService http/1.1
        2017-07-25 14:40:14.637  INFO 3399 --- [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring FrameworkServlet 'dispatcherServlet'
        2017-07-25 14:40:14.638  INFO 3399 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization started
        2017-07-25 14:40:14.660  INFO 3399 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 22 ms
        2017-07-25 14:40:14.694  WARN 3399 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : wait for 30000 ms
        2017-07-25 14:40:15.578  INFO 3399 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.net.SocketTimeoutException: timeout
         */

        test.awaitTerminalEvent()
        test.assertError { it is SocketTimeoutException }

    }

    /**
     * Retrofit cleanly interrupted by Rx
     */
    @Test
    fun retrofitRxTimeout() {
        httpClient = HttpClient.httpClient()
        val ws = HttpClient.ws(baseUrl, httpClient)


        val test = ws.longService()
                .timeout(short_timeout, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .test()

        /*
        2017-07-25 14:41:22.803  INFO 3403 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 14:41:22.805  WARN 3403 --- [           main] fr.ekito.rxtest.server.TimeoutTests      : longService >> 1 SECONDS
        2017-07-25 14:41:22.805  INFO 3403 --- [           main] fr.ekito.rxtest.server.HttpClient        :  ### httpClient - level BASIC & longService 30 seconds
        2017-07-25 14:41:22.893  INFO 3403 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : --> GET http://localhost:52102/longService http/1.1
        2017-07-25 14:41:22.988  INFO 3403 --- [o-auto-1-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring FrameworkServlet 'dispatcherServlet'
        2017-07-25 14:41:22.988  INFO 3403 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization started
        2017-07-25 14:41:23.002  INFO 3403 --- [o-auto-1-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 14 ms
        2017-07-25 14:41:23.033  WARN 3403 --- [o-auto-1-exec-1] f.ekito.rxtest.server.ServerApplication  : wait for 30000 ms
        2017-07-25 14:41:23.885  INFO 3403 --- [ionThreadPool-1] okhttp3.OkHttpClient                     : <-- HTTP FAILED: java.io.IOException: Canceled
         */

        test.awaitTerminalEvent()
        test.assertError { it is TimeoutException }
    }

}
