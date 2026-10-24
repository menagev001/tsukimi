package moe.antimony.hoshi.features.watch

import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPlaybackTest {
    private val capabilities = WatchDecoderCapabilities(
        videoDecoders = listOf(
            WatchVideoDecoder("h264", 4096, 2176),
            WatchVideoDecoder("hevc", 3840, 2160, supports10Bit = true),
        ),
        audioCodecs = setOf("aac", "opus", "ac3"),
    )

    @Test
    fun directPlayListsDetectedCodecs() {
        val profile = jellyfinDeviceProfile(capabilities, maxBitrate = 8_000_000)
        val direct = profile.directPlayProfiles.single()

        assertEquals("h264,hevc", direct.videoCodec)
        assertEquals("aac,ac3,opus", direct.audioCodec)
        assertTrue("mkv" in direct.container.split(","))
        assertEquals(8_000_000, profile.maxStreamingBitrate)
    }

    @Test
    fun h264ExcludesTenBitAndHevcAllowsMain10() {
        val profile = jellyfinDeviceProfile(capabilities, maxBitrate = 8_000_000)
        fun conditions(codec: String) =
            profile.codecProfiles.single { it.codec == codec }.conditions.associate { it.property to it.value }

        assertEquals("8", conditions("h264")[ProfileConditionValue.VIDEO_BIT_DEPTH])
        assertEquals("high|main|baseline|constrained baseline", conditions("h264")[ProfileConditionValue.VIDEO_PROFILE])
        assertEquals("main|main 10", conditions("hevc")[ProfileConditionValue.VIDEO_PROFILE])
        assertEquals("2160", conditions("hevc")[ProfileConditionValue.HEIGHT])
    }

    @Test
    fun transcodesToHlsAndNeverBurnsInSubtitles() {
        val profile = jellyfinDeviceProfile(capabilities, maxBitrate = 8_000_000)

        assertEquals(MediaStreamProtocol.HLS, profile.transcodingProfiles.single().protocol)
        assertEquals("aac", profile.transcodingProfiles.single().audioCodec)
        assertTrue(profile.subtitleProfiles.all { it.method == SubtitleDeliveryMethod.EXTERNAL })
    }

    @Test
    fun resumeSkipsFinishedAndBarelyStartedItems() {
        val item = WatchItem(id = "1", name = "Film", kind = WatchItemKind.Movie, runTimeMs = 100 * 60_000L)

        assertEquals(30 * 60_000L, resumePositionMs(item.copy(positionMs = 30 * 60_000L)))
        assertEquals(0L, resumePositionMs(item.copy(positionMs = 5_000L)))
        assertEquals(0L, resumePositionMs(item.copy(positionMs = 98 * 60_000L)))
        assertEquals(0L, resumePositionMs(item.copy(positionMs = 30 * 60_000L, played = true)))
    }

    @Test
    fun preferredAudioPicksFirstStreamInLanguage() {
        val streams = listOf(
            WatchAudioStream(index = 1, label = "English - AC3 - 5.1", language = "eng"),
            WatchAudioStream(index = 2, label = "Japanese - AAC - Stereo", language = "jpn"),
            WatchAudioStream(index = 3, label = "Japanese - Commentary", language = "jpn"),
        )

        assertEquals(2, preferredAudioStreamIndex(streams, WatchAudioLanguage.Japanese))
        assertEquals(1, preferredAudioStreamIndex(streams, WatchAudioLanguage.English))
        assertEquals(null, preferredAudioStreamIndex(streams, WatchAudioLanguage.ServerDefault))
        assertEquals(null, preferredAudioStreamIndex(streams.take(1), WatchAudioLanguage.Japanese))
        assertEquals(5, preferredAudioStreamIndex(listOf(WatchAudioStream(5, "ja", "ja")), WatchAudioLanguage.Japanese))
    }

    @Test
    fun playbackTimeAndSpeedFormatting() {
        assertEquals("0:05", formatPlaybackTime(5_400L))
        assertEquals("23:40", formatPlaybackTime(23 * 60_000L + 40_000L))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000L))
        assertEquals("1", formatPlaybackSpeed(1f))
        assertEquals("0.9", formatPlaybackSpeed(0.9f))
        assertEquals("1.25", formatPlaybackSpeed(1.25f))
    }

    @Test
    fun subtitlesSitAbovePictureNotScreenEdge() {
        // 16:9 video on a 16:10 tablet (1280x800 dp): 40 dp letterbox bars, 720 dp picture.
        assertEquals(40f + 720f * 0.06f, subtitleBottomInsetDp(1280f, 800f, 16f / 9f, controlsVisible = false), 0.01f)
        // Same aspect as the screen: no bars.
        assertEquals(800f * 0.06f, subtitleBottomInsetDp(1280f, 800f, 1.6f, controlsVisible = false), 0.01f)
        // Unknown video size falls back to the full screen.
        assertEquals(800f * 0.06f, subtitleBottomInsetDp(1280f, 800f, null, controlsVisible = false), 0.01f)
        // Controls push the lines above the timeline.
        assertEquals(120f, subtitleBottomInsetDp(1280f, 800f, 16f / 9f, controlsVisible = true), 0.01f)
    }

    @Test
    fun subtitleOffsetFormatting() {
        assertEquals("0.0", formatSubtitleOffset(0))
        assertEquals("+0.3", formatSubtitleOffset(300))
        assertEquals("-1.2", formatSubtitleOffset(-1_200))
        assertEquals("+12.0", formatSubtitleOffset(12_000))
    }
}
