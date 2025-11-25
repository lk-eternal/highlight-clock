package com.lk;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
    private int xOffset, yOffset;
    private TimeRangeMonitor timeRangeMonitor;

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

        // 初始化时间范围监控器
        timeRangeMonitor = new TimeRangeMonitor(clockPanel.getHighlightAreas());

        // 动态更新时钟
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                clockPanel.repaint();
                // 每秒检查是否需要触发进入/退出动作
                timeRangeMonitor.checkAndTrigger();
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

        // 3. 全局标签显示设置
        config.showLabels = clockPanel.isShowLabels();

        // 4. 高亮区域 (直接使用 Color 对象创建 SerializableHighlightSetting)
        List<ClockConfig.SerializableHighlightSetting> serializableList = new ArrayList<>();
        for (HighlightSetting setting : clockPanel.getHighlightAreas()) {
            serializableList.add(new ClockConfig.SerializableHighlightSetting(
                    setting.getStartHour(),
                    setting.getStartMinute(),
                    setting.getEndHour(),
                    setting.getEndMinute(),
                    setting.getHighlightColor(),
                    setting.getLabel(),
                    setting.getLabelColor(),
                    setting.getEnter(),
                    setting.getExit(),
                    setting.getInterval()
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

        // 全局设置：是否显示标签
        private boolean showLabels = true;

        // 鼠标悬停的高亮区域
        private HighlightSetting hoveredSetting = null;

        public ClockPanel(ClockConfig config) {
            // 1. 应用颜色和缩放 (直接使用配置中的 Color 对象)
            this.scale = config.scale;
            this.clockColor = config.clockColor;
            this.defaultHighlightColor = config.defaultHighlightColor;
            this.numberColor = config.numberColor;
            this.hourHandColor = config.hourHandColor;
            this.minuteHandColor = config.minuteHandColor;
            this.secondHandColor = config.secondHandColor;
            this.showLabels = config.showLabels;

            // 2. 应用高亮区域
            this.highlightAreas = new ArrayList<>();
            if (config.highlightAreas != null && !config.highlightAreas.isEmpty()) {
                for (ClockConfig.SerializableHighlightSetting shs : config.highlightAreas) {
                    this.highlightAreas.add(new HighlightSetting(
                            shs.startHour,
                            shs.startMinute,
                            shs.endHour,
                            shs.endMinute,
                            shs.highlightColor,
                            shs.label != null ? shs.label : "",
                            shs.labelColor != null ? shs.labelColor : Color.WHITE,
                            shs.enter,
                            shs.exit,
                            shs.interval
                    ));
                }
            } else {
                // 如果配置中没有高亮区域，使用初始默认值
                this.highlightAreas.add(new HighlightSetting(9, 0, 12, 0, defaultHighlightColor, "", Color.WHITE));
                this.highlightAreas.add(new HighlightSetting(13, 18, 18, 0, defaultHighlightColor, "", Color.WHITE));
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

                @Override
                public void mouseMoved(MouseEvent e) {
                    // 检查鼠标是否悬停在某个高亮区域上
                    HighlightSetting newHovered = getHighlightSettingAt(e.getX(), e.getY());
                    
                    // 只有真正的高亮区域才算悬停（排除临时创建的新建对象）
                    if (newHovered != null && !highlightAreas.contains(newHovered)) {
                        newHovered = null;
                    }
                    
                    if (newHovered != hoveredSetting) {
                        hoveredSetting = newHovered;
                        repaint(); // 触发重绘以显示悬停效果
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

        public boolean isShowLabels() { return showLabels; }
        public void setShowLabels(boolean showLabels) { this.showLabels = showLabels; repaint(); }

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

            int clickHour = time[0]; // 0-11 (12小时制)
            int clickMinute = time[1];

            // 检查所有高亮区域
            for (HighlightSetting setting : highlightAreas) {
                // 将24小时制的设置转换为12小时制进行匹配
                int startHour24 = setting.getStartHour();
                int startMinute24 = setting.getStartMinute();
                int endHour24 = setting.getEndHour();
                int endMinute24 = setting.getEndMinute();

                // 转换为12小时制
                int startHour12 = startHour24 % 12;
                int endHour12 = endHour24 % 12;

                // 计算12小时制的总分钟数
                int startMin12 = startHour12 * 60 + startMinute24;
                int endMin12 = endHour12 * 60 + endMinute24;
                int clickMin12 = clickHour * 60 + clickMinute;

                // 检查点击是否在这个12小时制区域内
                boolean inRange = false;
                if (startMin12 <= endMin12) {
                    // 不跨越12点的情况
                    inRange = (clickMin12 >= startMin12 && clickMin12 < endMin12);
                } else {
                    // 跨越12点的情况
                    inRange = (clickMin12 >= startMin12 || clickMin12 < endMin12);
                }

                if (inRange) {
                    return setting;
                }
            }

            // 没有匹配到任何高亮区域，创建临时对象用于新建
            int endH = (clickHour + 1) % 12;
            return new HighlightSetting(
                    clickHour, 0,
                    endH, 0,
                    defaultHighlightColor, "", Color.WHITE);
        }


        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int currentSize = (int) (BASE_CLOCK_SIZE * scale);
            currentSize = currentSize - currentSize%2;
            int centerX = currentSize / 2;
            int centerY = currentSize / 2;
            int radius = currentSize / 2;

            LocalTime now = LocalTime.now();
            int hour = now.getHour() % 12;
            int minute = now.getMinute();
            int second = now.getSecond();
            
            // 计算当前时间（24小时制，用于判断是否在高亮区域内）
            int currentHour24 = now.getHour();
            int currentMinute = now.getMinute();

            // 1. 绘制表盘背景（深色简洁背景）
            g2d.setColor(clockColor);
            g2d.fillOval(0, 0, currentSize, currentSize);

            // 2. 绘制圆环轨道背景（灰色底轨）- 底轨用圆头
            int ringWidth = (int)(10 * scale);
            int ringMargin = (int)(30 * scale);  // 增大边距，让圆环和数字之间有呼吸感
            int ringRadius = radius - ringMargin - ringWidth / 2;
            
            g2d.setColor(new Color(60, 60, 60, 180));
            g2d.setStroke(new BasicStroke(ringWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawOval(centerX - ringRadius, centerY - ringRadius, ringRadius * 2, ringRadius * 2);

            // 3. 绘制高亮圆环
            drawHighlightRings(g2d, currentSize, centerX, centerY, ringRadius, ringWidth, currentHour24, currentMinute);

            // 4. 绘制精细刻度
            drawMinuteMarks(g2d, currentSize, centerX, centerY);

            // 5. 绘制数字
            g2d.setColor(numberColor);
            drawNumbers(g2d, currentSize, centerX, centerY);

            // 6. 绘制指针（带阴影）
            drawHandWithShadow(g2d, Math.toRadians(hour * 30 + minute * 0.5 - 90), 
                              (int)(45 * scale), (int)(5 * scale), hourHandColor, centerX, centerY);
            drawHandWithShadow(g2d, Math.toRadians(minute * 6 + second * 0.1 - 90), 
                              (int)(65 * scale), (int)(3 * scale), minuteHandColor, centerX, centerY);
            drawHandWithShadow(g2d, Math.toRadians(second * 6 - 90), 
                              (int)(75 * scale), (int)(1.5f * scale), secondHandColor, centerX, centerY);

            // 7. 绘制中心点（带高光）
            int centerDotSize = (int)(12 * scale);
            // 外圈阴影
            g2d.setColor(new Color(0, 0, 0, 60));
            g2d.fillOval(centerX - centerDotSize / 2 + 1, centerY - centerDotSize / 2 + 1, centerDotSize, centerDotSize);
            // 主体
            g2d.setColor(new Color(240, 240, 240));
            g2d.fillOval(centerX - centerDotSize / 2, centerY - centerDotSize / 2, centerDotSize, centerDotSize);
            // 高光
            int highlightSize = (int)(4 * scale);
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.fillOval(centerX - highlightSize / 2 - 1, centerY - highlightSize / 2 - 1, highlightSize, highlightSize);
        }

        /**
         * 绘制高亮圆环（Apple Watch 风格）
         * - 检测相邻时间段，中间用平头连接，两端用圆头
         * - 文字沿弧线切线方向排列
         */
        private void drawHighlightRings(Graphics2D g2d, int currentSize, int centerX, int centerY, 
                                       int ringRadius, int ringWidth, int currentHour24, int currentMinute) {
            
            // 按开始时间排序
            List<HighlightSetting> sortedAreas = new ArrayList<>(highlightAreas);
            sortedAreas.sort((a, b) -> {
                int aStart = a.getStartHour() * 60 + a.getStartMinute();
                int bStart = b.getStartHour() * 60 + b.getStartMinute();
                return Integer.compare(aStart, bStart);
            });
            
            for (int idx = 0; idx < sortedAreas.size(); idx++) {
                HighlightSetting setting = sortedAreas.get(idx);
                
                // 1. 将 24 小时制的 HH:MM 转换为总分钟数（0 到 1440）
                int startTotalMinutes = setting.getStartHour() * 60 + setting.getStartMinute();
                int endTotalMinutes = setting.getEndHour() * 60 + setting.getEndMinute();

                // 2. 转换为 12 小时制下的总分钟数 (0 到 720)
                int startMin12 = startTotalMinutes % 720;

                // 3. 计算起始角度和扫描角度
                float sweepStartAngle = 90f - (startMin12 * 0.5f);
                float sweepAngle;
                if (endTotalMinutes > startTotalMinutes) {
                    sweepAngle = (endTotalMinutes - startTotalMinutes) * 0.5f;
                } else {
                    sweepAngle = (1440 - startTotalMinutes + endTotalMinutes) * 0.5f;
                }

                // 4. 判断当前时间是否在此高亮区域内
                int currentTotalMinutes = currentHour24 * 60 + currentMinute;
                boolean isCurrentTimeInRange = isTimeInHighlightRange(currentTotalMinutes, startTotalMinutes, endTotalMinutes);
                boolean isHovered = (setting == hoveredSetting);

                Color baseColor = setting.getHighlightColor();
                
                // 5. 检测是否与前一个/后一个时间段相邻
                boolean hasAdjacentBefore = false;
                boolean hasAdjacentAfter = false;
                
                for (int j = 0; j < sortedAreas.size(); j++) {
                    if (j == idx) continue;
                    HighlightSetting other = sortedAreas.get(j);
                    int otherStart = other.getStartHour() * 60 + other.getStartMinute();
                    int otherEnd = other.getEndHour() * 60 + other.getEndMinute();
                    
                    // 检查是否有其他段的结束时间等于当前段的开始时间
                    if (otherEnd == startTotalMinutes) {
                        hasAdjacentBefore = true;
                    }
                    // 检查是否有其他段的开始时间等于当前段的结束时间
                    if (otherStart == endTotalMinutes) {
                        hasAdjacentAfter = true;
                    }
                }
                
                // 悬停时放大圆环宽度
                int actualRingWidth = isHovered ? (int)(ringWidth + 4 * scale) : ringWidth;
                
                // 6. 创建圆环形状
                // 使用填充方式绘制圆环（内外两个椭圆的差集），端点用半圆
                float halfWidth = actualRingWidth / 2.0f;
                float innerRadius = ringRadius - halfWidth;
                float outerRadius = ringRadius + halfWidth;
                
                // 创建主体弧形区域（使用扇形差集方式）
                java.awt.geom.Arc2D outerArc = new java.awt.geom.Arc2D.Float(
                    centerX - outerRadius, centerY - outerRadius,
                    outerRadius * 2, outerRadius * 2,
                    sweepStartAngle, -sweepAngle,
                    java.awt.geom.Arc2D.PIE
                );
                java.awt.geom.Arc2D innerArc = new java.awt.geom.Arc2D.Float(
                    centerX - innerRadius, centerY - innerRadius,
                    innerRadius * 2, innerRadius * 2,
                    sweepStartAngle, -sweepAngle,
                    java.awt.geom.Arc2D.PIE
                );
                
                java.awt.geom.Area ringArea = new java.awt.geom.Area(outerArc);
                ringArea.subtract(new java.awt.geom.Area(innerArc));
                
                // 在独立端点添加半圆帽子（圆心在弧线的端点上，半径为线宽的一半）
                if (!hasAdjacentBefore) {
                    // 起始端的半圆帽子
                    double startRad = Math.toRadians(sweepStartAngle);
                    float capCenterX = (float)(centerX + ringRadius * Math.cos(startRad));
                    float capCenterY = (float)(centerY - ringRadius * Math.sin(startRad));
                    java.awt.geom.Ellipse2D startCap = new java.awt.geom.Ellipse2D.Float(
                        capCenterX - halfWidth, capCenterY - halfWidth,
                        actualRingWidth, actualRingWidth
                    );
                    ringArea.add(new java.awt.geom.Area(startCap));
                }
                
                if (!hasAdjacentAfter) {
                    // 结束端的半圆帽子
                    double endRad = Math.toRadians(sweepStartAngle - sweepAngle);
                    float capCenterX = (float)(centerX + ringRadius * Math.cos(endRad));
                    float capCenterY = (float)(centerY - ringRadius * Math.sin(endRad));
                    java.awt.geom.Ellipse2D endCap = new java.awt.geom.Ellipse2D.Float(
                        capCenterX - halfWidth, capCenterY - halfWidth,
                        actualRingWidth, actualRingWidth
                    );
                    ringArea.add(new java.awt.geom.Area(endCap));
                }

                // 根据状态设置不同的绘制效果
                if (isCurrentTimeInRange) {
                    // 当前时间在范围内：柔和渐变发光效果（多层，从外到内透明度递增）
                    int glowLayers = 5;
                    float maxGlowSize = 8 * scale;
                    
                    for (int layer = glowLayers; layer >= 1; layer--) {
                        float layerGlowSize = maxGlowSize * layer / glowLayers;
                        float glowHalfWidth = halfWidth + layerGlowSize;
                        float glowInnerRadius = ringRadius - glowHalfWidth;
                        float glowOuterRadius = ringRadius + glowHalfWidth;
                        
                        java.awt.geom.Arc2D glowOuterArc = new java.awt.geom.Arc2D.Float(
                            centerX - glowOuterRadius, centerY - glowOuterRadius,
                            glowOuterRadius * 2, glowOuterRadius * 2,
                            sweepStartAngle, -sweepAngle,
                            java.awt.geom.Arc2D.PIE
                        );
                        java.awt.geom.Arc2D glowInnerArc = new java.awt.geom.Arc2D.Float(
                            centerX - glowInnerRadius, centerY - glowInnerRadius,
                            glowInnerRadius * 2, glowInnerRadius * 2,
                            sweepStartAngle, -sweepAngle,
                            java.awt.geom.Arc2D.PIE
                        );
                        
                        java.awt.geom.Area glowArea = new java.awt.geom.Area(glowOuterArc);
                        glowArea.subtract(new java.awt.geom.Area(glowInnerArc));
                        
                        // 发光层的端点帽子
                        if (!hasAdjacentBefore) {
                            double startRad = Math.toRadians(sweepStartAngle);
                            float capCenterX = (float)(centerX + ringRadius * Math.cos(startRad));
                            float capCenterY = (float)(centerY - ringRadius * Math.sin(startRad));
                            java.awt.geom.Ellipse2D startCap = new java.awt.geom.Ellipse2D.Float(
                                capCenterX - glowHalfWidth, capCenterY - glowHalfWidth,
                                glowHalfWidth * 2, glowHalfWidth * 2
                            );
                            glowArea.add(new java.awt.geom.Area(startCap));
                        }
                        if (!hasAdjacentAfter) {
                            double endRad = Math.toRadians(sweepStartAngle - sweepAngle);
                            float capCenterX = (float)(centerX + ringRadius * Math.cos(endRad));
                            float capCenterY = (float)(centerY - ringRadius * Math.sin(endRad));
                            java.awt.geom.Ellipse2D endCap = new java.awt.geom.Ellipse2D.Float(
                                capCenterX - glowHalfWidth, capCenterY - glowHalfWidth,
                                glowHalfWidth * 2, glowHalfWidth * 2
                            );
                            glowArea.add(new java.awt.geom.Area(endCap));
                        }
                        
                        // 透明度从外层到内层递增：外层最淡，内层较浓
                        int alpha = 15 + (glowLayers - layer) * 8;
                        Color glowColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
                        g2d.setColor(glowColor);
                        g2d.fill(glowArea);
                    }
                    
                    // 主体颜色加亮
                    g2d.setColor(brightenColor(baseColor, 1.2f));
                } else {
                    g2d.setColor(baseColor);
                }
                
                // 一次性填充合并后的形状，避免重叠
                g2d.fill(ringArea);

                // 7. 绘制标签（沿弧线切线方向排列）
                if (showLabels && setting.getLabel() != null && !setting.getLabel().trim().isEmpty()) {
                    float startAngle = startTotalMinutes * 0.5f;
                    float endAngle = endTotalMinutes * 0.5f;

                    float midAngleDeg;
                    if (endAngle > startAngle) {
                        midAngleDeg = (startAngle + endAngle) / 2.0f;
                    } else {
                        midAngleDeg = (startAngle + endAngle + 360) / 2.0f;
                        if (midAngleDeg >= 360) midAngleDeg -= 360;
                    }

                    double awtAngleRad = Math.toRadians(90 - midAngleDeg);
                    int labelRadius = (int) (ringRadius * 0.65);
                    int labelX = (int) (centerX + labelRadius * Math.cos(awtAngleRad));
                    int labelY = (int) (centerY - labelRadius * Math.sin(awtAngleRad));

                    java.awt.geom.AffineTransform oldTransform = g2d.getTransform();
                    
                    // 文字沿切线方向：切线角度 = 径向角度 + 90度
                    // midAngleDeg 是从12点顺时针的角度
                    // 切线方向应该是垂直于径向的
                    double tangentAngle = midAngleDeg;  // 切线方向（沿圆周）
                    
                    // 如果在下半圆（90-270度），文字会倒过来，需要翻转
                    if (midAngleDeg > 90 && midAngleDeg < 270) {
                        tangentAngle += 180;
                    }
                    
                    g2d.translate(labelX, labelY);
                    g2d.rotate(Math.toRadians(tangentAngle));

                    g2d.setColor(setting.getLabelColor());
                    Font labelFont = new Font("Microsoft YaHei", Font.BOLD, (int)(9 * scale));
                    g2d.setFont(labelFont);

                    FontMetrics fm = g2d.getFontMetrics();
                    String label = setting.getLabel().trim();
                    int labelWidth = fm.stringWidth(label);
                    int labelHeight = fm.getAscent();

                    g2d.drawString(label, -labelWidth / 2, labelHeight / 3);
                    
                    g2d.setTransform(oldTransform);
                }
            }
            
            g2d.setStroke(new BasicStroke(1));
        }

        /**
         * 绘制分钟刻度
         */
        private void drawMinuteMarks(Graphics2D g2d, int currentSize, int centerX, int centerY) {
            int radius = currentSize / 2;
            
            for (int i = 0; i < 60; i++) {
                double angle = Math.toRadians(i * 6 - 90);
                
                int outerRadius, innerRadius;
                Color markColor;
                float strokeWidth;
                
                if (i % 5 == 0) {
                    // 小时刻度（较长较粗）
                    outerRadius = (int)(radius * 0.98);
                    innerRadius = (int)(radius * 0.90);
                    markColor = new Color(numberColor.getRed(), numberColor.getGreen(), numberColor.getBlue(), 200);
                    strokeWidth = 2 * scale;
                } else {
                    // 分钟刻度（较短较细）
                    outerRadius = (int)(radius * 0.98);
                    innerRadius = (int)(radius * 0.94);
                    markColor = new Color(numberColor.getRed(), numberColor.getGreen(), numberColor.getBlue(), 80);
                    strokeWidth = 1 * scale;
                }
                
                int x1 = (int)(centerX + outerRadius * Math.cos(angle));
                int y1 = (int)(centerY + outerRadius * Math.sin(angle));
                int x2 = (int)(centerX + innerRadius * Math.cos(angle));
                int y2 = (int)(centerY + innerRadius * Math.sin(angle));
                
                g2d.setColor(markColor);
                g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.drawLine(x1, y1, x2, y2);
            }
        }

        /**
         * 绘制数字 - 增加与圆环的间距
         */
        private void drawNumbers(Graphics2D g2d, int currentSize, int centerX, int centerY) {
            Font font = new Font("Arial", Font.PLAIN, (int)(12 * scale));
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics(font);
            int radius = currentSize / 2;

            for (int i = 1; i <= 12; i++) {
                double angle = Math.toRadians(i * 30 - 90);
                // 数字位置稍微外移，增加与圆环的间距
                int x = Math.toIntExact(Math.round(centerX + radius * 0.82 * Math.cos(angle)));
                int y = Math.toIntExact(Math.round(centerY + radius * 0.82 * Math.sin(angle)));

                String num = String.valueOf(i);
                int strWidth = fm.stringWidth(num);
                int strHeight = fm.getAscent();
                
                // 数字颜色带透明度，更柔和
                g2d.setColor(new Color(numberColor.getRed(), numberColor.getGreen(), numberColor.getBlue(), 220));
                g2d.drawString(num, x - strWidth / 2, y + strHeight / 3);
            }
        }

        /**
         * 绘制带阴影的指针
         */
        private void drawHandWithShadow(Graphics2D g2d, double angle, int length, int thickness, Color color, int centerX, int centerY) {
            int x = (int) (centerX + length * Math.cos(angle));
            int y = (int) (centerY + length * Math.sin(angle));

            // 绘制阴影
            g2d.setColor(new Color(0, 0, 0, 40));
            g2d.setStroke(new BasicStroke(thickness + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawLine(centerX + 2, centerY + 2, x + 2, y + 2);

            // 绘制指针主体
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawLine(centerX, centerY, x, y);
        }

        /**
         * 判断当前时间是否在高亮区域范围内
         */
        private boolean isTimeInHighlightRange(int currentMinutes, int startMinutes, int endMinutes) {
            if (startMinutes <= endMinutes) {
                // 不跨越午夜的情况
                return currentMinutes >= startMinutes && currentMinutes < endMinutes;
            } else {
                // 跨越午夜的情况
                return currentMinutes >= startMinutes || currentMinutes < endMinutes;
            }
        }

        /**
         * 增亮颜色（用于渐变和发光效果）
         */
        private Color brightenColor(Color color, float factor) {
            int r = color.getRed();
            int g = color.getGreen();
            int b = color.getBlue();
            int alpha = color.getAlpha();
            
            // 增亮RGB值，但不超过255
            r = Math.min(255, (int)(r * factor));
            g = Math.min(255, (int)(g * factor));
            b = Math.min(255, (int)(b * factor));
            
            return new Color(r, g, b, alpha);
        }
    }

    // =========================================================================
    // 内部类：高亮区域设置 (更新)
    // =========================================================================

    class HighlightSetting {
        private int startHour;
        private int startMinute;
        private int endHour;
        private int endMinute;
        private Color highlightColor;
        private String label;
        private Color labelColor;
        
        private ClockConfig.TriggerConfig enter;
        private ClockConfig.TriggerConfig exit;
        private ClockConfig.TriggerConfig interval;

        public HighlightSetting(int startHour, int startMinute, int endHour, int endMinute, Color highlightColor, String label, Color labelColor) {
            this(startHour, startMinute, endHour, endMinute, highlightColor, label, labelColor, 
                 new ClockConfig.TriggerConfig(), new ClockConfig.TriggerConfig(), new ClockConfig.TriggerConfig());
        }

        public HighlightSetting(int startHour, int startMinute, int endHour, int endMinute, Color highlightColor, String label, Color labelColor,
                              ClockConfig.TriggerConfig enter, ClockConfig.TriggerConfig exit, ClockConfig.TriggerConfig interval) {
            this.startHour = startHour;
            this.startMinute = startMinute;
            this.endHour = endHour;
            this.endMinute = endMinute;
            this.highlightColor = highlightColor;
            this.label = label;
            this.labelColor = labelColor;
            this.enter = enter != null ? enter : new ClockConfig.TriggerConfig();
            this.exit = exit != null ? exit : new ClockConfig.TriggerConfig();
            this.interval = interval != null ? interval : new ClockConfig.TriggerConfig();
        }

        public int getStartHour() { return startHour; }
        public int getStartMinute() { return startMinute; }
        public int getEndHour() { return endHour; }
        public int getEndMinute() { return endMinute; }
        public Color getHighlightColor() { return highlightColor; }
        public String getLabel() { return label; }
        public Color getLabelColor() { return labelColor; }
        
        public ClockConfig.TriggerConfig getEnter() { return enter; }
        public ClockConfig.TriggerConfig getExit() { return exit; }
        public ClockConfig.TriggerConfig getInterval() { return interval; }

        public void setHighlightColor(Color highlightColor) {
            this.highlightColor = highlightColor;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void setLabelColor(Color labelColor) {
            this.labelColor = labelColor;
        }

        @Override
        public String toString() {
            String hexColor = String.format("#%06X", (0xFFFFFF & highlightColor.getRGB()));
            String labelPart = (label != null && !label.trim().isEmpty()) ? " [" + label + "]" : "";
            return String.format("<html><span style='background-color:%s;'>&nbsp;&nbsp;&nbsp;</span> %02d:%02d - %02d:%02d%s</html>",
                    hexColor, startHour, startMinute, endHour, endMinute, labelPart);
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
            JPanel panel = new JPanel(new GridLayout(8, 1, 10, 10)); // 8行
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));

            // 添加全局标签显示设置
            JPanel labelSettingPanel = new JPanel(new BorderLayout(10, 5));
            labelSettingPanel.add(new JLabel("在表盘显示标签:"), BorderLayout.WEST);
            JCheckBox showLabelsCheckBox = new JCheckBox();
            showLabelsCheckBox.setSelected(clockPanel.isShowLabels());
            showLabelsCheckBox.addActionListener(e -> {
                clockPanel.setShowLabels(showLabelsCheckBox.isSelected());
            });
            labelSettingPanel.add(showLabelsCheckBox, BorderLayout.EAST);
            panel.add(labelSettingPanel);

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

            // 添加双击监听器，支持双击编辑
            highlightList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int index = highlightList.locationToIndex(e.getPoint());
                        if (index >= 0) {
                            highlightList.setSelectedIndex(index);
                            addHighlightArea(listModel.getElementAt(index), false);
                        }
                    }
                }
            });

            panel.add(new JScrollPane(highlightList), BorderLayout.CENTER);
            panel.add(createHighlightButtonPanel(), BorderLayout.SOUTH);

            return panel;
        }

        private JPanel createHighlightButtonPanel() {
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

            JButton addButton = new JButton("添加新区域");
            addButton.addActionListener(e -> addHighlightArea(new HighlightSetting(9, 0, 18, 0,
                    clockPanel.getDefaultHighlightColor(), "", Color.WHITE), true));

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
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            // 1. 基本设置面板
            JPanel basicPanel = new JPanel(new GridLayout(7, 2, 5, 5));
            basicPanel.setBorder(BorderFactory.createTitledBorder("基本设置"));

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

            // 标签文本框
            JTextField labelField = new JTextField(settingToEdit.getLabel());

            // 高亮颜色按钮
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

            // 标签颜色按钮
            JButton labelColorButton = new JButton("选择标签颜色");
            labelColorButton.setBackground(settingToEdit.getLabelColor());
            labelColorButton.setOpaque(true);
            labelColorButton.setBorderPainted(false);
            final Color[] tempLabelColor = {settingToEdit.getLabelColor()};
            labelColorButton.addActionListener(e -> {
                Color selectedColor = JColorChooser.showDialog(this, "选择标签颜色", tempLabelColor[0]);
                if (selectedColor != null) {
                    tempLabelColor[0] = selectedColor;
                    labelColorButton.setBackground(tempLabelColor[0]);
                }
            });

            basicPanel.add(new JLabel("起始时间 (时):"));
            basicPanel.add(startHourSpinner);
            basicPanel.add(new JLabel("起始时间 (分):"));
            basicPanel.add(startMinuteSpinner);
            basicPanel.add(new JLabel("结束时间 (时):"));
            basicPanel.add(endHourSpinner);
            basicPanel.add(new JLabel("结束时间 (分):"));
            basicPanel.add(endMinuteSpinner);
            basicPanel.add(new JLabel("标签文本:"));
            basicPanel.add(labelField);
            basicPanel.add(new JLabel("区域颜色:"));
            basicPanel.add(colorButton);
            basicPanel.add(new JLabel("标签颜色:"));
            basicPanel.add(labelColorButton);
            
            mainPanel.add(basicPanel);
            mainPanel.add(Box.createVerticalStrut(10));

            // 2. 创建触发器设置面板
            TriggerPanel enterTriggerPanel = new TriggerPanel(settingToEdit.getEnter(), "进入", false, tempColor, tempLabelColor);
            enterTriggerPanel.setBorder(BorderFactory.createTitledBorder("进入触发"));
            mainPanel.add(enterTriggerPanel);
            mainPanel.add(Box.createVerticalStrut(10));
            
            TriggerPanel exitTriggerPanel = new TriggerPanel(settingToEdit.getExit(), "退出", false, tempColor, tempLabelColor);
            exitTriggerPanel.setBorder(BorderFactory.createTitledBorder("退出触发"));
            mainPanel.add(exitTriggerPanel);
            mainPanel.add(Box.createVerticalStrut(10));
            
            TriggerPanel intervalTriggerPanel = new TriggerPanel(settingToEdit.getInterval(), "间隔", true, tempColor, tempLabelColor);
            intervalTriggerPanel.setBorder(BorderFactory.createTitledBorder("间隔触发"));
            mainPanel.add(intervalTriggerPanel);

            // 使用 JScrollPane 包装，防止内容过长
            JScrollPane scrollPane = new JScrollPane(mainPanel);
            scrollPane.setPreferredSize(new Dimension(480, 600));
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            scrollPane.setBorder(null);

            int result = JOptionPane.showConfirmDialog(this, scrollPane,
                    isNew ? "添加新的高亮时间区域" : "编辑高亮时间区域", JOptionPane.OK_CANCEL_OPTION);

            if (result == JOptionPane.OK_OPTION) {
                int startH = (int) startHourSpinner.getValue();
                int startM = (int) startMinuteSpinner.getValue();
                int endH = (int) endHourSpinner.getValue();
                int endM = (int) endMinuteSpinner.getValue();
                String label = labelField.getText();

                // 校验逻辑
                int startTotalMinutes = startH * 60 + startM;
                int endTotalMinutes = endH * 60 + endM;

                if (startTotalMinutes == endTotalMinutes) {
                    JOptionPane.showMessageDialog(this, "起始时间和结束时间不能相同。", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 应用更改
                settingToEdit.startHour = startH;
                settingToEdit.startMinute = startM;
                settingToEdit.endHour = endH;
                settingToEdit.endMinute = endM;
                settingToEdit.setHighlightColor(tempColor[0]);
                settingToEdit.setLabel(label);
                settingToEdit.setLabelColor(tempLabelColor[0]);
                
                // 更新触发器配置
                settingToEdit.enter.action = enterTriggerPanel.getAction();
                settingToEdit.enter.text = enterTriggerPanel.getText();
                
                settingToEdit.exit.action = exitTriggerPanel.getAction();
                settingToEdit.exit.text = exitTriggerPanel.getText();
                
                settingToEdit.interval.action = intervalTriggerPanel.getAction();
                settingToEdit.interval.text = intervalTriggerPanel.getText();
                settingToEdit.interval.intervalMinutes = intervalTriggerPanel.getInterval();

                if (isNew) {
                    listModel.addElement(settingToEdit);
                } else {
                    highlightList.repaint();
                }

                updateClockHighlights();
            }
        }

        // 内部类：用于构建触发器设置面板
        class TriggerPanel extends JPanel {
            private JComboBox<String> actionCombo;
            private JTextField textField;
            private JSpinner intervalSpinner;
            private String[] actionValues = {"none", "dialog", "fullscreen", "lock"};
            private String[] actionNames = {"无", "弹窗提醒", "全屏提醒", "自动锁屏"};

            public TriggerPanel(ClockConfig.TriggerConfig config, String typeName, boolean isInterval, Color[] colorRef, Color[] labelColorRef) {
                setLayout(new GridBagLayout());
                setBorder(new EmptyBorder(10, 10, 10, 10));
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = new Insets(5, 5, 5, 5);
                
                // 动作选择
                gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
                add(new JLabel("触发动作:"), gbc);
                
                actionCombo = new JComboBox<>(actionNames);
                String currentAction = config != null ? config.action : "none";
                for (int i = 0; i < actionValues.length; i++) {
                    if (actionValues[i].equals(currentAction)) {
                        actionCombo.setSelectedIndex(i);
                        break;
                    }
                }
                gbc.gridx = 1; gbc.weightx = 0.7;
                add(actionCombo, gbc);

                // 间隔设置 (仅间隔触发)
                if (isInterval) {
                    gbc.gridx = 0; gbc.gridy++; gbc.weightx = 0.3;
                    add(new JLabel("触发间隔 (分钟):"), gbc);
                    
                    int currentInterval = config != null ? config.intervalMinutes : 30;
                    if (currentInterval <= 0) currentInterval = 30;
                    intervalSpinner = new JSpinner(new SpinnerNumberModel(currentInterval, 1, 1440, 1));
                    gbc.gridx = 1; gbc.weightx = 0.7;
                    add(intervalSpinner, gbc);
                }

                // 自定义文案
                gbc.gridx = 0; gbc.gridy++; gbc.weightx = 0.3;
                add(new JLabel("提醒文案:"), gbc);
                
                textField = new JTextField(config != null ? config.text : "");
                textField.setToolTipText("留空则使用默认文案");
                gbc.gridx = 1; gbc.weightx = 0.7;
                add(textField, gbc);
                
                // 预览按钮
                gbc.gridx = 1; gbc.gridy++; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.EAST;
                JButton previewBtn = new JButton("预览效果");
                previewBtn.addActionListener(e -> {
                    String selectedAction = actionValues[actionCombo.getSelectedIndex()];
                    String text = textField.getText();
                    if (text.isEmpty()) {
                        text = "预览: " + typeName + "触发";
                    }
                    previewAction(selectedAction, text, colorRef[0], labelColorRef[0]);
                });
                add(previewBtn, gbc);
            }

            public String getAction() {
                return actionValues[actionCombo.getSelectedIndex()];
            }

            public String getText() {
                return textField.getText();
            }

            public int getInterval() {
                return intervalSpinner != null ? (int) intervalSpinner.getValue() : 0;
            }
        }

        private void deleteHighlightArea() {
            int selectedIndex = highlightList.getSelectedIndex();
            if (selectedIndex != -1) {
                listModel.remove(selectedIndex);
                updateClockHighlights();
                
                // 删除后自动选中相邻的项，方便连续删除
                if (listModel.getSize() > 0) {
                    // 如果删除的是最后一项，选中新的最后一项
                    if (selectedIndex >= listModel.getSize()) {
                        highlightList.setSelectedIndex(listModel.getSize() - 1);
                    } else {
                        // 否则选中当前位置的项（原来的下一项）
                        highlightList.setSelectedIndex(selectedIndex);
                    }
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

            AnalogClock parent = (AnalogClock) getParent();
            parent.saveCurrentConfig();
            
            // 更新时间范围监控器
            if (parent.timeRangeMonitor != null) {
                parent.timeRangeMonitor.updateHighlightAreas(newSettings);
            }
        }

        // 预览触发效果
        private void previewAction(String action, String message, Color bgColor, Color textColor) {
            if ("none".equals(action)) {
                JOptionPane.showMessageDialog(this, "未设置触发动作", "预览", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            TimeRangeMonitor tempMonitor = new TimeRangeMonitor(new ArrayList<>());
            switch (action) {
                case "dialog":
                    tempMonitor.previewDialogNotification(message);
                    break;
                case "fullscreen":
                    tempMonitor.previewFullscreenNotification(message, bgColor, textColor);
                    break;
                case "lock":
                    int result = JOptionPane.showConfirmDialog(this, 
                        "确定要预览锁屏功能吗？\n这将真的锁定您的屏幕！", 
                        "确认", 
                        JOptionPane.YES_NO_OPTION, 
                        JOptionPane.WARNING_MESSAGE);
                    if (result == JOptionPane.YES_OPTION) {
                        tempMonitor.previewLockScreen();
                    }
                    break;
            }
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