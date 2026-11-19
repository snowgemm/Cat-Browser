package io.github.snowgemm.catbrowser.browser

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAndroidPermissionTest {
    @Test
    fun approximateLocationIsAcceptedWhenCoarsePermissionIsGranted() {
        val requested = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val result = mapOf(
            Manifest.permission.ACCESS_COARSE_LOCATION to true,
            Manifest.permission.ACCESS_FINE_LOCATION to false
        )

        assertTrue(areRequestedAndroidPermissionsGranted(requested, result))
    }

    @Test
    fun locationIsRejectedWhenNeitherAccuracyPermissionIsGranted() {
        val requested = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val result = mapOf(
            Manifest.permission.ACCESS_COARSE_LOCATION to false,
            Manifest.permission.ACCESS_FINE_LOCATION to false
        )

        assertFalse(areRequestedAndroidPermissionsGranted(requested, result))
    }

    @Test
    fun cameraAndMicrophoneMustBothBeGrantedWhenBothAreRequested() {
        val requested = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        assertTrue(
            areRequestedAndroidPermissionsGranted(
                requested,
                mapOf(
                    Manifest.permission.CAMERA to true,
                    Manifest.permission.RECORD_AUDIO to true
                )
            )
        )
        assertFalse(
            areRequestedAndroidPermissionsGranted(
                requested,
                mapOf(
                    Manifest.permission.CAMERA to true,
                    Manifest.permission.RECORD_AUDIO to false
                )
            )
        )
    }
}
