package dev.lumbina.tv.vendor.generic

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.lumbina.tv.core.domain.device.DeviceProfile

@Module
@InstallIn(SingletonComponent::class)
internal interface GenericModule {
    @Binds
    @IntoSet
    fun profile(profile: GenericDeviceProfile): DeviceProfile
}
