package dev.lumbina.tv.core.domain.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppIdTest {

    @Test
    fun `flatten shortens activities inside the package`() {
        val id = AppId("ru.kinopoisk.tv", "ru.kinopoisk.tv.presentation.splash.SplashActivity")
        assertEquals("ru.kinopoisk.tv/.presentation.splash.SplashActivity", id.flatten())
    }

    @Test
    fun `flatten keeps activities from other packages`() {
        val id = AppId("com.acme.tv.home", "com.acme.shell.LauncherActivity")
        assertEquals("com.acme.tv.home/com.acme.shell.LauncherActivity", id.flatten())
    }

    @Test
    fun `blank package is rejected`() {
        assertThrows<IllegalArgumentException> { AppId(" ", "x.Main") }
    }
}
