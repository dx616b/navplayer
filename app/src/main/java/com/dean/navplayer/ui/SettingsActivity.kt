package com.dean.navplayer.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dean.navplayer.NavPlayerApp
import com.dean.navplayer.R
import com.dean.navplayer.data.ServerConfig
import com.dean.navplayer.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: NavPlayerApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HeadUnitUi.apply(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = application as NavPlayerApp

        app.credentials.load()?.let { config ->
            binding.serverUrlInput.setText(config.baseUrl.removeSuffix("/rest"))
            binding.usernameInput.setText(config.username)
            binding.passwordInput.setText(config.password)
        }
        binding.rememberSwitch.isChecked = app.credentials.rememberLogin
        binding.autoStartSwitch.isChecked = app.prefs.autoStartOnBoot
        binding.drivingModeSwitch.isChecked = app.prefs.drivingMode

        binding.autoStartSwitch.setOnCheckedChangeListener { _, checked ->
            app.prefs.autoStartOnBoot = checked
        }
        binding.drivingModeSwitch.setOnCheckedChangeListener { _, checked ->
            app.prefs.drivingMode = checked
        }

        binding.testButton.setOnClickListener { testConnection(save = false) }
        binding.saveButton.setOnClickListener { testConnection(save = true) }
    }

    private fun testConnection(save: Boolean) {
        val config = readConfig() ?: return
        binding.testButton.isEnabled = false
        binding.saveButton.isEnabled = false
        lifecycleScope.launch {
            val result = app.subsonic.testConnection(config)
            binding.testButton.isEnabled = true
            binding.saveButton.isEnabled = true
            result.onSuccess {
                Toast.makeText(this@SettingsActivity, R.string.connection_ok, Toast.LENGTH_SHORT).show()
                if (save) {
                    app.credentials.rememberLogin = binding.rememberSwitch.isChecked
                    if (binding.rememberSwitch.isChecked) {
                        app.credentials.save(config)
                    } else {
                        app.credentials.clearPassword()
                    }
                    app.subsonic.invalidatePlaylistsCache()
                    setResult(RESULT_OK)
                    finish()
                }
            }.onFailure {
                Toast.makeText(
                    this@SettingsActivity,
                    it.message ?: getString(R.string.connection_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun readConfig(): ServerConfig? {
        val rawUrl = binding.serverUrlInput.text?.toString()?.trim().orEmpty()
        val user = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val pass = binding.passwordInput.text?.toString().orEmpty()
        if (rawUrl.isBlank() || user.isBlank() || pass.isBlank()) {
            Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_SHORT).show()
            return null
        }
        if (!rawUrl.startsWith("https://")) {
            Toast.makeText(this, "HTTPS URL required (https://…)", Toast.LENGTH_LONG).show()
            return null
        }
        return ServerConfig(app.subsonic.normalizeBaseUrl(rawUrl), user, pass)
    }
}
