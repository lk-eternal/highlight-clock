package com.lk;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 时间范围监控器，负责检测进入/退出高亮区域并触发相应动作
 */
public class TimeRangeMonitor {
    
    private Map<String, Boolean> rangeStates = new HashMap<>();
    private Map<String, Long> lastIntervalTriggerTime = new HashMap<>();
    private List<AnalogClock.HighlightSetting> highlightAreas;
    
    // 防重复弹窗：记录当前正在显示的弹窗（按高亮区域key + 触发类型）
    private Set<String> activeNotifications = new HashSet<>();
    
    // 锁屏检测：上次检测到用户活动的时间
    private long lastUserActivityTime = System.currentTimeMillis();
    private Point lastMousePosition = MouseInfo.getPointerInfo().getLocation();
    
    public TimeRangeMonitor(List<AnalogClock.HighlightSetting> highlightAreas) {
        this.highlightAreas = highlightAreas;
        initializeStates();
    }
    
    private void initializeStates() {
        LocalTime now = LocalTime.now();
        int currentMinutes = now.getHour() * 60 + now.getMinute();
        
        for (AnalogClock.HighlightSetting setting : highlightAreas) {
            String key = getSettingKey(setting);
            int startMinutes = setting.getStartHour() * 60 + setting.getStartMinute();
            int endMinutes = setting.getEndHour() * 60 + setting.getEndMinute();
            
            boolean inRange = isTimeInRange(currentMinutes, startMinutes, endMinutes);
            rangeStates.put(key, inRange);
            
            // 初始化时，如果在范围内，设置最后触发时间为当前时间，避免立即触发间隔
            if (inRange) {
                lastIntervalTriggerTime.put(key, System.currentTimeMillis());
            }
        }
    }
    
    public void checkAndTrigger() {
        // 检测用户是否活跃（通过鼠标位置变化判断）
        if (!isUserActive()) {
            return; // 用户不活跃（可能锁屏或离开），跳过提醒
        }
        
        LocalTime now = LocalTime.now();
        int currentMinutes = now.getHour() * 60 + now.getMinute();
        long currentTimeMillis = System.currentTimeMillis();
        
        for (AnalogClock.HighlightSetting setting : highlightAreas) {
            String key = getSettingKey(setting);
            int startMinutes = setting.getStartHour() * 60 + setting.getStartMinute();
            int endMinutes = setting.getEndHour() * 60 + setting.getEndMinute();
            
            boolean wasInRange = rangeStates.getOrDefault(key, false);
            boolean isInRange = isTimeInRange(currentMinutes, startMinutes, endMinutes);
            
            // 状态变化：进入区域
            if (!wasInRange && isInRange) {
                triggerAction(setting, setting.getEnter(), "enter");
                rangeStates.put(key, true);
                // 进入时重置间隔触发计时
                lastIntervalTriggerTime.put(key, currentTimeMillis);
            }
            // 状态变化：退出区域
            else if (wasInRange && !isInRange) {
                triggerAction(setting, setting.getExit(), "exit");
                rangeStates.put(key, false);
                lastIntervalTriggerTime.remove(key);
            }
            // 持续在区域内：检查间隔触发
            else if (isInRange) {
                ClockConfig.TriggerConfig intervalConfig = setting.getInterval();
                if (intervalConfig != null && intervalConfig.intervalMinutes > 0 && !"none".equals(intervalConfig.action)) {
                    long lastTrigger = lastIntervalTriggerTime.getOrDefault(key, 0L);
                    long intervalMillis = intervalConfig.intervalMinutes * 60 * 1000L;
                    
                    if (currentTimeMillis - lastTrigger >= intervalMillis) {
                        triggerAction(setting, intervalConfig, "interval");
                        lastIntervalTriggerTime.put(key, currentTimeMillis);
                    }
                }
            }
        }
    }
    
