package com.chatapp.server.repository

import com.chatapp.server.entity.Message
import com.chatapp.server.entity.User
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MessageRepository : JpaRepository<Message, UUID> {

    /**
     * Fetch conversation messages between two users, ordered by sent_at ascending.
     * Uses Pageable for limiting results.
     */
    @Query(
        """
        SELECT m FROM Message m
        WHERE (m.sender = :user1 AND m.recipient = :user2)
           OR (m.sender = :user2 AND m.recipient = :user1)
        ORDER BY m.sentAt ASC
        """
    )
    fun findConversation(
        @Param("user1") user1: User,
        @Param("user2") user2: User,
        pageable: Pageable
    ): List<Message>

    /**
     * Find all undelivered messages for a given recipient, ordered by sent_at.
     */
    @Query(
        """
        SELECT m FROM Message m
        WHERE m.recipient = :recipient AND m.delivered = false
        ORDER BY m.sentAt ASC
        """
    )
    fun findUndeliveredMessages(@Param("recipient") recipient: User): List<Message>

    /**
     * Find recent chat partners for a user with their last message.
     * Returns the most recent message per conversation partner.
     */
    @Query(
        value = """
        SELECT DISTINCT ON (other_id) *
        FROM (
            SELECT 
                m.id, m.content, m.sent_at, m.delivered,
                m.sender_id, m.recipient_id,
                CASE 
                    WHEN m.sender_id = :userId THEN m.recipient_id 
                    ELSE m.sender_id 
                END AS other_id
            FROM messages m
            WHERE m.sender_id = :userId OR m.recipient_id = :userId
            ORDER BY m.sent_at DESC
        ) sub
        ORDER BY other_id, sent_at DESC
        """,
        nativeQuery = true
    )
    fun findRecentChatsRaw(@Param("userId") userId: UUID): List<Array<Any>>

    /**
     * Find the last N messages for a conversation (most recent first, then reversed in service).
     */
    @Query(
        """
        SELECT m FROM Message m
        WHERE (m.sender = :user1 AND m.recipient = :user2)
           OR (m.sender = :user2 AND m.recipient = :user1)
        ORDER BY m.sentAt DESC
        """
    )
    fun findRecentMessages(
        @Param("user1") user1: User,
        @Param("user2") user2: User,
        pageable: Pageable
    ): List<Message>
}
