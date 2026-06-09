package com.dozenesstudio.chunkoid.termux;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class TermuxExecutor {
    private static final String TAG = "TermuxExecutor";

    private final Context context;
    private final RootFSInstaller rootFSInstaller;
    private final JavaCommandExecutor javaExecutor;
    private boolean initialized = false;

    public TermuxExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.rootFSInstaller = new RootFSInstaller();
        this.javaExecutor = new JavaCommandExecutor(this.context);
    }

    public boolean initialize() {
        return initialize(null);
    }

    public boolean initialize(RootFSInstaller.ProgressCallback callback) {
        if (initialized) {
            return true;
        }

        Log.d(TAG, "Initializing Termux environment...");

        // 1. 解压RootFS
        if (!RootFSInstaller.extractRootFS(context, callback)) {
            Log.e(TAG, "Failed to extract RootFS");
            return false;
        }

        // 2. 验证Java环境
        if (!javaExecutor.checkJava()) {
            Log.e(TAG, "Java environment validation failed");
            return false;
        }

        initialized = true;
        Log.d(TAG, "Termux environment initialized successfully");
        return true;
    }

    public Process executeJava(String... args) throws IOException {
        if (!initialized && !initialize()) {
            throw new IOException("Termux environment not initialized");
        }
        return javaExecutor.executeJava(args);
    }

    public Process runJar(File jarFile, String... arguments) throws IOException {
        if (!initialized && !initialize()) {
            throw new IOException("Termux environment not initialized");
        }
        return javaExecutor.runJar(jarFile, arguments);
    }

    public String executeJavaAndGetOutput(String... args) throws IOException, InterruptedException {
        Process proc = executeJava(args);
        StringBuilder output = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        proc.waitFor();
        return output.toString();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setMaxMemory(int mb) {
        javaExecutor.setMaxMemory(mb);
    }

    public void reset() {
        initialized = false;
    }

    public String getRootFsDirectory() {
        return RootFSInstaller.getRootFSDir(context).getAbsolutePath();
    }

    public String getJavaPath() {
        return javaExecutor.getJavaPath();
    }

    public String executeCommand(String command, String workingDir) {
        if (!initialized && !initialize()) {
            return "Error: Termux environment not initialized";
        }
        return javaExecutor.executeCommand(command, workingDir);
    }

    public String executeCommandWithLinker(String[] command, String workingDir) {
        if (!initialized && !initialize()) {
            return "Error: Termux environment not initialized";
        }
        return javaExecutor.executeCommandWithLinker(command, workingDir);
    }
}