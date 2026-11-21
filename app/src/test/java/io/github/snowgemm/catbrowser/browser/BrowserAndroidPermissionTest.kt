package io.github.snowgemm.catbrowser.browser

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAndroidPermissionTest {
    @Test
    fun approximateLocationIsAcceptedWhenCoarsePermissionIsGranted() {
        val requested =
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        val result =
            mapOf(
                Manifest.permission.ACCESS_COARSE_LOCATION to true,
                Manifest.permission.ACCESS_FINE_LOCATION to false,
            )

        assertTrue(
            areRequestedAndroidPermissionsGranted(requested, result),
        )
    }

    @Test
    fun locationIsRejectedWhenNeitherAccuracyPermissionIsGranted() {
        val requested =
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        val result =
            mapOf(
                Manifest.permission.ACCESS_COARSE_LOCATION to false,
                Manifest.permission.ACCESS_FINE_LOCATION to false,
            )

        assertFalse(
            areRequestedAndroidPermissionsGranted(requested, result),
        )
    }

    @Test
    fun deniedPermissionsAreRememberedWithoutMarkingGrantedPermissionsDenied() {
        val requested =
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )

        assertTrue(
            BrowserAndroidPermissionPolicy.deniedPermissionsAfterRequest(
                requested,
                mapOf(
                    Manifest.permission.CAMERA to true,
                    Manifest.permission.RECORD_AUDIO to false,
                ),
            ) == setOf(Manifest.permission.RECORD_AUDIO),
        )
    }

    @Test
    fun approximateLocationDoesNotPersistFineLocationDenial() {
        val requested =
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )

        assertTrue(
            BrowserAndroidPermissionPolicy
                .deniedPermissionsAfterRequest(
                    requested,
                    mapOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION to true,
                        Manifest.permission.ACCESS_FINE_LOCATION to false,
                    ),
                ).isEmpty(),
        )
    }

    @Test
    fun previouslyDeniedPermissionIsNotRequestedAgainUntilGrantedInSystemSettings() {
        val requested = listOf(Manifest.permission.CAMERA)
        val denied = setOf(Manifest.permission.CAMERA)

        assertTrue(
            BrowserAndroidPermissionPolicy.shouldRejectPreviouslyDenied(
                requested,
                denied,
                emptySet(),
            ),
        )
        assertFalse(
            BrowserAndroidPermissionPolicy.shouldRejectPreviouslyDenied(
                requested,
                denied,
                setOf(Manifest.permission.CAMERA),
            ),
        )
    }

    @Test
    fun cameraAndMicrophoneMustBothBeGrantedWhenBothAreRequested() {
        val requested =
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )

        assertTrue(
            areRequestedAndroidPermissionsGranted(
                requested,
                mapOf(
                    Manifest.permission.CAMERA to true,
                    Manifest.permission.RECORD_AUDIO to true,
                ),
            ),
        )
        assertFalse(
            areRequestedAndroidPermissionsGranted(
                requested,
                mapOf(
                    Manifest.permission.CAMERA to true,
                    Manifest.permission.RECORD_AUDIO to false,
                ),
            ),
        )
    }
}
