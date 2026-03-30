package com.chatapp.server

import com.chatapp.server.repository.UserRepository
import mu.KotlinLogging
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@SpringBootApplication
class ChatServerApplication {

    /**
     * On startup, mark all users as offline.
     * Handles the server-restart edge case where stale online statuses
     * may remain in the DB from a previous run.
     */
    @Bean
    fun onStartup(userRepository: UserRepository) = ApplicationRunner {
        val count = userRepository.markAllOffline()
        logger.info { "Server started — marked $count user(s) as offline" }
    }
}

fun main(args: Array<String>) {
    runApplication<ChatServerApplication>(*args)
}
