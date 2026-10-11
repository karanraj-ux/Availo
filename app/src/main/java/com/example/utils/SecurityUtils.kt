package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.nio.ByteBuffer
import java.security.SecureRandom
import android.util.Base64

object SecurityUtils {
    @Volatile
    private var cachedPassphrase: ByteArray? = null

    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed")
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
    fun getEncryptedPrefs(context: Context, fileName: String): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                
            return EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("SecurityUtils", "EncryptedSharedPreferences creation failed for $fileName, attempting recovery", e)
            try {
                // Try to delete existing corrupted file
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(fileName)
                } else {
                    context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply()
                }
                
                // Clear the keystore entry for MasterKey if possible
                try {
                    val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)
                    keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                } catch (keystoreEx: Exception) {
                    android.util.Log.e("SecurityUtils", "Failed to clear keystore entry during recovery", keystoreEx)
                }

                // Try creating again
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                    
                return EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (recoveryEx: Exception) {
                android.util.Log.e("SecurityUtils", "Recovery failed for $fileName. Falling back to standard SharedPreferences", recoveryEx)
                // Fallback to standard SharedPreferences as safety net to prevent crashes
                return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            }
        }
    }

    fun getDatabasePassphrase(context: Context): ByteArray {
        cachedPassphrase?.let { return it }
        return synchronized(this) {
            cachedPassphrase?.let { return it }
            val prefs = getEncryptedPrefs(context, "db_security_prefs")
            val key = "db_passphrase"
            var passphraseBase64 = prefs.getString(key, null)
            if (passphraseBase64 == null) {
                val random = SecureRandom()
                val passHash = ByteArray(32)
                random.nextBytes(passHash)
                passphraseBase64 = Base64.encodeToString(passHash, Base64.NO_WRAP)
                prefs.edit().putString(key, passphraseBase64).apply()
            }
            val decoded = Base64.decode(passphraseBase64, Base64.NO_WRAP)
            cachedPassphrase = decoded
            decoded
        }
    }

    fun encryptPayload(context: Context, payload: String): String {
        try {
            val keyBytes = getDatabasePassphrase(context)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
            
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            val encryptedBytes = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            
            val combined = ByteBuffer.allocate(iv.size + encryptedBytes.size)
                .put(iv)
                .put(encryptedBytes)
                .array()
                
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return payload // Fallback to plain if error, or throw depending on strictness
        }
    }
}

