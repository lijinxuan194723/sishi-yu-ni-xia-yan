# 原生状态保护修订：检查记录

本记录只涉及 `native/core`，不是完整原生 App 或 UI 的验收。

## JVM 逻辑测试：68 项通过

代码提交：`1e27c9483e96c6b40a8b9331ab6aa8b3af2d61ef`。

运行： https://github.com/lijinxuan194723/sishi-yu-ni-xia-yan/actions/runs/35091221487

Job `104777729405`，工作流 `Native migration checkpoint 210`。

实际命令：

```sh
gradle --no-daemon --console=plain --max-workers=2 :core:testDebugUnitTest
```

日志确认 `BUILD SUCCESSFUL`，XML 汇总：**68 tests，0 failures，0 errors，0 skipped**。报告产物 `native210-core-reports`，artifact id `10444457192`，20167 bytes。

组成：原有 NativeCoreTest 20 项、WeatherRepositoryTest 16 项、StateBoundaryTest 22 项、MemoryBatchingTest 10 项。

覆盖天气城市切换竞态、取消、缓存读写失败、异常预报；暂停学习保护、重复操作、倒计时替换及溢出检查；连续聊天上下文；ZIP 路径和技能身份；完整消息记忆分批、转义后请求预算、流式边界、旧摘要指纹失效以及非字符串／空摘要拒绝。

这些 JVM 用例不运行完整 Android 界面，平台方法仍使用测试桩；天气通过可注入协程 I/O 复现错误，不调用真实公共服务。

### 失败与修复记录

提交 `41df090d4939b69207436f4d1b3e189e103c38fb` 的运行 `35090425619` 执行 68 项，1 项失败。失败项 `MemoryBatchingTest.payloadBudgetIncludesJsonEscaping` 误用纯换行作为非空消息，而实现按设计排除空白消息。

提交 `1e27c94` 在该测试数据前加上正文，保留原来的批大小、编码后大小限制、正文完整相等三项断言；同时新增数值和布尔摘要不能通过校验的断言。这次修改不改变生产代码。复跑结果见上，未删掉失败断言或减少测试数量。

## Android Room 仪器测试：16 项通过

代码提交：`41df090d4939b69207436f4d1b3e189e103c38fb`。

运行： https://github.com/lijinxuan194723/sishi-yu-ni-xia-yan/actions/runs/35090425615

Job `104775257243`，工作流 `Native Room persistence 210`。

实际命令：

```sh
gradle --no-daemon --console=plain --max-workers=2 :core:connectedDebugAndroidTest
```

Android API 30 / x86_64 模拟器。日志确认 `Starting 16 tests`、`Finished 16 tests`、`BUILD SUCCESSFUL`。XML 汇总：**16 tests，0 failures，0 errors，0 skipped**。

该组使用 Android 上实际的 Room/SQLite 内存数据库，不是 JVM 上的平台方法桩。覆盖会话隔离、外键、消息序号冲突、消息身份不可变、更新正文、事务回滚、草稿和归档字段保护、笔记乐观版本、删除文件夹保留笔记、Skills 已审阅版本比较和卸载后迟到操作。

报告产物：`native210-room-reports`，artifact id `10443639970`，29435 bytes。GitHub Artifact 有保留期。

首次运行确实发现消息 `@Upsert` 对非主键唯一序号冲突没有按预期失败。保留原失败断言，改成事务内区分插入与更新、使用 ABORT 并限制身份变化后，16 项重新通过；没有删除冲突测试。

从 `41df090` 到 `1e27c94` 的比较仅包含该 JVM 测试文件和两份文档，生产代码与已通过的 16 项 Room 仪器测试代码均未变化。测试修订提交亦触发 Room 复跑 `35091221593`；本记录先以上述已完成并核对日志的运行作为通过依据，不把进行中的复跑算作通过。

## 尚不能据此声称完成的事项

没有全 App Assemble、安装启动、Compose UI、跟手手势、动态 Tab、Shared Element、Spring、粒子、磨砂／渐隐边缘、机械表盘或120Hz帧率验收。Room 测试不是文件磁盘数据库重启、空间不足或真实旧版备份迁移测试。模型输出质量、真实公共天气 API、手机闹钟权限和后台生存未验收。

记忆分批仍对单条超过 60,000 个序列化字符的历史消息明确失败并保留原文和检查点，尚未实现单条消息内部的断点分片。不能宣称所有长度的历史资料均已自动整理，也不能把测试通过解释为模型不会忘记任何语义细节。

原生界面批量写入被工具安全检查阻止，没有提交成功；本轮已停止该批上传。以上独立的核心状态修复已在迁移分支提交。没有合并 main，也没有发布新的原生 APK 或正式 Release。
