package com.voiceagent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Main activity with simple UI for configuring the voice agent.
 * Allows users to set backend URL, request permissions, and set as default phone app.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceAgent"
        private const val PREFS_NAME = "VoiceAgentPrefs"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val REQUEST_DEFAULT_DIALER = 2001
    }

    private lateinit var statusText: TextView
    private lateinit var backendUrlInput: EditText
    private lateinit var saveUrlButton: Button
    private lateinit var requestPermissionsButton: Button
    private lateinit var setDefaultPhoneButton: Button
    private lateinit var testConnectionButton: Button
    private lateinit var permissionsStatusText: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSettings()
        updateUI()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        backendUrlInput = findViewById(R.id.backendUrlInput)
        saveUrlButton = findViewById(R.id.saveUrlButton)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)
        setDefaultPhoneButton = findViewById(R.id.setDefaultPhoneButton)
        testConnectionButton = findViewById(R.id.testConnectionButton)
        permissionsStatusText = findViewById(R.id.permissionsStatusText)

        // Save backend URL
        saveUrlButton.setOnClickListener {
            saveBackendUrl()
        }

        // Request permissions
        requestPermissionsButton.setOnClickListener {
            PermissionsHelper.requestPermissions(this)
        }

        // Set as default phone app
        setDefaultPhoneButton.setOnClickListener {
            requestDefaultDialerRole()
        }

        // Test connection
        testConnectionButton.setOnClickListener {
            testBackendConnection()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_BACKEND_URL, "") ?: ""
        backendUrlInput.setText(savedUrl)
    }

    private fun saveBackendUrl() {
        val url = backendUrlInput.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a backend URL", Toast.LENGTH_SHORT).show()
            return
        }

        // Save to SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKEND_URL, url).apply()

        Toast.makeText(this, "Backend URL saved", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun requestDefaultDialerRole() {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            startActivityForResult(intent, REQUEST_DEFAULT_DIALER)
        } else {
            Toast.makeText(this, "Default dialer setting not available on this Android version", Toast.LENGTH_LONG).show()
        }
    }

    private fun testBackendConnection() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backendUrl = prefs.getString(KEY_BACKEND_URL, "") ?: ""

        if (backendUrl.isEmpty()) {
            Toast.makeText(this, "Please save backend URL first", Toast.LENGTH_SHORT).show()
            return
        }

        statusText.text = "Testing connection..."
        testConnectionButton.isEnabled = false

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    testConnection(backendUrl)
                }

                if (result) {
                    statusText.text = "✓ Connected to backend"
                    Toast.makeText(this@MainActivity, "Connection successful!", Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "✗ Connection failed"
                    Toast.makeText(this@MainActivity, "Connection failed - check URL and network", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection test error", e)
                statusText.text = "✗ Connection error"
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                testConnectionButton.isEnabled = true
            }
        }
    }

    private fun testConnection(backendUrl: String): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .build()

            // Build health check URL
            val healthUrl = if (backendUrl.startsWith("http://") || backendUrl.startsWith("https://")) {
                "$backendUrl/health"
            } else {
                "https://$backendUrl/health"
            }

            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful

        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            false
        }
    }

    private fun updateUI() {
        // Update permissions status
        val hasAllPermissions = PermissionsHelper.hasAllPermissions(this)
        val missingPermissions = PermissionsHelper.getMissingPermissions(this)

        if (hasAllPermissions) {
            permissionsStatusText.text = "✓ All permissions granted"
            permissionsStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            requestPermissionsButton.isEnabled = false
        } else {
            val missing = missingPermissions.joinToString(", ") {
                PermissionsHelper.getPermissionDisplayName(it)
            }
            permissionsStatusText.text = "✗ Missing: $missing"
            permissionsStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            requestPermissionsButton.isEnabled = true
        }

        // Update default phone app status
        val isDefaultDialer = isDefaultDialer()
        if (isDefaultDialer) {
            setDefaultPhoneButton.text = "✓ Default Phone App"
            setDefaultPhoneButton.isEnabled = false
        } else {
            setDefaultPhoneButton.text = "Set as Default Phone App"
            setDefaultPhoneButton.isEnabled = true
        }

        // Update overall status
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val backendUrl = prefs.getString(KEY_BACKEND_URL, "") ?: ""

        when {
            !hasAllPermissions -> statusText.text = "⚠ Permissions required"
            !isDefaultDialer -> statusText.text = "⚠ Not default phone app"
            backendUrl.isEmpty() -> statusText.text = "⚠ Backend URL not set"
            else -> statusText.text = "✓ Ready to accept calls"
        }
    }

    private fun isDefaultDialer(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            return packageName == telecomManager.defaultDialerPackage
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionsHelper.PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
            }

            updateUI()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DEFAULT_DIALER) {
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
