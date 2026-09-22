/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.tracking.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android credential storage backed by a non-exportable Android Keystore AES key.
 *
 * Ciphertext is kept under [Context.getNoBackupFilesDir], so normal settings export and Android
 * Auto Backup cannot copy it to another device. No credential value is exposed through Room or
 * shared preferences.
 */
class AndroidTrackingCredentialStore(
    context: Context,
    providerId: TrackingProviderId,
) : TrackingCredentialStore {
    private val mutex = Mutex()
    private val keyAlias = "animeko.tracking.${providerId.value}"
    private val file = AtomicFile(context.noBackupFilesDir.resolve("tracking-${providerId.value}.credentials"))

    override suspend fun load(): TrackingLoginCredentials? = mutex.withLock {
        if (!file.baseFile.exists()) return null
        val input = DataInputStream(ByteArrayInputStream(file.readFully()))
        require(input.readUnsignedByte() == FORMAT_VERSION) { "Unsupported tracking credential format" }
        val iv = ByteArray(input.readUnsignedByte()).also(input::readFully)
        val ciphertext = input.readBytes()
        val plaintext = cipher(Cipher.DECRYPT_MODE, iv).doFinal(ciphertext)
        DataInputStream(ByteArrayInputStream(plaintext)).use {
            TrackingLoginCredentials(username = it.readUTF(), secret = it.readUTF())
        }
    }

    override suspend fun save(credentials: TrackingLoginCredentials) = mutex.withLock {
        val plaintext = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.writeUTF(credentials.username)
                it.writeUTF(credentials.secret)
            }
        }.toByteArray()
        val cipher = cipher(Cipher.ENCRYPT_MODE)
        val ciphertext = cipher.doFinal(plaintext)
        val output = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.writeByte(FORMAT_VERSION)
                it.writeByte(cipher.iv.size)
                it.write(cipher.iv)
                it.write(ciphertext)
            }
        }.toByteArray()

        val stream = file.startWrite()
        try {
            stream.write(output)
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    override suspend fun clear() = mutex.withLock {
        file.delete()
    }

    private fun cipher(mode: Int, iv: ByteArray? = null): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        if (mode == Cipher.ENCRYPT_MODE) {
            init(mode, getOrCreateKey())
        } else {
            requireNotNull(iv) { "An IV is required to decrypt tracking credentials" }
            init(mode, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
