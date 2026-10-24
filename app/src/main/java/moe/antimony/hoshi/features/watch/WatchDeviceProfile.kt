package moe.antimony.hoshi.features.watch

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

/** A hardware video decoder, by Jellyfin codec name, with the largest frame it accepts. */
data class WatchVideoDecoder(
    val codec: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val supports10Bit: Boolean = false,
)

data class WatchDecoderCapabilities(
    val videoDecoders: List<WatchVideoDecoder>,
    /** Jellyfin audio codec names the device can decode. */
    val audioCodecs: Set<String>,
)

private val VideoMimeToCodec = mapOf(
    "video/avc" to "h264",
    "video/hevc" to "hevc",
    "video/x-vnd.on2.vp9" to "vp9",
    "video/av01" to "av1",
)

private val AudioMimeToCodec = mapOf(
    "audio/mp4a-latm" to "aac",
    "audio/mpeg" to "mp3",
    "audio/flac" to "flac",
    "audio/opus" to "opus",
    "audio/vorbis" to "vorbis",
    "audio/ac3" to "ac3",
    "audio/eac3" to "eac3",
    "audio/vnd.dts" to "dts",
    "audio/true-hd" to "truehd",
)

/**
 * Reads the decoders from [MediaCodecList]. Video only counts hardware decoders, so Jellyfin
 * transcodes instead of sending a file that a software decoder would play with dropped frames.
 */
fun detectWatchDecoderCapabilities(): WatchDecoderCapabilities {
    val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
    val video = VideoMimeToCodec.mapNotNull { (mime, codec) ->
        decoders
            .filter { info -> info.isHardwareDecoder() && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            .mapNotNull { info -> runCatching { info.getCapabilitiesForType(mime) }.getOrNull() }
            .maxByOrNull { caps -> caps.videoCapabilities?.supportedWidths?.upper ?: 0 }
            ?.let { caps ->
                WatchVideoDecoder(
                    codec = codec,
                    maxWidth = caps.videoCapabilities?.supportedWidths?.upper ?: 1920,
                    maxHeight = caps.videoCapabilities?.supportedHeights?.upper ?: 1080,
                    supports10Bit = codec == "hevc" && caps.profileLevels.any {
                        it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                    },
                )
            }
    }
    val audio = decoders
        .flatMap { it.supportedTypes.toList() }
        .mapNotNull { AudioMimeToCodec[it.lowercase()] }
        .toSet()
    return WatchDecoderCapabilities(
        videoDecoders = video.ifEmpty { listOf(WatchVideoDecoder("h264", 1920, 1080)) },
        // These are part of every Android version Tsukimi supports.
        audioCodecs = audio + setOf("aac", "mp3", "flac", "opus", "vorbis"),
    )
}

private fun MediaCodecInfo.isHardwareDecoder(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isHardwareAccelerated
    } else {
        val lower = name.lowercase()
        !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.")
    }

internal fun jellyfinDeviceProfile(
    capabilities: WatchDecoderCapabilities,
    maxBitrate: Int,
): DeviceProfile {
    val videoCodecs = capabilities.videoDecoders.map { it.codec }
    val audioCodecs = capabilities.audioCodecs.sorted()
    return DeviceProfile(
        name = "Tsukimi",
        maxStreamingBitrate = maxBitrate,
        maxStaticBitrate = maxBitrate,
        directPlayProfiles = listOf(
            DirectPlayProfile(
                container = "mp4,m4v,mov,mkv,webm",
                videoCodec = videoCodecs.joinToString(","),
                audioCodec = audioCodecs.joinToString(","),
                type = DlnaProfileType.VIDEO,
            ),
        ),
        transcodingProfiles = listOf(
            TranscodingProfile(
                container = "ts",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264",
                audioCodec = "aac",
                protocol = MediaStreamProtocol.HLS,
                context = EncodingContext.STREAMING,
                maxAudioChannels = "2",
                breakOnNonKeyFrames = true,
                conditions = emptyList(),
            ),
        ),
        containerProfiles = emptyList(),
        codecProfiles = capabilities.videoDecoders.map { decoder ->
            CodecProfile(
                type = CodecType.VIDEO,
                codec = decoder.codec,
                conditions = videoCodecConditions(decoder),
                applyConditions = emptyList(),
            )
        },
        // Subtitles are fetched and drawn by Tsukimi, so the server must never burn them in.
        subtitleProfiles = listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt").map {
            SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL)
        },
    )
}

private fun videoCodecConditions(decoder: WatchVideoDecoder): List<ProfileCondition> = buildList {
    when (decoder.codec) {
        // Android hardware decoders do not support 10-bit H.264 (Hi10P), common in fansubbed anime.
        "h264" -> {
            add(condition(ProfileConditionType.EQUALS_ANY, ProfileConditionValue.VIDEO_PROFILE, "high|main|baseline|constrained baseline"))
            add(condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.VIDEO_BIT_DEPTH, "8"))
        }
        "hevc" -> {
            add(condition(ProfileConditionType.EQUALS_ANY, ProfileConditionValue.VIDEO_PROFILE, if (decoder.supports10Bit) "main|main 10" else "main"))
            add(condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.VIDEO_BIT_DEPTH, if (decoder.supports10Bit) "10" else "8"))
        }
    }
    add(condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.WIDTH, decoder.maxWidth.toString()))
    add(condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.HEIGHT, decoder.maxHeight.toString()))
}

private fun condition(type: ProfileConditionType, property: ProfileConditionValue, value: String) =
    ProfileCondition(condition = type, property = property, value = value, isRequired = false)
