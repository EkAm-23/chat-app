package com.chatapp.client

import com.chatapp.client.ui.ChatSession
import com.chatapp.client.ui.MainMenu
import com.chatapp.proto.StreamEvent
import io.grpc.StatusException
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main() = runBlocking {
    println()
    println("  ╔══════════════════════════════════════════════╗")
    println("  ║         Welcome to Chat App! 💬              ║")
    println("  ╚══════════════════════════════════════════════╝")
    println()

    val grpcClient = GrpcClient()

    // ─── Step 1: Login / Register ────────────────────────────────
    var username: String? = null
    while (username == null) {
        print("  Enter your username: ")
        System.out.flush()
        val input = withContext(Dispatchers.IO) { readlnOrNull() }?.trim()

        if (input.isNullOrBlank()) {
            println("  ⚠ Username cannot be empty. Try again.")
            continue
        }

        try {
            val response = grpcClient.register(input)
            username = response.username
            println("  ✅ Logged in as '$username'")
        } catch (e: StatusException) {
            println("  ❌ ${e.status.description}")
            println("  Please try a different username.")
        }
    }

    // ─── Step 2: Subscribe to event stream ───────────────────────
    val mainMenu = MainMenu(grpcClient, username)
    var currentChatSession: ChatSession? = null

    // Background job to collect events from the Subscribe stream
    val subscribeJob = launch(Dispatchers.IO) {
        try {
            grpcClient.subscribe(username).collect { event: StreamEvent ->
                when {
                    event.hasMessage() -> {
                        // Route to current chat session if it matches, otherwise notify
                        val session = currentChatSession
                        if (session != null) {
                            session.handleEvent(event)
                        } else {
                            // Show notification in menu context
                            val msg = event.message
                            println("\r  📨 New message from ${msg.senderUsername}: ${msg.content.take(40)}")
                            print("  > ")
                            System.out.flush()
                        }
                    }
                    event.hasStatusChange() -> {
                        // Route to both menu and current chat session
                        mainMenu.handleStatusChange(event)
                        currentChatSession?.handleEvent(event)
                    }
                }
            }
        } catch (e: CancellationException) {
            // Normal shutdown
            logger.debug { "Subscribe stream cancelled" }
        } catch (e: Exception) {
            logger.error(e) { "Subscribe stream error" }
            println("\r  ⚠ Connection to server lost. Please restart the application.")
        }
    }

    // ─── Step 3: Main menu loop ──────────────────────────────────
    try {
        while (true) {
            val targetUser = mainMenu.show() ?: break

            // Start a chat session
            val session = ChatSession(grpcClient, username, targetUser)
            currentChatSession = session
            session.start()
            currentChatSession = null
        }
    } finally {
        println("\n  👋 Goodbye!")
        subscribeJob.cancel()
        grpcClient.shutdown()
    }
}
