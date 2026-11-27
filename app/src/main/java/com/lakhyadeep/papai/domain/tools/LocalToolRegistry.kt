package com.lakhyadeep.papai.domain.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class ToolCallRequest(
    val toolName: String,
    val arguments: Map<String, String>,
    val requiresUserConfirmation: Boolean = false,
    val confirmationDescription: String? = null
)

data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val output: String
)

class LocalToolRegistry {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildToolsSystemInstruction(): String = """
        You have access to safe local offline tools. When a user explicitly asks for math calculations, current date/time, unit conversions, text analysis, or document summarization, you may output a single JSON block inside <tool_call> tags:
        <tool_call>{"name": "tool_name", "arguments": {...}}</tool_call>
        Available tools:
        1. calculator: {"expression": "math expression e.g. (12.5 * 4) + sqrt(81)"}
        2. datetime: {"format": "full|date|time|iso"}
        3. unit_converter: {"value": 100, "from": "km|m|cm|mm|mi|ft|kg|g|lb|oz|c|f|k|mb|gb|tb|kb|mph|kph", "to": "..."}
        4. text_processor: {"text": "...", "operation": "stats|uppercase|lowercase|keywords"}
        5. document_summarizer: {"text": "...", "max_sentences": 3}
        Only invoke a tool when helpful. Do not invent tools outside this list.
    """.trimIndent()

    fun parseToolCall(modelResponse: String): ToolCallRequest? {
        val regex = Regex("<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>", setOf(RegexOption.DOT_MATCHES_ALL))
        val match = regex.find(modelResponse) ?: return null
        val jsonText = match.groupValues[1]
        return try {
            val obj = json.parseToJsonElement(jsonText).jsonObject
            val name = obj["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (name.isEmpty()) return null
            val argsObj = obj["arguments"] as? JsonObject ?: JsonObject(emptyMap())
            val args = argsObj.entries.associate { (k, v) ->
                k to (v.jsonPrimitive.content)
            }
            ToolCallRequest(
                toolName = name,
                arguments = args,
                requiresUserConfirmation = false
            )
        } catch (_: Exception) {
            null
        }
    }

    fun execute(request: ToolCallRequest): ToolExecutionResult {
        return when (request.toolName.lowercase(Locale.ROOT)) {
            "calculator" -> executeCalculator(request.arguments)
            "datetime" -> executeDateTime(request.arguments)
            "unit_converter" -> executeUnitConverter(request.arguments)
            "text_processor" -> executeTextProcessor(request.arguments)
            "document_summarizer" -> executeSummarizer(request.arguments)
            else -> ToolExecutionResult(
                toolName = request.toolName,
                success = false,
                output = "Unsupported local tool '${request.toolName}'. Only built-in safe tools are allowed."
            )
        }
    }

    private fun executeCalculator(args: Map<String, String>): ToolExecutionResult {
        val expr = args["expression"]?.trim().orEmpty()
        if (expr.isEmpty() || expr.length > 256) {
            return ToolExecutionResult("calculator", false, "Invalid or empty math expression.")
        }
        return try {
            val value = MathEvaluator(expr).parse()
            val formatted = if (value % 1.0 == 0.0 && abs(value) < 1e15) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
            }
            ToolExecutionResult("calculator", true, "$expr = $formatted")
        } catch (e: Exception) {
            ToolExecutionResult("calculator", false, "Calculation error: ${e.message}")
        }
    }

    private fun executeDateTime(args: Map<String, String>): ToolExecutionResult {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val format = args["format"]?.lowercase(Locale.ROOT) ?: "full"
        val result = when (format) {
            "date" -> now.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy", Locale.getDefault()))
            "time" -> now.format(DateTimeFormatter.ofPattern("hh:mm:ss a z", Locale.getDefault()))
            "iso" -> now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            else -> now.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy • hh:mm a z", Locale.getDefault()))
        }
        return ToolExecutionResult("datetime", true, result)
    }

    private fun executeUnitConverter(args: Map<String, String>): ToolExecutionResult {
        val value = args["value"]?.toDoubleOrNull()
            ?: return ToolExecutionResult("unit_converter", false, "Invalid numeric 'value' argument.")
        val from = args["from"]?.lowercase(Locale.ROOT)?.trim().orEmpty()
        val to = args["to"]?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (from.isEmpty() || to.isEmpty()) {
            return ToolExecutionResult("unit_converter", false, "Both 'from' and 'to' units are required.")
        }

        val converted = convertUnits(value, from, to)
            ?: return ToolExecutionResult(
                "unit_converter",
                false,
                "Unsupported conversion from '$from' to '$to'."
            )

        val formatted = String.format(Locale.US, "%.4f", converted).trimEnd('0').trimEnd('.')
        return ToolExecutionResult("unit_converter", true, "$value $from = $formatted $to")
    }

