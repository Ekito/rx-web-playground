package fr.ekito.rxtest.server

/**
 * Created by arnaud on 04/05/2017.
 */
object ThreadUtil {

//    val logger = LoggerFactory.getLogger(ThreadUtil::class.java)

    fun threadName() = "[" + Thread.currentThread().name + "] "
}