package com.dozenesstudio.chunkoid

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import com.dozenesstudio.chunkoid.databinding.ActivityMainBinding
import com.dozenesstudio.chunkoid.model.ConversionSettings
import com.dozenesstudio.chunkoid.utils.ToastUtils
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private var inputUri: Uri? = null
    private var inputWorldName: String = ""
    private var selectedFormat: String = ""
    private var selectedFormatDisplayName: String = ""
    private var isArchiveMode = false
    private var isWorldInfoVisible = false
    private var worldPlatform = Platform.BEDROCK

    private fun mapVersionToChunkerFormat(displayName: String): String {
        return when(displayName) {
            getString(R.string.java_1_8_8) -> "JAVA_1_8_8"
            getString(R.string.java_1_9) -> "JAVA_1_9"
            getString(R.string.java_1_10) -> "JAVA_1_10"
            getString(R.string.java_1_11) -> "JAVA_1_11"
            getString(R.string.java_1_12) -> "JAVA_1_12"
            getString(R.string.java_1_13) -> "JAVA_1_13"
            getString(R.string.java_1_14) -> "JAVA_1_14"
            getString(R.string.java_1_15) -> "JAVA_1_15"
            getString(R.string.java_1_16) -> "JAVA_1_16"
            getString(R.string.java_1_17) -> "JAVA_1_17"
            getString(R.string.java_1_18) -> "JAVA_1_18"
            getString(R.string.java_1_19) -> "JAVA_1_19"
            getString(R.string.java_1_20) -> "JAVA_1_20"
            getString(R.string.java_1_21) -> "JAVA_1_21"
            getString(R.string.bedrock_1_12) -> "BEDROCK_R12"
            getString(R.string.bedrock_1_13) -> "BEDROCK_R13"
            getString(R.string.bedrock_1_14) -> "BEDROCK_R14"
            getString(R.string.bedrock_1_16) -> "BEDROCK_R16"
            getString(R.string.bedrock_1_17) -> "BEDROCK_R17"
            getString(R.string.bedrock_1_18) -> "BEDROCK_R18"
            getString(R.string.bedrock_1_19) -> "BEDROCK_R19"
            getString(R.string.bedrock_1_20) -> "BEDROCK_R20"
            getString(R.string.bedrock_1_21) -> "BEDROCK_R21"
            getString(R.string.bedrock_1_26) -> "BEDROCK_R26"
            else -> displayName
        }
    }

    private var activeItemId = R.id.nav_item_converter
    private val inactiveScale = 0.97f
    private val inactiveTextAlpha = 0f
    private val animDuration = 150L

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            inputUri = it
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            isArchiveMode = false
            processSelectedWorld(it)
        }
    }

    private val archivePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            inputUri = it
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            isArchiveMode = true
            processSelectedWorld(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout

        // Make status bar transparent and allow content to draw under it
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        // Set status bar icons to light mode for visibility on dark backgrounds
        val decorView = window.decorView
        decorView.systemUiVisibility = decorView.systemUiVisibility or 
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        window.decorView.postDelayed({
            showCustomToast("请选择一个世界")
        }, 500)

        setupDrawer()
        setupClickListeners()
        setupBottomNavigation()
        checkPermissions()
        setupSplashText()
        updateNavDrawerVisibility()
        updateBackground()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateNavDrawerVisibility()
        updateBackground()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_OUTPUT_MANAGER, false) == true) {
            switchToOutputManager(false)
        }
        if (intent?.getBooleanExtra(EXTRA_RESET_TO_INITIAL_STATE, false) == true) {
            switchToInitialState()
        }
    }

    private fun setupBottomNavigation() {
        val converterIcon = binding.navItemConverter.root.findViewById<ImageView>(R.id.nav_icon)
        val converterText = binding.navItemConverter.root.findViewById<TextView>(R.id.nav_text)
        converterIcon.setImageResource(R.drawable.converter)
        converterText.text = getString(R.string.converter)

        val outputIcon = binding.navItemOutput.root.findViewById<ImageView>(R.id.nav_icon)
        val outputText = binding.navItemOutput.root.findViewById<TextView>(R.id.nav_text)
        outputIcon.setImageResource(R.drawable.output)
        outputText.text = getString(R.string.output_manager)

        binding.navItemConverter.root.setOnClickListener {
            switchToConverter()
        }

        binding.navItemOutput.root.setOnClickListener {
            switchToOutputManager(true)
        }

        updateNavItemStates(false)
    }

    private fun switchToConverter() {
        if (activeItemId == R.id.nav_item_converter) return
        activeItemId = R.id.nav_item_converter

        binding.tvSplash.visibility = View.VISIBLE
        startSplashAnimation()
        supportFragmentManager.popBackStack()

        updateNavItemStates(true)
    }

    private fun switchToOutputManager(animate: Boolean) {
        if (activeItemId == R.id.nav_item_output) return
        activeItemId = R.id.nav_item_output

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment !is OutputManagerFragment) {
            binding.tvSplash.visibility = View.GONE
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragment_container, OutputManagerFragment())
                .addToBackStack(null)
                .commit()
        }

        updateNavItemStates(animate)
    }

    private fun updateNavItemStates(animate: Boolean) {
        val converterItem = binding.navItemConverter.root
        val outputItem = binding.navItemOutput.root

        val converterIcon = converterItem.findViewById<ImageView>(R.id.nav_icon)
        val converterText = converterItem.findViewById<TextView>(R.id.nav_text)
        val outputIcon = outputItem.findViewById<ImageView>(R.id.nav_icon)
        val outputText = outputItem.findViewById<TextView>(R.id.nav_text)

        val isConverterActive = activeItemId == R.id.nav_item_converter

        if (animate) {
            animateNavItem(converterIcon!!, converterText!!, isConverterActive)
            animateNavItem(outputIcon!!, outputText!!, !isConverterActive)
        } else {
            setNavItemState(converterIcon!!, converterText!!, isConverterActive)
            setNavItemState(outputIcon!!, outputText!!, !isConverterActive)
        }
    }

    private fun animateNavItem(icon: ImageView, text: TextView, isActive: Boolean) {
        val targetScale = if (isActive) 1f else inactiveScale
        val targetTextAlpha = if (isActive) 1f else inactiveTextAlpha
        val targetTranslationY = if (isActive) 0f else 8f

        val scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, icon.scaleX, targetScale).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, icon.scaleY, targetScale).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
        }
        val translationY = ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, icon.translationY, targetTranslationY).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
        }
        val textAlpha = ObjectAnimator.ofFloat(text, View.ALPHA, text.alpha, targetTextAlpha).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, translationY, textAlpha)
            start()
        }
    }

    private fun setNavItemState(icon: ImageView, text: TextView, isActive: Boolean) {
        icon.scaleX = if (isActive) 1f else inactiveScale
        icon.scaleY = if (isActive) 1f else inactiveScale
        icon.translationY = if (isActive) 0f else 8f
        text.alpha = if (isActive) 1f else inactiveTextAlpha
    }

    private fun setupSplashText() {
        val splashTexts = loadSplashesFromAssets()
        if (splashTexts.isNotEmpty()) {
            val randomSplash = splashTexts.random()
            binding.tvSplash.text = randomSplash

            try {
                val pixelFont = resources.getFont(R.font.uranus_pixel)
                binding.tvSplash.typeface = pixelFont
            } catch (e: Exception) {
                e.printStackTrace()
            }

            startSplashAnimation()
        }
    }

    private fun startSplashAnimation() {
        val scaleX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.25f, 1.0f)
        val scaleY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.25f, 1.0f)

        val animator = android.animation.ObjectAnimator.ofPropertyValuesHolder(binding.tvSplash, scaleX, scaleY)
        animator.duration = 1000
        animator.repeatCount = android.animation.ObjectAnimator.INFINITE
        animator.repeatMode = android.animation.ObjectAnimator.RESTART
        animator.start()
    }

    private fun loadSplashesFromAssets(): List<String> {
        return try {
            val inputStream = assets.open("splashes.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            reader.close()

            val jsonObject = JSONObject(jsonString)
            val splashesArray = jsonObject.getJSONArray("splashes")
            val splashes = mutableListOf<String>()

            for (i in 0 until splashesArray.length()) {
                splashes.add(splashesArray.getString(i))
            }
            splashes
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun showCustomToast(message: String, isError: Boolean = false) {
        ToastUtils.show(this, message, isError)
    }

    private fun setupDrawer() {
        val navDrawer = binding.navDrawer
        val navDrawerView = navDrawer.root

        val navItemSettings = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_settings)
        val navItemTerminal = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_terminal)
        val navItemMapDownloader = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_map_downloader)
        val navItemResourceConverter = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_resource_converter)
        val navItemDecrypt = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_decrypt)
        val navItemAbout = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_about)

        navItemSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            drawerLayout.closeDrawer(navDrawerView)
        }

        navItemTerminal.setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
            drawerLayout.closeDrawer(navDrawerView)
        }

        val featureNotAvailableListener = View.OnClickListener {
            drawerLayout.closeDrawer(navDrawerView)
            showCustomToast("功能尚未开发，敬请期待")
        }

        navItemMapDownloader.setOnClickListener(featureNotAvailableListener)
        navItemResourceConverter.setOnClickListener(featureNotAvailableListener)
        navItemDecrypt.setOnClickListener(featureNotAvailableListener)

        navItemAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
            drawerLayout.closeDrawer(navDrawerView)
        }
    }

    fun updateNavDrawerVisibility() {
        val navDrawerView = binding.navDrawer.root
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        val navItemTerminal = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_terminal)
        val navItemMapDownloader = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_map_downloader)
        val navItemResourceConverter = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_resource_converter)
        val navItemDecrypt = navDrawerView.findViewById<LinearLayout>(R.id.nav_item_decrypt)
        
        val showTerminal = prefs.getBoolean("show_terminal", true)
        val showUndeveloped = prefs.getBoolean("show_undeveloped", false)
        
        navItemTerminal.visibility = if (showTerminal) View.VISIBLE else View.GONE
        navItemMapDownloader.visibility = if (showUndeveloped) View.VISIBLE else View.GONE
        navItemResourceConverter.visibility = if (showUndeveloped) View.VISIBLE else View.GONE
        navItemDecrypt.visibility = if (showUndeveloped) View.VISIBLE else View.GONE
    }

    fun updateBackground() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedTheme = prefs.getString("selected_theme", "bg")
        
        val drawableRes = when (selectedTheme) {
            "bg1" -> R.drawable.bg1
            "bg2" -> R.drawable.bg2
            "bg3" -> R.drawable.bg3
            "bg4" -> R.drawable.bg4
            else -> R.drawable.bg
        }
        
        binding.ivBackground.setImageResource(drawableRes)
    }

    private fun setupClickListeners() {
        binding.btnMenu.setOnClickListener {
            drawerLayout.openDrawer(binding.navDrawer.root)
        }

        binding.btnArchive.setOnClickListener {
            archivePickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }

        binding.btnFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        binding.btnSelectTarget.setOnClickListener {
            showVersionPickerDialog()
        }
    }

    private fun showVersionPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_version_picker, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)

        var selectedVersion: String? = null

        // Setup Bedrock versions RecyclerView
        val bedrockRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_bedrock_versions)
        bedrockRecyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        val bedrockVersions = listOf(
            getString(R.string.bedrock_1_12),
            getString(R.string.bedrock_1_13),
            getString(R.string.bedrock_1_14),
            getString(R.string.bedrock_1_16),
            getString(R.string.bedrock_1_17),
            getString(R.string.bedrock_1_18),
            getString(R.string.bedrock_1_19),
            getString(R.string.bedrock_1_20),
            getString(R.string.bedrock_1_21),
            getString(R.string.bedrock_1_26)
        )
        bedrockRecyclerView.adapter = VersionAdapter(bedrockVersions) { version ->
            selectedVersion = version
        }

        // Setup Java versions RecyclerView
        val javaRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_java_versions)
        javaRecyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        val javaVersions = listOf(
            getString(R.string.java_1_8_8),
            getString(R.string.java_1_9),
            getString(R.string.java_1_10),
            getString(R.string.java_1_11),
            getString(R.string.java_1_12),
            getString(R.string.java_1_13),
            getString(R.string.java_1_14),
            getString(R.string.java_1_15),
            getString(R.string.java_1_16),
            getString(R.string.java_1_17),
            getString(R.string.java_1_18),
            getString(R.string.java_1_19),
            getString(R.string.java_1_20),
            getString(R.string.java_1_21)
        )
        javaRecyclerView.adapter = VersionAdapter(javaVersions) { version ->
            selectedVersion = version
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_confirm).setOnClickListener {
            if (selectedVersion.isNullOrEmpty()) {
                showCustomToast(getString(R.string.error_no_format), isError = true)
            } else {
                dialog.dismiss()
                updateTargetVersionDisplay(selectedVersion!!)
            }
        }

        dialog.show()
    }

    private class VersionAdapter(
        private val versions: List<String>,
        private val onSelect: ((String) -> Unit)? = null
    ) :
        androidx.recyclerview.widget.RecyclerView.Adapter<VersionAdapter.VersionViewHolder>() {

        var selectedVersion: String? = null

        inner class VersionViewHolder(itemView: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val versionName: TextView = itemView.findViewById(R.id.tv_version_name)
            val card: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.card_version)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VersionViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_version_card, parent, false)
            return VersionViewHolder(view)
        }

        override fun onBindViewHolder(holder: VersionViewHolder, position: Int) {
            val version = versions[position]
            holder.versionName.text = version

            if (version == selectedVersion) {
                holder.card.strokeColor = holder.itemView.context.resources.getColor(R.color.minecraft_green)
            } else {
                holder.card.strokeColor = holder.itemView.context.resources.getColor(R.color.transparent)
            }

            holder.itemView.setOnClickListener {
                selectedVersion = version
                onSelect?.invoke(version)
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int = versions.size
    }

    private fun processSelectedWorld(uri: Uri) {
        if (isArchiveMode) {
            // 隐藏初始状态卡片，显示世界信息卡片
            binding.cardInitialState.visibility = View.GONE
            binding.cardWorldInfo.visibility = View.VISIBLE
            
            // 显示解压进度条
            binding.layoutExtractProgress.visibility = View.VISIBLE
            // 隐藏世界信息卡片（World Info Bar）
            binding.cardWorldInfoBar.visibility = View.GONE
            // 隐藏信息卡片和按钮
            binding.layoutInfoCards.visibility = View.GONE
            binding.btnSelectTarget.visibility = View.GONE
            
            // 在后台线程执行所有文件操作
            Thread {
                val success = extractArchiveToInput(uri)
                
                if (!success) {
                    // 解压失败，回到主线程更新UI
                    runOnUiThread {
                        binding.layoutExtractProgress.visibility = View.GONE
                        binding.cardWorldInfoBar.visibility = View.VISIBLE
                        binding.layoutInfoCards.visibility = View.VISIBLE
                        binding.btnSelectTarget.visibility = View.VISIBLE
                    }
                    return@Thread
                }
                
                // 继续在后台线程执行读取操作
                readWorldNameFromArchive(uri)
                detectPlatformFromArchive(uri)
                val iconBitmap = loadWorldIconFromArchive(uri)
                
                // 所有读取完成，回到主线程更新UI
                runOnUiThread {
                    // 设置图标
                    if (iconBitmap != null) {
                        binding.ivWorldIcon.setImageBitmap(iconBitmap)
                    }
                    
                    // 解压完成，隐藏进度条
                    binding.layoutExtractProgress.visibility = View.GONE
                    // 显示世界信息卡片
                    binding.cardWorldInfoBar.visibility = View.VISIBLE
                    // 显示信息卡片和按钮
                    binding.layoutInfoCards.visibility = View.VISIBLE
                    binding.btnSelectTarget.visibility = View.VISIBLE
                    
                    if (!validateWorld()) {
                        clearOutput()
                        switchToInitialState()
                        showCustomToast("世界读取失败！", isError = true)
                        return@runOnUiThread
                    }
                    
                    updateWorldInfoDisplay()
                    switchToWorldInfoState()
                }
            }.start()
        } else {
            readWorldNameFromDirectory(uri)
            detectPlatformFromDirectory(uri)
            loadWorldIconFromDirectory(uri)

            if (!validateWorld()) {
                clearOutput()
                switchToInitialState()
                showCustomToast("世界读取失败！", isError = true)
                return
            }

            updateWorldInfoDisplay()
            switchToWorldInfoState()
        }
    }

    private fun validateWorld(): Boolean {
        if (isArchiveMode) {
            val inputDir = File(filesDir, "input")
            val dbDir = File(inputDir, "db")
            val regionDir = File(inputDir, "region")
            return dbDir.exists() && dbDir.isDirectory || regionDir.exists() && regionDir.isDirectory
        } else {
            if (inputUri == null) return false
            try {
                val documentFile = DocumentFile.fromTreeUri(this, inputUri!!)
                if (documentFile?.isDirectory != true) return false
                
                val dbDir = documentFile.findFile("db")
                val regionDir = documentFile.findFile("region")
                return dbDir != null && dbDir.isDirectory || regionDir != null && regionDir.isDirectory
            } catch (e: Exception) {
                return false
            }
        }
    }

    private fun clearOutput() {
        val inputDir = File(filesDir, "input")
        if (inputDir.exists()) {
            deleteDir(inputDir)
        }
    }

    private fun deleteDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        dir.delete()
    }

    private fun switchToInitialState() {
        binding.cardInitialState.visibility = View.VISIBLE
        binding.cardWorldInfo.visibility = View.GONE
        binding.cardTargetVersion.visibility = View.GONE
        binding.btnSelectTarget.text = getString(R.string.select_target_format)
        binding.btnSelectTarget.setOnClickListener {
            showVersionPickerDialog()
        }
        selectedFormat = ""
        selectedFormatDisplayName = ""
        isWorldInfoVisible = false
    }

    private fun extractArchiveToInput(uri: Uri): Boolean {
        try {
            val inputDir = File(filesDir, "input")
            if (inputDir.exists()) {
                inputDir.deleteRecursively()
            }
            inputDir.mkdirs()

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                
                // 先统计条目总数
                var totalEntries = 0
                while (entry != null) {
                    totalEntries++
                    entry = zipInputStream.nextEntry
                }
                
                // 重新打开流进行解压
                contentResolver.openInputStream(uri)?.use { inputStream2 ->
                    val zipInputStream2 = ZipInputStream(inputStream2)
                    entry = zipInputStream2.nextEntry
                    var currentEntry = 0
                    
                    while (entry != null) {
                        val entryName = entry.name
                        val entryFile = File(inputDir, entryName)

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                            entryFile.setReadable(true)
                            entryFile.setWritable(true)
                        } else {
                            entryFile.parentFile?.mkdirs()
                            entryFile.outputStream().use { outputStream ->
                                zipInputStream2.copyTo(outputStream)
                            }
                            entryFile.setReadable(true)
                            entryFile.setWritable(true)
                        }

                        zipInputStream2.closeEntry()
                        entry = zipInputStream2.nextEntry
                        
                        // 更新进度
                        currentEntry++
                        val progress = (currentEntry * 100 / totalEntries).coerceAtMost(99)
                        runOnUiThread {
                            binding.progressExtract.progress = progress
                            binding.tvExtractProgress.text = getString(R.string.extracting_world) + " $progress%"
                        }
                    }

                    zipInputStream2.close()
                }
                
                zipInputStream.close()
            }
            
            // 完成进度
            runOnUiThread {
                binding.progressExtract.progress = 100
                binding.tvExtractProgress.text = getString(R.string.extracting_world) + " 100%"
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                showCustomToast("归档文件解压失败: ${e.message}", isError = true)
            }
            return false
        }
    }

    private fun detectPlatformFromDirectory(uri: Uri) {
        try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            documentFile?.let { dir ->
                // Check for /region folder (Java edition has region folder, Bedrock does not)
                val regionFolder = dir.findFile("region")
                if (regionFolder != null && regionFolder.isDirectory) {
                    worldPlatform = Platform.JAVA
                    return
                }

                // Default to Bedrock if no region folder found
                worldPlatform = Platform.BEDROCK
            }
        } catch (e: Exception) {
            worldPlatform = Platform.BEDROCK
        }
    }

    private fun detectPlatformFromArchive(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                var hasRegionFolder = false

                while (entry != null) {
                    val entryName = entry.name.lowercase()

                    // Check for /region folder (Java edition)
                    if (entryName.startsWith("region/") || entryName == "region") {
                        hasRegionFolder = true
                    }

                    entry = zipInputStream.nextEntry
                }

                zipInputStream.close()

                if (hasRegionFolder) {
                    worldPlatform = Platform.JAVA
                } else {
                    worldPlatform = Platform.BEDROCK
                }
            }
        } catch (e: Exception) {
            worldPlatform = Platform.BEDROCK
        }
    }

    private fun loadWorldIconFromDirectory(uri: Uri) {
        try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            documentFile?.let { dir ->
                // Try icon.png first
                var iconFile = dir.findFile("icon.png")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }

                // Try world_icon.jpeg
                iconFile = dir.findFile("world_icon.jpeg")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }

                // Try world_icon.jpg
                iconFile = dir.findFile("world_icon.jpg")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }

                // No icon found - keep default
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadWorldIconFromArchive(uri: Uri): Bitmap? {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry

                while (entry != null) {
                    val entryName = entry.name.lowercase()

                    // Check for icon files
                    if (entryName.contains("icon.png") ||
                        entryName.contains("world_icon.jpeg") ||
                        entryName.contains("world_icon.jpg")) {
                        val bitmap = BitmapFactory.decodeStream(zipInputStream)
                        zipInputStream.closeEntry()
                        zipInputStream.close()
                        return bitmap
                    }

                    entry = zipInputStream.nextEntry
                }

                zipInputStream.closeEntry()
                zipInputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun loadIconFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    binding.ivWorldIcon.setImageBitmap(bitmap)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun readWorldNameFromDirectory(uri: Uri?) {
        if (uri == null) {
            inputWorldName = ""
            return
        }

        try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            if (documentFile?.isDirectory == true) {
                val levelNameFile = documentFile.findFile("levelname.txt")
                if (levelNameFile != null && levelNameFile.isFile) {
                    contentResolver.openInputStream(levelNameFile.uri)?.use { inputStream ->
                        val content = inputStream.bufferedReader().use { it.readText() }
                        inputWorldName = content.trim()
                        return
                    }
                }

                val folderName = documentFile.name ?: "Unknown World"
                inputWorldName = folderName
            }
        } catch (e: Exception) {
            inputWorldName = ""
        }
    }

    private fun readWorldNameFromArchive(uri: Uri) {
        if (uri == null) {
            inputWorldName = ""
            return
        }

        try {
            val fileName = getFileNameFromUri(uri) ?: "Unknown Archive"
            inputWorldName = fileName

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry

                while (entry != null) {
                    // Check for levelname.txt in any directory
                    if (entry.name.lowercase().endsWith("levelname.txt")) {
                        val content = zipInputStream.bufferedReader().use { it.readText() }
                        inputWorldName = content.trim()
                        break
                    }
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.close()
            }
        } catch (e: Exception) {
            inputWorldName = ""
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }

        return fileName?.substringBeforeLast('.')
    }

    private fun updateWorldInfoDisplay() {
        // Update world name
        binding.tvWorldName.text = inputWorldName

        // Update platform card
        if (worldPlatform == Platform.JAVA) {
            binding.cardPlatform.strokeColor = resources.getColor(R.color.minecraft_green)
            binding.tvPlatform.text = getString(R.string.java_edition)
        } else {
            binding.cardPlatform.strokeColor = resources.getColor(R.color.minecraft_green)
            binding.tvPlatform.text = getString(R.string.bedrock_edition)
        }
    }

    private fun updateTargetVersionDisplay(versionDisplayName: String) {
        selectedFormatDisplayName = versionDisplayName
        selectedFormat = mapVersionToChunkerFormat(versionDisplayName)
        binding.tvTargetVersion.text = "转换为 $versionDisplayName"
        binding.cardTargetVersion.visibility = View.VISIBLE
        binding.btnSelectTarget.text = getString(R.string.start_conversion)
        
        binding.btnSelectTarget.setOnClickListener {
            startConversion()
        }
    }

    private fun switchToWorldInfoState() {
        binding.cardInitialState.visibility = View.GONE
        binding.cardWorldInfo.visibility = View.VISIBLE
        isWorldInfoVisible = true
        showCustomToast("世界已成功读取！")
    }

    private fun startConversion() {
        if (!validateInputs()) return

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val maxMemory = prefs.getInt("max_memory", 2048)
        val keepOriginalNbt = prefs.getBoolean("keep_original_nbt", false)

        val settings = ConversionSettings(
            inputUri = inputUri!!,
            inputWorldName = inputWorldName,
            targetFormat = selectedFormat,
            sourcePlatform = worldPlatform.name,
            maxMemory = maxMemory,
            keepOriginalNbt = keepOriginalNbt
        )

        val intent = Intent(this, ConversionActivity::class.java).apply {
            putExtra(ConversionActivity.EXTRA_SETTINGS, settings)
        }
        startActivity(intent)
    }

    private fun validateInputs(): Boolean {
        var valid = true

        if (inputUri == null) {
            showCustomToast(getString(R.string.error_no_input), isError = true)
            valid = false
        }

        if (inputWorldName.isEmpty()) {
            showCustomToast("无法获取世界名称", isError = true)
            valid = false
        }

        return valid
    }

    private fun checkPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (!Environment.isExternalStorageManager()) {
                    requestManageStoragePermission()
                }
            }
            else -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    private fun requestManageStoragePermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_required_title)
            .setMessage(R.string.storage_permission_required_message)
            .setPositiveButton(R.string.grant) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showCustomToast(getString(R.string.permission_required), isError = true)
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        const val EXTRA_SHOW_OUTPUT_MANAGER = "extra_show_output_manager"
        const val EXTRA_RESET_TO_INITIAL_STATE = "extra_reset_to_initial_state"
        private const val PREFS_NAME = "chunkoid_prefs"
    }

    enum class Platform {
        JAVA, BEDROCK
    }
}
