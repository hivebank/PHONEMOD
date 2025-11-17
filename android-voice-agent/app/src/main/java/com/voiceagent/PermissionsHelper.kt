package com.voiceagent

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper class to manage runtime permissions required for the voice agent.
 */
object PermissionsHelper {

    const val PERMISSION_REQUEST_CODE = 1001

    // All permissions required by the app
    val REQUIRED_PERMISSIONS = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.INTERNET)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(Manifest.permission.FOREGROUND_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    /**
     * Check if all required permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get list of permissions that are not yet granted.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request all missing permissions.
     */
    fun requestPermissions(activity: Activity) {
        val missingPermissions = getMissingPermissions(activity)

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Check specific permission.
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get user-friendly permission names for display.
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_PHONE_STATE -> "Phone State"
            Manifest.permission.CALL_PHONE -> "Make Calls"
            Manifest.permission.RECORD_AUDIO -> "Record Audio"
            Manifest.permission.ANSWER_PHONE_CALLS -> "Answer Calls"
            Manifest.permission.INTERNET -> "Internet Access"
            Manifest.permission.FOREGROUND_SERVICE -> "Foreground Service"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission
        }
    }
}
