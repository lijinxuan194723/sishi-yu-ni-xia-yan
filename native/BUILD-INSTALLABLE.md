# 2.0.10 原生开发版：生成可以直接安装的 APK

这是仍在逐步迁移的原生开发版本，不是已完成全部功能的 2.0.9 替代品。首页、悄悄话、计时、设置及 Skills 已接入实际 MainActivity。三个尚未整合的页面明确提示迁移状态，不把未显示的旧记录解释为空。请保留旧应用和完整备份。

## 正确的构建目标

在本目录（含 settings.gradle.kts 的 native 目录）使用 JDK 17、Gradle 8.11.1、Android SDK Platform 35。将本机 SDK 路径写入未提交的 local.properties，例如 `sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk`。

运行：

```sh
gradle --no-daemon --console=plain --max-workers=2 :app:assembleSideload
```

安装的文件只有：`app/build/outputs/apk/sideload/app-sideload.apk`。

- 它是已签名、非 testOnly 的单 APK，包含 ARM/ARM64 与模拟器所需原生库；不是需要拆分安装的 App Bundle。
- 最低 Android 8.0（API 26），包名 `cn.sishiyuni.nativeapp.preview`，桌面名称“四时与你·原生”，启动入口 `cn.sishiyuni.app.MainActivity`。
- 不要发送 `app-release-unsigned.apk`；它尚未签名。
- 不要安装 `*-androidTest.apk` 或 `uitesthost` 来代替应用；它们只用于仪器测试。
- 2.0.9 的包名和数据独立，不会自动覆盖或搬入旧记录。

本工程没有内置 Gradle Wrapper。已安装 Gradle 后可执行 `gradle wrapper --gradle-version 8.11.1 --distribution-type bin`，以后使用 `./gradlew :app:assembleSideload`（Windows 为 `gradlew.bat`）。

## 签名与后续覆盖安装

未提供额外配置时，sideload 使用构建电脑的 Android debug keystore，但应用本身按 release 配置优化且不可调试。请保留这台电脑的 `~/.android/debug.keystore`，不要将其提交到公开仓库。不同电脑或不同 CI 运行生成的密钥可能不同，因此不能承诺你本机构建的 APK 可以覆盖另一台电脑构建的同包名 APK。

需要固定私有签名时，在 Android Studio 创建并妥善备份自己的 keystore，然后为构建进程设置：

```text
LUKE_KEYSTORE=私有密钥文件的绝对路径
LUKE_STORE_PASSWORD=密钥库口令
LUKE_KEY_ALIAS=实际别名
LUKE_KEY_PASSWORD=密钥口令
```

同样执行 `:app:assembleSideload`，即可使用指定签名。设置了路径但缺少口令时构建直接失败，不会悄悄换用其他签名。不要把这些口令写进提交的源码或日志。

同包名覆盖要求兼容的签名与不降低的 versionCode。遇到签名不匹配，不要通过卸载旧应用或清除数据来“解决”；先确保已有记录已经安全导出。这个开发包不是旧版签名的升级包。

## 本项目的安装验收

`.github/workflows/native210-installable.yml` 构建一次 APK，再将同一份已签名文件下载到 Android API 26、30、35 模拟器安装，不使用 `adb install -t` 绕过测试包限制。脚本从桌面入口启动实际应用，输入新草稿，切换计时，保留数据重装同一 APK，然后核验草稿。

另外对 debug 应用执行真实 Activity 的界面测试；只对专用 androidTest 包使用 `-t`。这和分发 APK 的普通安装验收分开记录。测试结果必须对应具体 commit 与 APK SHA-256。模拟器通过不等于所有品牌真机、完整功能迁移、未知旧备份兼容或 120Hz 性能通过。
