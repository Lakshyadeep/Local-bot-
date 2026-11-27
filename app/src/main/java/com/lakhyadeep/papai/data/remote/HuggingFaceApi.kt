package com.lakhyadeep.papai.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class HfSiblingDto(
    @Json(name = "rfilename") val rfilename: String = "",
    @Json(name = "size") val size: Long? = null
)

@JsonClass(generateAdapter = true)
data class HfModelSummaryDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "author") val author: String? = null,
    @Json(name = "downloads") val downloads: Long = 0L,
    @Json(name = "likes") val likes: Long = 0L,
    @Json(name = "tags") val tags: List<String> = emptyList(),
    @Json(name = "pipeline_tag") val pipelineTag: String? = null,
    @Json(name = "gated") val gated: Any? = null,
    @Json(name = "private") val isPrivate: Boolean = false,
    @Json(name = "siblings") val siblings: List<HfSiblingDto> = emptyList(),
    @Json(name = "lastModified") val lastModified: String? = null
)

@JsonClass(generateAdapter = true)
data class HfTreeFileDto(
    @Json(name = "type") val type: String = "",
    @Json(name = "path") val path: String = "",
    @Json(name = "size") val size: Long? = null
)

interface HuggingFaceApi {
    @GET("api/models")
    suspend fun searchGgufModels(
        @Query("search") search: String? = null,
        @Query("filter") filter: String = "gguf",
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: Int = -1,
        @Query("limit") limit: Int = 25,
        @Query("full") full: Boolean = true
    ): List<HfModelSummaryDto>

    @GET("api/models/{repoId}")
    suspend fun getModelDetails(
        @Path(value = "repoId", encoded = true) repoId: String,
        @Query("files_metadata") filesMetadata: Boolean = true
    ): HfModelSummaryDto

    @GET("api/models/{repoId}/tree/main")
    suspend fun getRepoTreeFiles(
        @Path(value = "repoId", encoded = true) repoId: String,
        @Query("recursive") recursive: Boolean = true
    ): List<HfTreeFileDto>
}

data class HfModelInfo(
    val repoId: String,
    val displayName: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val tags: List<String>,
    val isGated: Boolean,
    val description: String,
    val capabilities: String,
    val ggufFiles: List<HfGgufVariant>
)

data class HfGgufVariant(
    val fileName: String,
    val fileSizeBytes: Long,
    val quantization: String,
    val downloadUrl: String
)

class HuggingFaceRepository(private val tokenProvider: () -> String) {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val token = tokenProvider().trim()
                val reqBuilder = chain.request().newBuilder()
                    .header("User-Agent", "PapiAI-Android/1.0")
                if (token.isNotEmpty()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
                chain.proceed(reqBuilder.build())
            })
            .build()
    }

    private val api: HuggingFaceApi by lazy {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl("https://huggingface.co/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HuggingFaceApi::class.java)
    }

    suspend fun searchModels(query: String): Result<List<HfModelInfo>> = runCatching {
        val cleanQuery = query.trim().ifEmpty { null }
        val response = api.searchGgufModels(search = cleanQuery, limit = 30)
        response
            .filter { !it.isPrivate && it.id.isNotBlank() }
            .map { dto -> dto.toDomainModel() }
    }

    suspend fun fetchModelVariants(repoId: String): Result<HfModelInfo> = runCatching {
        val details = api.getModelDetails(repoId = repoId, filesMetadata = true)
        val treeFiles = runCatching { api.getRepoTreeFiles(repoId = repoId) }.getOrDefault(emptyList())

        val sizeByPath = treeFiles.associate { it.path to (it.size ?: 0L) }
        val rawFiles = details.siblings
            .filter { it.rfilename.endsWith(".gguf", ignoreCase = true) }
            .map { sibling ->
                val resolvedSize = sibling.size ?: sizeByPath[sibling.rfilename] ?: 0L
                HfGgufVariant(
                    fileName = sibling.rfilename,
                    fileSizeBytes = resolvedSize,
                    quantization = inferQuantization(sibling.rfilename),
                    downloadUrl = "https://huggingface.co/$repoId/resolve/main/${sibling.rfilename}?download=true"
                )
            }
            .ifEmpty {
                treeFiles
                    .filter { it.type == "file" && it.path.endsWith(".gguf", ignoreCase = true) }
                    .map { tf ->
                        HfGgufVariant(
                            fileName = tf.path,
                            fileSizeBytes = tf.size ?: 0L,
                            quantization = inferQuantization(tf.path),
                            downloadUrl = "https://huggingface.co/$repoId/resolve/main/${tf.path}?download=true"
                        )
                    }
            }
            .sortedBy { it.fileSizeBytes }

        details.toDomainModel(overrideVariants = rawFiles)
    }

    private fun HfModelSummaryDto.toDomainModel(
        overrideVariants: List<HfGgufVariant>? = null
    ): HfModelInfo {
        val variants = overrideVariants ?: siblings
            .filter { it.rfilename.endsWith(".gguf", ignoreCase = true) }
            .map { sibling ->
                HfGgufVariant(
                    fileName = sibling.rfilename,
                    fileSizeBytes = sibling.size ?: 0L,
                    quantization = inferQuantization(sibling.rfilename),
                    downloadUrl = "https://huggingface.co/$id/resolve/main/${sibling.rfilename}?download=true"
                )
            }
            .sortedBy { it.fileSizeBytes }

        val shortName = id.substringAfterLast('/')
        val authorName = author ?: id.substringBefore('/', missingDelimiterValue = "Community")
        val caps = buildList {
            if (!pipelineTag.isNullOrBlank()) add(pipelineTag.replace('-', ' ').replaceFirstChar { it.uppercase() })
            if (tags.any { it.contains("conversational", true) || it.contains("chat", true) }) add("Chat")
            if (tags.any { it.contains("code", true) }) add("Coding")
            if (tags.any { it.contains("multilingual", true) }) add("Multilingual")
        }.distinct().ifEmpty { listOf("Text Generation", "GGUF") }.joinToString(" • ")

        val isGatedRepo = when (gated) {
            is Boolean -> gated
            is String -> gated != "false" && gated.isNotBlank()
            else -> false
        }

        val desc = buildString {
            append("Hugging Face GGUF repository by $authorName.")
            if (isGatedRepo) append(" Requires Hugging Face access token.")
            if (variants.isNotEmpty()) append(" ${variants.size} GGUF quantization(s) available.")
        }

        return HfModelInfo(
            repoId = id,
            displayName = shortName,
            author = authorName,
            downloads = downloads,
            likes = likes,
            tags = tags.take(6),
            isGated = isGatedRepo,
            description = desc,
            capabilities = caps,
            ggufFiles = variants
        )
    }

    private fun inferQuantization(fileName: String): String {
        val regex = Regex("(IQ[1-4]_[A-Z0-9_]+|Q[2-8]_[0-9A-Z_]+|F16|BF16|F32)", RegexOption.IGNORE_CASE)
        return regex.find(fileName)?.value?.uppercase() ?: "GGUF"
    }
}
