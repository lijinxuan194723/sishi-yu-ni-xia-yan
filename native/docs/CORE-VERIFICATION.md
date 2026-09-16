# 原生数据层检查结果

检查代码提交：`0a3aa58a12b153822f4cf322d1a5b0bf4244b66e`

GitHub Actions：
https://github.com/lijinxuan194723/sishi-yu-ni-xia-yan/actions/runs/35085338608

Job：`core`，编号 `104758709973`。

实际执行命令：

```sh
gradle --no-daemon --console=plain --max-workers=2 :core:testDebugUnitTest
```

结果：`BUILD SUCCESSFUL`。JUnit XML 汇总：20 tests，0 failures，0 errors，0 skipped。第一次检查发现了节假日 JSON 编码作用域及 Flow import 编译错误，修复后重新运行通过；没有删掉失败断言来伪造通过。

## 覆盖范围

- 默认全局缩放 95%、聊天字号 13；四季月份映射及手动季节覆盖。
- 日期合法性及闰年。
- 模型请求地址规范化，拒绝 HTTP、URL 内嵌认证信息及查询参数。
- SSE 多行数据事件。
- 倒计时同一启动周期使用单调时间，系统时间更改不影响剩余时长；重启时回退期限、暂停不扣时、上下界。
- 滑尺分钟范围，学习记录跨午夜分段。
- 长期事实只接受用户原话中的连续证据；被遗忘、禁用来源和 AI 自述不会成为事实证据；原文改变导致指纹改变。
- ZIP 路径检查、技能安装默认停用、压缩包不执行脚本。
- 旧主存档原文及未知字段保留，结构缺失时拒绝空导入。
- 缺失天气信息不生成伪造数值。

## 不能据此得出的结论

这不是全应用测试，不覆盖 Compose UI、Shared Element、实际手势、磨砂层、粒子、真机 120Hz、Android 输入法、图库、通知／闹钟权限、真实公共接口、真实模型质量或完整旧备份往返迁移。

Room DAO 已通过 KAPT 和 Java/Kotlin 编译，但未进行设备数据库仪器测试。Core 测试中 Android 平台方法使用 Gradle 单元测试桩。各个 feature 目录存在不代表对应界面已经完成。

产物 `native210-core-reports` 包含 JUnit XML、HTML 报告、Room schema 和 summary.json；artifact id：`10441866742`。没有生成、签名或发布新的完整应用 APK。
