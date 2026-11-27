package com.lakhyadeep.papai.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val repoId: String,
    val fileName: String,
    val localPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val quantization: String = "Q4_K_M",
    val parameterSizeLabel: String = "",
    val contextLength: Int = 4096,
    val architecture: String = "llama",
    val description: String = "",
    val capabilities: String = "Chat, Reasoning",
    val downloadUrl: String = "",
    val isDownloaded: Boolean = false,
    val isImported: Boolean = false,
    val isLoaded: Boolean = false,
    val downloadStatus: String = DownloadStatus.NOT_DOWNLOADED.name,
    val downloadedBytes: Long = 0L,
    val downloadError: String? = null,
    val ggufVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "assistants")
data class AssistantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val avatarKey: String,
    val colorHex: Long,
    val systemPrompt: String,
    val preferredModelId: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val contextLength: Int = 4096,
    val maxTokens: Int = 512,
    val isPredefined: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val assistantId: String,
    val modelId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // USER, ASSISTANT, SYSTEM, TOOL
    val content: String,
    val modelName: String? = null,
    val tokensPerSecond: Float = 0f,
    val tokenCount: Int = 0,
    val generationTimeMs: Long = 0L,
    val toolCallsSummary: String? = null,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

@Entity(tableName = "benchmarks")
data class BenchmarkEntity(
    @PrimaryKey val id: String,
    val modelId: String,
    val modelName: String,
    val quantization: String,
    val loadTimeMs: Long,
    val promptEvalSpeedTps: Float,
    val tokenGenSpeedTps: Float,
    val totalGenTimeMs: Long,
    val tokensGenerated: Int,
    val estimatedRamMb: Long,
    val deviceModel: String,
    val cpuAbi: String,
    val totalRamMb: Long,
    val threadsUsed: Int,
    val contextLength: Int,
    val timestamp: Long = System.currentTimeMillis()
)
