package com.chatapp.client.ui

import com.chatapp.client.GrpcClient
import com.chatapp.proto.StreamEvent
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

/**
 * Active chat session — locks the user into a 1:1 conversation.
 * Messages typed go directly to the other person.
 * Incoming messages appear in real-time.
 * Type /exit to return to the main menu.
 */
class ChatSession(
    private val grpcClient: GrpcClient,
    private val myUsername: String,
    private val otherUsername: String
) {
    private var isOtherOnline = AtomicBoolean(false)
    private val isActive = AtomicBoolean(true)

    /**
     * Handle a stream event while in this chat session.
     */
    fun handleEvent(event: StreamEvent) {
        if (!isActive.get()) return

        when {
            event.hasMessage() -> {
                val msg = event.message
                if (msg.senderUsername == otherUsername) {
                    val time = timeFormatter.format(Instant.ofEpochMilli(msg.sentAt))
                    println("\r  [$time] ${msg.senderUsername}: ${msg.content}")
                    print("  > ")
                    System.out.flush()
                }
            }
            event.hasStatusChange() -> {
                val change = event.statusChange
                if (change.username == otherUsername) {
                    isOtherOnline.set(change.isOnline)
                    val statusText = if (change.isOnline) "🟢 online" else "⚫ offline"
                    println("\r  ── $otherUsername is now $statusText ──")
                    if (!change.isOnline) {
                        println("  💤 Messages will be delivered when they come back")
                    }
                    print("  > ")
                    System.out.flush()
                }
            }
        }
    }

    /**
     * Enter the chat session.
     * Loads message history, then enters send loop.
     * Returns when user types /exit.
     */
    suspend fun start() {
        // Validate the other user exists and load history
        try {
            val historyResponse = grpcClient.getMessageHistory(myUsername, otherUsername, 50)
            val recentChats = grpcClient.getRecentChats(myUsername)

            // Determine online status
            val chatInfo = recentChats.chatsList.find { it.username == otherUsername }
            isOtherOnline.set(chatInfo?.isOnline ?: false)

            printHeader()

            // Show message history
            if (historyResponse.messagesList.isNotEmpty()) {
                println("  ── Message History ──")
                for (msg in historyResponse.messagesList) {
                    val time = timeFormatter.format(Instant.ofEpochMilli(msg.sentAt))
                    val displayName = if (msg.senderUsername == myUsername) "You" else msg.senderUsername
                    println("  [$time] $displayName: ${msg.content}")
                }
                println("  ── End of History ──")
                println()
            }

            if (!isOtherOnline.get()) {
                println("  💤 $otherUsername is offline — your messages will be delivered when they come back")
                println()
            }

        } catch (e: StatusException) {
            if (e.status.code == Status.Code.NOT_FOUND) {
                println("  ❌ Error: ${e.status.description}")
                println("  Press Enter to return to the menu...")
                withContext(Dispatchers.IO) { readlnOrNull() }
                isActive.set(false)
                return
            }
            throw e
        }

        // Message send loop
        println("  Type your messages below. Use /exit to return to the menu.")
        println()

        while (isActive.get()) {
            print("  > ")
            System.out.flush()
            val input = withContext(Dispatchers.IO) { readlnOrNull() }?.trim()

            if (input == null || input.equals("/exit", ignoreCase = true)) {
                isActive.set(false)
                println("  ── Left chat with $otherUsername ──")
                break
            }

            if (input.isBlank()) continue

            try {
                val response = grpcClient.sendMessage(myUsername, otherUsername, input)
                val time = timeFormatter.format(Instant.ofEpochMilli(response.sentAt))
                // Move cursor up to overwrite the "> " prompt, show the sent message
                println("\r  [$time] You: $input")

                if (!response.recipientOnline) {
                    isOtherOnline.set(false)
                }
            } catch (e: StatusException) {
                println("  ⚠ Failed to send: ${e.status.description}")
            }
        }
    }

    private fun printHeader() {
        val statusIcon = if (isOtherOnline.get()) "🟢" else "⚫"
        val statusText = if (isOtherOnline.get()) "online" else "offline"

        println()
        println("  ╔══════════════════════════════════════════════╗")
        println("  ║  Chat with: ${"%-33s".format("$otherUsername $statusIcon $statusText")}║")
        println("  ╚══════════════════════════════════════════════╝")
        println()
    }
}
