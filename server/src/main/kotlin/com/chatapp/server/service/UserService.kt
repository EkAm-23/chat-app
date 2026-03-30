package com.chatapp.server.service

import com.chatapp.server.entity.User
import com.chatapp.server.repository.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class UserService(
    private val userRepository: UserRepository
) {

    /**
     * Register or login a user.
     * - If username not found → create new user, mark online
     * - If found and OFFLINE → mark online (returning user)
     * - If found and ONLINE → throw exception (duplicate session)
     */
    @Transactional
    fun register(username: String): User {
        val existing = userRepository.findByUsername(username)

        if (existing != null) {
            if (existing.isOnline) {
                throw IllegalStateException("User '$username' is already online")
            }
            // Returning user — mark them online
            existing.isOnline = true
            existing.lastSeenAt = Instant.now()
            logger.info { "Returning user '$username' logged in" }
            return userRepository.save(existing)
        }

        // New user
        val newUser = User(
            username = username,
            isOnline = true,
            lastSeenAt = Instant.now()
        )
        logger.info { "New user '$username' registered" }
        return userRepository.save(newUser)
    }

    /**
     * Mark a user as online.
     */
    @Transactional
    fun markOnline(username: String): User {
        val user = userRepository.findByUsername(username)
            ?: throw NoSuchElementException("User '$username' not found")
        user.isOnline = true
        user.lastSeenAt = Instant.now()
        return userRepository.save(user)
    }

    /**
     * Mark a user as offline.
     */
    @Transactional
    fun markOffline(username: String) {
        val user = userRepository.findByUsername(username) ?: return
        user.isOnline = false
        user.lastSeenAt = Instant.now()
        userRepository.save(user)
        logger.info { "User '$username' marked offline" }
    }

    /**
     * Find a user by username, or null if not found.
     */
    fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    /**
     * Check if a user is currently online (from DB).
     */
    fun isOnline(username: String): Boolean {
        return userRepository.findByUsername(username)?.isOnline ?: false
    }
}
