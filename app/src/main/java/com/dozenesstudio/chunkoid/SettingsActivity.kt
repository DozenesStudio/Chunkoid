package com.dozenesstudio.chunkoid

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.dozenesstudio.chunkoid.databinding.ActivitySettingsBinding
import com.dozenesstudio.chunkoid.utils.ToastUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private var themeExpanded = false
    private var outputExpanded = false
    private var developerExpanded = false

    companion object {
        const val CHUNKOID_OUTPUT_DIR = "chunkoid output"
        const val KEY_SELECTED_THEME = "selected_theme"
        const val THEME_BEDROCK_BLACK = "bg"
        const val THEME_JUNGLE = "bg2"
        const val THEME_FURRY = "bg4"
        const val THEME_CUSTOM = "custom"
        const val REQUEST_CODE_PICK_IMAGE = 1001
        const val CUSTOM_BG_FILE_NAME = "custom_bg.jpg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyNoElevationToCards()

        setupToolbar()
        setupSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }

    private fun setupSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val maxMemory = prefs.getInt("max_memory", 4096)
        binding.sliderMaxMemory.value = maxMemory.toFloat()
        binding.tvMemoryValue.text = "$maxMemory MB"

        binding.sliderMaxMemory.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val memoryValue = value.toInt()
                binding.tvMemoryValue.text = "$memoryValue MB"
                prefs.edit().putInt("max_memory", memoryValue).apply()
            }
        }

        binding.switchKeepNbt.isChecked = prefs.getBoolean("keep_original_nbt", false)
        binding.switchKeepNbt.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("keep_original_nbt", isChecked).apply()
        }

        binding.itemThemeHeader.setOnClickListener {
            toggleTheme()
        }

        val currentTheme = prefs.getString(KEY_SELECTED_THEME, THEME_BEDROCK_BLACK)
        updateThemeChecks(currentTheme)

        binding.themeOption1.setOnClickListener {
            selectTheme(THEME_BEDROCK_BLACK)
        }
        binding.themeOption3.setOnClickListener {
            selectTheme(THEME_JUNGLE)
        }
        binding.themeOption5.setOnClickListener {
            selectTheme(THEME_FURRY)
        }
        binding.themeOptionCustom.setOnClickListener {
            pickCustomBackground()
        }

        binding.itemOutputHeader.setOnClickListener {
            toggleOutput()
        }
        binding.btnClearOutput.setOnClickListener {
            clearAllOutput()
        }

        binding.itemDeveloperHeader.setOnClickListener {
            toggleDeveloper()
        }

        binding.switchShowUndeveloped.isChecked = prefs.getBoolean("show_undeveloped", false)
        binding.switchShowUndeveloped.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_undeveloped", isChecked).apply()
            updateNavDrawerVisibility()
        }

        binding.switchShowTerminal.isChecked = prefs.getBoolean("show_terminal", true)
        binding.switchShowTerminal.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_terminal", isChecked).apply()
            updateNavDrawerVisibility()
        }

        binding.switchEnableLogOutput.isChecked = prefs.getBoolean("enable_conversion_log", false)
        binding.switchEnableLogOutput.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_conversion_log", isChecked).apply()
        }
    }

    private fun pickCustomBackground() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                saveCustomBackground(uri)
            }
        }
    }

    private fun saveCustomBackground(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val customBgFile = File(filesDir, CUSTOM_BG_FILE_NAME)
                FileOutputStream(customBgFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                selectTheme(THEME_CUSTOM)
                ToastUtils.show(this, getString(R.string.custom_bg_set))
            }
        } catch (e: Exception) {
            ToastUtils.show(this, getString(R.string.custom_bg_error))
        }
    }

    private fun toggleTheme() {
        themeExpanded = !themeExpanded
        if (themeExpanded) {
            binding.layoutThemeContent.visibility = android.view.View.VISIBLE
            rotateArrow(binding.ivThemeArrow, true)
        } else {
            binding.layoutThemeContent.visibility = android.view.View.GONE
            rotateArrow(binding.ivThemeArrow, false)
        }
    }

    private fun toggleOutput() {
        outputExpanded = !outputExpanded
        if (outputExpanded) {
            binding.layoutOutputContent.visibility = android.view.View.VISIBLE
            rotateArrow(binding.ivOutputArrow, true)
        } else {
            binding.layoutOutputContent.visibility = android.view.View.GONE
            rotateArrow(binding.ivOutputArrow, false)
        }
    }

    private fun toggleDeveloper() {
        developerExpanded = !developerExpanded
        if (developerExpanded) {
            binding.layoutDeveloperContent.visibility = android.view.View.VISIBLE
            rotateArrow(binding.ivDeveloperArrow, true)
        } else {
            binding.layoutDeveloperContent.visibility = android.view.View.GONE
            rotateArrow(binding.ivDeveloperArrow, false)
        }
    }

    private fun rotateArrow(view: android.view.View, expand: Boolean) {
        val fromDegrees = if (expand) 0f else 90f
        val toDegrees = if (expand) 90f else 0f
        val animation = RotateAnimation(
            fromDegrees, toDegrees,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        animation.duration = 200
        animation.fillAfter = true
        view.startAnimation(animation)
    }

    private fun clearAllOutput() {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val chunkoidOutputDir = File(documentsDir, CHUNKOID_OUTPUT_DIR)

        if (!chunkoidOutputDir.exists() || !chunkoidOutputDir.isDirectory) {
            ToastUtils.show(this, getString(R.string.output_cleared))
            return
        }

        var count = 0
        chunkoidOutputDir.listFiles()?.forEach { worldDir ->
            if (worldDir.isDirectory) {
                if (worldDir.deleteRecursively()) {
                    count++
                }
            }
        }

        ToastUtils.show(this, getString(R.string.output_cleared) + " ($count)")
    }

    private fun updateNavDrawerVisibility() {
        (this as? MainActivity)?.updateNavDrawerVisibility()
    }

    private fun selectTheme(theme: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString(KEY_SELECTED_THEME, theme).apply()
        updateThemeChecks(theme)
        (this as? MainActivity)?.updateBackground()
        ToastUtils.show(this, getString(R.string.theme_changed_immediate))
    }

    private fun updateThemeChecks(selectedTheme: String?) {
        binding.themeCheck1.visibility = if (selectedTheme == THEME_BEDROCK_BLACK) android.view.View.VISIBLE else android.view.View.GONE
        binding.themeCheck3.visibility = if (selectedTheme == THEME_JUNGLE) android.view.View.VISIBLE else android.view.View.GONE
        binding.themeCheck5.visibility = if (selectedTheme == THEME_FURRY) android.view.View.VISIBLE else android.view.View.GONE
        binding.themeCheckCustom.visibility = if (selectedTheme == THEME_CUSTOM) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyNoElevationToCards() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        removeCardElevation(rootView)
    }

    private fun removeCardElevation(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is com.google.android.material.card.MaterialCardView) {
                child.cardElevation = 0f
            } else if (child is ViewGroup) {
                removeCardElevation(child)
            }
        }
    }
}
