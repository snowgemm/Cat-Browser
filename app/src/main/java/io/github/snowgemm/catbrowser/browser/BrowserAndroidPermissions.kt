package io.github.snowgemm.catbrowser.browser

internal fun areRequestedAndroidPermissionsGranted(
    requestedPermissions: List<String>,
    grantResults: Map<String, Boolean>
): Boolean {
    val locationPermissions = setOf(
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
