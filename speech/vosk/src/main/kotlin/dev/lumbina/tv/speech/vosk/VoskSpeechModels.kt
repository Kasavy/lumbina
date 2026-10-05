package dev.lumbina.tv.speech.vosk

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.ModelState
import dev.lumbina.tv.core.domain.speech.SpeechModelInfo
import dev.lumbina.tv.core.domain.speech.SpeechModels
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Installs [VoskModelCatalog] models in an app-wide scope, so a download keeps going when the
 * screen that started it goes away. When a model lands or goes, tells voice input, which then
 * knows which languages it has.
 */
@Singleton
internal class VoskSpeechModels @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: VoskModelStore,
    private val installer: ModelInstaller,
    private val log: LumbinaLog,
) : SpeechModels {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val deletions = mutableMapOf<String, Job>()
    private val mutableStates = MutableStateFlow(
        VoskModelCatalog.models.associate { model ->
            model.language to if (store.isInstalled(model.language)) ModelState.Installed else ModelState.Missing
        },
    )

    override val catalog: List<SpeechModelInfo> = VoskModelCatalog.models.map {
        SpeechModelInfo(it.language, it.sizeBytes)
    }

    override val states: StateFlow<Map<String, ModelState>> = mutableStates.asStateFlow()

    @Synchronized
    override fun install(language: String) {
        val model = VoskModelCatalog.model(language) ?: return
        val deleting = deletions[language]?.takeIf { it.isActive }
        if (jobs[language]?.isActive == true) return
        if (deleting == null && states.value[language] == ModelState.Installed) return
        jobs[language] = scope.launch {
            deleting?.join() // "delete" then "download": the old files go first, or they'd take the new ones along
            install(model)
        }
    }

    @Synchronized
    override fun delete(language: String) {
        val model = VoskModelCatalog.model(language) ?: return
        val running = jobs.remove(language)?.apply { cancel() }
        val deleting = deletions[language]
        deletions[language] = scope.launch {
            running?.join() // an unpack in progress finishes first; then its result is deleted too
            deleting?.join()
            store.directory(language).deleteRecursively()
            store.staging(language).deleteRecursively()
            File(store.downloads, "${model.name}.zip").delete()
            set(language, ModelState.Missing)
            log.log(TAG, "deleted ${model.name}")
            modelsChanged()
            forget(language, coroutineContext[Job])
        }
    }

    @Synchronized
    private fun forget(language: String, deletion: Job?) {
        if (deletions[language] === deletion) deletions.remove(language)
    }

    private suspend fun install(model: VoskModel) {
        log.log(TAG, "installing ${model.name}")
        try {
            installer.install(model) { state -> set(model.language, state) }
            set(model.language, ModelState.Installed)
            log.log(TAG, "installed ${model.name}")
            modelsChanged()
        } catch (e: InstallFailure) {
            log.log(TAG, "${model.name} failed: ${e.reason} ${e.cause ?: ""}")
            set(model.language, ModelState.Failed(e.reason))
        }
    }

    private fun modelsChanged() =
        context.sendBroadcast(Intent(LumbinaActions.SPEECH_MODELS_CHANGED).setPackage(context.packageName))

    private fun set(language: String, state: ModelState) = mutableStates.update { it + (language to state) }

    private companion object {
        const val TAG = "models"
    }
}
