# 四时与你 · 原生迁移工作区

状态：正在迁移，尚不替代 2.0.9。这个目录是独立 Kotlin / Jetpack Compose 工程；旧 React 工程只保留在分支的父目录用于逐项比对，不会加入原生 APK。没有合并 main 或创建 Release。

架构：app + core + designsystem + 七个 feature 模块；MVVM、StateFlow、Room、DataStore。

迁移要求：跟手 Pager、动态指示器、共享元素、可中断弹簧、四季粒子、真实通透滚动边缘；多会话、Skills、头像/气泡独立配置；默认 95% / 13；天气、正计时、拖尺倒计时；保留计划、日历、长期记忆、推荐、手记与备份。

验收原则：保留旧备份原文；禁止 destructive migration；导入不能静默覆盖本机记录；不因动画延迟业务状态；不把源码检查或编译成功冒充真机 120Hz 性能验收。具体完成状态随迁移清单更新。
