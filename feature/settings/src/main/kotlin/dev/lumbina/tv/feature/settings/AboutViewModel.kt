package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import dev.lumbina.tv.core.domain.diagnostics.snapshot
import dev.lumbina.tv.core.domain.update.AppUpdates
import dev.lumbina.tv.core.domain.update.Release
import dev.lumbina.tv.core.domain.update.UpdateCheck
import dev.lumbina.tv.core.domain.update.UpdateStep
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AboutViewModel @Inject constructor(
    profile: DeviceProfile,
    system: SystemInfo,
    private val access: AccessSettingsRepository,
    private val updates: AppUpdates,
) : ViewModel() {
    val system = system.snapshot()

    /** Null where this edition does not update itself. */
    val update: StateFlow<UpdateUi?> get() = updateState
    private val updateState = MutableStateFlow<UpdateUi?>(UpdateUi.Idle.takeIf { updates.supported })
    private var updateJob: Job? = null

    /** OK on the update row: does what the row offers ([action]). */
    fun onUpdate() {
        when (val action = updateState.value?.action()) {
            null, UpdateAction.None -> Unit

            UpdateAction.Check -> check()

            is UpdateAction.Install -> install(action.release)

            is UpdateAction.StopWaiting -> {
                updateJob?.cancel()
                updateState.value = UpdateUi.Available(action.release)
            }
        }
    }

    /**
     * The page is left: an answer is forgotten, a version found stays offered. What is under way
     * goes on.
     */
    fun forgetDone() {
        updateState.value = when (val now = updateState.value) {
            UpdateUi.UpToDate, UpdateUi.CheckFailed, UpdateUi.Refused, is UpdateUi.Older -> UpdateUi.Idle
            is UpdateUi.Broken -> UpdateUi.Available(now.release)
            is UpdateUi.NotInstalled -> UpdateUi.Available(now.release)
            else -> now
        }
    }

    private fun check() {
        updateState.value = UpdateUi.Checking
        updateJob = viewModelScope.launch {
            updateState.value = when (val found = updates.check()) {
                UpdateCheck.UpToDate -> UpdateUi.UpToDate
                is UpdateCheck.Available -> UpdateUi.Available(found.release)
                UpdateCheck.Failed -> UpdateUi.CheckFailed
            }
        }
    }

    private fun install(release: Release) {
        updateJob?.cancel()
        // At once, before the download says anything: a second OK must find it under way.
        updateState.value = UpdateUi.Downloading(0)
        updateJob = viewModelScope.launch {
            // An installer's or a server's surprise is a failed try, not a crash of the settings.
            updates.install(release).catch { emit(UpdateStep.Failed) }.collect { step ->
                updateState.value = when (step) {
                    is UpdateStep.Downloading -> UpdateUi.Downloading((step.fraction * PERCENT).toInt())
                    UpdateStep.Installing -> UpdateUi.Installing(release)
                    UpdateStep.InstallingItself -> UpdateUi.InstallingItself
                    UpdateStep.Cancelled -> UpdateUi.Available(release)
                    UpdateStep.Failed -> UpdateUi.Broken(release, corrupt = false)
                    UpdateStep.Corrupt -> UpdateUi.Broken(release, corrupt = true)
                    UpdateStep.Refused -> UpdateUi.Refused
                    UpdateStep.Older -> UpdateUi.Older(release)
                    UpdateStep.NotInstalled -> UpdateUi.NotInstalled(release)
                }
            }
        }
    }

    val deviceProfile: String = profile.id

    /** Whether the first-run setup is over (finished or skipped): it can be asked back here. */
    val setupHidden: StateFlow<Boolean> =
        access.settings.map { it.setupCardHidden }.stateIn(viewModelScope, WhileUiSubscribed, false)

    fun showSetup() {
        // Asked back: it opens at its steps, with nobody to welcome.
        viewModelScope.launch { access.update { it.copy(setupCardHidden = false, setupReopened = true) } }
    }

    private companion object {
        const val PERCENT = 100
    }
}

/** Where checking for an update and installing it is, as the row on "About Lumbina" says it. */
sealed interface UpdateUi {
    data object Idle : UpdateUi

    data object Checking : UpdateUi

    data object UpToDate : UpdateUi

    data object CheckFailed : UpdateUi

    data class Available(val release: Release) : UpdateUi

    data class Downloading(val percent: Int) : UpdateUi

    /** The download failed, or what came was [corrupt]; OK tries again. */
    data class Broken(val release: Release, val corrupt: Boolean) : UpdateUi

    /** Handed to the system, which asks the user now; OK stops waiting. */
    data class Installing(val release: Release) : UpdateUi

    /** Lumbina installs it itself and will restart. */
    data object InstallingItself : UpdateUi

    /** The system could not install it; OK tries again. */
    data class NotInstalled(val release: Release) : UpdateUi

    /** The system would not install it over this build, signed with another key. */
    data object Refused : UpdateUi

    /** The published [release] is older than this build: there is nothing to update to. */
    data class Older(val release: Release) : UpdateUi
}

/** What OK on the update row does. */
sealed interface UpdateAction {
    data object Check : UpdateAction

    data class Install(val release: Release) : UpdateAction

    data class StopWaiting(val release: Release) : UpdateAction

    /** Something is under way, or an answer that stays until the page is left. */
    data object None : UpdateAction
}

/** What OK does where things stand now. */
fun UpdateUi.action(): UpdateAction = when (this) {
    UpdateUi.Idle, UpdateUi.UpToDate, UpdateUi.CheckFailed -> UpdateAction.Check

    is UpdateUi.Available -> UpdateAction.Install(release)

    is UpdateUi.Broken -> UpdateAction.Install(release)

    is UpdateUi.NotInstalled -> UpdateAction.Install(release)

    is UpdateUi.Installing -> UpdateAction.StopWaiting(release)

    UpdateUi.Checking, is UpdateUi.Downloading, UpdateUi.InstallingItself, UpdateUi.Refused, is UpdateUi.Older ->
        UpdateAction.None
}
