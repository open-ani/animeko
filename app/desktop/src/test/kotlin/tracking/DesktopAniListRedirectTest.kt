package me.him188.ani.app.desktop.tracking

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopAniListRedirectTest {
    @Test
    fun acceptsOnlyAniListCallbackToken() {
        assertEquals("synthetic-token", aniListTokenFromRedirect(URI("ani://anilist-auth#access_token=synthetic-token&token_type=Bearer")))
        assertNull(aniListTokenFromRedirect(URI("ani://other-host#access_token=synthetic-token")))
        assertNull(aniListTokenFromRedirect(URI("ani://anilist-auth#token_type=Bearer")))
    }
}
