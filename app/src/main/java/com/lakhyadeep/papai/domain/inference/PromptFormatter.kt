package com.lakhyadeep.papai.domain.inference

import com.lakhyadeep.papai.data.local.db.MessageEntity
import com.lakhyadeep.papai.data.local.db.MessageRole

data class FormattedPromptResult(
    val prompt: String,
    val estimatedTokens: Int,
    val resolvedTemplate: ChatTemplateType,
    val stopTokens: List<String>
)

object PromptFormatter {

    fun detectTemplate(modelNameOrArch: String, configured: ChatTemplateType): ChatTemplateType {
        if (configured != ChatTemplateType.AUTO) return configured
        val lower = modelNameOrArch.lowercase()
        return when {
            lower.contains("llama-3") || lower.contains("llama3") -> ChatTemplateType.LLAMA3
            lower.contains("phi-3") || lower.contains("phi3") || lower.contains("phi-4") -> ChatTemplateType.PHI3
            lower.contains("gemma") -> ChatTemplateType.GEMMA
            lower.contains("alpaca") -> ChatTemplateType.ALPACA
            else -> ChatTemplateType.CHATML
        }
    }

    fun formatConversation(
        systemPrompt: String,
        history: List<MessageEntity>,
        modelNameOrArch: String,
        templateType: ChatTemplateType,
        contextLength: Int,
        maxResponseTokens: Int,
        customStopSequences: String = "",
        toolsPromptBlock: String? = null
    ): FormattedPromptResult {
        val resolved = detectTemplate(modelNameOrArch, templateType)
        val combinedSystem = buildString {
            append(systemPrompt.trim())
            if (!toolsPromptBlock.isNullOrBlank()) {
                append("\n\n")
                append(toolsPromptBlock.trim())
            }
        }

        // Reserve space for generation + system prompt
        val tokenBudgetForHistory = (contextLength - maxResponseTokens - estimateTokens(combinedSystem) - 64)
            .coerceAtLeast(256)

        val validMessages = history.filter {
            !it.isError && (it.role == MessageRole.USER.name || it.role == MessageRole.ASSISTANT.name)
        }

        // Keep most recent turns fitting within context budget
        val selectedReversed = mutableListOf<MessageEntity>()
        var usedTokens = 0
        for (msg in validMessages.asReversed()) {
            val msgTokens = estimateTokens(msg.content) + 12
            if (usedTokens + msgTokens > tokenBudgetForHistory && selectedReversed.isNotEmpty()) {
                break
            }
            selectedReversed.add(msg)
            usedTokens += msgTokens
        }
        val trimmedHistory = selectedReversed.asReversed()

        val prompt = when (resolved) {
            ChatTemplateType.CHATML, ChatTemplateType.AUTO -> buildChatMl(combinedSystem, trimmedHistory)
            ChatTemplateType.LLAMA3 -> buildLlama3(combinedSystem, trimmedHistory)
            ChatTemplateType.PHI3 -> buildPhi3(combinedSystem, trimmedHistory)
            ChatTemplateType.GEMMA -> buildGemma(combinedSystem, trimmedHistory)
            ChatTemplateType.ALPACA -> buildAlpaca(combinedSystem, trimmedHistory)
            ChatTemplateType.RAW -> buildRaw(combinedSystem, trimmedHistory)
        }

        val defaultStops = when (resolved) {
            ChatTemplateType.CHATML, ChatTemplateType.AUTO -> listOf("<|im_end|>", "<|endoftext|>")
            ChatTemplateType.LLAMA3 -> listOf("<|eot_id|>", "<|end_of_text|>")
            ChatTemplateType.PHI3 -> listOf("<|end|>", "<|endoftext|>")
            ChatTemplateType.GEMMA -> listOf("<end_of_turn>", "<eos>")
            ChatTemplateType.ALPACA -> listOf("### Instruction:", "</s>")
            ChatTemplateType.RAW -> listOf("\nUser:", "</s>")
        }

        val userStops = customStopSequences
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return FormattedPromptResult(
            prompt = prompt,
            estimatedTokens = estimateTokens(prompt),
            resolvedTemplate = resolved,
            stopTokens = (defaultStops + userStops).distinct()
        )
    }

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length / 3.6).toInt().coerceAtLeast(1)
    }

    private fun buildChatMl(system: String, messages: List<MessageEntity>): String = buildString {
        if (system.isNotBlank()) {
            append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        }
        for (msg in messages) {
            val role = if (msg.role == MessageRole.USER.name) "user" else "assistant"
            append("<|im_start|>").append(role).append("\n")
            append(msg.content.trim()).append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }

    private fun buildLlama3(system: String, messages: List<MessageEntity>): String = buildString {
        append("<|begin_of_text|>")
        if (system.isNotBlank()) {
            append("<|start_header_id|>system<|end_header_id|>\n\n")
            append(system).append("<|eot_id|>")
        }
        for (msg in messages) {
            val role = if (msg.role == MessageRole.USER.name) "user" else "assistant"
            append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
            append(msg.content.trim()).append("<|eot_id|>")
        }
        append("<|start_header_id|>assistant<|end_header_id|>\n\n")
    }

    private fun buildPhi3(system: String, messages: List<MessageEntity>): String = buildString {
        if (system.isNotBlank()) {
            append("<|system|>\n").append(system).append("<|end|>\n")
        }
        for (msg in messages) {
            val tag = if (msg.role == MessageRole.USER.name) "<|user|>" else "<|assistant|>"
            append(tag).append("\n").append(msg.content.trim()).append("<|end|>\n")
        }
        append("<|assistant|>\n")
    }

    private fun buildGemma(system: String, messages: List<MessageEntity>): String = buildString {
        var firstUser = true
        for (msg in messages) {
            val role = if (msg.role == MessageRole.USER.name) "user" else "model"
            append("<start_of_turn>").append(role).append("\n")
            if (firstUser && role == "user" && system.isNotBlank()) {
                append(system).append("\n\n")
                firstUser = false
            }
            append(msg.content.trim()).append("<end_of_turn>\n")
        }
        append("<start_of_turn>model\n")
    }

    private fun buildAlpaca(system: String, messages: List<MessageEntity>): String = buildString {
        if (system.isNotBlank()) {
            append(system).append("\n\n")
        }
        for (msg in messages) {
            if (msg.role == MessageRole.USER.name) {
                append("### Instruction:\n").append(msg.content.trim()).append("\n\n")
            } else {
                append("### Response:\n").append(msg.content.trim()).append("\n\n")
            }
        }
        append("### Response:\n")
    }

    private fun buildRaw(system: String, messages: List<MessageEntity>): String = buildString {
        if (system.isNotBlank()) {
            append(system).append("\n\n")
        }
        for (msg in messages) {
            val prefix = if (msg.role == MessageRole.USER.name) "User: " else "Assistant: "
            append(prefix).append(msg.content.trim()).append("\n")
        }
        append("Assistant: ")
    }
}
