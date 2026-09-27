package me.him188.ani.app.tracking.anilist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AniListOAuthRedirectTest {
    @Test
    fun extractsTokenFromRegisteredRedirect() {
        assertEquals(
            "token+value",
            parseAniListOAuthRedirect("ani://anilist-auth#state=opaque&access_token=token%2Bvalue&token_type=Bearer"),
        )
    }

    @Test
    fun rejectsRedirectLookalikes() {
        val lookalikes = listOf(
            "ani://anilist-auth.evil#access_token=token",
            "ani://anilist-auth@evil#access_token=token",
            "ani://anilist-auth:443#access_token=token",
            "ani://anilist-auth/path#access_token=token",
            "ani://anilist-auth?next=evil#access_token=token",
            "ani://anilist-auth.evil/path#access_token=token",
        )
        lookalikes.forEach { assertNull(parseAniListOAuthRedirect(it), it) }
    }

    @Test
    fun rejectsMissingOrMalformedTokens() {
        assertNull(parseAniListOAuthRedirect("ani://anilist-auth"))
        assertNull(parseAniListOAuthRedirect("ani://anilist-auth#state=opaque"))
        assertNull(parseAniListOAuthRedirect("ani://anilist-auth#access_token="))
        assertNull(parseAniListOAuthRedirect("ani://anilist-auth#access_token=%ZZ"))
    }
}
