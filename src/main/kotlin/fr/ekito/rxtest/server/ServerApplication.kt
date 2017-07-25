package fr.ekito.rxtest.server

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@RestController
class ServerApplication {

    val logger = LoggerFactory.getLogger(ServerApplication::class.java)

    @GetMapping("/ping")
    fun ping() = ResponseEntity<MessageData>(MessageData("pong"), HttpStatus.OK)

    @GetMapping("/longService")
    fun rxtimeout(): ResponseEntity<MessageData> {
        val time = MAX_TIME * 1000
        logger.warn("wait for $time ms")
        Thread.sleep(time)
        return ResponseEntity(MessageData("OK"), HttpStatus.OK)
    }

    var nbOfFails = MAX_FAILS

    @GetMapping("/failService")
    fun fail(): ResponseEntity<MessageData> {
        if (nbOfFails > 0) {
            nbOfFails--
            val w = MAX_FAILS - nbOfFails
            logger.warn("fail - retry : $nbOfFails ----> wait $w sec")
            Thread.sleep(w * 1000)
            return ResponseEntity(MessageData("fail $w"), HttpStatus.BAD_REQUEST)
        } else {
            nbOfFails = MAX_FAILS
            logger.warn("ok - no retry")
            return ResponseEntity(MessageData("OK"), HttpStatus.OK)
        }
    }

    companion object {
        const val MAX_TIME = 30L //seconds
        const val MAX_FAILS = 3L
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(ServerApplication::class.java, *args)
}