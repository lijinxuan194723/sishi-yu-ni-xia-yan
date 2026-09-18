# 夏彦角色依据与接口说明

核验日期：2026-09-09。

## 夏彦设定

- 私家侦探、阳光开朗、行动技能与青梅竹马的感情：官方主要角色介绍 https://tot.tw.hoyoverse.com/tw/zh-tw/information/all/detail/129025
- 回到未名市、重逢背景：官方首支宣传报道 https://wd.mihoyo.com/information/detail/3625
- 生日 12 月 5 日：官方生日公告 https://wd.mihoyo.com/information/detail/113871
- 立绘：https://tot.hoyoverse.com/en-us/character

系统提示词在 lib/model.ts。角色事实与同人演绎规则分开；不把预设台词当成用户亲历，不编造未知卡面台词，不主动展开病情等剧情。问到现实身份时如实回答 AI 同人聊天。

## 当前2.0.10模型与记忆

HTTPS Chat Completions模型由用户配置，代码不附带共享Key。每段对话独立保留近期上下文，长期原文账本和有引用的事实持续保留；前台空闲自动整理，允许更正、固定和忘记。密钥存储/备份边界见实际lib/model-credentials.ts与lib/full-backup.ts，不能把旧历史文档的sessionStorage说明当作本版状态。

## 当前天气

仅Open-Meteo，不请求彩云；支持实况、小时、七天、体感、湿度和风速。天气同来源/同坐标缓存，过期数据不作为实时上下文。场景动画使用四季主题，不复制小米天气协议固定签名或Smartisan原版资源。

背景/锁屏提醒仍使用用户确认的系统时钟入口；网页动画不是精确闹钟。公开分发前需核对数据提供方署名要求。当前测试使用模拟响应，不代表真实提供方或模型质量验收。
