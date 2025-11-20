package com.lk;

import java.awt.Color;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

        // 进入和退出触发效果: "none", "dialog", "fullscreen", "lock"
        public String enterAction = "none";
        public String exitAction = "none";

        public SerializableHighlightSetting() {}

        // 构造函数
        public SerializableHighlightSetting(int startH, int startM, int endH, int endM, Color color, String label, Color labelColor, String enterAction, String exitAction) {
            this.startHour = startH;
            this.startMinute = startM;
            this.endHour = endH;
            this.endMinute = endM;
            this.highlightColor = color;
            this.label = label;
            this.labelColor = labelColor;
            this.enterAction = enterAction != null ? enterAction : "none";
            this.exitAction = exitAction != null ? exitAction : "none";
        }
    }
}
