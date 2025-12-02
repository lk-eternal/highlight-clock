package com.lk;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间范围监控器，负责检测进入/退出高亮区域并触发相应动作
 */
public class TimeRangeMonitor {
    
    private Map<String, Boolean> rangeStates = new HashMap<>();
    private Map<String, Long> lastIntervalTriggerTime = new HashMap<>();
    private List<AnalogClock.HighlightSetting> highlightAreas;
    
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
        
        switch (config.action) {
            case "dialog":
                showDialogNotification(message, setting.getHighlightColor(), setting.getLabelColor());
                break;
            case "fullscreen":
                showFullscreenNotification(message, setting.getHighlightColor(), setting.getLabelColor());
                break;
            case "lock":
                lockScreen();
                break;
        }
    }
    
    private void showDialogNotification(String message, Color bgColor, Color textColor) {
        SwingUtilities.invokeLater(() -> {
            // 使用 JDialog 并设置模态排除，使其不受其他模态对话框的阻塞
            JDialog dialog = new JDialog((Frame)null, "时间提醒", false);
            dialog.setUndecorated(true);
            dialog.setAlwaysOnTop(true);
            // 关键：设置模态排除类型，使窗口不受任何模态对话框的阻塞
            dialog.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
            
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
    
    private void showFullscreenNotification(String message, Color bgColor, Color textColor) {
        SwingUtilities.invokeLater(() -> {
            // 使用 JDialog 并设置模态排除，使其不受其他模态对话框的阻塞
            JDialog dialog = new JDialog((Frame)null, "全屏提醒", false);
            dialog.setUndecorated(true);
            dialog.setAlwaysOnTop(true);
            // 关键：设置模态排除类型，使窗口不受任何模态对话框的阻塞
            dialog.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
            
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
            
            dialog.add(panel);
            dialog.setVisible(true);
            
            // 确保窗口显示在最前面
            dialog.toFront();
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

    // 预览方法（供外部调用）
    public void previewDialogNotification(String message, Color bgColor, Color textColor) {
        showDialogNotification(message, bgColor, textColor);
    }

    public void previewFullscreenNotification(String message, Color bgColor, Color textColor) {
        showFullscreenNotification(message, bgColor, textColor);
    }

    public void previewLockScreen() {
        lockScreen();
    }
}