    private void triggerAction(AnalogClock.HighlightSetting setting, ClockConfig.TriggerConfig config, String triggerType) {
        if (config == null || config.action == null || "none".equals(config.action)) {
            return;
        }
        
        // 防重复弹窗：生成唯一标识
        String notificationKey = getSettingKey(setting) + "_" + triggerType;
        
        // 如果该提醒已经在显示中，跳过
        if (activeNotifications.contains(notificationKey)) {
            return;
        }
        
        String message = config.text;
        if (message == null || message.trim().isEmpty()) {
            // 如果没有自定义文案，使用默认文案
            String label = setting.getLabel().isEmpty() ? "时间段" : setting.getLabel();
            switch (triggerType) {
                case "enter":
                    message = "进入" + label;
                    break;
                case "exit":
                    message = "退出" + label;
                    break;
                case "interval":
                    message = label + "提醒";
                    break;
                default:
                    message = "时间提醒";
            }
        }
        
        // 播放提示音（如果启用）
        if (config.playSound) {
            playNotificationSound();
        }
        
        switch (config.action) {
            case "dialog":
                showDialogNotification(message, setting.getHighlightColor(), setting.getLabelColor(), notificationKey);
                break;
            case "fullscreen":
                showFullscreenNotification(message, setting.getHighlightColor(), setting.getLabelColor(), notificationKey);
                break;
            case "lock":
                lockScreen();
                break;
        }
    }
    
    /**
     * 播放提示音
     */
    private void playNotificationSound() {
        new Thread(() -> {
            try {
                // 生成简单的提示音（双音调）
                float sampleRate = 8000;
                int duration1 = 150; // 第一个音持续时间 (ms)
                int duration2 = 200; // 第二个音持续时间 (ms)
                int freq1 = 880;  // A5
                int freq2 = 1047; // C6
                
                byte[] buf1 = generateTone(freq1, duration1, sampleRate);
                byte[] buf2 = generateTone(freq2, duration2, sampleRate);
                
                // 合并两个音调
                byte[] combined = new byte[buf1.length + buf2.length];
                System.arraycopy(buf1, 0, combined, 0, buf1.length);
                System.arraycopy(buf2, 0, combined, buf1.length, buf2.length);
                
                AudioFormat af = new AudioFormat(sampleRate, 8, 1, true, false);
                SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(combined, 0, combined.length);
                sdl.drain();
                sdl.close();
            } catch (Exception e) {
                // 如果无法播放自定义音频，使用系统蜂鸣
                Toolkit.getDefaultToolkit().beep();
            }
        }).start();
    }
    
