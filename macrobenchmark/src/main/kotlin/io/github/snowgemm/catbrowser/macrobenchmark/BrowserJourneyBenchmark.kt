package io.github.snowgemm.catbrowser.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class BrowserJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }

    @Test
    fun tabSwitcherGridScroll() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                device.executeShellCommand("pm clear $TARGET_PACKAGE")
                startActivityAndWait()

                repeat(7) {
                    device.wait(Until.findObject(By.descContains("Các tab")), UI_TIMEOUT_MS)
                        ?.click() ?: error("Không tìm thấy nút mở Tab Switcher.")
                    device.wait(Until.findObject(By.text("Tab thường")), UI_TIMEOUT_MS)
                        ?.click() ?: error("Không tìm thấy nút tạo tab thường.")
                }

                device.wait(Until.findObject(By.descContains("Các tab")), UI_TIMEOUT_MS)
                    ?.click() ?: error("Không thể mở Tab Switcher sau khi tạo tab.")
                check(device.wait(Until.findObject(By.textContains("8/20")), UI_TIMEOUT_MS) != null) {
                    "Tab Switcher chưa hiển thị đủ 8 tab để benchmark cuộn."
                }
            },
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val centerX = device.displayWidth / 2
            val startY = (device.displayHeight * 0.78f).toInt()
            val endY = (device.displayHeight * 0.34f).toInt()

            repeat(3) {
                device.swipe(centerX, startY, centerX, endY, 18)
                device.swipe(centerX, endY, centerX, startY, 18)
            }
        }

    private companion object {
        const val TARGET_PACKAGE = "io.github.snowgemm.catbrowser"
        const val UI_TIMEOUT_MS = 10_000L
    }
}
