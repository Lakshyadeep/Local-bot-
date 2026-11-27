package com.lakhyadeep.papai

import com.lakhyadeep.papai.data.local.db.MessageEntity
import com.lakhyadeep.papai.data.local.db.MessageRole
import com.lakhyadeep.papai.domain.device.CompatibilityStatus
import com.lakhyadeep.papai.domain.device.DeviceCapabilities
import com.lakhyadeep.papai.domain.device.GgufInspector
import com.lakhyadeep.papai.domain.device.ModelRecommendationEngine
import com.lakhyadeep.papai.domain.inference.ChatTemplateType
import com.lakhyadeep.papai.domain.inference.InferenceSettings
import com.lakhyadeep.papai.domain.inference.InferenceSettingsValidator
import com.lakhyadeep.papai.domain.inference.PromptFormatter
import com.lakhyadeep.papai.domain.tools.LocalToolRegistry
import com.lakhyadeep.papai.domain.tools.ToolCallRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PapiUnitTest {

    @Test
    fun `gguf inspector rejects invalid magic bytes`() {
        val badStream = ByteArrayInputStream("NOT_A_GGUF_FILE_1234567890".toByteArray())
        val meta = GgufInspector.inspectStream(badStream)
        assertFalse(meta.isValidGguf)
    }

    @Test
    fun `local tool registry executes safe math and unit conversion`() {
        val registry = LocalToolRegistry()
        val calc = registry.execute(ToolCallRequest("calculator", mapOf("expression" to "(10 + 5) * 2 + sqrt(81)")))
        assertTrue(calc.success)
        assertTrue(calc.output.endsWith("39"))

        val conv = registry.execute(ToolCallRequest("unit_converter", mapOf("value" to "100", "from" to "c", "to" to "f")))
        assertTrue(conv.success)
        assertTrue(conv.output.contains("212"))
    }

    @Test
    fun `inference settings validator rejects gpu offload on cpu-only engine`() {
        val res = InferenceSettingsValidator.validate(
            settings = InferenceSettings(gpuLayers = 12, temperature = 0.7f),
            hasGpuSupport = false,
            maxCpuCores = 8
        )
        assertFalse(res.isValid)
        assertEquals(0, res.sanitized.gpuLayers)
    }

    @Test
    fun `model recommendation flags insufficient RAM on constrained device`() {
        val lowRamDevice = DeviceCapabilities(
            deviceModel = "TestPhone",
            manufacturer = "OEM",
            totalRamBytes = 3L * 1024 * 1024 * 1024,
            availableRamBytes = 1200L * 1024 * 1024,
            isLowMemory = false,
            availableStorageBytes = 20L * 1024 * 1024 * 1024,
            totalStorageBytes = 64L * 1024 * 1024 * 1024,
            cpuCores = 8,
            primaryAbi = "arm64-v8a",
            supportedAbis = listOf("arm64-v8a"),
            is64Bit = true,
            hasGpuInferenceSupport = false,
            gpuBackendInfo = "CPU",
            isNetworkConnected = true
        )
        val fourGbModel = 4L * 1024 * 1024 * 1024
        val rec = ModelRecommendationEngine.evaluate(fourGbModel, "Q4_K_M", 4096, lowRamDevice)
        assertEquals(CompatibilityStatus.INSUFFICIENT_RAM, rec.status)
    }

    @Test
    fun `prompt formatter applies chatml template cleanly`() {
        val formatted = PromptFormatter.formatConversation(
            systemPrompt = "Be concise.",
            history = listOf(
                MessageEntity(id = "1", conversationId = "c", role = MessageRole.USER.name, content = "Hi")
            ),
            modelNameOrArch = "Qwen2.5-0.5B",
            templateType = ChatTemplateType.AUTO,
            contextLength = 2048,
            maxResponseTokens = 256
        )
        assertTrue(formatted.prompt.contains("<|im_start|>system"))
        assertTrue(formatted.prompt.endsWith("<|im_start|>assistant\n"))
    }
}
