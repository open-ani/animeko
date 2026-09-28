package me.him188.ani.android.activity

import android.content.Intent
import kotlinx.coroutines.CoroutineScope

internal interface TrackingAuthRedirectHandler {
    val host: String
    val providerName: String

    fun handle(intent: Intent, scope: CoroutineScope, onResult: (Boolean) -> Unit): Boolean
}

internal class TrackingAuthRedirectRouter(handlers: List<TrackingAuthRedirectHandler>) {
    private val byHost = handlers.associateBy { it.host }.also {
        require(it.size == handlers.size) { "Duplicate tracking auth redirect host" }
    }

    fun handlerFor(host: String?): TrackingAuthRedirectHandler? = byHost[host]
}
