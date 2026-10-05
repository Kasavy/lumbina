package dev.lumbina.tv.platform.weather

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.domain.weather.PlaceLocator
import dev.lumbina.tv.core.domain.weather.WeatherProvider

@Module
@InstallIn(SingletonComponent::class)
internal interface WeatherModule {
    @Binds fun provider(impl: FallbackWeather): WeatherProvider

    @Binds fun locator(impl: IpPlaceLocator): PlaceLocator
}
