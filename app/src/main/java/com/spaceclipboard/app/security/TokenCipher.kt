package com.spaceclipboard.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String?
}

/**
 * Protege as credenciais de saída com AES-GCM.
 *
 * Em aparelhos com Android Keystore funcional a chave nunca sai do Keystore.
 * Projetores econômicos (por exemplo, HY300) podem ter o Keymaster quebrado; nesse caso
 * usamos uma chave AES local no sandbox privado do aplicativo. É um fallback transparente,
 * documentado no README, para manter o pareamento funcional nesses aparelhos.
 */
class AndroidTokenCipher(context: Context) : TokenCipher {
    private val appContext = context.applicationContext
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }
    private val fallbackPrefs = appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)

    @Volatile private var hardwareUnavailable = false

    override fun encrypt(plainText: String): String {
        val selected = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, selected.key)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val prefix = if (selected.hardware) HARDWARE_PREFIX else SOFTWARE_PREFIX
        return prefix + Base64.encodeToString(
            cipher.iv + encrypted,
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
    }

    override fun decrypt(cipherText: String): String? {
        val hardware = when {
            cipherText.startsWith(HARDWARE_PREFIX) -> true
            cipherText.startsWith(SOFTWARE_PREFIX) -> false
            else -> null
        }
        val encoded = when {
            cipherText.startsWith(HARDWARE_PREFIX) -> cipherText.removePrefix(HARDWARE_PREFIX)
            cipherText.startsWith(SOFTWARE_PREFIX) -> cipherText.removePrefix(SOFTWARE_PREFIX)
            else -> cipherText
        }
        if (hardware != null) {
            val key = runCatching {
                if (hardware) keystoreKey() else fallbackKey()
            }.getOrNull() ?: return null
            return decryptWith(encoded, key)
        }
        // Compatibilidade com o formato anterior, sem marcador.
        return decryptWith(encoded, runCatching { keystoreKey() }.getOrNull())
            ?: decryptWith(encoded, runCatching { fallbackKey() }.getOrNull())
    }

    private fun decryptWith(encoded: String, key: SecretKey?): String? {
        if (key == null) return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE)
            require(payload.size > IV_SIZE) { "Payload curto" }
            val iv = payload.copyOfRange(0, IV_SIZE)
            val encrypted = payload.copyOfRange(IV_SIZE, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.onFailure { Log.w(TAG, "Falha ao decifrar credencial", it) }.getOrNull()
    }

    private data class SelectedKey(val key: SecretKey, val hardware: Boolean)

    private fun getSecretKey(): SelectedKey {
        if (!hardwareUnavailable) {
            runCatching { return SelectedKey(keystoreKey(), hardware = true) }
                .onFailure {
                    Log.w(TAG, "Android Keystore indisponível; usando chave local protegida", it)
                    hardwareUnavailable = true
                    runCatching { keyStore.deleteEntry(KEY_ALIAS) }
                }
        }
        return SelectedKey(fallbackKey(), hardware = false)
    }

    private fun keystoreKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun fallbackKey(): SecretKey {
        val stored = fallbackPrefs.getString(FALLBACK_KEY, null)
        if (stored != null) {
            return runCatching {
                val bytes = Base64.decode(stored, Base64.NO_WRAP or Base64.URL_SAFE)
                javax.crypto.spec.SecretKeySpec(bytes, "AES")
            }.getOrElse {
                fallbackPrefs.edit().remove(FALLBACK_KEY).commit()
                createFallbackKey()
            }
        }
        return createFallbackKey()
    }

    private fun createFallbackKey(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val key = generator.generateKey()
        fallbackPrefs.edit()
            .putString(FALLBACK_KEY, Base64.encodeToString(key.encoded, Base64.NO_WRAP or Base64.URL_SAFE))
            .putString(FALLBACK_ID, UUID.randomUUID().toString())
            .commit()
        return key
    }

    private companion object {
        const val TAG = "SpaceTokenCipher"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "space_clipboard_device_tokens_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val FALLBACK_PREFS = "space_clipboard_secure_fallback"
        const val FALLBACK_KEY = "aes_key_v1"
        const val FALLBACK_ID = "install_id"
        const val HARDWARE_PREFIX = "H1:"
        const val SOFTWARE_PREFIX = "F1:"
    }
}
