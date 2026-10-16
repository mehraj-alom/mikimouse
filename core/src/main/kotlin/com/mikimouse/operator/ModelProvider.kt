package com.mikimouse.operator

interface KeyProvider {
    fun apiKey(alias: String): String?
}

class EnvKeyProvider : KeyProvider {
    override fun apiKey(alias: String): String? = System.getenv(alias)
}

data class ChatRequest(
    val system: String = "",
    val user: String,
    val maxTokens: Int = 1024,
    val temperature: Double = 0.2,
)

interface ModelProvider {
    val id: String
    fun chat(req: ChatRequest): String
}

class LocalStubProvider : ModelProvider {
    override val id = "local-stub"
    override fun chat(req: ChatRequest): String =
        "LOCAL-DRAFT for: ${req.user.take(400)}\n\n(This is the offline stub. Wire llama.cpp/ONNX/MediaPipe in P1; planner and pipelines do not depend on it.)"
}

abstract class HttpLlmProvider(
    override val id: String,
    protected val endpoint: String,
    protected val model: String,
    protected val keys: KeyProvider,
    protected val keyAlias: String,
) : ModelProvider {
    protected fun requireKey(): String =
        keys.apiKey(keyAlias) ?: throw IllegalStateException("missing API key: $keyAlias")
}

class OpenAICompatibleProvider(
    endpoint: String = "https://api.openai.com/v1",
    model: String = "gpt-4o-mini",
    keys: KeyProvider = EnvKeyProvider(),
) : HttpLlmProvider("openai-compatible", endpoint, model, keys, "OPENAI_API_KEY") {
    override fun chat(req: ChatRequest): String {
        val key = requireKey()
        val url = java.net.URL("$endpoint/chat/completions")
        val body = buildJson(model, req)
        return post(url, key, body)
    }

    private fun post(url: java.net.URL, key: String, body: String): String {
        val c = (url.openConnection() as java.net.HttpURLConnection)
        c.requestMethod = "POST"
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code !in 200..299) throw IllegalStateException("llm http $code: ${text.take(300)}")
        return text.take(4000)
    }

    private fun buildJson(model: String, req: ChatRequest): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").take(8000)
        return """{"model":"${esc(model)}","max_tokens":${req.maxTokens},"temperature":${req.temperature},"messages":[{"role":"system","content":"${esc(req.system)}"},{"role":"user","content":"${esc(req.user)}"}]}"""
    }
}

class OpenRouterProvider(
    model: String = "openrouter/auto",
    keys: KeyProvider = EnvKeyProvider(),
) : HttpLlmProvider("openrouter", "https://openrouter.ai/api/v1", model, keys, "OPENROUTER_API_KEY") {
    private val inner = OpenAICompatibleProvider(endpoint, model, keys)
    override fun chat(req: ChatRequest): String = inner.chat(req)
}

class CustomAPIProvider(
    endpoint: String,
    model: String,
    keys: KeyProvider = EnvKeyProvider(),
    keyAlias: String = "CUSTOM_API_KEY",
) : HttpLlmProvider("custom", endpoint, model, keys, keyAlias) {
    private val inner = OpenAICompatibleProvider(endpoint, model, keys)
    override fun chat(req: ChatRequest): String = inner.chat(req)
}

class ModelRouter(
    var primary: ModelProvider = LocalStubProvider(),
    var fallback: ModelProvider = LocalStubProvider(),
) {
    fun chat(req: ChatRequest): String = try {
        primary.chat(req)
    } catch (t: Throwable) {
        fallback.chat(req)
    }

    fun switch(id: String, make: (String) -> ModelProvider) {
        primary = make(id)
    }
}
