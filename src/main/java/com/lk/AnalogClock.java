package com.lk;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

public class AnalogClock extends JFrame {

    private ClockPanel clockPanel;
    private JPopupMenu settingsMenu;
    private int xOffset, yOffset;

    public AnalogClock() {
        ClockConfig config = ConfigManager.loadConfig();
        setTitle("LK Clock");
        setUndecorated(true);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        setType(Window.Type.UTILITY);

        boolean traySetupSuccess = setupSystemTray();
        // 隐藏任务栏图标的配置（取决于操作系统和 JVM，通常与 setVisible(false) 结合使用）
        if (traySetupSuccess) {
            // 设置为最小化到托盘的行为 (Optional but recommended)
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        } else {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }

        clockPanel = new ClockPanel(config);
        add(clockPanel);

        setSize(clockPanel.getPreferredSize());
        if (config.windowX > 0 && config.windowY > 0) {
            setLocation(config.windowX, config.windowY);
        } else {
            setLocationRelativeTo(null);
        }

        // 动态更新时钟
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                clockPanel.repaint();
            }
        }, 0, 1000);

        addMouseWheelListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ||
                        e.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {

                    // 1. 记录缩放前的状态
                    float oldScale = clockPanel.getScale();
                    Point mousePointScreen = e.getLocationOnScreen(); // 鼠标在屏幕上的绝对位置
                    Point clockLocation = getLocation();              // 时钟窗口的左上角位置

                    float newScale;
                    if (e.getWheelRotation() < 0) { // 向上滚动 (放大)
                        newScale = Math.min(2.0f, oldScale + 0.1f);
                    } else { // 向下滚动 (缩小)
                        newScale = Math.max(0.5f, oldScale - 0.1f);
                    }

                    if (newScale != oldScale) {
                        // 2. 更新 scale
                        clockPanel.setScale(newScale);
                        pack(); // 重新计算并设置窗口大小

                        // 3. 计算并重新定位窗口
                        repositionFrameAfterScaling(oldScale, newScale, mousePointScreen, clockLocation);
                    }
                }
            }

            /**
             * 根据缩放比例和鼠标位置重新定位窗口，实现以鼠标为中心缩放。
             * @param oldScale 缩放前的比例
             * @param newScale 缩放后的比例
             * @param mousePointScreen 鼠标在屏幕上的绝对坐标
             * @param oldClockLocation 缩放前窗口的左上角坐标
             */
            private void repositionFrameAfterScaling(float oldScale, float newScale, Point mousePointScreen, Point oldClockLocation) {
                if (oldScale == newScale) return;

                // 比例因子 R
                double scaleFactor = newScale / oldScale;

                // 鼠标在旧窗口内的相对位置
                int mouseXInFrame = mousePointScreen.x - oldClockLocation.x;
                int mouseYInFrame = mousePointScreen.y - oldClockLocation.y;

                // 鼠标在缩放后的窗口内的新相对位置 (期望位置)
                // 鼠标到左上角的距离 D' = D * R

                // 新窗口的左上角坐标 (NewX, NewY)
                // NewX = MouseScreenX - (MouseInFrameX * R)
                int newX = (int) (mousePointScreen.x - (mouseXInFrame * scaleFactor));
                int newY = (int) (mousePointScreen.y - (mouseYInFrame * scaleFactor));

                setLocation(newX, newY);
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                saveCurrentConfig();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveCurrentConfig(); // 关闭时保存最终状态
            }
        });

        setVisible(true);
    }

    private boolean setupSystemTray() {
        // 检查系统是否支持托盘
        if (!SystemTray.isSupported()) {
            System.err.println("System Tray is not supported on this platform.");
            return false;
        }

        // 创建托盘图标和菜单
        SystemTray tray = SystemTray.getSystemTray();

        // 确保图标足够小，例如 16x16，这里使用默认的 Image.ICON_IMAGE
        // 注意：在实际应用中，你可能需要加载一个自定义的图标文件
        Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/tray.png"));

        // 创建托盘菜单
        PopupMenu trayPopupMenu = new PopupMenu();

        // 2. 设置 (打开设置对话框)
        MenuItem settingsItem = new MenuItem("Settings");
        settingsItem.addActionListener(e -> {
            SettingsDialog dialog = new SettingsDialog(this, clockPanel);
            dialog.setVisible(true);
        });
        trayPopupMenu.add(settingsItem);

        trayPopupMenu.addSeparator();


        TrayIcon trayIcon = new TrayIcon(image, "LK Clock", trayPopupMenu);
        trayIcon.setImageAutoSize(true);

        // 【新增】左键双击事件：显示/隐藏窗口
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    setVisible(!isVisible());
                }
            }
        });

        // 3. 退出
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            saveCurrentConfig();
            tray.remove(trayIcon); // 退出前移除托盘图标
            System.exit(0);
        });
        trayPopupMenu.add(exitItem);

        try {
            tray.add(trayIcon);
            return true;
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
            return false;
        }
    }

    private void saveCurrentConfig() {
        ClockConfig config = new ClockConfig();

        // 1. 窗口位置和大小 (保持不变)
        config.scale = clockPanel.getScale();
        config.windowX = getX();
        config.windowY = getY();

        // 2. 颜色属性 (直接复制 Color 对象，Jackson 会自动处理序列化)
        config.clockColor = clockPanel.getClockColor();
        config.defaultHighlightColor = clockPanel.getDefaultHighlightColor();
        config.numberColor = clockPanel.getNumberColor();
        config.hourHandColor = clockPanel.getHourHandColor();
        config.minuteHandColor = clockPanel.getMinuteHandColor();
        config.secondHandColor = clockPanel.getSecondHandColor();

        // 3. 高亮区域 (直接使用 Color 对象创建 SerializableHighlightSetting)
        List<ClockConfig.SerializableHighlightSetting> serializableList = new ArrayList<>();
        for (HighlightSetting setting : clockPanel.getHighlightAreas()) {
            serializableList.add(new ClockConfig.SerializableHighlightSetting(
                    setting.getStartHour(),
                    setting.getStartMinute(),
                    setting.getEndHour(),
                    setting.getEndMinute(),
                    setting.getHighlightColor() // <--- 直接传递 Color 对象
            ));
        }
        config.highlightAreas = serializableList;

        ConfigManager.saveConfig(config);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AnalogClock::new);
    }

    // =========================================================================
    // 内部类：时钟绘制面板
    // =========================================================================

    class ClockPanel extends JPanel {

        private float scale = 1.0f;
        private final int BASE_CLOCK_SIZE = 200;

        private List<HighlightSetting> highlightAreas;
        private Color clockColor;

        // 保留默认高亮颜色，用于新建时的默认值
        private Color defaultHighlightColor;

        private Color numberColor;
        private Color hourHandColor;
        private Color minuteHandColor;
        private Color secondHandColor;

        public ClockPanel(ClockConfig config) {
            // 1. 应用颜色和缩放 (直接使用配置中的 Color 对象)
            this.scale = config.scale;
            this.clockColor = config.clockColor;
            this.defaultHighlightColor = config.defaultHighlightColor;
            this.numberColor = config.numberColor;
            this.hourHandColor = config.hourHandColor;
            this.minuteHandColor = config.minuteHandColor;
            this.secondHandColor = config.secondHandColor;

            // 2. 应用高亮区域
            this.highlightAreas = new ArrayList<>();
            if (config.highlightAreas != null && !config.highlightAreas.isEmpty()) {
                for (ClockConfig.SerializableHighlightSetting shs : config.highlightAreas) {
                    this.highlightAreas.add(new HighlightSetting(
                            shs.startHour,
                            shs.startMinute,
                            shs.endHour,
                            shs.endMinute,
                            shs.highlightColor // <--- 直接使用 Color 对象
                    ));
                }
            } else {
                // 如果配置中没有高亮区域，使用初始默认值
                this.highlightAreas.add(new HighlightSetting(9, 0, 12, 0, defaultHighlightColor));
                this.highlightAreas.add(new HighlightSetting(13, 18, 18, 0, defaultHighlightColor));
            }

            setOpaque(false);
            setPreferredSize(new Dimension(BASE_CLOCK_SIZE, BASE_CLOCK_SIZE));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // 【新增/修改】：记录偏移量，用于拖动
                    AnalogClock parent = (AnalogClock) SwingUtilities.getWindowAncestor(ClockPanel.this);
                    if (parent != null) {
                        parent.xOffset = e.getX();
                        parent.yOffset = e.getY();
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    // 1. 双击 (左键) 逻辑
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                        HighlightSetting clickedSetting = getHighlightSettingAt(e.getX(), e.getY());
                        SettingsDialog dialog =
                                new SettingsDialog((JFrame) SwingUtilities.getWindowAncestor(ClockPanel.this),
                                        ClockPanel.this, true);
                        if (clickedSetting != null) {
                            boolean isNew = !highlightAreas.contains(clickedSetting);
                            dialog.openHighlightEdit(clickedSetting, isNew);
                        }
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    // 2. 右键 (Popup) 逻辑
                    if (e.isPopupTrigger()) {
                        JPopupMenu popup = new JPopupMenu();
                        HighlightSetting clickedSetting = getHighlightSettingAt(e.getX(), e.getY());

                        if (clickedSetting != null) {
                            boolean isNew = !highlightAreas.contains(clickedSetting);
                            if(isNew){
                                // 右键非高亮区域 -> 新建菜单
                                JMenuItem newItem = new JMenuItem("新建高亮区域");
                                newItem.addActionListener(action -> {
                                    SettingsDialog dialog =
                                            new SettingsDialog((JFrame) SwingUtilities.getWindowAncestor(ClockPanel.this)
                                                    , ClockPanel.this, true);
                                    dialog.openHighlightEdit(clickedSetting, true); // 新建
                                });
                                popup.add(newItem);
                            }else{
                                // 右键高亮区域 -> 编辑/删除菜单
                                JMenuItem editItem = new JMenuItem("编辑高亮区域");
                                editItem.addActionListener(action -> {
                                    SettingsDialog dialog =
                                            new SettingsDialog((JFrame) SwingUtilities.getWindowAncestor(ClockPanel.this)
                                                    , ClockPanel.this, true);
                                    dialog.openHighlightEdit(clickedSetting, false); // 编辑
                                });

                                JMenuItem deleteItem = new JMenuItem("删除高亮区域");
                                deleteItem.addActionListener(action -> {
                                    int confirm = JOptionPane.showConfirmDialog(ClockPanel.this,
                                            "确定删除区域: " + clickedSetting.format(),
                                            "确认删除", JOptionPane.YES_NO_OPTION);
                                    if (confirm == JOptionPane.YES_OPTION) {
                                        highlightAreas.remove(clickedSetting);
                                        repaint();
                                        ((AnalogClock) SwingUtilities.getWindowAncestor(ClockPanel.this)).saveCurrentConfig(); // 实时保存
                                    }
                                });

                                popup.add(editItem);
                                popup.add(deleteItem);
                            }
                        }
                        popup.addSeparator();

                        JMenuItem settings = new JMenuItem("设置");
                        settings.addActionListener(action -> {
                            SettingsDialog dialog =
                                    new SettingsDialog((JFrame) SwingUtilities.getWindowAncestor(ClockPanel.this)
                                            , ClockPanel.this);
                            dialog.setVisible(true);
                        });
                        popup.add(settings);

                        JMenuItem exitItem = new JMenuItem("退出");
                        exitItem.addActionListener(igonre -> {
                            saveCurrentConfig();
                            System.exit(0);
                        });
                        popup.add(exitItem);

                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    AnalogClock parent = (AnalogClock) SwingUtilities.getWindowAncestor(ClockPanel.this);
                    if (parent != null) {
                        // 使用父窗口的 xOffset 和 yOffset
                        Point newLocation = parent.getLocation();
                        newLocation.translate(e.getX() - parent.xOffset, e.getY() - parent.yOffset);
                        parent.setLocation(newLocation);
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int currentSize = (int) (BASE_CLOCK_SIZE * scale);
            return new Dimension(currentSize, currentSize);
        }

        public float getScale() { return scale; }
        public void setScale(float newScale) {
            this.scale = Math.max(0.5f, Math.min(2.0f, newScale));
            revalidate();
        }

        public Color getDefaultHighlightColor() { return defaultHighlightColor; }
        public void setDefaultHighlightColor(Color defaultHighlightColor) {
            this.defaultHighlightColor = defaultHighlightColor;
            repaint();
        }

        public List<HighlightSetting> getHighlightAreas() { return highlightAreas; }
        public void setHighlightAreas(List<HighlightSetting> highlightAreas) {
            this.highlightAreas = highlightAreas;
            repaint();
        }

        public Color getClockColor() { return clockColor; }
        public void setClockColor(Color clockColor) { this.clockColor = clockColor; repaint(); }
        public Color getNumberColor() { return numberColor; }
        public void setNumberColor(Color numberColor) { this.numberColor = numberColor; repaint(); }
        public Color getHourHandColor() { return hourHandColor; }
        public void setHourHandColor(Color hourHandColor) { this.hourHandColor = hourHandColor; repaint(); }
        public Color getMinuteHandColor() { return minuteHandColor; }
        public void setMinuteHandColor(Color minuteHandColor) { this.minuteHandColor = minuteHandColor; repaint(); }
        public Color getSecondHandColor() { return secondHandColor; }
        public void setSecondHandColor(Color secondHandColor) { this.secondHandColor = secondHandColor; repaint(); }

        // ClockPanel.java 内部类 ClockPanel 的部分

        /**
         * 将面板上的坐标转换为小时和分钟。
         * @param x 鼠标x坐标
         * @param y 鼠标y坐标
         * @return 包含 {hour, minute} 的数组（hour为0-11，0表示12点），或 null 如果点击在表盘外部。
         */
        private int[] getTimeAt(int x, int y) {
            int currentSize = (int) (BASE_CLOCK_SIZE * scale);
            int centerX = currentSize / 2;
            int centerY = currentSize / 2;

            // 检查是否在圆内 (使用半径 * 0.95 来忽略边缘)
            int radius = currentSize / 2;
            int dx = x - centerX;
            int dy = centerY - y; // 修正：Y坐标取反，因为AWT的Y轴向下为正

            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance > radius * 0.95 || distance < radius * 0.1) {
                // 点击在太边缘或中心点，忽略
                return null;
            }

            // 1. 计算AWT角度 (从3点钟位置开始, 0度, 逆时针增加)
            double angleRad = Math.atan2(dy, dx);
            double awtAngleDeg = Math.toDegrees(angleRad);

            // 标准化角度到 0-360 度
            if (awtAngleDeg < 0) {
                awtAngleDeg += 360;
            }

            // 2. 转换为时钟角度（从12点钟位置开始，顺时针）
            double clockAngleFrom12 = (90 - awtAngleDeg + 360) % 360;

            // 3. 将角度转换为12小时制的总分钟数
            int totalMinutes12Hour = (int) Math.round(clockAngleFrom12 * 2);
            totalMinutes12Hour = totalMinutes12Hour % 720; // 确保在0-719范围内

            // 4. 转换为小时和分钟（12小时制）
            int minute = totalMinutes12Hour % 60;
            int hour12 = totalMinutes12Hour / 60; // 0-11 (0表示12点)

            // 直接返回12小时制时间，hour12的范围是0-11
            return new int[] {hour12, minute};
        }


        /**
         * 根据鼠标点击位置，查找匹配的高亮区域。
         * @param x 鼠标x坐标
         * @param y 鼠标y坐标
         * @return 匹配的 HighlightSetting 对象，如果没有匹配则返回 null。
         */
        public HighlightSetting getHighlightSettingAt(int x, int y) {
            int[] time = getTimeAt(x, y);
            if (time == null) {
                return null; // 点击不在表盘范围内
            }

            int clickHour = time[0];
            int clickMinute = time[1];
            int clickTotalMinutes = clickHour * 60 + clickMinute;

            for (HighlightSetting setting : highlightAreas) {
                int startTotalMinutes = setting.getStartHour() * 60 + setting.getStartMinute();
                int endTotalMinutes = setting.getEndHour() * 60 + setting.getEndMinute();

                // 尝试匹配原始时间段
                if (isTimeInRange(clickTotalMinutes, startTotalMinutes, endTotalMinutes)) {
                    return setting;
                }

                // 同时尝试匹配对应的12小时后的时间段（如果原始时间段在0-12小时）
                if (startTotalMinutes < 720) { // 如果开始时间在0-12小时内
                    int startPlus12 = startTotalMinutes + 720;
                    int endPlus12 = endTotalMinutes + 720;

                    // 确保不超过24小时
                    if (endPlus12 >= 1440) {
                        endPlus12 = 1439; // 最大到23:59
                    }

                    if (isTimeInRange(clickTotalMinutes, startPlus12, endPlus12)) {
                        return setting;
                    }
                }

                // 同时尝试匹配对应的12小时前的时间段（如果原始时间段在12-24小时）
                if (startTotalMinutes >= 720) { // 如果开始时间在12-24小时内
                    int startMinus12 = startTotalMinutes - 720;
                    int endMinus12 = endTotalMinutes - 720;

                    if (isTimeInRange(clickTotalMinutes, startMinus12, endMinus12)) {
                        return setting;
                    }
                }
            }
            int endH = (clickHour + 1) % 24; // 下一个小时，处理 23 -> 0 的情况

            // 创建临时对象，用于填充新建对话框
            return new HighlightSetting(
                    clickHour, 0,
                    endH, 0,
                    defaultHighlightColor);
        }

        /**
         * 检查给定的时间是否在指定范围内（处理跨越午夜的情况）
         */
        private boolean isTimeInRange(int checkTime, int startTime, int endTime) {
            // 场景 1: 区域不跨越午夜 (Start <= End)
            if (startTime <= endTime) {
                return checkTime >= startTime && checkTime < endTime;
            }
            // 场景 2: 区域跨越午夜 (Start > End)
            else {
                return (checkTime >= startTime && checkTime < 1440) ||
                        (checkTime >= 0 && checkTime < endTime);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int currentSize = (int) (BASE_CLOCK_SIZE * scale);
            int centerX = currentSize / 2;
            int centerY = currentSize / 2;

            LocalTime now = LocalTime.now();
            int hour = now.getHour() % 12;
            int minute = now.getMinute();
            int second = now.getSecond();

            // 1. 绘制表盘背景
            g2d.setColor(clockColor);
            g2d.fillOval(0, 0, currentSize, currentSize);

            // 2. 绘制时间区域高亮 (使用单独的颜色)
            drawHighlightSections(g2d, currentSize);

            // 3. 绘制刻度和数字
            g2d.setColor(numberColor);
            drawMarksAndNumbers(g2d, currentSize, centerX, centerY);

            // 4. 绘制指针
            double secondAngle = Math.toRadians(second * 6 - 90);
            drawHand(g2d, secondAngle, (int)(90*scale), (int)(2*scale), secondHandColor, centerX, centerY);

            double minuteAngle = Math.toRadians(minute * 6 + second * 0.1 - 90);
            drawHand(g2d, minuteAngle, (int)(70*scale), (int)(4*scale), minuteHandColor, centerX, centerY);

            double hourAngle = Math.toRadians(hour * 30 + minute * 0.5 - 90);
            drawHand(g2d, hourAngle, (int)(50*scale), (int)(6*scale), hourHandColor, centerX, centerY);

            // 5. 绘制中心点
            g2d.setColor(Color.DARK_GRAY);
            int centerDotSize = (int)(10*scale);
            g2d.fillOval(centerX - centerDotSize / 2, centerY - centerDotSize / 2, centerDotSize, centerDotSize);
        }

        /**
         * 绘制高亮区域 (现在从 HighlightSetting 中获取颜色)
         */
        private void drawHighlightSections(Graphics2D g2d, int currentSize) {
            for (HighlightSetting setting : highlightAreas) {
                g2d.setColor(setting.getHighlightColor());

                // 1. 将 24 小时制的 HH:MM 转换为总分钟数（0 到 1440）
                int startTotalMinutes = setting.getStartHour() * 60 + setting.getStartMinute();
                int endTotalMinutes = setting.getEndHour() * 60 + setting.getEndMinute();

                // 2. 将总分钟数转换为 12 小时时钟上的角度
                // 12小时制总分钟数: 12 * 60 = 720
                // 角度/分钟: 360 / 720 = 0.5 度/分钟

                // 我们从 12 点钟位置 (0 度) 顺时针计算。
                // 时钟上的 12 点是 90 度 (在 fillArc 坐标系中)。

                // 转换为 12 小时制下的总分钟数 (0 到 720)
                int startMin12 = startTotalMinutes % 720;
                int endMin12 = endTotalMinutes % 720;

                // 计算起始角度 (AWT fillArc 角度从 3点钟位置开始，逆时针增加)
                // 我们的时钟是顺时针的，所以角度取负值。
                // 12点钟对应 90度 (3点钟在0度，逆时针到12点是90度)

                // 距离 12 点钟位置的分钟数
                // 距离 12 点钟的度数 (顺时针) = (startMin12 / 2)
                // AWT 的起始角 (逆时针) = 90 - (距离12点的度数)
                float sweepStartAngle = 90f - (startMin12 * 0.5f);

                // 计算扫描角度 (始终顺时针扫描)
                float sweepAngle;
                if (endTotalMinutes > startTotalMinutes) {
                    // 简单情况：未跨越午夜/中午12点线
                    sweepAngle = (endTotalMinutes - startTotalMinutes) * 0.5f;
                } else {
                    // 跨越午夜/中午12点线 (例如 23:30 到 01:30)
                    // 假设 span = 26:00 - 23:30 = 2.5 hours = 150 minutes
                    sweepAngle = (1440 - startTotalMinutes + endTotalMinutes) * 0.5f;
                }

                // fillArc 扫描角度为负值表示顺时针
                int margin = (int) (BASE_CLOCK_SIZE * scale * 0.05);
                g2d.fillArc(margin / 2, margin / 2,
                        currentSize - margin, currentSize - margin,
                        (int) sweepStartAngle, (int) -sweepAngle);
            }
        }

        // ... (drawHand, drawMarksAndNumbers 保持不变) ...
        private void drawHand(Graphics2D g2d, double angle, int length, int thickness, Color color, int centerX, int centerY) {
            int x = (int) (centerX + length * Math.cos(angle));
            int y = (int) (centerY + length * Math.sin(angle));

            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawLine(centerX, centerY, x, y);
        }

        private void drawMarksAndNumbers(Graphics2D g2d, int currentSize, int centerX, int centerY) {
            Font font = new Font("Arial", Font.BOLD, (int)(14 * scale));
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics(font);
            int radius = currentSize / 2;

            for (int i = 1; i <= 12; i++) {
                double angle = Math.toRadians(i * 30 - 90);
                int x = (int) (centerX + radius * 0.9 * Math.cos(angle));
                int y = (int) (centerY + radius * 0.9 * Math.sin(angle));

                String num = String.valueOf(i);
                int strWidth = fm.stringWidth(num);
                int strHeight = fm.getHeight();
                g2d.drawString(num, x - strWidth / 2, y + strHeight / 4);
            }
        }
    }

    // =========================================================================
    // 内部类：高亮区域设置 (更新)
    // =========================================================================

    class HighlightSetting {
        private int startHour;
        private int startMinute; // 【新增】起始分钟
        private int endHour;
        private int endMinute;   // 【新增】结束分钟
        private Color highlightColor;

        // 【修改】构造函数以支持分钟
        public HighlightSetting(int startHour, int startMinute, int endHour, int endMinute, Color highlightColor) {
            this.startHour = startHour;
            this.startMinute = startMinute;
            this.endHour = endHour;
            this.endMinute = endMinute;
            this.highlightColor = highlightColor;
        }

        public int getStartHour() { return startHour; }
        public int getStartMinute() { return startMinute; } // 【新增】
        public int getEndHour() { return endHour; }
        public int getEndMinute() { return endMinute; }     // 【新增】
        public Color getHighlightColor() { return highlightColor; }

        public void setHighlightColor(Color highlightColor) {
            this.highlightColor = highlightColor;
        }

        @Override
        public String toString() {
            // 【修改】在列表中显示分钟数
            String hexColor = String.format("#%06X", (0xFFFFFF & highlightColor.getRGB()));
            return String.format("<html><span style='background-color:%s;'>&nbsp;&nbsp;&nbsp;</span> %02d:%02d - %02d:%02d</html>",
                    hexColor, startHour, startMinute, endHour, endMinute);
        }

        public String format(){
            return String.format("%02d:%02d ~ %02d:%02d", startHour, startMinute, endHour, endMinute);
        }
    }

    // =========================================================================
    // 内部类：设置对话框 (更新)
    // =========================================================================

    class SettingsDialog extends JDialog {

        private ClockPanel clockPanel;
        private DefaultListModel<HighlightSetting> listModel;
        private JList<HighlightSetting> highlightList;
        private boolean openedByShortcut = false;

        public SettingsDialog(JFrame parent, ClockPanel clockPanel) {
            this(parent, clockPanel, false);
        }

        public SettingsDialog(JFrame parent, ClockPanel clockPanel, boolean openedByShortcut) {
            super(parent, "时钟设置", true);
            this.clockPanel = clockPanel;
            this.openedByShortcut = openedByShortcut; // 设置标记位
            setLayout(new BorderLayout(10, 10));

            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.addTab("样式/颜色设置", createStylePanel());
            tabbedPane.addTab("高亮区域设置", createHighlightPanel());

            add(tabbedPane, BorderLayout.CENTER);
            add(createButtonPanel(), BorderLayout.SOUTH);

            setSize(400, 500);
            setLocationRelativeTo(parent);

            // 【新增】监听窗口关闭事件，如果是由快捷操作打开，则直接退出。
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    if (openedByShortcut) {
                        // 如果是通过双击/右键菜单打开的，在编辑/新建窗口关闭后，直接退出 SettingsDialog
                        dispose();
                    }
                }
            });
        }

        // SettingsDialog.java 内部类 SettingsDialog 的部分

        // 【新增公共方法】直接打开高亮区域设置，并预先选中/编辑特定区域
        public void openHighlightEdit(HighlightSetting setting, boolean isNew) {
            JTabbedPane tabbedPane = (JTabbedPane) getContentPane().getComponent(0);

            // 切换到高亮区域设置 Tab
            int highlightTabIndex = tabbedPane.indexOfTab("高亮区域设置");
            if (highlightTabIndex != -1) {
                tabbedPane.setSelectedIndex(highlightTabIndex);
            }

            // 如果是编辑模式，预选列表中的项
            if (!isNew) {
                int index = listModel.indexOf(setting);
                if (index != -1) {
                    highlightList.setSelectedValue(setting, true);
                }
            } else {
                // 新建模式，确保列表不选中任何东西
                highlightList.clearSelection();
            }

            // 调用 addHighlightArea，无论是新建还是编辑，都使用传入的 setting 对象初始化对话框
            // 如果是新建，setting 是临时的，包含默认 H:00~(H+1):00 时间。
            // 如果是编辑，setting 是列表中的，包含现有时间。
            addHighlightArea(setting, isNew);

            if (openedByShortcut) {
                dispose();
            } else {
                setVisible(true);
            }
        }

        /**
         * 创建样式和颜色设置面板 (更新默认高亮颜色设置)
         */
        private JPanel createStylePanel() {
            JPanel panel = new JPanel(new GridLayout(7, 1, 10, 10)); // 7行
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));

            // 使用辅助方法创建颜色行 (ColorSetter 接口已改为 Consumer<Color>)
            Consumer<Color> defaultHighlightSetter = c -> clockPanel.setDefaultHighlightColor(c);
            createColorRow(panel, "默认高亮颜色", clockPanel.getDefaultHighlightColor(),
                    c -> {
                        // 保持透明度逻辑
                        int alpha = c.getAlpha();
                        if(alpha < 50) alpha = 50;
                        c = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
                        defaultHighlightSetter.accept(c);
                    });

            createColorRow(panel, "表盘背景颜色", clockPanel.getClockColor(), clockPanel::setClockColor);
            createColorRow(panel, "表盘数字颜色", clockPanel.getNumberColor(), clockPanel::setNumberColor);
            createColorRow(panel, "时针颜色", clockPanel.getHourHandColor(), clockPanel::setHourHandColor);
            createColorRow(panel, "分针颜色", clockPanel.getMinuteHandColor(), clockPanel::setMinuteHandColor);
            createColorRow(panel, "秒针颜色", clockPanel.getSecondHandColor(), clockPanel::setSecondHandColor);

            return panel;
        }

        /**
         * 辅助方法：创建包含实时功能的颜色选择行
         */
        private void createColorRow(JPanel parentPanel, String labelText, Color initialColor, Consumer<Color> setter) {
            JPanel rowPanel = new JPanel(new BorderLayout(10, 5));
            rowPanel.add(new JLabel(labelText + ":"), BorderLayout.WEST);

            JButton colorButton = new JButton("选择颜色");
            colorButton.setBackground(initialColor);
            colorButton.setOpaque(true);
            colorButton.setBorderPainted(false);
            colorButton.addActionListener(e -> {
                Color selectedColor = JColorChooser.showDialog(this, "选择 " + labelText, colorButton.getBackground());
                if (selectedColor != null) {
                    colorButton.setBackground(selectedColor);
                    setter.accept(selectedColor);
                }
            });

            rowPanel.add(colorButton, BorderLayout.EAST);

            parentPanel.add(rowPanel);
        }

        /**
         * 创建高亮区域设置面板 (更新)
         */
        private JPanel createHighlightPanel() {
            JPanel panel = new JPanel(new BorderLayout(5, 5));

            listModel = new DefaultListModel<>();
            for (HighlightSetting setting : clockPanel.getHighlightAreas()) {
                listModel.addElement(setting);
            }
            // 使用自定义的 CellRenderer 来确保 HTML 渲染
            highlightList = new JList<>(listModel);
            highlightList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    // HighlightSetting.toString() 使用了 HTML，确保能正确渲染
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                }
            });

            highlightList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            panel.add(new JScrollPane(highlightList), BorderLayout.CENTER);
            panel.add(createHighlightButtonPanel(), BorderLayout.SOUTH);

            return panel;
        }

        private JPanel createHighlightButtonPanel() {
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

            JButton addButton = new JButton("添加新区域");
            addButton.addActionListener(e -> addHighlightArea(new HighlightSetting(9, 0, 18, 0,
                    clockPanel.getDefaultHighlightColor()), true));

            JButton editButton = new JButton("编辑选中区域");
            editButton.addActionListener(e -> {
                int selectedIndex = highlightList.getSelectedIndex();
                if (selectedIndex != -1) {
                    addHighlightArea(listModel.getElementAt(selectedIndex), false);
                } else {
                    JOptionPane.showMessageDialog(this, "请先选择一个要编辑的区域。", "提示", JOptionPane.INFORMATION_MESSAGE);
                }
            });

            JButton deleteButton = new JButton("删除选中区域");
            deleteButton.addActionListener(e -> deleteHighlightArea());

            buttonPanel.add(addButton);
            buttonPanel.add(editButton);
            buttonPanel.add(deleteButton);

            return buttonPanel;
        }

        /**
         * 添加/编辑高亮区域
         */
        private void addHighlightArea(HighlightSetting settingToEdit, boolean isNew) {
            // UI 组件 - 【修改】布局改为 5行2列
            JPanel inputPanel = new JPanel(new GridLayout(5, 2, 5, 5));

            // 小时 Spinner (0-23)
            SpinnerModel startHourModel = new SpinnerNumberModel(settingToEdit.getStartHour(), 0, 23, 1);
            JSpinner startHourSpinner = new JSpinner(startHourModel);
            SpinnerModel endHourModel = new SpinnerNumberModel(settingToEdit.getEndHour(), 0, 23, 1);
            JSpinner endHourSpinner = new JSpinner(endHourModel);

            // 分钟 Spinner (0-59)
            SpinnerModel startMinuteModel = new SpinnerNumberModel(settingToEdit.getStartMinute(), 0, 59, 1);
            JSpinner startMinuteSpinner = new JSpinner(startMinuteModel);
            SpinnerModel endMinuteModel = new SpinnerNumberModel(settingToEdit.getEndMinute(), 0, 59, 1);
            JSpinner endMinuteSpinner = new JSpinner(endMinuteModel);

            // 颜色按钮 (逻辑保持不变)
            JButton colorButton = new JButton("更改颜色");
            colorButton.setBackground(settingToEdit.getHighlightColor());
            colorButton.setOpaque(true);
            colorButton.setBorderPainted(false);
            final Color[] tempColor = {settingToEdit.getHighlightColor()};
            colorButton.addActionListener(e -> {
                Color selectedColor = JColorChooser.showDialog(this, "选择高亮颜色", tempColor[0]);
                if (selectedColor != null) {
                    int alpha = selectedColor.getAlpha();
                    if(alpha < 50) alpha = 50;
                    tempColor[0] = new Color(selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), alpha);
                    colorButton.setBackground(tempColor[0]);
                }
            });

            // 【修改】添加新的组件到面板
            inputPanel.add(new JLabel("起始时间 (时):"));
            inputPanel.add(startHourSpinner);
            inputPanel.add(new JLabel("起始时间 (分):")); // 【新增】
            inputPanel.add(startMinuteSpinner);         // 【新增】
            inputPanel.add(new JLabel("结束时间 (时):"));
            inputPanel.add(endHourSpinner);
            inputPanel.add(new JLabel("结束时间 (分):")); // 【新增】
            inputPanel.add(endMinuteSpinner);           // 【新增】
            inputPanel.add(new JLabel("区域颜色:"));
            inputPanel.add(colorButton);

            int result = JOptionPane.showConfirmDialog(this, inputPanel,
                    isNew ? "添加新的高亮时间区域" : "编辑高亮时间区域", JOptionPane.OK_CANCEL_OPTION);

            if (result == JOptionPane.OK_OPTION) {
                int startH = (int) startHourSpinner.getValue();
                int startM = (int) startMinuteSpinner.getValue();
                int endH = (int) endHourSpinner.getValue();
                int endM = (int) endMinuteSpinner.getValue();

                // 校验逻辑 (现在基于总分钟数进行校验)
                int startTotalMinutes = startH * 60 + startM;
                int endTotalMinutes = endH * 60 + endM;

                if (startTotalMinutes == endTotalMinutes) {
                    JOptionPane.showMessageDialog(this, "起始时间和结束时间不能相同。", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // 【注意】：为了支持跨越午夜的周期（例如 23:00-05:00），我们允许 startTotalMinutes > endTotalMinutes。
                // 仅在新增时需要考虑，如果是编辑，最好允许用户调整。

                // 简单的校验：如果起始时间大于结束时间，且差值大于 12 小时，可能存在问题，但此处我们假定用户输入正确。

                // 【修改】应用更改
                settingToEdit.startHour = startH;
                settingToEdit.startMinute = startM;
                settingToEdit.endHour = endH;
                settingToEdit.endMinute = endM;
                settingToEdit.setHighlightColor(tempColor[0]);

                if (isNew) {
                    listModel.addElement(settingToEdit);
                } else {
                    highlightList.repaint();
                }

                updateClockHighlights();
            }
        }

        private void deleteHighlightArea() {
            int selectedIndex = highlightList.getSelectedIndex();
            if (selectedIndex != -1) {
                listModel.remove(selectedIndex);
                updateClockHighlights();
                if(selectedIndex > 0){
                    highlightList.setSelectedIndex(selectedIndex - 1);
                }
            } else {
                JOptionPane.showMessageDialog(this, "请先选择一个要删除的区域。", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        // 提取公共方法，用于实时应用高亮设置
        private void updateClockHighlights() {
            List<HighlightSetting> newSettings = new ArrayList<>();
            for (int i = 0; i < listModel.getSize(); i++) {
                newSettings.add(listModel.getElementAt(i));
            }
            clockPanel.setHighlightAreas(newSettings);

            ((AnalogClock) getParent()).saveCurrentConfig();
        }

        private JPanel createButtonPanel() {
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

            JButton closeButton = new JButton("关闭设置");
            closeButton.addActionListener(e -> {
                // 确保高亮区域的最终状态被应用（因为 listModel 可能被修改）
                updateClockHighlights();
                dispose();
            });

            buttonPanel.add(closeButton);
            return buttonPanel;
        }
    }
}