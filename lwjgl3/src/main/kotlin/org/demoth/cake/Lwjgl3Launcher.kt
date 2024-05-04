package org.demoth.cake

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

fun main() {
    if (StartupHelper.startNewJvmIfRequired()) return  // This handles macOS support and helps on Windows.

    createApplication()
}

private fun createApplication(): Lwjgl3Application {
    return Lwjgl3Application(CakeModelViewer(), defaultConfiguration)
}

private val defaultConfiguration: Lwjgl3ApplicationConfiguration
    get() {
        val configuration = Lwjgl3ApplicationConfiguration()
        configuration.setTitle("Cake Model Viewer 0.0.1")
        configuration.useVsync(true)
        //// Limits FPS to the refresh rate of the currently active monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate)
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
        configuration.setWindowedMode(640, 480)
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")
        return configuration
    }
