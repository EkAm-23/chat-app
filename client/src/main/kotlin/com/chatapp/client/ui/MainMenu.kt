package com.chatapp.client.ui

import com.chatapp.client.GrpcClient
import com.chatapp.proto.RecentChat
import com.chatapp.proto.StreamEvent
import io.grpc.StatusException
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

private val timeFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
    .withZone(ZoneId.systemDefault())

/**
 * Main menu screen — displays recent conversations and allows starting new chats.
 */
class MainMenu(
    private val grpcClient: GrpcClient,
    private val username: String
) {
    // Live online/offline status from Subscribe stream events
    private val onlineStatus = mutableMapOf<String, Boolean>()

    // Cached recent chats
    private var recentChats: List<RecentChat> = emptyList()

    /**
     * Update status from a stream event (called from background coroutine).
     */
    fun handleStatusChange(event: StreamEvent) {
        if (event.hasStatusChange()) {
            val change = event.statusChange
            onlineStatus[change.username] = change.isOnline
        }
    }

    /**
     * Display the main menu and handle user input.
     * Returns the username to chat with, or null to quit.
     */
    suspend fun show(): String? {
        // Fetch fresh recent chats
        try {
            val response = grpcClient.getRecentChats(username)
            recentChats = response.chatsList
            // Update online statuses from server data
            recentChats.forEach { chat ->
                onlineStatus[chat.username] = chat.isOnline
            }
        } catch (e: StatusException) {
            println("  ⚠ Error loading recent chats: ${e.status.description}")
        }

        printMenu()

        while (true) {
            print("\n  > ")
            System.out.flush()
            val input = withContext(Dispatchers.IO) { readlnOrNull() }?.trim() ?: return null

            when {
                input.equals("/quit", ignoreCase = true) || input.equals("/exit", ignoreCase = true) -> {
                    return null
                }
                input.equals("/refresh", ignoreCase = true) -> {
                    return show() // Recursive refresh
                }
                input.isNotBlank() -> {
                    return input // Username to chat with
                }
                else -> {
                    println("  Type a username to start chatting, /refresh, or /quit")
                }
            }
        }
    }

    private fun printMenu() {
        println()
        println("  ╔══════════════════════════════════════════════╗")
        println("  ║              💬  CHAT APP                    ║")
        println("  ╠══════════════════════════════════════════════╣")
        println("  ║  Logged in as: ${"%-29s".format(username)}║")
        println("  ╚══════════════════════════════════════════════╝")
        println()

        if (recentChats.isEmpty()) {
            println("  📭 No recent chats yet!")
            println("  Type a username to start a new conversation.")
        } else {
            println("  📋 Recent Conversations:")
            println("  ${"─".repeat(44)}")

            for (chat in recentChats) {
                val status = if (onlineStatus[chat.username] == true) "🟢" else "⚫"
                val time = timeFormatter.format(Instant.ofEpochMilli(chat.lastMessageAt))
                val preview = chat.lastMessage.take(30).let {
                    if (chat.lastMessage.length > 30) "$it..." else it
                }
                println("  $status ${chat.username}")
                println("     $preview  ·  $time")
                println()
            }
        }

        println("  ${"─".repeat(44)}")
        println("  Commands:")
        println("    <username>  — Start or resume a chat")
        println("    /refresh    — Refresh the conversation list")
        println("    /quit       — Exit the application")
    }
}
