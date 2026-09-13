# 柔光圆章 · Android 开屏优化

基于 v2.0.0 的细节优化提交 a3ce3511dd65bfefa11d8c1504d48e797fcda383，保留此前独立聊天优化。

## 视觉与节奏

保留钥匙意象，使用圆润钥匙轮廓、同心圆章和四季色点；没有方形绿色底块、幕布、翻牌、闪光、夸张弹跳和旋转。浅色暖米白、深色墨绿，按 Android 系统日夜模式选择启动配色；进入应用后交还给原有四季主题。

矢量徽章以 520ms 缓慢展开，退出用 260ms 轻微收拢及淡出。没有最短显示时间：页面更早就绪时可直接结束，不必播放完毕。没有新增网络资源或动画依赖。

## 原生接入

- Android 12+ 使用系统 SplashScreen、AnimatedVectorDrawable 和退出回调，页面首帧准备完成后释放预绘制门控，不另播第二段开屏。
- Android 8–11 使用同一矢量圆章与原生浮层；不改 WebView 的尺寸和缩放，避免文字变糊、底栏跳动。
- 使用原有 pageReady / postVisualStateCallback；8 秒仍未完成时给出圆角重试卡片，不能永久卡在开屏。
- 重试不清空应用记录；对旧视觉回调加入 generation 检查；销毁时清理计时、绘制监听及动画。
- 关闭系统动画时直接切换。动画仅在 Activity 创建时接入，普通后台回前台不重复创建开屏。

未修改正式包名、WebView origin、存储键、签名配置、桌面图标或聊天逻辑。没有更新正式版号或发布正式版 APK。

## 构建依赖

恢复 Preview APK #129 成功使用的 package-lock.json（blob 3ede08c13d24f710713ba7334a7c9b370f58d6ea，来自 664fd2a33dee201e65b8026f212a54c56c859f78）。该提交的直接依赖与当前 package.json 一致，包含之前缺失的 @emnapi/core 和 @emnapi/runtime。锁文件顶层保留历史 0.1.0 元数据；应用 package.json 和 Android 版本不变。本次不修改工作流、不关闭 npm ci 校验、不新增依赖。

## 参考

参考缓动和过渡模式，不复制第三方实现，也未引入其库：
- https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md
- https://github.com/android/animation-samples
- https://developer.android.com/develop/ui/views/launch/splash-screen

## 验证边界

静态资源引用和生命周期检查脚本：

```sh
python3 scripts/checks/check-rounded-startup.py
```

本地 Java 8 语法解析通过，但语法解析不等于 Android SDK 编译。独立 Chromium 动效预览通过 17 项模拟检查，包括六种窗口尺寸的日夜模式、退出、超时重试、过期回调、减少动态效果和无最短等待。这些不是 Android 真机测试，预览也不是安装包。

仍须完成：完整依赖安装、移动 Web 构建、Android 8/12/15 真机或模拟器测试、签名及覆盖升级核验。原有预览工作流使用独立测试包名，不能当作覆盖正式应用的升级包。不要卸载原应用。
