package com.chatapp.server.repository

import com.chatapp.server.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {

    fun findByUsername(username: String): User?

    /**
     * Mark all users as offline — used on server startup to clear stale state.
     * Returns the number of rows updated.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.isOnline = false WHERE u.isOnline = true")
    fun markAllOffline(): Int
}