    private byte[] generateTone(int freq, int durationMs, float sampleRate) {
        int samples = (int) (durationMs * sampleRate / 1000);
        byte[] buf = new byte[samples];
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i * freq / sampleRate;
            buf[i] = (byte) (Math.sin(angle) * 100);
        }
        return buf;
    }
    
    private void showDialogNotification(String message, Color bgColor, Color textColor, String notificationKey) {
        // 标记该提醒正在显示
        if (notificationKey != null) {
            activeNotifications.add(notificationKey);
        }
        
        SwingUtilities.invokeLater(() -> {
            // 使用 JDialog 并设置模态排除，使其不受其他模态对话框的阻塞
            JDialog dialog = new JDialog((Frame)null, "时间提醒", false);
            dialog.setUndecorated(true);
            dialog.setAlwaysOnTop(true);
            // 关键：设置模态排除类型，使窗口不受任何模态对话框的阻塞
            dialog.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
            
            // 弹窗关闭时移除标记
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    if (notificationKey != null) {
                        activeNotifications.remove(notificationKey);
                    }
                }
            });
            
            JPanel panel = new JPanel(new BorderLayout(30, 30));
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bgColor.darker(), 4),
                BorderFactory.createEmptyBorder(90, 150, 90, 150)
            ));
            panel.setBackground(bgColor);
            
            JLabel label = new JLabel(message);
            label.setFont(new Font("Microsoft YaHei", Font.BOLD, 48));
            label.setForeground(textColor);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(label, BorderLayout.CENTER);
            
            JButton okButton = new JButton("确定");
            okButton.setFont(new Font("Microsoft YaHei", Font.PLAIN, 24));
            okButton.setPreferredSize(new Dimension(140, 50));
            okButton.addActionListener(e -> dialog.dispose());
            
            JPanel buttonPanel = new JPanel();
            buttonPanel.setOpaque(false);
            buttonPanel.add(okButton);
            panel.add(buttonPanel, BorderLayout.SOUTH);
            
            dialog.add(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
            
            // 确保窗口显示在最前面并获取焦点
            dialog.toFront();
            okButton.requestFocusInWindow();
        });
    }
    
    private void showFullscreenNotification(String message, Color bgColor, Color textColor, String notificationKey) {
        // 标记该提醒正在显示
        if (notificationKey != null) {
            activeNotifications.add(notificationKey);
        }
        
        SwingUtilities.invokeLater(() -> {
            // 使用 JDialog 并设置模态排除，使其不受其他模态对话框的阻塞
            JDialog dialog = new JDialog((Frame)null, "全屏提醒", false);
            dialog.setUndecorated(true);
            dialog.setAlwaysOnTop(true);
            // 关键：设置模态排除类型，使窗口不受任何模态对话框的阻塞
            dialog.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
            
            // 弹窗关闭时移除标记
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    if (notificationKey != null) {
                        activeNotifications.remove(notificationKey);
                    }
                }
            });
            
            // 获取屏幕尺寸
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            dialog.setBounds(0, 0, screenSize.width, screenSize.height);
            
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(bgColor);
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            JLabel label = new JLabel(message);
            label.setFont(new Font("Microsoft YaHei", Font.BOLD, 120));
            label.setForeground(textColor);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            
            panel.add(label);
            
            // 点击任意位置关闭
            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    dialog.dispose();
                }
            });
            
            // 添加键盘监听器，按空格、回车或ESC关闭
            dialog.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE || 
                        e.getKeyCode() == KeyEvent.VK_ENTER ||
                        e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        dialog.dispose();
                    }
                }
            });
            
            dialog.add(panel);
            dialog.setVisible(true);
            
            // 确保窗口显示在最前面并获取键盘焦点
            dialog.toFront();
            dialog.requestFocus();
        });
    }
    
    private void lockScreen() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation");
                pb.start();
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("/System/Library/CoreServices/Menu Extras/User.menu/Contents/Resources/CGSession", "-suspend");
                pb.start();
            } else if (os.contains("nux")) {
                pb = new ProcessBuilder("gnome-screensaver-command", "-l");
                pb.start();
            }
        } catch (Exception e) {
            System.err.println("无法锁定屏幕: " + e.getMessage());
        }
    }
    
    private boolean isTimeInRange(int currentMinutes, int startMinutes, int endMinutes) {
        if (startMinutes <= endMinutes) {
            return currentMinutes >= startMinutes && currentMinutes < endMinutes;
        } else {
            return currentMinutes >= startMinutes || currentMinutes < endMinutes;
        }
    }
    
    private String getSettingKey(AnalogClock.HighlightSetting setting) {
        return String.format("%02d:%02d-%02d:%02d", 
            setting.getStartHour(), setting.getStartMinute(),
            setting.getEndHour(), setting.getEndMinute());
    }
    
    public void updateHighlightAreas(List<AnalogClock.HighlightSetting> newAreas) {
        this.highlightAreas = newAreas;
        initializeStates();
    }
    
    /**
     * 检测用户是否活跃（通过鼠标位置变化判断）
     * 如果鼠标位置在5分钟内没有变化，认为用户不活跃（可能锁屏或离开）
     */
    private boolean isUserActive() {
        try {
            Point currentMousePosition = MouseInfo.getPointerInfo().getLocation();
            long currentTime = System.currentTimeMillis();
            
            // 如果鼠标位置发生变化，更新最后活动时间
            if (!currentMousePosition.equals(lastMousePosition)) {
                lastMousePosition = currentMousePosition;
                lastUserActivityTime = currentTime;
            }
            
            // 如果超过5分钟没有鼠标移动，认为用户不活跃
            long inactiveThreshold = 5 * 60 * 1000; // 5分钟
            return (currentTime - lastUserActivityTime) < inactiveThreshold;
        } catch (Exception e) {
            // 如果无法获取鼠标位置，默认认为用户活跃
            return true;
        }
    }

    // 预览方法（供外部调用）- 预览不受防重复限制
    public void previewDialogNotification(String message, Color bgColor, Color textColor) {
        showDialogNotification(message, bgColor, textColor, null);
    }

    public void previewFullscreenNotification(String message, Color bgColor, Color textColor) {
        showFullscreenNotification(message, bgColor, textColor, null);
    }

    public void previewLockScreen() {
        lockScreen();
    }
    
    public void previewSound() {
        playNotificationSound();
    }
}
