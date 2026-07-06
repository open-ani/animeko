package me.him188.ani.app.domain.media.fetch

import me.him188.ani.app.data.models.preference.ProxyAuthorization
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyAuthorizationTest {
    @Test
    fun `toHeader encodes basic credentials`() {
        assertEquals(
            "Basic dXNlcjpwYXNz",
            ProxyAuthorization(username = "user", password = "pass").toHeader(),
        )
    }
}
