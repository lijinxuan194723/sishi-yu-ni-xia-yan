# 四时与你 · WebView 2.0.10 功能修订版

本工程保留 **Android WebView + React**，与原生Compose迁移分开。完整功能代码、静态数据、素材、锁文件和Android构建入口都在本包里，不是仅头像配置补丁。

本轮起点是实际取回的GitHub WebView基线与已核验的外观兼容修改；不是原始2.0.9完整ZIP的逐字节恢复。日历双源、外观、科目、会话、Skills、天气与计时动效等已按现有要求补齐，具体实现和不能宣称已验收的边界见 [交付说明](docs/WEBVIEW-2.0.10-DELIVERY.md)。没有写入远端、改动main或Compose分支。

## 构建

```sh
npm ci --no-audit --no-fund
npm run check:types
npm run test:unit
npm run build:mobile
node mobile/build-local.mjs --sdk /path/to/android-sdk --unsigned --skip-web
```

需要Node22.13+、JDK和Android SDK平台/构建工具35。当前已完成415项单元检查、89项离线浏览器检查及真正Android壳编译/签名；报告在 `verification/webview210-functional`。生产代码曾被所有浏览器套件测试，APK资源逐字节核对。未做真机安装、相册、输入法、覆盖升级、真实IndexedDB/CSP、实时接口或120Hz性能验收。

**先读 [BUILD-WebView-2.0.10.md](BUILD-WebView-2.0.10.md)**。不同包名/签名无法覆盖旧版。提供的APK使用独立包名`com.luke.summer.webview210`，不自动共享旧数据；先保留旧版并导出备份。源码没有密钥，不会自动生成所谓“升级签名”。

## 实现要点

右上角对话目录支持新建、历史、归档、重命名和导出；全局原文账本保留索引，近期上下文分会话。双方头像/气泡独立，默认95%与13px，旧配置兼容。自动记忆在联网前台空闲后自行整理并核对原文，不需手动点击。Skills导入和启用要确认，正文及引用资料可进入模型请求，但不执行任意脚本。

主页面跟手滑动与指示器共用Motion进度；透明标题/悬浮输入框、图片共享转场和可中断弹簧适配旧风格。天气采用Open-Meteo并按四季配色，不使用来历不明的客户端签名。计时新增外围刻度/扫针和拖尺倒计时，原学习记录、番茄钟和系统提醒入口保留。

本地节假日JSON为2023—2026，静态优先/Timor缺失备用，只有取得有效年度数据后才标记。手记、读后感、科目CRUD、收藏与偏好推荐分别验证。原历史文档保留作资料，当前事实以交付说明和本批报告为准。

## 浏览器回归

```sh
python scripts/checks/chat-compat-browser.py
python scripts/checks/browser-functional210.py
python scripts/checks/browser-layout210.py
python scripts/checks/browser-memory210.py
```

需要Python/Playwright/Chromium，脚本明确是生产资源离线回放，使用测试存储和网络替身；测试夹具不进入安装包。源码可在具备网络的本机重新安装锁定依赖；本轮环境通过既有离线依赖包恢复，没有伪称fresh npm ci成功。

许可证见 `docs/licenses` 与随APK静态资源打包的 `public/licenses`。用户提供的透明图标只裁透明边、等比例缩小，没有添加底板；原人物素材版权说明延续既有项目。只有手动验证工作流，没有自动推送、主线合并或Release。
