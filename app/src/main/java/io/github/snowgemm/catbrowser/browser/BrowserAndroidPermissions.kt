package io.github.snowgemm.catbrowser.browser

internal fun areRequestedAndroidPermissionsGranted(
    requestedPermissions: List<String>,
    grantResults: Map<String, Boolean>
): Boolean {
    val locationPermissions =
        setOf(
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )
    val requestedLocation = requestedPermissions.any { it in locationPermissions }
    val locationGranted = requestedPermissions
        .filter { it in locationPermissions }
        .any { grantResults[it] == true }
    val otherPermissionsGranted = requestedPermissions
        .filterNot { it in locationPermissions }
        .all { grantResults[it] == true }

    return (!requestedLocation || locationGranted) && otherPermissionsGranted
}

internal object BrowserAndroidPermissionPolicy {
    private val locationPermissions = setOf(
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun deniedPermissionsAfterRequest(
        requestedPermissions: List<String>,
        grantResults: Map<String, Boolean>,
    ): Set<String> {
        val requestedLocation = requestedPermissions.filter { it in locationPermissions }
        val locationGranted = requestedLocation.any { grantResults[it] == true }
        return buildSet {
            requestedPermissions
                .filterNot { it in locationPermissions }
                .filter { grantResults[it] != true }
                .forEach(::add)
            if (requestedLocation.isNotEmpty() && !locationGranted) {
                addAll(requestedLocation)
            }
        }
    }

    fun shouldRejectPreviouslyDenied(
        requestedPermissions: List<String>,
        previouslyDenied: Set<String>,
        currentlyGranted: Set<String>,
    ): Boolean {
        val locationRequested = requestedPermissions.any { it in locationPermissions }
        val locationAlreadyGranted =
            requestedPermissions.any {
            it in locationPermissions && it in currentlyGranted
        }
        val locationDeniedBefore =
            requestedPermissions.any {
            it in locationPermissions && it in previouslyDenied && it !in currentlyGranted
        }
        if (locationRequested && !locationAlreadyGranted && locationDeniedBefore) return true

        return requestedPermissions.any {
            it !in locationPermissions && it in previouslyDenied && it !in currentlyGranted
        }
    }
}
