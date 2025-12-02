# 高亮时钟

<img width="223" height="216" alt="image" src="https://github.com/user-attachments/assets/b788e015-6142-4f99-966b-a9591007825a" />


## 什么是高亮时钟?
高亮时钟是一款高效管理时间的工具,具有以下功能:
- 强置顶(永远在前,可隐藏当托盘)
- 自定义表盘
  - 表盘背景颜色
  - 表盘数字颜色
  - 时针颜色
  - 分针颜色
  - 秒针颜色
- 自定义高亮时间区间
  - 高亮颜色
  - 文字标签
  - 触发事件(可预览)
    - 事件类型
      - 进入触发
      - 退出触发
      - 间隔触发
    - 触发行为
      - 弹窗提醒,支持自定义文案
      - 全屏提醒,支持自定义文案
      - 自动锁屏
- 退出自动保存当前配置,打开自动加载上次配置

### 操作指南
- 中键滚轮缩放
- 鼠标随意拖动位置
- 点击高亮区域快速编辑此高亮区域
- 右键唤起菜单

# 启动
编辑`run.bat`
设置JAVA_HOME,请使用java 21及以上环境
```bat
@echo off
set "JAVA_HOME=D:\Apps\Java\jdk-21.0.2"
"%JAVA_HOME%\bin\java" -jar "clock.jar"
EXIT
```
双击`lk-clock.vbs`启动

# 开机自动启动
创建`lk-clock.vbs`的快捷方式
按下`Win+R`,输入`shell:startup`
复制快捷方式到启动目录
