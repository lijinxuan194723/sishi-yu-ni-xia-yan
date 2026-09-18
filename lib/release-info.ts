/** Public build metadata only. Never include a model key or user record here. */
export const RELEASE_INFO = Object.freeze({
  version: '2.0.10',
  androidVersionCode: 902063,
  channel: 'WebView 指针与计时排版修复版',
  revision: 'hotfix.3-visible-clock-hands',
  base: '已上传 WebView 2.0.10 hotfix.2 完整源码 + 指针/时钟/计时排版修复',
  repository: 'https://github.com/lijinxuan194723/sishi-yu-ni-xia-yan',
  branch: 'feature/v2.0.10-webview-polish',
});
export const RELEASE_SOURCE_URL = `${RELEASE_INFO.repository}/tree/${RELEASE_INFO.branch}`;
export const RELEASE_BUILDS_URL = `${RELEASE_INFO.repository}/actions/workflows/review-isolated205.yml`;
export function releaseSummary(): string {
  return `四时与你 ${RELEASE_INFO.version}（${RELEASE_INFO.channel}）\nAndroid versionCode：${RELEASE_INFO.androidVersionCode}\n建议开发分支（不代表已同步）：${RELEASE_INFO.branch}\n仓库分支参考（未同步）：${RELEASE_SOURCE_URL}\n本轮完整源码随交付包提供，不从旧工作流下载本版。\n安装前先导出完整备份；不同包名的应用不会自动共享数据。`;
}
