package com.chatapp.server.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "messages",
    indexes = [
        Index(name = "idx_messages_sender", columnList = "sender_id"),
        Index(name = "idx_messages_recipient", columnList = "recipient_id"),
        Index(name = "idx_messages_sent_at", columnList = "sent_at"),
        Index(name = "idx_messages_delivered", columnList = "delivered")
    ]
)
data class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User = User(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    val recipient: User = User(),

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String = "",

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now(),

    @Column(name = "delivered", nullable = false)
    var delivered: Boolean = false
)
