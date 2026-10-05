package dev.lumbina.tv.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold start and home scrolling, without ahead-of-time compilation and with the baseline profile. */
@RunWith(AndroidJUnit4::class)
class HomeBenchmark {

    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun startupWithoutCompilation() = startup(CompilationMode.None())

    @Test fun startupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun scrollWithoutCompilation() = scroll(CompilationMode.None())

    @Test fun scrollWithBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        pressHome()
        startActivityAndWait()
    }

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        iterations = ITERATIONS,
        setupBlock = { startActivityAndWait() },
    ) {
        browseHome()
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
