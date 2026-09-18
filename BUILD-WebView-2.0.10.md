# WebView 2.0.10 hotfix.3 本机打包

版本2.0.10 / versionCode902063。本批 `hotfix.3-visible-clock-hands`，直接基于hotfix.2。Node22.13+、JDK、本机Android Platform35和Build Tools35.0.0。依赖版本和锁文件没有变化。

```sh
npm ci --no-audit --no-fund
npm run check:types
npm run test:unit
npm run build:mobile
node mobile/build-local.mjs --sdk "/path/to/android-sdk" --unsigned --skip-web
```

未签名包不能直接安装。使用你已有的签名和旧版真实包名，才有可能覆盖升级；保留旧数据并先导出备份。签名操作由现有构建脚本提供，不会自动冒充旧证书。

```sh
node mobile/build-local.mjs --sdk "/path/to/android-sdk" --skip-web   --app-id com.luke.summer.webview210fix3 --label "四时与你·修复3"   --keystore "/private/your-key.p12" --alias your-alias   --store-pass-file "/private/password.txt"
```

上面是独立包示例；持有旧密钥时应替换为旧版实际包名、标签和原密钥。`--key-pass-file` 可指定独立私钥口令。Windows可直接运行同一Node入口或mobile/build-local.ps1，命令行续行写法需按PowerShell调整。源码不含此次私钥。

## 浏览器复测

需要Python、Playwright、Chromium及Pillow。以下读取本次生产构建的JS/CSS，但使用明确的MemoryStorage/Android网络测试替身，不是CSP或真机数据库验证。

```sh
node scripts/checks/build-hotfix2-harness.mjs
python scripts/checks/browser-clock-hotfix3.py
python scripts/checks/browser-functional210.py
python scripts/checks/browser-layout210.py
python scripts/checks/browser-weather-timer-hotfix.py
python scripts/checks/chat-compat-browser.py
python scripts/checks/browser-memory210.py
python scripts/checks/browser-detail-hotfix2.py
```

截图/中间帧/几何矩阵在work/clock-hotfix3，其余历史命名脚本仍读当前生产文件。交付报告在verification/clock-hotfix3。不得将测试harness、SDK、work或outputs里的私密材料加入正式源码或APK。

本轮恢复已提供的固定离线依赖，没有重新联网npm ci；实际Android设备、相册、输入法、通知和安装迁移未验收。
