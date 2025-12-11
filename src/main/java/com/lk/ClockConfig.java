package com.lk;

import java.awt.Color;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.lk.ConfigManager.ColorDeserializer;
import com.lk.ConfigManager.ColorSerializer;

/**
 * 时钟的所有可配置属性
 */
public class ClockConfig {
    // 窗口属性
    public float scale = 1.0f;
    public int windowX = 100;
    public int windowY = 100;
    public boolean alwaysOnTop = true; // 窗口是否置顶
    public float opacity = 1.0f; // 窗口透明度 (0.0-1.0)

    // 全局设置：是否在表盘显示标签
    public boolean showLabels = true;

    // 颜色属性 - 直接使用 Color 类型，并指定序列化/反序列化器
    @JsonSerialize(using = ColorSerializer.class)
    @JsonDeserialize(using = ColorDeserializer.class)
    public Color clockColor = new Color(50, 50, 50, 255);

    @JsonSerialize(using = ColorSerializer.class)
    @JsonDeserialize(using = ColorDeserializer.class)
    public Color defaultHighlightColor = new Color(0xDD, 0x77, 0x0, 80);

    @JsonSerialize(using = ColorSerializer.class)
    @JsonDeserialize(using = ColorDeserializer.class)
    public Color numberColor = Color.WHITE;

    @JsonSerialize(using = ColorSerializer.class)
    @JsonDeserialize(using = ColorDeserializer.class)
    public Color hourHandColor = Color.WHITE;

    @JsonSerialize(using = ColorSerializer.class)
    @JsonDeserialize(using = ColorDeserializer.class)
    public Color minuteHandColor = Color.LIGHT_GRAY;

    @JsonSerialize(using = ColorSerializer.class)
    @JsonDeserialize(using = ColorDeserializer.class)
    public Color secondHandColor = Color.RED;

    // 高亮区域列表
    public List<SerializableHighlightSetting> highlightAreas;

    public ClockConfig() {}

    // 触发器配置类
    public static class TriggerConfig {
        public String action = "none"; // "none", "dialog", "fullscreen", "lock"
        public String text; // 自定义文案
        public int intervalMinutes = 0; // 仅用于间隔触发
        public boolean playSound = false; // 是否播放提示音

        public TriggerConfig() {}
        
        public TriggerConfig(String action, String text) {
            this.action = action;
            this.text = text;
        }

        public TriggerConfig(String action, String text, int intervalMinutes) {
            this.action = action;
            this.text = text;
            this.intervalMinutes = intervalMinutes;
        }
    }

    // 辅助类：用于存储高亮区域的可序列化版本
    public static class SerializableHighlightSetting {
        public int startHour;
        public int startMinute;
        public int endHour;
        public int endMinute;

        // 标签文本
        public String label;

        // 标签颜色
        @JsonSerialize(using = ConfigManager.ColorSerializer.class)
        @JsonDeserialize(using = ConfigManager.ColorDeserializer.class)
        public Color labelColor;

        @JsonSerialize(using = ConfigManager.ColorSerializer.class)
        @JsonDeserialize(using = ConfigManager.ColorDeserializer.class)
        public Color highlightColor;

        // 触发器配置
        public TriggerConfig enter = new TriggerConfig();
        public TriggerConfig exit = new TriggerConfig();
        public TriggerConfig interval = new TriggerConfig();

        public SerializableHighlightSetting() {}

        // 兼容旧配置的 Setter
        @JsonSetter("enterAction")
        public void setLegacyEnterAction(String action) {
            if (this.enter == null) this.enter = new TriggerConfig();
            this.enter.action = action;
        }

        @JsonSetter("exitAction")
        public void setLegacyExitAction(String action) {
            if (this.exit == null) this.exit = new TriggerConfig();
            this.exit.action = action;
        }

        // 构造函数
        public SerializableHighlightSetting(int startH, int startM, int endH, int endM, Color color, String label, Color labelColor, 
                                          TriggerConfig enter, TriggerConfig exit, TriggerConfig interval) {
            this.startHour = startH;
            this.startMinute = startM;
            this.endHour = endH;
            this.endMinute = endM;
            this.highlightColor = color;
            this.label = label;
            this.labelColor = labelColor;
            this.enter = enter != null ? enter : new TriggerConfig();
            this.exit = exit != null ? exit : new TriggerConfig();
            this.interval = interval != null ? interval : new TriggerConfig();
        }
    }
}
