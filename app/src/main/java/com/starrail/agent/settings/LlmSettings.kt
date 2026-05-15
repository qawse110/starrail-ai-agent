package com.starrail.agent.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * LLM 配置数据
 */
data class LlmSettingsData(
    val provider: LlmProvider = LlmProvider.OPENAI,
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val enabled: Boolean = false,
    val darkMode: Boolean = false
)

/** 支持的 LLM 提供商 */
enum class LlmProvider(val displayName: String, val defaultEndpoint: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash"),
    CUSTOM("自定义", "https://", "gpt-4o-mini")
}

/**
 * LLM 配置持久化
 */
class LlmSettings(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_ENABLED = "llm_enabled"
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_BASE_URL = "llm_base_url"
        private const val KEY_MODEL = "llm_model"
        private const val KEY_TEMPERATURE = "llm_temperature"
        private const val KEY_MAX_TOKENS = "llm_max_tokens"
        private const val KEY_DARK_MODE = "dark_mode"

        fun getInstance(context: Context): LlmSettings {
            val prefs = context.getSharedPreferences("starrail_llm", Context.MODE_PRIVATE)
            return LlmSettings(prefs)
        }
    }

    fun load(): LlmSettingsData {
        val providerName = prefs.getString(KEY_PROVIDER, LlmProvider.OPENAI.name) ?: LlmProvider.OPENAI.name
        val provider = try { LlmProvider.valueOf(providerName) } catch (_: Exception) { LlmProvider.OPENAI }

        return LlmSettingsData(
            provider = provider,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            baseUrl = prefs.getString(KEY_BASE_URL, provider.defaultEndpoint)
                ?: provider.defaultEndpoint,
            model = prefs.getString(KEY_MODEL, provider.defaultModel)
                ?: provider.defaultModel,
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f).toDouble(),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 2048),
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            darkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        )
    }

    fun save(settings: LlmSettingsData) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_PROVIDER, settings.provider.name)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_BASE_URL, settings.baseUrl)
            .putString(KEY_MODEL, settings.model)
            .putFloat(KEY_TEMPERATURE, settings.temperature.toFloat())
            .putInt(KEY_MAX_TOKENS, settings.maxTokens)
            .putBoolean(KEY_DARK_MODE, settings.darkMode)
            .apply()
    }

    /** 根据提供商切换时自动填充默认端点 */
    fun applyProviderDefaults(provider: LlmProvider): LlmSettingsData {
        val current = load()
        return current.copy(
            provider = provider,
            baseUrl = provider.defaultEndpoint,
            model = provider.defaultModel
        )
    }
}