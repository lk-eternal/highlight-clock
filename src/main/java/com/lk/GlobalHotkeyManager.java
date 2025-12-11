package com.lk;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局快捷键管理器
 * 使用 JNativeHook 库实现系统级全局快捷键
 */
public class GlobalHotkeyManager implements NativeKeyListener {
    
    private final AnalogClock clockFrame;
    private boolean altPressed = false;
    
    public GlobalHotkeyManager(AnalogClock clockFrame) {
        this.clockFrame = clockFrame;
    }
    
    /**
     * 初始化全局快捷键监听
     */
    public void start() {
        try {
            // 禁用 JNativeHook 的日志输出
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);
            
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
        } catch (NativeHookException e) {
            System.err.println("无法注册全局快捷键: " + e.getMessage());
        }
    }
    
    /**
     * 停止全局快捷键监听
     */
    public void stop() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            System.err.println("无法注销全局快捷键: " + e.getMessage());
        }
    }
    
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        // 检测 Alt 键
        if (e.getKeyCode() == NativeKeyEvent.VC_ALT) {
            altPressed = true;
        }
        
        // Alt + C: 显示/隐藏时钟
        if (altPressed && e.getKeyCode() == NativeKeyEvent.VC_C) {
            SwingUtilities.invokeLater(() -> {
                clockFrame.setVisible(!clockFrame.isVisible());
            });
        }
        
        // Alt + T: 切换置顶
        if (altPressed && e.getKeyCode() == NativeKeyEvent.VC_T) {
            SwingUtilities.invokeLater(() -> {
                boolean newState = !clockFrame.isAlwaysOnTop();
                clockFrame.setAlwaysOnTop(newState);
                clockFrame.showToast(newState ? "已置顶" : "已取消置顶");
            });
        }
    }
    
    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        if (e.getKeyCode() == NativeKeyEvent.VC_ALT) {
            altPressed = false;
        }
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // 不处理
    }
}

