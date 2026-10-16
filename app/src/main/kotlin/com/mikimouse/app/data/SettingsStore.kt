package com.mikimouse.app.data

import android.content.Context
import com.mikimouse.app.models.SecureKeyStore
import com.mikimouse.operator.CpuPolicy
import com.mikimouse.operator.CustomAPIProvider
import com.mikimouse.operator.EnvKeyProvider
import com.mikimouse.operator.KeyProvider
import com.mikimouse.operator.LocalStubProvider
import com.mikimouse.operator.ModelProvider
import com.mikimouse.operator.ModelRouter
import com.mikimouse.operator.NetworkPolicy
import com.mikimouse.operator.OpenAICompatibleProvider
import com.mikimouse.operator.OpenRouterProvider
import com.mikimouse.operator.ResourceBudget
import java.io.File

class SettingsStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("operator_settings", Context.MODE_PRIVATE)
    private val keysDir: File get() = File(ctx.filesDir, "keys")

    var ramTargetMB: Int
        get() = prefs.getInt("ram_target", 2048)
        set(v) { prefs.edit().putInt("ram_target", v).apply() }
    var storageQuotaMB: Int
        get() = prefs.getInt("storage_quota", 5120)
        set(v) { prefs.edit().putInt("storage_quota", v).apply() }
    var cpu: CpuPolicy
        get() = runCatching { CpuPolicy.valueOf(prefs.getString("cpu", "BALANCED")!!) }.getOrDefault(CpuPolicy.BALANCED)
        set(v) { prefs.edit().putString("cpu", v.name).apply() }
    var network: NetworkPolicy
        get() = runCatching { NetworkPolicy.valueOf(prefs.getString("network", "ASK_BEFORE")!!) }.getOrDefault(NetworkPolicy.ASK_BEFORE)
        set(v) { prefs.edit().putString("network", v.name).apply() }
    var providerId: String
        get() = prefs.getString("provider", "local") ?: "local"
        set(v) { prefs.edit().putString("provider", v).apply() }
    var endpoint: String
        get() = prefs.getString("endpoint", "") ?: ""
        set(v) { prefs.edit().putString("endpoint", v).apply() }
    var model: String
        get() = prefs.getString("model", "") ?: ""
        set(v) { prefs.edit().putString("model", v).apply() }
    var memoryEnabled: Boolean
        get() = prefs.getBoolean("memory_enabled", true)
        set(v) { prefs.edit().putBoolean("memory_enabled", v).apply() }

    fun budget() = ResourceBudget(ramTargetMB, storageQuotaMB, cpu, network)

    fun keyAliasFor(id: String = providerId): String = when (id) {
        "openai" -> "OPENAI_API_KEY"
        "openrouter" -> "OPENROUTER_API_KEY"
        else -> "CUSTOM_API_KEY"
    }

    fun modelStatus(keystore: SecureKeyStore): String = when (providerId) {
        "local" -> "local stub (offline)"
        else -> {
            val has = KeystoreKeysFile.loadFrom(keysDir, keyAliasFor(), keystore) != null
            "$providerId · ${model.ifBlank { "default model" }} · " +
                if (has) "key ✓" else "no key — local stub until saved"
        }
    }

    fun router(keystore: SecureKeyStore): ModelRouter {
        val keys = KeystoreKeys(keystore, keysDir)
        val primary: ModelProvider = when (providerId) {
            "openai" -> OpenAICompatibleProvider(model = model.ifBlank { "gpt-4o-mini" }, keys = keys)
            "openrouter" -> OpenRouterProvider(model = model.ifBlank { "openrouter/auto" }, keys = keys)
            "custom" -> CustomAPIProvider(
                endpoint.ifBlank { throw IllegalStateException("custom endpoint empty") },
                model.ifBlank { "default" }, keys,
            )
            else -> LocalStubProvider()
        }
        return ModelRouter(primary = primary, fallback = LocalStubProvider())
    }
}

class KeystoreKeys(private val ks: SecureKeyStore, private val dir: File) : KeyProvider {
    override fun apiKey(alias: String): String? = KeystoreKeysFile.loadFrom(dir, alias, ks)
}

object KeystoreKeysFile {
    // Key alias per provider: OPENAI_API_KEY / OPENROUTER_API_KEY / CUSTOM_API_KEY.
    fun save(alias: String, value: String, ks: SecureKeyStore, dir: File) {
        val (iv, cipher) = ks.encrypt(value.toByteArray())
        dir.mkdirs()
        File(dir, "$alias.iv").writeBytes(iv)
        File(dir, "$alias.bin").writeBytes(cipher)
    }

    fun loadFrom(dir: File, alias: String, ks: SecureKeyStore): String? = try {
        val iv = File(dir, "$alias.iv").takeIf { it.exists() }?.readBytes() ?: return null
        val cipher = File(dir, "$alias.bin").takeIf { it.exists() }?.readBytes() ?: return null
        ks.decrypt(iv, cipher).decodeToString()
    } catch (_: Exception) { null }

    fun clear(dir: File, alias: String) {
        File(dir, "$alias.iv").delete()
        File(dir, "$alias.bin").delete()
    }
}
