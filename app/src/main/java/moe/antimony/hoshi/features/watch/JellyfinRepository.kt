package moe.antimony.hoshi.features.watch

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.BuildConfig
import moe.antimony.hoshi.R
import moe.antimony.hoshi.di.IoDispatcher
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.ServerVersion

enum class JellyfinLoginFailure(@param:StringRes val messageRes: Int) {
    InvalidServerUrl(R.string.watch_login_error_invalid_url),
    ServerUnreachable(R.string.watch_login_error_unreachable),
    SecureConnectionFailed(R.string.watch_login_error_secure_connection),
    NotJellyfinServer(R.string.watch_login_error_not_jellyfin),
    UnsupportedServerVersion(R.string.watch_login_error_unsupported_version),
    InvalidCredentials(R.string.watch_login_error_invalid_credentials),
    Unknown(R.string.watch_login_error_unknown),
}

/** An API client for the signed-in session, plus the user every user-scoped request needs. */
class JellyfinSession internal constructor(
    val api: ApiClient,
    val userId: UUID,
)

class JellyfinNotSignedInException : IllegalStateException("Not signed in to Jellyfin")

sealed interface JellyfinLoginResult {
    data class Success(val userName: String) : JellyfinLoginResult
    data class Failure(val failure: JellyfinLoginFailure) : JellyfinLoginResult
}

@Singleton
class JellyfinRepository @Inject constructor(
    private val settingsRepository: JellyfinSettingsRepository,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val jellyfin: Jellyfin by lazy {
        createJellyfin {
            context = this@JellyfinRepository.context
            clientInfo = ClientInfo(name = ClientName, version = BuildConfig.VERSION_NAME)
        }
    }

    @Volatile
    private var cachedSession: Pair<JellyfinSettings, JellyfinSession>? = null

    val settings: Flow<JellyfinSettings> = settingsRepository.settings

    /** Throws [JellyfinNotSignedInException] when there is no stored session. */
    suspend fun session(): JellyfinSession {
        val current = settingsRepository.settings.first()
        if (!current.isLoggedIn) throw JellyfinNotSignedInException()
        cachedSession?.let { (settings, session) -> if (settings == current) return session }
        val userId = runCatching { UUID.fromString(current.userId) }.getOrNull()
            ?: throw JellyfinNotSignedInException()
        return JellyfinSession(apiClient(current), userId).also { cachedSession = current to it }
    }

    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ): JellyfinLoginResult = withContext(ioDispatcher) {
        val baseUrl = normalizeJellyfinServerUrl(serverUrl)
            ?: return@withContext JellyfinLoginResult.Failure(JellyfinLoginFailure.InvalidServerUrl)
        val api = jellyfin.createApi(baseUrl = baseUrl)

        val serverVersion = try {
            api.systemApi.getPublicSystemInfo().content.version?.let(ServerVersion::fromString)
        } catch (error: ApiClientException) {
            Log.w(Tag, "Jellyfin server check failed: ${error.message}")
            return@withContext JellyfinLoginResult.Failure(
                jellyfinLoginFailure(error, JellyfinLoginStage.ServerCheck),
            )
        } ?: return@withContext JellyfinLoginResult.Failure(JellyfinLoginFailure.NotJellyfinServer)
        if (serverVersion < Jellyfin.minimumVersion) {
            return@withContext JellyfinLoginResult.Failure(JellyfinLoginFailure.UnsupportedServerVersion)
        }

        val authentication = try {
            api.authenticationApi.authenticateUserByName(username = username.trim(), password = password).content
        } catch (error: ApiClientException) {
            Log.w(Tag, "Jellyfin login failed: ${error.message}")
            return@withContext JellyfinLoginResult.Failure(
                jellyfinLoginFailure(error, JellyfinLoginStage.Authentication),
            )
        }
        val user = authentication.user
        val accessToken = authentication.accessToken
        if (user == null || accessToken.isNullOrBlank()) {
            return@withContext JellyfinLoginResult.Failure(JellyfinLoginFailure.Unknown)
        }
        val userName = user.name?.takeIf(String::isNotBlank) ?: username.trim()
        settingsRepository.saveSession(
            serverUrl = baseUrl,
            userId = user.id.toString(),
            userName = userName,
            accessToken = accessToken,
        )
        JellyfinLoginResult.Success(userName)
    }

    suspend fun logout() = withContext(ioDispatcher) {
        val current = settingsRepository.settings.first()
        if (current.isLoggedIn) {
            try {
                apiClient(current).sessionApi.reportSessionEnded()
            } catch (error: ApiClientException) {
                Log.w(Tag, "Jellyfin session end report failed: ${error.message}")
            }
        }
        settingsRepository.clearSession()
        cachedSession = null
    }

    private fun apiClient(settings: JellyfinSettings): ApiClient =
        jellyfin.createApi(baseUrl = settings.serverUrl, accessToken = settings.accessToken)

    private companion object {
        const val Tag = "JellyfinRepository"
        const val ClientName = "Tsukimi"
    }
}

internal enum class JellyfinLoginStage { ServerCheck, Authentication }

internal fun jellyfinLoginFailure(
    error: ApiClientException,
    stage: JellyfinLoginStage,
): JellyfinLoginFailure = when (error) {
    is TimeoutException -> JellyfinLoginFailure.ServerUnreachable
    is SecureConnectionException -> JellyfinLoginFailure.SecureConnectionFailed
    is InvalidStatusException -> when {
        stage == JellyfinLoginStage.ServerCheck -> JellyfinLoginFailure.NotJellyfinServer
        error.status == 401 || error.status == 403 -> JellyfinLoginFailure.InvalidCredentials
        else -> JellyfinLoginFailure.Unknown
    }
    is InvalidContentException -> when (stage) {
        JellyfinLoginStage.ServerCheck -> JellyfinLoginFailure.NotJellyfinServer
        JellyfinLoginStage.Authentication -> JellyfinLoginFailure.Unknown
    }
    else -> JellyfinLoginFailure.Unknown
}

/**
 * Returns the server base URL without a trailing slash, adding `https://` when the user typed only a host.
 */
internal fun normalizeJellyfinServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    if (uri.query != null || uri.fragment != null) return null
    return withScheme.trimEnd('/')
}
