package com.chatapp.client

import com.chatapp.proto.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * gRPC client wrapper — manages channel lifecycle and exposes the ChatService stub.
 */
class GrpcClient(
    host: String = System.getenv("GRPC_SERVER_HOST") ?: "localhost",
    port: Int = System.getenv("GRPC_SERVER_PORT")?.toIntOrNull() ?: 9090
) {
    private val channel: ManagedChannel = ManagedChannelBuilder
        .forTarget("dns:///$host:$port")
        .usePlaintext()
        .keepAliveTime(30, TimeUnit.SECONDS)
        .keepAliveTimeout(10, TimeUnit.SECONDS)
        .build()

    private val stub = ChatServiceGrpcKt.ChatServiceCoroutineStub(channel)

    /**
     * Register or login a user.
     */
    suspend fun register(username: String): RegisterResponse {
        return stub.register(
            RegisterRequest.newBuilder()
                .setUsername(username)
                .build()
        )
    }

    /**
     * Send a message to another user.
     */
    suspend fun sendMessage(sender: String, recipient: String, content: String): SendMessageResponse {
        return stub.sendMessage(
            SendMessageRequest.newBuilder()
                .setSenderUsername(sender)
                .setRecipientUsername(recipient)
                .setContent(content)
                .build()
        )
    }

    /**
     * Subscribe to the event stream (incoming messages + status changes).
     */
    fun subscribe(username: String): Flow<StreamEvent> {
        return stub.subscribe(
            SubscribeRequest.newBuilder()
                .setUsername(username)
                .build()
        )
    }

    /**
     * Get recent conversations.
     */
    suspend fun getRecentChats(username: String): GetRecentChatsResponse {
        return stub.getRecentChats(
            GetRecentChatsRequest.newBuilder()
                .setUsername(username)
                .build()
        )
    }

    /**
     * Get message history with a specific user.
     */
    suspend fun getMessageHistory(username: String, otherUsername: String, limit: Int = 50): GetMessageHistoryResponse {
        return stub.getMessageHistory(
            GetMessageHistoryRequest.newBuilder()
                .setUsername(username)
                .setOtherUsername(otherUsername)
                .setLimit(limit)
                .build()
        )
    }

    /**
     * Gracefully shut down the gRPC channel.
     */
    fun shutdown() {
        logger.info { "Shutting down gRPC channel..." }
        channel.shutdown()
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            channel.shutdownNow()
        }
    }
}
