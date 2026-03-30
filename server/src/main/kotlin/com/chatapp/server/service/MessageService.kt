package com.chatapp.server.service

import com.chatapp.server.entity.Message
import com.chatapp.server.entity.User
import com.chatapp.server.repository.MessageRepository
import com.chatapp.server.repository.UserRepository
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class RecentChatInfo(
    val otherUser: User,
    val lastMessageContent: String,
    val lastMessageAt: Instant
)

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) {

    /**
     * Send a message from sender to recipient.
     * Returns the persisted message.
     */
    @Transactional
    fun sendMessage(sender: User, recipient: User, content: String): Message {
        val message = Message(
            sender = sender,
            recipient = recipient,
            content = content,
            sentAt = Instant.now(),
            delivered = recipient.isOnline
        )
        val saved = messageRepository.save(message)
        logger.info { "Message ${saved.id} from '${sender.username}' to '${recipient.username}' (delivered=${saved.delivered})" }
        return saved
    }

    /**
     * Get undelivered messages for a user and mark them as delivered.
     */
    @Transactional
    fun getAndMarkUndeliveredMessages(recipient: User): List<Message> {
        val messages = messageRepository.findUndeliveredMessages(recipient)
        if (messages.isNotEmpty()) {
            messages.forEach { it.delivered = true }
            messageRepository.saveAll(messages)
            logger.info { "Flushed ${messages.size} undelivered messages to '${recipient.username}'" }
        }
        return messages
    }

    /**
     * Get recent chat conversations for a user.
     * Returns list of conversation partners with their last message, sorted by most recent.
     */
    @Transactional(readOnly = true)
    fun getRecentChats(user: User): List<RecentChatInfo> {
        val rawResults = messageRepository.findRecentChatsRaw(user.id)

        return rawResults.mapNotNull { row ->
            try {
                val senderId = row[4] as UUID
                val recipientId = row[5] as UUID
                val otherId = if (senderId == user.id) recipientId else senderId
                val otherUser = userRepository.findById(otherId).orElse(null) ?: return@mapNotNull null
                val content = row[1] as String
                val sentAt = when (val ts = row[2]) {
                    is Instant -> ts
                    is java.sql.Timestamp -> ts.toInstant()
                    else -> Instant.now()
                }
                RecentChatInfo(
                    otherUser = otherUser,
                    lastMessageContent = content,
                    lastMessageAt = sentAt
                )
            } catch (e: Exception) {
                logger.warn(e) { "Error parsing recent chat row" }
                null
            }
        }.sortedByDescending { it.lastMessageAt }
    }

    /**
     * Get message history between two users.
     * Returns the last N messages in chronological order.
     */
    @Transactional(readOnly = true)
    fun getMessageHistory(user1: User, user2: User, limit: Int = 50): List<Message> {
        val effectiveLimit = if (limit <= 0) 50 else limit
        val messages = messageRepository.findRecentMessages(user1, user2, PageRequest.of(0, effectiveLimit))
        return messages.reversed() // reverse to get chronological order
    }
}
