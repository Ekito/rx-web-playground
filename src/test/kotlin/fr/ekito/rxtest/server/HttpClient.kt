package fr.ekito.rxtest.server

import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * Created by arnaud on 04/05/2017.
 */
object HttpClient {

    val logger = LoggerFactory.getLogger(HttpClient::class.java)

    private fun logger(level: HttpLoggingInterceptor.Level): HttpLoggingInterceptor {
        val httpLoggingInterceptor = HttpLoggingInterceptor()
        httpLoggingInterceptor.level = level
        return httpLoggingInterceptor
    }

    fun httpClient(level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC, timeout: Long = ServerApplication.MAX_TIME): OkHttpClient {

        logger.info(" ### httpClient - level $level & longService $timeout seconds")

        val httpLoggingInterceptor = logger(level)

        val builder = OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout * 3, TimeUnit.SECONDS)
                .addInterceptor(httpLoggingInterceptor)

        return builder.build()
    }

    fun ws(baseUrl: String, client: OkHttpClient = httpClient()): WebServices {
        val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create()).build()
        return retrofit.create(WebServices::class.java)
    }
}

interface WebServices {
    @GET("/longService")
    fun longService(): Single<MessageData>

    @GET("/failService")
    fun failService(): Single<MessageData>
}