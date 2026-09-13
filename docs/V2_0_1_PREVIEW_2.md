# 四时与你 2.0.1-preview.2 · 连续动效修复

基于 2.0.1-preview.1（b8ad1eb6995a0d58ebbf1252a5226116f3e71a49），保留原有圆润开屏、聊天、笔记及背景设置。候选 Android versionCode：902002。最终以 APK 内的版本字段、签名及校验值为准。

## 本次针对的问题

上一版的实际 Web 包仍带有失效的 data-started 全局动画门控，以及两组无条件 transition:none!important。这些规则会压过组件动画。部分弹窗在关闭时立即卸载，图片切换逻辑也没有连续过渡。不能靠单纯调大 CSS duration 解决这些问题。

移除冲突规则及未使用的 Web 开屏样式，在 app/motion.css、lib/motion.ts 中统一节奏：页面/弹窗进入 320ms，退出 240ms，照片交叉淡化 520ms，光照 720ms。背景装饰与操作反馈分开管理，不用全局 transition:all。

原生圆章采用均衡的加速/减速曲线，已准备好的页面保持位置不动，由遮罩以 380ms 退出。入场矢量不再在 pageReady 时强行停到终点，系统栏从当前颜色起算过渡。无重复 Web 开屏，不依赖网络动画素材。

移动构建将超大照片限制到最长边 1920 像素及约 250 万像素，原始仓库素材不变；原生开屏退出期间暂停背景粒子，避免同时绘制多个动态层。

## 生命周期与安全

- Base UI 对话框在退出完成后才移除父组件；独立话题的保存失败保护不变。
- 手记编辑器、笔记本和局部菜单保留退出帧，退出中不可误点；快速反向操作从当前画面继续。
- 图片解码后才开始淡化，旧图在底层保持不透明。已开始的切换自然完成后再处理最后一次选择，避免尾帧回闪。
- 相册更换季节时使用旧图覆盖必要的轮播几何重置，不裸露空白或跳到第一张。
- 聊天流式文字按绘制帧合并发布，按 Unicode 码点前进；后台、取消或减少动态效果时保留已经收到的文字，不等待暂停的绘制计时器。
- 保留系统减少动态效果设置；隐藏文档时完成瞬时界面过渡，暂停装饰动画。

没有改动聊天/笔记存储键、WebView origin、模型密钥、网络权限或签名方式。预览包不能作为正式版覆盖升级包。需对上一交付 APK 比较包名、证书与递增版本号，并测试不卸载安装和测试笔记保留。

## 验证命令与交付门槛

```sh
npm run test:detail-polish
npm run test:motion
python3 scripts/checks/check-rounded-startup.py
python3 scripts/checks/motion_browser.py --root work/mobile-web
python3 scripts/checks/motion_android.py
```

Motion Verification 工作流读取同一提交、同一分支已完成的 Preview APK。浏览器测试直接使用 APK 中的 Web 资源，采集中间帧、退出完成、快速反转、多尺寸及减少动态效果。Android 测试在 API 30/35 隔离模拟器中安装实际签名包，保存冷启动视频、原生过渡计时和界面操作结果。

自动测试失败的候选包不作为本次交付包。逻辑测试、样式契约、浏览器渲染和 Android 运行验证应分别报告，不能相互替代。通过覆盖到的场景不意味着已证明所有手机、系统或网络配置都不存在缺陷。

最终交付记录应包含：实际版本/构建编号、完整提交 SHA、对应构建与验证 run、APK 文件名/SHA-256、签名与覆盖安装核验、测试通过范围及未验证项目。工作分支为 fix/motion-continuity，稳定签名预览交付分支仍为 fix/rounded-native-startup；未合并 main 或发布正式 Release。

Android 11 模拟器原始 WebView 为 83，不能运行现有 ES2022 界面；测试环境使用官方 AOSP 128 预编译 WebView，并记录来源和校验值，不随安装包分发。模拟器分辨率 720×1280、density 320；Android 15 使用镜像自带的 WebView 124。测试成功不能扩大解释为兼容所有旧版 WebView。