    private fun executeTextProcessor(args: Map<String, String>): ToolExecutionResult {
        val text = args["text"].orEmpty()
        if (text.isBlank()) {
            return ToolExecutionResult("text_processor", false, "Input 'text' cannot be empty.")
        }
        val op = args["operation"]?.lowercase(Locale.ROOT) ?: "stats"
        val output = when (op) {
            "uppercase" -> text.uppercase(Locale.getDefault())
            "lowercase" -> text.lowercase(Locale.getDefault())
            "keywords" -> extractTopKeywords(text, 8).joinToString(", ")
            else -> {
                val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val lines = text.lines().size
                val sentences = text.split(Regex("[.!?]+")).count { it.isNotBlank() }
                "Characters: ${text.length} | Words: ${words.size} | Sentences: $sentences | Lines: $lines"
            }
        }
        return ToolExecutionResult("text_processor", true, output)
    }

    private fun executeSummarizer(args: Map<String, String>): ToolExecutionResult {
        val text = args["text"].orEmpty()
        if (text.isBlank()) {
            return ToolExecutionResult("document_summarizer", false, "Document text cannot be empty.")
        }
        val maxSentences = args["max_sentences"]?.toIntOrNull()?.coerceIn(1, 8) ?: 3
        val summary = summarizeExtractive(text, maxSentences)
        return ToolExecutionResult("document_summarizer", true, summary)
    }

    fun summarizeExtractive(text: String, maxSentences: Int = 3): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 15 }
        if (sentences.size <= maxSentences) return text.trim()

        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "is", "are", "was", "were", "be", "this", "that", "it", "as"
        )
        val freq = mutableMapOf<String, Int>()
        for (sentence in sentences) {
            sentence.lowercase(Locale.ROOT)
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length > 2 && it !in stopWords }
                .forEach { w -> freq[w] = (freq[w] ?: 0) + 1 }
        }

        val scored = sentences.mapIndexed { idx, s ->
            val words = s.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            val score = words.sumOf { freq[it] ?: 0 }.toDouble() / (words.size.coerceAtLeast(1))
            Triple(idx, s, score)
        }
        return scored.sortedByDescending { it.third }
            .take(maxSentences)
            .sortedBy { it.first }
            .joinToString(" ") { it.second }
    }

    private fun extractTopKeywords(text: String, limit: Int): List<String> {
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "is", "are", "was", "were", "be", "this", "that", "it", "from"
        )
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 3 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    private fun convertUnits(value: Double, from: String, to: String): Double? {
        if (from == to) return value

        // Temperature
        val tempUnits = setOf("c", "f", "k")
        if (from in tempUnits && to in tempUnits) {
            val celsius = when (from) {
                "c" -> value
                "f" -> (value - 32.0) * 5.0 / 9.0
                "k" -> value - 273.15
                else -> return null
            }
            return when (to) {
                "c" -> celsius
                "f" -> (celsius * 9.0 / 5.0) + 32.0
                "k" -> celsius + 273.15
                else -> null
            }
        }

        // Length (base: meter)
        val lengthToM = mapOf(
            "m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001,
            "mi" to 1609.344, "ft" to 0.3048, "in" to 0.0254, "yd" to 0.9144
        )
        if (from in lengthToM && to in lengthToM) {
            return value * lengthToM.getValue(from) / lengthToM.getValue(to)
        }

        // Weight (base: gram)
        val weightToG = mapOf(
            "g" to 1.0, "kg" to 1000.0, "mg" to 0.001, "lb" to 453.59237, "oz" to 28.3495231
        )
        if (from in weightToG && to in weightToG) {
            return value * weightToG.getValue(from) / weightToG.getValue(to)
        }

        // Data storage (base: byte, binary IEC/1024)
        val dataToBytes = mapOf(
            "b" to 1.0, "kb" to 1024.0, "mb" to 1024.0 * 1024.0,
            "gb" to 1024.0 * 1024.0 * 1024.0, "tb" to 1024.0 * 1024.0 * 1024.0 * 1024.0
        )
        if (from in dataToBytes && to in dataToBytes) {
            return value * dataToBytes.getValue(from) / dataToBytes.getValue(to)
        }

        // Speed (base: m/s)
        val speedToMs = mapOf("m/s" to 1.0, "kph" to 1.0 / 3.6, "km/h" to 1.0 / 3.6, "mph" to 0.44704)
        if (from in speedToMs && to in speedToMs) {
            return value * speedToMs.getValue(from) / speedToMs.getValue(to)
        }

        return null
    }

    private class MathEvaluator(private val expr: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < expr.length) expr[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < expr.length) throw IllegalArgumentException("Unexpected character '${ch.toChar()}'")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val d = parseFactor()
                        if (d == 0.0) throw ArithmeticException("Division by zero")
                        x /= d
                    }
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = expr.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                val func = expr.substring(startPos, pos).lowercase(Locale.ROOT)
                x = when (func) {
                    "pi" -> Math.PI
                    "e" -> Math.E
                    else -> {
                        val arg = parseFactor()
                        when (func) {
                            "sqrt" -> sqrt(arg)
                            "sin" -> sin(Math.toRadians(arg))
                            "cos" -> cos(Math.toRadians(arg))
                            "tan" -> tan(Math.toRadians(arg))
                            "log" -> log10(arg)
                            "ln" -> ln(arg)
                            "abs" -> abs(arg)
                            else -> throw IllegalArgumentException("Unknown function '$func'")
                        }
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected symbol at position $pos")
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
