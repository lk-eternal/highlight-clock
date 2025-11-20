# 介绍
退出自动保存记录,打开自动加载记录
<br>
中键滚轮缩放,鼠标随意拖动位置
<br>
<img width="199" height="199" alt="image" src="https://github.com/user-attachments/assets/40062b5b-f726-4ea3-8b3c-3bcf0e4cd937" />

<br>
双击指定区域快速添加/编辑高亮区域或右键唤起菜单
<br>
<img width="279" height="279" alt="image" src="https://github.com/user-attachments/assets/b0bcc339-8268-40fd-b111-17d1b977e233" />
<img width="279" height="279" alt="image" src="https://github.com/user-attachments/assets/adac3ea8-7535-409f-9ef3-1ccfe2b9c874" />
<br>
<img width="386" height="493" alt="image" src="https://github.com/user-attachments/assets/de06c858-599e-4bd0-9dbe-e4ef98e6d69f" />
<img width="386" height="493" alt="image" src="https://github.com/user-attachments/assets/ee647849-21bd-4e25-8bf2-48c0e308a266" />
<br>
<img width="358" height="363" alt="image" src="https://github.com/user-attachments/assets/a875bedf-c42d-4e42-97d0-64e68c0fa9ba" />
<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/83edd740-4b62-49ec-9acf-16272061ef0e" />

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
