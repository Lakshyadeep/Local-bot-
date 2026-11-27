package com.lakhyadeep.papai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY isLoaded DESC, isDownloaded DESC, lastUsedAt DESC, createdAt ASC")
    fun observeAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE isDownloaded = 1 ORDER BY isLoaded DESC, lastUsedAt DESC")
    fun observeDownloadedModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE isLoaded = 1 LIMIT 1")
    fun observeLoadedModel(): Flow<ModelEntity?>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelEntity?

    @Query("SELECT * FROM models")
    suspend fun getAllModelsOnce(): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(models: List<ModelEntity>)

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Query("UPDATE models SET isLoaded = 0")
    suspend fun clearAllLoadedFlags()

    @Query("UPDATE models SET isLoaded = 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun markModelLoaded(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE models SET name = :newName WHERE id = :id")
    suspend fun renameModel(id: String, newName: String)

    @Query("UPDATE models SET downloadStatus = :status, downloadedBytes = :bytes, downloadError = :error WHERE id = :id")
    suspend fun updateDownloadProgress(id: String, status: String, bytes: Long, error: String? = null)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteModelById(id: String)
}

@Dao
interface AssistantDao {
    @Query("SELECT * FROM assistants ORDER BY isPredefined DESC, updatedAt DESC")
    fun observeAllAssistants(): Flow<List<AssistantEntity>>

    @Query("SELECT * FROM assistants WHERE id = :id LIMIT 1")
    suspend fun getAssistantById(id: String): AssistantEntity?

    @Query("SELECT * FROM assistants WHERE id = :id LIMIT 1")
    fun observeAssistantById(id: String): Flow<AssistantEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssistant(assistant: AssistantEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(assistants: List<AssistantEntity>)

    @Query("DELETE FROM assistants WHERE id = :id")
    suspend fun deleteAssistantById(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE assistantId = :assistantId ORDER BY updatedAt DESC")
    fun observeConversationsForAssistant(assistantId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET modelId = :modelId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationModel(id: String, modelId: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesForConversationOnce(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMessageContent(id: String, content: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt > :afterTimestamp")
    suspend fun deleteMessagesAfter(conversationId: String, afterTimestamp: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversationMessages(conversationId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)
}

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmarks ORDER BY timestamp DESC")
    fun observeAllBenchmarks(): Flow<List<BenchmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenchmark(benchmark: BenchmarkEntity)

    @Query("DELETE FROM benchmarks WHERE id = :id")
    suspend fun deleteBenchmarkById(id: String)

    @Query("DELETE FROM benchmarks")
    suspend fun clearAllBenchmarks()
}
