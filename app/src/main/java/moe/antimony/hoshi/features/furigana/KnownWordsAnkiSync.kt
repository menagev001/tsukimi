package moe.antimony.hoshi.features.furigana

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.features.anki.AnkiFetchException
import moe.antimony.hoshi.features.anki.AnkiFetchFailure
import moe.antimony.hoshi.features.anki.AnkiRepository

sealed interface KnownWordsAnkiSyncStatus {
    data object Idle : KnownWordsAnkiSyncStatus

    data object Syncing : KnownWordsAnkiSyncStatus

    data class Done(val ankiWordCount: Int) : KnownWordsAnkiSyncStatus

    data class Failed(val failure: AnkiFetchFailure) : KnownWordsAnkiSyncStatus
}

/**
 * Keeps the Anki part of the known words equal to the words of mature cards (an interval of
 * [MatureIntervalDays] days or more), like Migaku's known status. Runs when the app comes to the
 * foreground, at most every [ForegroundIntervalMillis], and on request.
 */
@Singleton
internal class KnownWordsAnkiSync @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val knownWordsRepository: KnownWordsRepository,
    private val settingsRepository: KnownWordsSettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<KnownWordsAnkiSyncStatus>(KnownWordsAnkiSyncStatus.Idle)
    val status: StateFlow<KnownWordsAnkiSyncStatus> = _status.asStateFlow()

    fun registerProcessForegroundChecks(
        lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    ) {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    appScope.launch { syncIfDue(System.currentTimeMillis()) }
                }
            },
        )
    }

    internal suspend fun syncIfDue(nowMillis: Long) {
        val settings = settingsRepository.settings.first()
        if (!settings.ankiSyncEnabled) return
        val last = settings.lastAnkiSyncMillis
        if (last != null && nowMillis - last in 0 until ForegroundIntervalMillis) return
        sync()
    }

    suspend fun sync(): KnownWordsAnkiSyncStatus = mutex.withLock {
        _status.value = KnownWordsAnkiSyncStatus.Syncing
        val result = try {
            val words = ankiRepository.wordsWithInterval(MatureIntervalDays)
                .map { KnownWordEntry(it.word, it.reading) }
            val count = knownWordsRepository.replaceAnkiWords(words)
            Log.i(Tag, "Known words from Anki: $count")
            settingsRepository.updateLastAnkiSync(System.currentTimeMillis())
            KnownWordsAnkiSyncStatus.Done(count)
        } catch (error: CancellationException) {
            throw error
        } catch (error: AnkiFetchException) {
            Log.w(Tag, "Known words sync failed: ${error.failure}")
            KnownWordsAnkiSyncStatus.Failed(error.failure)
        } catch (error: Exception) {
            Log.w(Tag, "Known words sync failed", error)
            KnownWordsAnkiSyncStatus.Failed(AnkiFetchFailure.ProviderFailure)
        }
        _status.value = result
        result
    }

    companion object {
        const val MatureIntervalDays = 21
        private const val ForegroundIntervalMillis = 30 * 60 * 1000L
        private const val Tag = "KnownWordsAnkiSync"
    }
}
