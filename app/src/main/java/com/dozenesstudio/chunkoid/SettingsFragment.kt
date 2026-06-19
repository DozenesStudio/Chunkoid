package com.dozenesstudio.chunkoid

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.dozenesstudio.chunkoid.databinding.FragmentSettingsBinding
import com.dozenesstudio.chunkoid.utils.ToastUtils
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var themeExpanded = false
    private var outputExpanded = false
    private var developerExpanded = false

    companion object {
        const val CHUNKOID_OUTPUT_DIR = "chunkoid output"
        const val KEY_SELECTED_THEME = "selected_theme"
        const val THEME_BEDROCK_BLACK = "bg"
        const val THEME_FAR_EAST = "bg1"
        const val THEME_JUNGLE = "bg2"
        const val THEME_XINGYANG = "bg3"
        const val THEME_FURRY = "bg4"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // JVM Max Memory
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

        // Keep Original NBT Switch
        binding.switchKeepNbt.isChecked = prefs.getBoolean("keep_original_nbt", false)
        binding.switchKeepNbt.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("keep_original_nbt", isChecked).apply()
        }

        // Theme Switch
        binding.itemThemeHeader.setOnClickListener {
            toggleTheme()
        }

        // Theme Options
        val currentTheme = prefs.getString(KEY_SELECTED_THEME, THEME_BEDROCK_BLACK)
        updateThemeChecks(currentTheme)

        binding.themeOption1.setOnClickListener {
            selectTheme(THEME_BEDROCK_BLACK)
        }
        binding.themeOption2.setOnClickListener {
            selectTheme(THEME_FAR_EAST)
        }
        binding.themeOption3.setOnClickListener {
            selectTheme(THEME_JUNGLE)
        }
        binding.themeOption4.setOnClickListener {
            selectTheme(THEME_XINGYANG)
        }
        binding.themeOption5.setOnClickListener {
            selectTheme(THEME_FURRY)
        }

        // Output Management
        binding.itemOutputHeader.setOnClickListener {
            toggleOutput()
        }
        binding.btnClearOutput.setOnClickListener {
            clearAllOutput()
        }

        // Developer Mode
        binding.itemDeveloperHeader.setOnClickListener {
            toggleDeveloper()
        }

        // Switch for Show Undeveloped Features (default: true)
        binding.switchShowUndeveloped.isChecked = prefs.getBoolean("show_undeveloped", true)
        binding.switchShowUndeveloped.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_undeveloped", isChecked).apply()
            updateNavDrawerVisibility()
        }

        // Switch for Show Terminal (default: true)
        binding.switchShowTerminal.isChecked = prefs.getBoolean("show_terminal", true)
        binding.switchShowTerminal.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_terminal", isChecked).apply()
            updateNavDrawerVisibility()
        }
    }

    private fun toggleTheme() {
        themeExpanded = !themeExpanded
        if (themeExpanded) {
            binding.layoutThemeContent.visibility = View.VISIBLE
            rotateArrow(binding.ivThemeArrow, true)
        } else {
            binding.layoutThemeContent.visibility = View.GONE
            rotateArrow(binding.ivThemeArrow, false)
        }
    }

    private fun toggleOutput() {
        outputExpanded = !outputExpanded
        if (outputExpanded) {
            binding.layoutOutputContent.visibility = View.VISIBLE
            rotateArrow(binding.ivOutputArrow, true)
        } else {
            binding.layoutOutputContent.visibility = View.GONE
            rotateArrow(binding.ivOutputArrow, false)
        }
    }

    private fun toggleDeveloper() {
        developerExpanded = !developerExpanded
        if (developerExpanded) {
            binding.layoutDeveloperContent.visibility = View.VISIBLE
            rotateArrow(binding.ivDeveloperArrow, true)
        } else {
            binding.layoutDeveloperContent.visibility = View.GONE
            rotateArrow(binding.ivDeveloperArrow, false)
        }
    }

    private fun rotateArrow(view: View, expand: Boolean) {
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
            ToastUtils.show(requireContext(), getString(R.string.output_cleared))
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

        ToastUtils.show(requireContext(), getString(R.string.output_cleared) + " ($count)")
    }

    private fun updateNavDrawerVisibility() {
        // Notify MainActivity to update the nav drawer
        (activity as? MainActivity)?.updateNavDrawerVisibility()
    }

    private fun selectTheme(theme: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit().putString(KEY_SELECTED_THEME, theme).apply()
        updateThemeChecks(theme)
        // Notify MainActivity to update background immediately
        (activity as? MainActivity)?.updateBackground()
        ToastUtils.show(requireContext(), getString(R.string.theme_changed_immediate))
    }

    private fun updateThemeChecks(selectedTheme: String?) {
        binding.themeCheck1.visibility = if (selectedTheme == THEME_BEDROCK_BLACK) View.VISIBLE else View.GONE
        binding.themeCheck2.visibility = if (selectedTheme == THEME_FAR_EAST) View.VISIBLE else View.GONE
        binding.themeCheck3.visibility = if (selectedTheme == THEME_JUNGLE) View.VISIBLE else View.GONE
        binding.themeCheck4.visibility = if (selectedTheme == THEME_XINGYANG) View.VISIBLE else View.GONE
        binding.themeCheck5.visibility = if (selectedTheme == THEME_FURRY) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
