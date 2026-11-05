package dev.lumbina.tv.vendor.generic

import android.content.Context
import android.media.tv.TvInputManager
import dev.lumbina.tv.core.domain.device.DeviceAnswer
import dev.lumbina.tv.core.domain.device.DeviceDiagnostics

/**
 * What a report asks any TV: every input a picture comes in through as Android lists it, hidden
 * ones and the devices' own inputs under the ports too, with the state Android tells of each.
 */
internal class GenericDiagnostics(private val context: Context) : DeviceDiagnostics {

    override fun probe(): List<DeviceAnswer> {
        val manager = context.getSystemService(TvInputManager::class.java)
            ?: return listOf(DeviceAnswer("tv inputs", "no TvInputManager"))
        val all = runCatching { manager.tvInputList.orEmpty() }.getOrElse {
            return listOf(DeviceAnswer("tv inputs", "failed: $it"))
        }
        return all.filter { it.isPassthroughInput }.map { input ->
            val facts = listOf(
                "label \"${input.loadLabel(context)}\"",
                "custom \"${input.loadCustomLabel(context) ?: ""}\"",
                "type ${input.type}",
                "parent ${input.parentId}",
                "hidden ${input.isHidden(context)}",
                "state ${manager.state(input.id)}",
            )
            DeviceAnswer("tv input ${input.id}", facts.joinToString())
        }
    }
}
