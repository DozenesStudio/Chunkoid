package com.dozenesstudio.chunkoid.termux;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class JavaCommandExecutor {
    private static final String TAG = "JavaCommandExecutor";

    private final Context context;
    private final File rootfsDir;
    private int maxMemoryMB = 2048;

    public JavaCommandExecutor(Context context) {
        this.context = context;
        this.rootfsDir = RootFSInstaller.getRootFSDir(context);
    }

    public void setMaxMemory(int mb) {
        this.maxMemoryMB = mb;
        Log.d(TAG, "Max memory set to: " + mb + " MB");
    }

    public Process executeJava(String... javaArgs) throws IOException {
        File javaBin = findJavaBinary();
        if (!javaBin.exists()) {
            throw new IOException("Java not found in rootfs: " + javaBin.getAbsolutePath());
        }

        String[] cmd = buildCommand(javaBin, javaArgs);
        Log.d(TAG, "Executing command: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();
        setupEnvironment(env);

        pb.directory(context.getFilesDir());
        pb.redirectErrorStream(true);

        try {
            return pb.start();
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                Log.d(TAG, "Direct execution failed, trying with linker64");
                return executeJavaWithLinker(javaBin, javaArgs);
            }
            throw e;
        }
    }

    private Process executeJavaWithLinker(File javaBin, String... javaArgs) throws IOException {
        String linker = Build.SUPPORTED_64_BIT_ABIS.length > 0 ? "/system/bin/linker64" : "/system/bin/linker";
        String[] cmd = new String[javaArgs.length + 2];
        cmd[0] = linker;
        cmd[1] = javaBin.getAbsolutePath();
        System.arraycopy(javaArgs, 0, cmd, 2, javaArgs.length);

        Log.d(TAG, "Linker command: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();
        setupEnvironment(env);

        pb.directory(context.getFilesDir());
        pb.redirectErrorStream(true);

        return pb.start();
    }

    private void setupEnvironment(Map<String, String> env) {
        env.put("JAVA_HOME", getJavaHome());
        env.put("PATH",
            rootfsDir + "/bin:" +
            rootfsDir + "/sbin:" +
            rootfsDir + "/usr/bin:" +
            rootfsDir + "/usr/sbin:" +
            "/system/bin:" +
            "/system/sbin:" +
            "/vendor/bin");
        env.put("LD_LIBRARY_PATH",
            rootfsDir + "/lib:" +
            rootfsDir + "/lib/aarch64-linux-gnu:" +
            rootfsDir + "/usr/lib:" +
            rootfsDir + "/usr/lib/aarch64-linux-gnu:" +
            rootfsDir + "/lib/jvm/java-17-openjdk/lib:" +
            "/system/lib64:" +
            "/system/lib:" +
            "/vendor/lib64:" +
            "/vendor/lib");
        env.remove("LD_PRELOAD");
        env.put("ANDROID_ROOT", "/system");
        env.put("ANDROID_DATA", "/data");
    }

    public Process runJar(File jarFile, String... arguments) throws IOException {
        String[] args = new String[arguments.length + 4];
        args[0] = "-Xms256m";                    // 初始堆大小
        args[1] = "-Xmx" + maxMemoryMB + "m";   // 最大堆大小（从设置读取）
        args[2] = "-jar";
        args[3] = jarFile.getAbsolutePath();
        System.arraycopy(arguments, 0, args, 4, arguments.length);
        return executeJava(args);
    }

    public boolean checkJava() {
        try {
            Log.d(TAG, "=== Java Environment Check ===");
            Log.d(TAG, "RootFS path: " + rootfsDir.getAbsolutePath());
            Log.d(TAG, "RootFS exists: " + rootfsDir.exists());
            
            File javaBin = findJavaBinary();
            Log.d(TAG, "Java binary path: " + javaBin.getAbsolutePath());
            Log.d(TAG, "Java binary exists: " + javaBin.exists());
            if (javaBin.exists()) {
                Log.d(TAG, "Java binary canExecute: " + javaBin.canExecute());
                Log.d(TAG, "Java binary length: " + javaBin.length());
            }
            
            File javaHome = new File(getJavaHome());
            Log.d(TAG, "JAVA_HOME: " + getJavaHome());
            Log.d(TAG, "JAVA_HOME exists: " + javaHome.exists());
            
            Log.d(TAG, "=== Testing Java Execution ===");
            Process proc = executeJava("-version");
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    Log.d(TAG, "stdout: " + line);
                }
            }
            
            int exitCode = proc.waitFor();
            String outputStr = output.toString();
            Log.d(TAG, "Java check exit code: " + exitCode);
            Log.d(TAG, "Java output length: " + outputStr.length());
            Log.d(TAG, "Java output:\n" + outputStr);
            
            // 直接使用 exitCode 判断（Java -version 成功时返回 0）
            // 同时检查输出不为空作为额外验证
            boolean result = exitCode == 0 && outputStr.length() > 0;
            Log.d(TAG, "CheckJava result: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Java check failed", e);
            return false;
        }
    }

    private File findJavaBinary() {
        // 优先尝试当前目录结构（rootfs/bin/java）
        File javaBin = new File(rootfsDir, "bin/java");
        if (javaBin.exists() && javaBin.length() > 0) {
            return javaBin;
        }
        
        // 备选路径（原始 Termux 结构）
        javaBin = new File(rootfsDir, "usr/lib/jvm/java-17-openjdk/bin/java");
        if (javaBin.exists() && javaBin.length() > 0) {
            return javaBin;
        }
        
        // 备选路径
        javaBin = new File(rootfsDir, "usr/lib/jvm/openjdk-17/bin/java");
        if (javaBin.exists() && javaBin.length() > 0) {
            return javaBin;
        }
        
        // 最后尝试标准路径
        javaBin = new File(rootfsDir, "usr/bin/java");
        return javaBin;
    }

    private String getJavaHome() {
        // 当前目录结构 - 查找 lib/jvm 目录
        File javaHome = new File(rootfsDir, "lib/jvm/java-17-openjdk");
        if (javaHome.exists()) {
            return javaHome.getAbsolutePath();
        }
        
        // 备选路径（原始 Termux 结构）
        javaHome = new File(rootfsDir, "usr/lib/jvm/java-17-openjdk");
        if (javaHome.exists()) {
            return javaHome.getAbsolutePath();
        }
        
        // 备选路径
        javaHome = new File(rootfsDir, "usr/lib/jvm/openjdk-17");
        if (javaHome.exists()) {
            return javaHome.getAbsolutePath();
        }
        
        // 返回空或默认路径
        return rootfsDir.getAbsolutePath();
    }

    private String[] buildCommand(File target, String... args) {
        String[] result = new String[args.length + 1];
        result[0] = target.getAbsolutePath();
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }

    public File getRootfsDir() {
        return rootfsDir;
    }

    public String getJavaPath() {
        return findJavaBinary().getAbsolutePath();
    }

    public String executeCommand(String command, String workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", command);
            
            Map<String, String> env = pb.environment();
            env.put("PATH", 
                rootfsDir + "/bin:" + 
                rootfsDir + "/sbin:" +
                rootfsDir + "/usr/bin:" + 
                rootfsDir + "/usr/sbin:" +
                "/system/bin:" + 
                "/system/sbin:" +
                "/vendor/bin");
            
            env.put("LD_LIBRARY_PATH", 
                rootfsDir + "/lib:" + 
                rootfsDir + "/lib/aarch64-linux-gnu:" +
                rootfsDir + "/usr/lib:" + 
                rootfsDir + "/usr/lib/aarch64-linux-gnu:" +
                "/system/lib64:" +
                "/system/lib:" +
                "/vendor/lib64:" +
                "/vendor/lib");
            
            env.remove("LD_PRELOAD");
            env.put("JAVA_HOME", getJavaHome());
            
            pb.directory(new File(workingDir));
            pb.redirectErrorStream(true);
            
            Process proc = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                output.append("\nExit code: ").append(exitCode);
            }
            
            return output.toString();
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed", e);
            return "Error: " + e.getMessage();
        }
    }

    public String executeCommandWithLinker(String[] command, String workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            
            Map<String, String> env = pb.environment();
            env.put("PATH", 
                rootfsDir + "/bin:" + 
                rootfsDir + "/sbin:" +
                rootfsDir + "/usr/bin:" + 
                rootfsDir + "/usr/sbin:" +
                "/system/bin:" + 
                "/system/sbin:" +
                "/vendor/bin");
            
            env.put("LD_LIBRARY_PATH", 
                rootfsDir + "/lib:" + 
                rootfsDir + "/lib/aarch64-linux-gnu:" +
                rootfsDir + "/usr/lib:" + 
                rootfsDir + "/usr/lib/aarch64-linux-gnu:" +
                rootfsDir + "/lib/jvm/java-17-openjdk/lib:" +
                "/system/lib64:" +
                "/system/lib:" +
                "/vendor/lib64:" +
                "/vendor/lib");
            
            env.remove("LD_PRELOAD");
            env.put("JAVA_HOME", getJavaHome());
            env.put("ANDROID_ROOT", "/system");
            env.put("ANDROID_DATA", "/data");
            
            pb.directory(new File(workingDir));
            pb.redirectErrorStream(true);
            
            Process proc = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                output.append("\nExit code: ").append(exitCode);
            }
            
            return output.toString();
        } catch (Exception e) {
            Log.e(TAG, "Linker command execution failed", e);
            return "Error: " + e.getMessage();
        }
    }
}
