package com.lakhyadeep.papai.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ModelEntity::class,
        AssistantEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        BenchmarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PapiDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun assistantDao(): AssistantDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun benchmarkDao(): BenchmarkDao

    companion object {
        @Volatile
        private var INSTANCE: PapiDatabase? = null

        fun getInstance(context: Context): PapiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PapiDatabase::class.java,
                    "papi_ai.db"
                )
                    .addCallback(SeedCallback(context.applicationContext))
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): PapiDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                PapiDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }

    private class SeedCallback(private val appContext: Context) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val database = getInstance(appContext)
                database.assistantDao().insertAllIgnore(PredefinedData.assistants())
            }
        }
    }
}

object PredefinedData {
    fun assistants(): List<AssistantEntity> = listOf(
        AssistantEntity(
            id = "pal_general",
            name = "General Assistant",
            description = "Balanced everyday helper for Q&A, brainstorming, and clear explanations.",
            avatarKey = "spark",
            colorHex = 0xFF0D9488,
            systemPrompt = "You are Papi AI, a helpful, concise, and accurate private on-device AI assistant. Give clear, honest answers and admit when you are unsure.",
            temperature = 0.7f,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            contextLength = 4096,
            maxTokens = 512,
            isPredefined = true
        ),
        AssistantEntity(
            id = "pal_coding",
            name = "Coding Assistant",
            description = "Focused software engineer for Kotlin, Python, algorithms, debugging, and clean code.",
            avatarKey = "code",
            colorHex = 0xFF2563EB,
            systemPrompt = "You are an expert software engineer. Provide clean, well-structured, bug-free code with concise explanations and markdown code blocks.",
            temperature = 0.25f,
            topP = 0.85f,
            topK = 32,
            repeatPenalty = 1.08f,
            contextLength = 4096,
            maxTokens = 768,
            isPredefined = true
        ),
        AssistantEntity(
            id = "pal_writing",
            name = "Writing Assistant",
            description = "Polished editor for emails, essays, tone adjustments, and crisp summaries.",
            avatarKey = "edit",
            colorHex = 0xFFD97706,
            systemPrompt = "You are a skilled editor and writer. Improve clarity, flow, and tone while preserving the user's intent.",
            temperature = 0.65f,
            topP = 0.92f,
            topK = 45,
            repeatPenalty = 1.12f,
            contextLength = 4096,
            maxTokens = 640,
            isPredefined = true
        ),
        AssistantEntity(
            id = "pal_learning",
            name = "Learning Assistant",
            description = "Patient tutor who breaks down complex topics step-by-step with examples.",
            avatarKey = "school",
            colorHex = 0xFF059669,
            systemPrompt = "You are a supportive tutor. Break concepts into simple steps, check understanding, and give memorable real-world analogies.",
            temperature = 0.55f,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            contextLength = 4096,
            maxTokens = 640,
            isPredefined = true
        ),
        AssistantEntity(
            id = "pal_creative",
            name = "Creative Assistant",
            description = "Imaginative partner for stories, world-building, poetry, and fresh ideas.",
            avatarKey = "palette",
            colorHex = 0xFFDB2777,
            systemPrompt = "You are an imaginative creative partner. Offer vivid imagery, original angles, and expressive storytelling.",
            temperature = 0.9f,
            topP = 0.95f,
            topK = 60,
            repeatPenalty = 1.15f,
            contextLength = 4096,
            maxTokens = 768,
            isPredefined = true
        )
    )
}
