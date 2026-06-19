package com.dozenesstudio.chunkoid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dozenesstudio.chunkoid.utils.ToastUtils
import com.dozenesstudio.chunkoid.databinding.ActivityTerminalBinding
import com.dozenesstudio.chunkoid.termux.TermuxExecutor
import java.io.File

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private lateinit var termuxExecutor: TermuxExecutor
    private var isInitialized = false
    private var commandHistory = mutableListOf<String>()
    private var historyIndex = -1
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentScale = 1.0f
    private val minScale = 0.5f
    private val maxScale = 3.0f

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
        private const val CHUNKER_CLI_PATH = "chunker-cli-1.18.1.jar"
        private const val APP_FILES_DIR = "/data/user/0/com.dozenesstudio.chunkoid/files"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTerminalOutput.text = ""
        termuxExecutor = TermuxExecutor(this)

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(minScale, maxScale)
                binding.tvTerminalOutput.textSize = 14f * currentScale
                return true
            }
        })

        setupListeners()
        checkPermissions()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    private fun setupListeners() {
        binding.etCommandInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                executeCommand()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnSendCommand.setOnClickListener {
            executeCommand()
        }

        binding.tvExit.setOnClickListener {
            finish()
        }

        binding.tvClear.setOnClickListener {
            binding.tvTerminalOutput.text = ""
            appendPrompt()
        }

        binding.tvDebugJdk.setOnClickListener {
            executeAutoCommand("java -version")
        }

        binding.tvDebugChunker.setOnClickListener {
            executeAutoCommand("java -jar chunker-cli-1.18.1.jar")
        }

        binding.tvSymbolDash.setOnClickListener {
            insertSymbol("-")
        }

        binding.tvSymbolSlash.setOnClickListener {
            insertSymbol("/")
        }

        binding.etCommandInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                historyIndex = -1
            }
        })
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                PERMISSION_REQUEST_CODE
            )
        } else {
            initializeTerminal()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeTerminal()
            } else {
                ToastUtils.show(this, "需要存储权限才能使用终端", isError = true)
                finish()
            }
        }
    }

    private fun initializeTerminal() {
        Thread {
            try {
                termuxExecutor.initialize()
                runOnUiThread {
                    isInitialized = true
                    binding.imgTerminalStatus.setBackgroundResource(R.drawable.circle_green)
                    val grayColor = ContextCompat.getColor(this, R.color.minecraft_gray)
                    appendColoredOutput("- 欢迎使用Chunkoid控制台 v2.0\n", grayColor)
                    appendPrompt()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendOutput("\u274C 初始化失败: ${e.message}\n")
                    appendPrompt()
                }
            }
        }.start()
    }

    private fun executeAutoCommand(command: String) {
        appendOutput("\n$ ")
        appendOutput(command)
        appendOutput("\n")

        if (!isInitialized) {
            appendOutput("\u274C 终端未初始化，请等待...\n")
            appendPrompt()
            return
        }

        Thread {
            try {
                val output = when {
                    command.startsWith("java") -> executeJavaCommand(command)
                    else -> executeShellCommand(command)
                }

                runOnUiThread {
                    appendOutput(output)
                    appendPrompt()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendOutput("\u274C 错误: ${e.message}\n")
                    appendPrompt()
                }
            }
        }.start()
    }

    private fun executeCommand() {
        val command = binding.etCommandInput.text.toString().trim()
        if (command.isEmpty()) return

        binding.etCommandInput.text.clear()
        commandHistory.add(command)
        historyIndex = -1

        appendOutput("\n$ ")
        appendOutput(command)
        appendOutput("\n")

        if (!isInitialized) {
            appendOutput("\u274C 终端未初始化，请等待...\n")
            appendPrompt()
            return
        }

        Thread {
            try {
                val output = when {
                    command.equals("ls", ignoreCase = true) -> executeLs()
                    command.equals("pwd", ignoreCase = true) -> executePwd()
                    command.startsWith("java") -> executeJavaCommand(command)
                    else -> executeShellCommand(command)
                }

                runOnUiThread {
                    appendOutput(output)
                    appendPrompt()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendOutput("\u274C 错误: ${e.message}\n")
                    appendPrompt()
                }
            }
        }.start()
    }

    private fun executeLs(): String {
        val files = File(APP_FILES_DIR).listFiles()
        return if (files != null) {
            files.joinToString("\n") { it.name } + "\n"
        } else {
            "无法列出目录\n"
        }
    }

    private fun executePwd(): String {
        return APP_FILES_DIR + "\n"
    }

    private fun executeJavaCommand(command: String): String {
        val chunkerCliPath = "$APP_FILES_DIR/$CHUNKER_CLI_PATH"

        var modifiedCommand = command
        if (command.contains("chunker-cli-1.18.1.jar")) {
            modifiedCommand = command.replace("chunker-cli-1.18.1.jar", chunkerCliPath)
        }

        val javaArgs = modifiedCommand.substringAfter("java ").trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }

        return try {
            val proc = termuxExecutor.executeJava(*javaArgs.toTypedArray())
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                output + "\nExit code: $exitCode"
            } else {
                output
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun executeShellCommand(command: String): String {
        return termuxExecutor.executeCommand(command, APP_FILES_DIR)
    }

    private fun appendOutput(text: String) {
        appendColoredOutput(text, ContextCompat.getColor(this, R.color.minecraft_green))
    }

    private fun appendColoredOutput(text: String, color: Int) {
        val spannable = SpannableString(text)
        spannable.setSpan(ForegroundColorSpan(color), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvTerminalOutput.append(spannable)
    }

    private fun appendPrompt() {
        appendOutput("\n$ ")
        scrollToBottom()
    }

    private fun scrollToBottom() {
        binding.tvTerminalOutput.post {
            binding.tvTerminalOutput.parent.requestLayout()
        }
    }

    private fun insertSymbol(symbol: String) {
        val editText = binding.etCommandInput
        val start = editText.selectionStart
        val end = editText.selectionEnd
        editText.text?.replace(start, end, symbol)
        editText.setSelection(start + symbol.length)
        editText.requestFocus()
    }

    override fun onBackPressed() {
        if (commandHistory.isNotEmpty()) {
            if (historyIndex == -1) {
                historyIndex = commandHistory.size - 1
            } else if (historyIndex > 0) {
                historyIndex--
            }
            binding.etCommandInput.setText(commandHistory[historyIndex])
        } else {
            super.onBackPressed()
        }
    }
}