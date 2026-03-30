package com.chatapp.server.grpc

import com.chatapp.proto.*
import com.chatapp.server.service.MessageService
import com.chatapp.server.service.SessionManager
import com.chatapp.server.service.UserService
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import net.devh.boot.grpc.server.service.GrpcService

private val logger = KotlinLogging.logger {}

@GrpcService
class ChatGrpcService(
    private val userService: UserService,
    private val messageService: MessageService,
    private val sessionManager: SessionManager
) : ChatServiceGrpcKt.ChatServiceCoroutineImplBase() {

    /**
     * Register / Login RPC.
     * - New user → create and return
     * - Returning user (offline) → allow and return
     * - Duplicate user (online) → reject with ALREADY_EXISTS
     */
    override suspend fun register(request: RegisterRequest): RegisterResponse {
        val username = request.username.trim()

        if (username.isBlank()) {
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("Username cannot be empty"))
        }

        return try {
            val user = userService.register(username)
            RegisterResponse.newBuilder()
                .setUserId(user.id.toString())
                .setUsername(user.username)
                .build()
        } catch (e: IllegalStateException) {
            throw StatusException(
                Status.ALREADY_EXISTS.withDescription(e.message)
            )
        }
    }

    /**
     * SendMessage RPC.
     * - Validates sender and recipient exist
     * - Persists message via MessageService
     * - If recipient online, pushes via SessionManager
     * - Returns message metadata including recipient online status
     */
    override suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse {
        val senderUsername = request.senderUsername.trim()
        val recipientUsername = request.recipientUsername.trim()
        val content = request.content

        if (content.isBlank()) {
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("Message content cannot be empty"))
        }

        val sender = userService.findByUsername(senderUsername)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Sender '$senderUsername' not found"))

        val recipient = userService.findByUsername(recipientUsername)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("User '$recipientUsername' not found"))

        val message = messageService.sendMessage(sender, recipient, content)

        // Track contacts for status broadcasts
        sessionManager.addContact(senderUsername, recipientUsername)

        // Push to recipient if online
        val recipientOnline = sessionManager.isOnline(recipientUsername)
        if (recipientOnline) {
            val event = StreamEvent.newBuilder()
                .setMessage(
                    IncomingMessage.newBuilder()
                        .setMessageId(message.id.toString())
                        .setSenderUsername(senderUsername)
                        .setContent(content)
                        .setSentAt(message.sentAt.toEpochMilli())
                        .build()
                )
                .build()
            sessionManager.sendEvent(recipientUsername, event)
        }

        return SendMessageResponse.newBuilder()
            .setMessageId(message.id.toString())
            .setSentAt(message.sentAt.toEpochMilli())
            .setRecipientOnline(recipientOnline)
            .build()
    }

    /**
     * Subscribe RPC (server-streaming).
     * Returns a Flow<StreamEvent> carrying both messages and status changes.
     *
     * Lifecycle:
     * 1. Mark user online, register session
     * 2. Flush undelivered messages as events
     * 3. Broadcast status change to contacts
     * 4. Emit from SessionManager shared flow
     * 5. On cancellation: mark offline, unregister, broadcast
     */
    override fun subscribe(request: SubscribeRequest): Flow<StreamEvent> = flow {
        val username = request.username.trim()

        if (username.isBlank()) {
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("Username cannot be empty"))
        }

        val user = userService.findByUsername(username)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("User '$username' not found"))

        // Register session and mark online
        val eventFlow = sessionManager.register(username)
        userService.markOnline(username)

        logger.info { "User '$username' subscribed to event stream" }

        try {
            // Flush undelivered messages
            val undelivered = messageService.getAndMarkUndeliveredMessages(user)
            for (msg in undelivered) {
                emit(
                    StreamEvent.newBuilder()
                        .setMessage(
                            IncomingMessage.newBuilder()
                                .setMessageId(msg.id.toString())
                                .setSenderUsername(msg.sender.username)
                                .setContent(msg.content)
                                .setSentAt(msg.sentAt.toEpochMilli())
                                .build()
                        )
                        .build()
                )
                // Track contact relationship
                sessionManager.addContact(username, msg.sender.username)
            }

            // Broadcast that this user is now online
            sessionManager.broadcastStatusChange(username, isOnline = true)

            // Collect events from the shared flow and emit to the gRPC stream
            eventFlow.collect { event ->
                emit(event)
            }
        } catch (e: CancellationException) {
            logger.info { "User '$username' stream cancelled (client disconnected)" }
            throw e
        } finally {
            // Cleanup: mark offline, unregister session, broadcast status
            sessionManager.unregister(username)
            userService.markOffline(username)
            sessionManager.broadcastStatusChange(username, isOnline = false)
            logger.info { "User '$username' fully disconnected and cleaned up" }
        }
    }

    /**
     * GetRecentChats RPC.
     * Returns recent conversations with last message and online status.
     */
    override suspend fun getRecentChats(request: GetRecentChatsRequest): GetRecentChatsResponse {
        val username = request.username.trim()

        val user = userService.findByUsername(username)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("User '$username' not found"))

        val recentChats = messageService.getRecentChats(user)

        val response = GetRecentChatsResponse.newBuilder()
        for (chat in recentChats) {
            response.addChats(
                RecentChat.newBuilder()
                    .setUsername(chat.otherUser.username)
                    .setLastMessage(chat.lastMessageContent)
                    .setLastMessageAt(chat.lastMessageAt.toEpochMilli())
                    .setIsOnline(sessionManager.isOnline(chat.otherUser.username))
                    .build()
            )
        }

        return response.build()
    }

    /**
     * GetMessageHistory RPC.
     * Returns the last N messages for a given conversation.
     */
    override suspend fun getMessageHistory(request: GetMessageHistoryRequest): GetMessageHistoryResponse {
        val username = request.username.trim()
        val otherUsername = request.otherUsername.trim()
        val limit = if (request.limit > 0) request.limit else 50

        val user = userService.findByUsername(username)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("User '$username' not found"))

        val otherUser = userService.findByUsername(otherUsername)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("User '$otherUsername' not found"))

        val messages = messageService.getMessageHistory(user, otherUser, limit)

        val response = GetMessageHistoryResponse.newBuilder()
        for (msg in messages) {
            response.addMessages(
                ChatMessage.newBuilder()
                    .setMessageId(msg.id.toString())
                    .setSenderUsername(msg.sender.username)
                    .setRecipientUsername(msg.recipient.username)
                    .setContent(msg.content)
                    .setSentAt(msg.sentAt.toEpochMilli())
                    .build()
            )
        }

        return response.build()
    }
}
