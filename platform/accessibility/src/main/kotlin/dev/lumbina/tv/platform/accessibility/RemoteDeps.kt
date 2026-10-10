package dev.lumbina.tv.platform.accessibility

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.RecentAppsRepository
import dev.lumbina.tv.core.domain.backdrop.PhotoFolder
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.device.FirmwareParts
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettingsRepository
import dev.lumbina.tv.core.domain.remote.LumbinaHome
import dev.lumbina.tv.core.domain.remote.OtherHome
import dev.lumbina.tv.core.domain.remote.RemoteSettingsRepository
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.speech.SpeechEngineChoice
import dev.lumbina.tv.core.domain.speech.SpeechKeys
import dev.lumbina.tv.core.domain.speech.SpeechSetups
import dev.lumbina.tv.core.domain.speech.VoiceSettingsRepository
import dev.lumbina.tv.core.domain.text.Search
import dev.lumbina.tv.core.domain.text.YouTube
import dev.lumbina.tv.core.domain.text.YouTubeSearch
import javax.inject.Inject

/** What [RemoteFeatures.Factory] builds the remote's features from, grouped by concern. */
internal object RemoteDeps {

    class Device @Inject constructor(
        val profile: DeviceProfile,
        val firmware: FirmwareParts,
        val lumbinaHome: LumbinaHome,
        val log: LumbinaLog,
    )

    class Apps @Inject constructor(
        val installed: InstalledApps,
        val launcher: AppLauncher,
        val recent: RecentAppsRepository,
        val accessSettings: AccessSettingsRepository,
        val localAccess: LocalAccess,
    )

    class Voice @Inject constructor(
        val engine: SpeechEngineChoice,
        val setups: SpeechSetups,
        val keys: SpeechKeys,
        val layout: HomeLayoutRepository,
        val settings: VoiceSettingsRepository,
        val youTubeSearch: YouTubeSearch,
        val appSpeech: SpeechAppEngine,
        val search: Search,
        val youTube: YouTube,
    )

    class Stores @Inject constructor(
        val sleep: SleepSettingsRepository,
        val remote: RemoteSettingsRepository,
        val otherHome: OtherHome,
        val phoneRemote: PhoneRemoteSettingsRepository,
        val photoFolder: PhotoFolder,
        val wallpaper: WallpaperSettingsRepository,
    )
}
