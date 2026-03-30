package com.chatapp.server.service

import com.chatapp.proto.StatusChange
import com.chatapp.proto.StreamEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * In-memory session registry managing active gRPC subscribe streams.
 * Maps username → their event SharedFlow for real-time event delivery.
 */
@Component
class SessionManager {

    // Each user's event flow — messages and status changes are emitted here
    private val sessions = ConcurrentHashMap<String, MutableSharedFlow<StreamEvent>>()

    // Track which users each user has recently interacted with (for status broadcast)
    private val contacts = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * Register a new session for a user.
     * Returns the SharedFlow that the Subscribe RPC will collect from.
     */
    fun register(username: String): MutableSharedFlow<StreamEvent> {
        val flow = MutableSharedFlow<StreamEvent>(
            replay = 0,
            extraBufferCapacity = 64
        )
        sessions[username] = flow
        logger.info { "Session registered for '$username' (total active: ${sessions.size})" }
        return flow
    }

    /**
     * Unregister a user's session.
     */
    fun unregister(username: String) {
        sessions.remove(username)
        logger.info { "Session unregistered for '$username' (total active: ${sessions.size})" }
    }

    /**
     * Check if a user has an active session (is online).
     */
    fun isOnline(username: String): Boolean {
        return sessions.containsKey(username)
    }

    /**
     * Send an event to a specific user (if they're online).
     * Returns true if the event was emitted, false if user is offline.
     */
    fun sendEvent(username: String, event: StreamEvent): Boolean {
        val flow = sessions[username] ?: return false
        val emitted = flow.tryEmit(event)
        if (!emitted) {
            logger.warn { "Failed to emit event to '$username' — buffer full" }
        }
        return emitted
    }

    /**
     * Record that two users are contacts (have exchanged messages).
     * Used to know who to notify about status changes.
     */
    fun addContact(user1: String, user2: String) {
        contacts.getOrPut(user1) { ConcurrentHashMap.newKeySet() }.add(user2)
        contacts.getOrPut(user2) { ConcurrentHashMap.newKeySet() }.add(user1)
    }

    /**
     * Broadcast a status change to all contacts of a user who are currently online.
     */
    fun broadcastStatusChange(username: String, isOnline: Boolean) {
        val statusEvent = StreamEvent.newBuilder()
            .setStatusChange(
                StatusChange.newBuilder()
                    .setUsername(username)
                    .setIsOnline(isOnline)
                    .build()
            )
            .build()

        val userContacts = contacts[username] ?: return

        userContacts.forEach { contact ->
            if (sessions.containsKey(contact)) {
                sendEvent(contact, statusEvent)
                logger.debug { "Status change for '$username' (online=$isOnline) sent to '$contact'" }
            }
        }
    }

    /**
     * Get all currently online usernames.
     */
    fun getOnlineUsers(): Set<String> = sessions.keys.toSet()
}
