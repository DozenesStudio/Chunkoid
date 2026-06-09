package com.dozenesstudio.chunkoid

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dozenesstudio.chunkoid.termux.RootFSInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private var progressText: TextView? = null
    private var isFirstLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Check if RootFS is already ready
        if (RootFSInstaller.isRootFSReady(this)) {
            // Not first launch, skip initialization
            proceedToMainActivity()
            return
        }

        // First launch - show progress
        isFirstLaunch = true
        progressText = findViewById(R.id.progress_text)
        progressText?.visibility = TextView.VISIBLE
        progressText?.text = getString(R.string.initializing)

        // Start initialization immediately
        performInitialization()
    }

    private fun performInitialization() {
        CoroutineScope(Dispatchers.IO).launch {
            val success = initializeFiles()
            withContext(Dispatchers.Main) {
                if (success) {
                    proceedToMainActivity()
                } else {
                    showRetryDialog()
                }
            }
        }
    }

    private suspend fun initializeFiles(): Boolean {
        return withContext(Dispatchers.IO) {
            var success = false

            // Copy rootfs
            success = copyRootFS()
            if (!success) {
                return@withContext false
            }

            // Copy chunker-cli.jar
            success = copyChunkerJar()
            if (!success) {
                return@withContext false
            }

            return@withContext true
        }
    }

    private fun copyRootFS(): Boolean {
        return RootFSInstaller.extractRootFS(this, object : RootFSInstaller.ProgressCallback {
            override fun onProgress(progress: Int) {
                updateProgress(progress)
            }

            override fun onComplete(success: Boolean) {
                // Not used here, we check the return value
            }
        })
    }

    private fun copyChunkerJar(): Boolean {
        val chunkerJar = getFileStreamPath("chunker-cli-1.15.0.jar")
        if (chunkerJar.exists()) {
            updateProgress(100)
            return true
        }

        try {
            assets.open("chunker-cli-1.15.0.jar").use { input ->
                chunkerJar.outputStream().use { output ->
                    val totalSize = input.available()
                    var copied = 0
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copied += bytesRead
                        val progress = ((copied.toDouble() / totalSize) * 50 + 50).toInt()
                        updateProgress(progress)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun updateProgress(progress: Int) {
        Handler(Looper.getMainLooper()).post {
            progressText?.text = getString(R.string.initializing_copying, progress)
        }
    }

    private fun showRetryDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.initializing_failed)
            .setPositiveButton(R.string.retry) { _, _ ->
                progressText?.text = getString(R.string.initializing)
                performInitialization()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun proceedToMainActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 1000)
    }
}