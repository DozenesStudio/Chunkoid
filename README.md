# Chunkoid

众所周知，在 Chunker 网页转换器下架多年以来，MC 世界转换方面就几乎被电脑端软件所垄断。

在这期间，想在安卓端转换世界存档，门槛极高，需要技术基础，零基础小白几乎无从下手。

我在制作了上一期视频之后，历经了长期观察，发现很少接触 Windows 电脑的手机/平板的玩家群体需求量很大。很多学生党、出差党、甚至是没有电脑的玩家，想把自己手机上的基岩版存档转到电脑上的 Java 版，或者反过来，都只能望洋兴叹。

想弥补这方面的空白，**急需一款移动端的手机转换器！**

于是——

***

## Chunkoid，现已发布！

一款专为安卓端打造的 Minecraft 世界转换器，无需电脑、无需命令行、无需技术基础，点点按钮就能完成转换。

### 核心功能

- **界面简洁自然**，一目了然，熟悉的味道，零学习成本
- **双端互转 / 版本升降**，电脑上有的，手机也有
- **日志输出**，白箱运行，每一步都看得见，出问题不迷茫
- **输出管理器**，转换结果统一管理，导入导出更方便

### 更多功能规划

> 展示【材质转换】【存档解密】选项

材质转换、存档解密、线上地图下载……更多功能正在开发中，请继续关注 Chunkoid！

## 下载

- [GitHub Releases](https://github.com/DozenesStudio/Chunkoid/releases)
- [官网](https://dozenesstudio.github.io/Chunkoid/)

## 使用说明

### 基本使用

1. 打开应用，点击"选择世界"按钮
2. 选择要转换的世界文件（支持 .zip、.mcworld 格式）
3. 选择目标平台和版本
4. 点击"开始转换"按钮
5. 等待转换完成
6. 在输出管理器中查看或导出转换后的世界

### 高级设置

- **JVM 最大内存**：转换大存档时建议增加内存分配
- **保留原始 NBT**：保留世界中的原始 NBT 数据
- **外观切换**：在设置中选择喜欢的壁纸主题

## 技术栈

- **语言**：Kotlin
- **框架**：Android SDK, Material Components
- **架构**：MVVM
- **构建工具**：Gradle

## 开源协议

本项目采用 GNU General Public License v3.0 协议开源。详见 [LICENSE](LICENSE) 文件。

## 联系方式

- **作者**：Dozener
- **邮箱**：[DozenesStudio@qq.com](mailto:contact@dozenesstudio.com)
- **官网**：<https://dozenesstudio.github.io/Chunkoid/>

## 致谢

Ryan steven

Weiyin 1A

以及图片提供者：Offical Bean

### 第三方依赖

本项目使用了以下开源软件：

- **chunker-cli** (v1.15.0) - 核心转换引擎，源自 [chunker](https://github.com/MCChunker/Chunker) 开源项目
  - 许可证：MIT License
  - 文件位置：`app/src/main/assets/chunker-cli-1.15.0.jar`

## 许可证

Copyright © 2024 DozenesStudio. Licensed under the GNU General Public License v3.0.
