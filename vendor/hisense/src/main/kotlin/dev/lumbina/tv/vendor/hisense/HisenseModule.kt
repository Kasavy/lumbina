package dev.lumbina.tv.vendor.hisense

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.lumbina.tv.core.domain.device.DeviceProfile

@Module
@InstallIn(SingletonComponent::class)
internal interface HisenseModule {
    @Binds
    @IntoSet
    fun profile(profile: HisenseDeviceProfile): DeviceProfile
}
