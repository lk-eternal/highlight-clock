package com.lk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 开机自启动管理器
 * 支持 Windows 系统
 */
public class StartupManager {
    
    private static final String APP_NAME = "LKClock";
    
    /**
     * 检查是否已设置开机自启动
     */
    public static boolean isStartupEnabled() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return isWindowsStartupEnabled();
        }
        return false;
    }
    
    /**
     * 启用开机自启动
     */
    public static boolean enableStartup() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return enableWindowsStartup();
        }
        return false;
    }
    
    /**
     * 禁用开机自启动
     */
    public static boolean disableStartup() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return disableWindowsStartup();
        }
        return false;
    }
    
    // ========== Windows 实现 ==========
    
    private static File getWindowsStartupFolder() {
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            return new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup");
        }
        return null;
    }
    
    private static File getStartupScript() {
        File startupFolder = getWindowsStartupFolder();
        if (startupFolder != null) {
            return new File(startupFolder, APP_NAME + ".vbs");
        }
        return null;
    }
    
    private static boolean isWindowsStartupEnabled() {
        File script = getStartupScript();
        return script != null && script.exists();
    }
    
    private static boolean enableWindowsStartup() {
        try {
            File startupFolder = getWindowsStartupFolder();
            if (startupFolder == null || !startupFolder.exists()) {
                return false;
            }
            
            // 获取当前 JAR 文件路径
            String jarPath = getJarPath();
            if (jarPath == null) {
                return false;
            }
            
            // 获取 Java 路径
            String javaHome = System.getProperty("java.home");
            String javawPath = javaHome + "\\bin\\javaw.exe";
            
            // 创建 VBS 脚本（静默启动）
            File script = getStartupScript();
            if (script == null) {
                return false;
            }
            
            String vbsContent = String.format(
                "Set WshShell = CreateObject(\"WScript.Shell\")\n" +
                "WshShell.Run \"\"\"%s\"\" -jar \"\"%s\"\"\", 0, False\n",
                javawPath, jarPath
            );
            
            try (FileWriter writer = new FileWriter(script)) {
                writer.write(vbsContent);
            }
            
            return true;
        } catch (IOException e) {
            System.err.println("无法创建启动脚本: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean disableWindowsStartup() {
        File script = getStartupScript();
        if (script != null && script.exists()) {
            return script.delete();
        }
        return true;
    }
    
    private static String getJarPath() {
        try {
            // 获取当前运行的 JAR 文件路径
            String path = StartupManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            
            // Windows 路径处理
            if (path.startsWith("/") && path.contains(":")) {
                path = path.substring(1);
            }
            
            // 确保是 JAR 文件
            if (path.endsWith(".jar")) {
                return path;
            }
            
            // 如果是开发环境（classes 目录），尝试找到打包的 JAR
            File classesDir = new File(path);
            File projectDir = classesDir.getParentFile();
            if (projectDir != null) {
                File jarDir = new File(projectDir.getParentFile(), "out/artifacts/clock_jar");
                File jarFile = new File(jarDir, "clock.jar");
                if (jarFile.exists()) {
                    return jarFile.getAbsolutePath();
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

