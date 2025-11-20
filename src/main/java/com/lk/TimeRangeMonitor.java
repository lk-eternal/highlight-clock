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
        }
    }
    
    public void checkAndTrigger() {
        LocalTime now = LocalTime.now();
        int currentMinutes = now.getHour() * 60 + now.getMinute();
        
        for (AnalogClock.HighlightSetting setting : highlightAreas) {
            String key = getSettingKey(setting);
            int startMinutes = setting.getStartHour() * 60 + setting.getStartMinute();
            int endMinutes = setting.getEndHour() * 60 + setting.getEndMinute();
            
            boolean wasInRange = rangeStates.getOrDefault(key, false);
            boolean isInRange = isTimeInRange(currentMinutes, startMinutes, endMinutes);
            
            // 状态变化：进入区域
            if (!wasInRange && isInRange) {
                triggerAction(setting, true);
                rangeStates.put(key, true);
            }
            // 状态变化：退出区域
            else if (wasInRange && !isInRange) {
                triggerAction(setting, false);
                rangeStates.put(key, false);
            }
        }
    }
    
    private void triggerAction(AnalogClock.HighlightSetting setting, boolean isEnter) {
        String action = isEnter ? setting.getEnterAction() : setting.getExitAction();
        if (action == null || "none".equals(action)) {
            return;
        }
        
        String message = (isEnter ? "进入" : "退出") + 
                        (setting.getLabel().isEmpty() ? "时间段" : setting.getLabel());
        
        switch (action) {
            case "dialog":
                showDialogNotification(message);
                break;
            case "fullscreen":
                showFullscreenNotification(message, setting.getHighlightColor(), setting.getLabelColor());
                break;
            case "lock":
                lockScreen();
                break;
        }
    }
    
    private void showDialogNotification(String message) {
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog((Frame)null, "时间提醒", false);
            dialog.setUndecorated(true);
            dialog.setAlwaysOnTop(true);
            
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100), 2),
                BorderFactory.createEmptyBorder(20, 30, 20, 30)
            ));
            panel.setBackground(new Color(245, 245, 245));
            
            JLabel label = new JLabel(message);
            label.setFont(new Font("Microsoft YaHei", Font.BOLD, 18));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(label, BorderLayout.CENTER);
            
            JButton okButton = new JButton("确定");
            okButton.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            okButton.addActionListener(e -> dialog.dispose());
            
            JPanel buttonPanel = new JPanel();
            buttonPanel.setOpaque(false);
            buttonPanel.add(okButton);
            panel.add(buttonPanel, BorderLayout.SOUTH);
            
            dialog.add(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
            
            // 5秒后自动关闭
            Timer timer = new Timer(5000, e -> dialog.dispose());
            timer.setRepeats(false);
            timer.start();
        });
    }
    
    private void showFullscreenNotification(String message, Color bgColor, Color textColor) {
        SwingUtilities.invokeLater(() -> {
            JWindow window = new JWindow();
            window.setAlwaysOnTop(true);
            
            // 获取屏幕尺寸
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            window.setBounds(0, 0, screenSize.width, screenSize.height);
            
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
                    window.dispose();
                }
            });
            
            window.add(panel);
            window.setVisible(true);
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
    public void previewDialogNotification(String message) {
        showDialogNotification(message);
    }

    public void previewFullscreenNotification(String message, Color bgColor, Color textColor) {
        showFullscreenNotification(message, bgColor, textColor);
    }

    public void previewLockScreen() {
        lockScreen();
    }
}

