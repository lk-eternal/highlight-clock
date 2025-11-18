package com.lk;

import java.awt.Color;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize; // 导入
import com.fasterxml.jackson.databind.annotation.JsonSerialize;   // 导入

// 导入自定义的序列化/反序列化工具类
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
        public int startMinute; // 【新增】
        public int endHour;
        public int endMinute;   // 【新增】

        @JsonSerialize(using = ConfigManager.ColorSerializer.class)
        @JsonDeserialize(using = ConfigManager.ColorDeserializer.class)
        public Color highlightColor;

        public SerializableHighlightSetting() {}

        // 【修改】构造函数
        public SerializableHighlightSetting(int startH, int startM, int endH, int endM, Color color) {
            this.startHour = startH;
            this.startMinute = startM;
            this.endHour = endH;
            this.endMinute = endM;
            this.highlightColor = color;
        }
    }
}