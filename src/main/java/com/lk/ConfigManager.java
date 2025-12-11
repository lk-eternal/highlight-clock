package com.lk;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private static final String CONFIG_DIR_NAME = ".lkclock";
    private static final String CONFIG_FILE_NAME = "clock_config.json";
    private static final File CONFIG_DIR;
    private static final File CONFIG_FILE;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    static {
        // 配置文件存储在用户目录下的 .lkclock 文件夹
        String userHome = System.getProperty("user.home");
        CONFIG_DIR = new File(userHome, CONFIG_DIR_NAME);
        CONFIG_FILE = new File(CONFIG_DIR, CONFIG_FILE_NAME);
        
        // 确保配置目录存在
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
    }

    // --- 自定义 Color 序列化器和反序列化器 ---

    /**
     * 将 Color 对象序列化为 #AARRGGBB 或 #RRGGBB 字符串。
     */
    public static class ColorSerializer extends JsonSerializer<Color> {
        @Override
        public void serialize(Color color, JsonGenerator jsonGenerator,
                              SerializerProvider serializerProvider) throws IOException {

            // 如果颜色是完全不透明的，只使用 #RRGGBB 格式
            if (color.getAlpha() == 255) {
                // #RRGGBB
                String hex = String.format("#%06X", (0xFFFFFF & color.getRGB()));
                jsonGenerator.writeString(hex);
            } else {
                // 如果是半透明的，使用 #AARRGGBB 格式
                // 注意：Color.getRGB() 返回的是 AARRGGBB，但 Java 是小端序
                String hex = String.format("#%08X", (0xFFFFFFFF & color.getRGB()));
                jsonGenerator.writeString(hex);
            }
        }
    }

    /**
     * 将 #AARRGGBB 或 #RRGGBB 字符串反序列化为 Color 对象。
     */
    public static class ColorDeserializer extends JsonDeserializer<Color> {
        @Override
        public Color deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            String hex = jsonParser.getText();

            if (hex == null || hex.isEmpty()) return Color.BLACK;

            // 移除 # 号
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }

            try {
                long value = Long.parseLong(hex, 16);

                if (hex.length() == 6) {
                    // #RRGGBB 格式，默认 Alpha 为 255 (不透明)
                    return new Color((int) value | 0xFF000000, true);
                } else if (hex.length() == 8) {
                    // #AARRGGBB 格式
                    return new Color((int) value, true);
                } else {
                    System.err.println("WARN: 颜色格式不正确: " + jsonParser.getText() + ", 默认黑色");
                    return Color.BLACK;
                }
            } catch (NumberFormatException e) {
                System.err.println("WARN: 颜色值无法解析: " + jsonParser.getText() + ", 默认黑色");
                return Color.BLACK;
            }
        }
    }

    // --- 配置读写方法 (保持不变) ---

    public static ClockConfig loadConfig() {
        if (CONFIG_FILE.exists()) {
            try {
                return MAPPER.readValue(CONFIG_FILE, ClockConfig.class);
            } catch (IOException e) {
                // ... (保持不变)
            }
        }
        return new ClockConfig();
    }

    public static void saveConfig(ClockConfig config) {
        try {
            MAPPER.writeValue(CONFIG_FILE, config);
        } catch (IOException e) {
            // ... (保持不变)
        }
    }
}