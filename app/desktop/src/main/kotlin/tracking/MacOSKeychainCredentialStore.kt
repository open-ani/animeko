package me.him188.ani.app.desktop.tracking

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.tracking.api.TrackingCredentialStore
import me.him188.ani.tracking.api.TrackingLoginCredentials

/** Stores the AniList token as a generic password in the user's macOS Keychain. */
class MacOSKeychainCredentialStore(
    serviceName: String = "me.him188.ani.tracking",
    accountName: String = "anilist",
) : TrackingCredentialStore {
    private val service = serviceName.toByteArray(Charsets.UTF_8)
    private val account = accountName.toByteArray(Charsets.UTF_8)

    override suspend fun load(): TrackingLoginCredentials? = withContext(Dispatchers.IO) {
        val length = IntByReference()
        val data = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(
            null, service.size, service, account.size, account, length, data, null,
        )
        if (status == ITEM_NOT_FOUND) return@withContext null
        checkStatus(status)
        try {
            val bytes = data.value.getByteArray(0, length.value)
            try {
                TrackingLoginCredentials(secret = bytes.toString(Charsets.UTF_8))
            } finally {
                bytes.fill(0)
            }
        } finally {
            checkStatus(security.SecKeychainItemFreeContent(null, data.value))
        }
    }

    override suspend fun save(credentials: TrackingLoginCredentials) = withContext(Dispatchers.IO) {
        val bytes = credentials.secret.toByteArray(Charsets.UTF_8)
        try {
            val item = PointerByReference()
            val lookup = security.SecKeychainFindGenericPassword(
                null, service.size, service, account.size, account, null, null, item,
            )
            when (lookup) {
                ITEM_NOT_FOUND -> checkStatus(security.SecKeychainAddGenericPassword(
                    null, service.size, service, account.size, account, bytes.size, bytes, null,
                ))
                0 -> checkStatus(security.SecKeychainItemModifyAttributesAndData(item.value, null, bytes.size, bytes))
                else -> checkStatus(lookup)
            }
        } finally {
            bytes.fill(0)
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        val item = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(
            null, service.size, service, account.size, account, null, null, item,
        )
        if (status != ITEM_NOT_FOUND) {
            checkStatus(status)
            checkStatus(security.SecKeychainItemDelete(item.value))
        }
    }

    private fun checkStatus(status: Int) {
        check(status == 0) { "macOS Keychain operation failed with status $status" }
    }

    private interface Security : Library {
        fun SecKeychainFindGenericPassword(
            keychain: Pointer?, serviceLength: Int, serviceName: ByteArray,
            accountLength: Int, accountName: ByteArray, passwordLength: IntByReference?,
            passwordData: PointerByReference?, itemRef: PointerByReference?,
        ): Int

        fun SecKeychainAddGenericPassword(
            keychain: Pointer?, serviceLength: Int, serviceName: ByteArray,
            accountLength: Int, accountName: ByteArray, passwordLength: Int,
            passwordData: ByteArray, itemRef: PointerByReference?,
        ): Int

        fun SecKeychainItemModifyAttributesAndData(itemRef: Pointer, attributes: Pointer?, length: Int, data: ByteArray): Int
        fun SecKeychainItemDelete(itemRef: Pointer): Int
        fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer?): Int
    }

    private companion object {
        const val ITEM_NOT_FOUND = -25300
        val security: Security = Native.load("Security", Security::class.java)
    }
}
