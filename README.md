# 高亮时钟

<img width="223" height="216" alt="image" src="https://github.com/user-attachments/assets/b788e015-6142-4f99-966b-a9591007825a" />


## 什么是高亮时钟?
高亮时钟是一款高效管理时间的工具,具有以下功能:

### 基本功能
- 置顶显示(可切换,可隐藏到托盘)
- 平滑秒针动画(60fps)
- 窗口透明度调节(10%-100%)
- 退出自动保存配置,打开自动加载配置
- 配置文件存储在用户目录 `%USERPROFILE%\.lkclock\`

### 自定义表盘
- 预设主题(深色经典、浅色简约、护眼绿、暗夜蓝、暖橙色)
- 表盘背景颜色
- 表盘数字颜色
- 时针颜色
- 分针颜色
- 秒针颜色

### 自定义高亮时间区间
- 高亮颜色
- 文字标签
- 触发事件(可预览)
  - 事件类型
    - 进入触发
    - 退出触发
    - 间隔触发
  - 触发行为
    - 弹窗提醒,支持自定义文案
    - 全屏提醒,支持自定义文案(空格/回车/ESC关闭)
    - 自动锁屏
  - 声音提醒(可选)

### 全局快捷键
- `Alt + C` - 显示/隐藏时钟
- `Alt + T` - 切换置顶状态(会显示Toast提示)

### 开机自启动
- 在设置面板中勾选"开机自启动"即可

## 操作指南
- 中键滚轮缩放
- 鼠标随意拖动位置
- 点击高亮区域快速编辑此高亮区域
- 右键唤起菜单
- 托盘图标双击显示/隐藏

## 启动方式

### 系统要求
- Java 21 或更高版本
- Windows 操作系统

### 方式一：直接启动
编辑 `clock.vbs`:
```shell
Set WshShell = CreateObject("WScript.Shell")
WshShell.Run """D:\Apps\Java\jdk-21.0.2\bin\javaw.exe"" -jar ""clock.jar""", 0, False
```
双击 `clock.vbs` 启动

### 方式二：命令行启动
```bash
D:\Apps\Java\jdk-21.0.2\bin\java -jar clock.jar
```

## 配置文件位置
配置文件存储在用户目录下，无论从哪里启动都会读取同一份配置：
- Windows: `C:\Users\用户名\.lkclock\clock_config.json`
