package moe.antimony.hoshi.features.watch

import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinRepositoryTest {
    @Test
    fun serverUrlAddsHttpsAndDropsTrailingSlash() {
        assertEquals("https://jellyfin.feathercomet.com", normalizeJellyfinServerUrl(" jellyfin.feathercomet.com/ "))
        assertEquals("https://jellyfin.feathercomet.com", normalizeJellyfinServerUrl("https://jellyfin.feathercomet.com//"))
    }

    @Test
    fun serverUrlKeepsExplicitSchemePortAndPath() {
        assertEquals("http://192.168.2.48:8097", normalizeJellyfinServerUrl("http://192.168.2.48:8097"))
        assertEquals("https://example.com/jellyfin", normalizeJellyfinServerUrl("https://example.com/jellyfin/"))
    }

    @Test
    fun serverUrlRejectsInvalidInput() {
        assertNull(normalizeJellyfinServerUrl(""))
        assertNull(normalizeJellyfinServerUrl("   "))
        assertNull(normalizeJellyfinServerUrl("ftp://example.com"))
        assertNull(normalizeJellyfinServerUrl("https://"))
        assertNull(normalizeJellyfinServerUrl("jelly fin.example.com"))
        assertNull(normalizeJellyfinServerUrl("https://example.com/?q=1"))
    }

    @Test
    fun networkErrorsMapToConnectionFailures() {
        assertEquals(
            JellyfinLoginFailure.ServerUnreachable,
            jellyfinLoginFailure(TimeoutException("down"), JellyfinLoginStage.ServerCheck),
        )
        assertEquals(
            JellyfinLoginFailure.SecureConnectionFailed,
            jellyfinLoginFailure(SecureConnectionException("tls"), JellyfinLoginStage.Authentication),
        )
    }

    @Test
    fun badServerResponsesMeanNotJellyfin() {
        assertEquals(
            JellyfinLoginFailure.NotJellyfinServer,
            jellyfinLoginFailure(InvalidStatusException(404), JellyfinLoginStage.ServerCheck),
        )
        assertEquals(
            JellyfinLoginFailure.NotJellyfinServer,
            jellyfinLoginFailure(InvalidContentException("html"), JellyfinLoginStage.ServerCheck),
        )
    }

    @Test
    fun rejectedAuthenticationMeansWrongCredentials() {
        assertEquals(
            JellyfinLoginFailure.InvalidCredentials,
            jellyfinLoginFailure(InvalidStatusException(401), JellyfinLoginStage.Authentication),
        )
        assertEquals(
            JellyfinLoginFailure.Unknown,
            jellyfinLoginFailure(InvalidStatusException(500), JellyfinLoginStage.Authentication),
        )
        assertEquals(
            JellyfinLoginFailure.Unknown,
            jellyfinLoginFailure(ApiClientException("io"), JellyfinLoginStage.Authentication),
        )
    }

    @Test
    fun loggedInRequiresUserAndToken() {
        assertFalse(JellyfinSettings().isLoggedIn)
        assertFalse(JellyfinSettings(userId = "u").isLoggedIn)
        assertTrue(JellyfinSettings(userId = "u", accessToken = "t").isLoggedIn)
        assertEquals("https://jellyfin.feathercomet.com", JellyfinSettings().serverUrl)
    }
}
