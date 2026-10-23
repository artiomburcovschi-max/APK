package com.agenda.client

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Хранит логин и пароль на устройстве.
 * Ключ AES-256 живёт в Android Keystore (из него нельзя вытащить сам ключ),
 * в SharedPreferences лежит только зашифрованный текст (AES-GCM).
 */
class CredentialStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // 12 байт IV + шифртекст, одной строкой
        return Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String? = try {
        val raw = Base64.decode(data, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    } catch (e: Exception) {
        null // ключ потерян или данные повреждены -> считаем, что логина нет
    }

    fun save(user: String, pass: String, type: AccountType = AccountType.PARENT) {
        prefs.edit()
            .putString("u", encrypt(user))
            .putString("p", encrypt(pass))
            .putString("t", type.key) // тип не секрет, шифровать незачем
            .apply()
    }

    /** Тип аккаунта. Если не записан (вход сделан в старой версии), то это родитель. */
    fun loadType(): AccountType = AccountType.fromKey(prefs.getString("t", null))

    fun load(): Pair<String, String>? {
        val u = prefs.getString("u", null)?.let { decrypt(it) } ?: return null
        val p = prefs.getString("p", null)?.let { decrypt(it) } ?: return null
        return u to p
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "creds"   // имя файла creds.xml (его же исключаем из бэкапа)
        private const val KEY_ALIAS = "agenda_creds_key"
    }
}