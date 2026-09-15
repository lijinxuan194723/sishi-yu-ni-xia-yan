/** Public build metadata only. Never include a model key or user record here. */
export const RELEASE_INFO = Object.freeze({
  version: '2.0.5',
  androidVersionCode: 902010,
  channel: '本地应用',
  base: 'v2.0.4 + 设置、聊天与自动长期记忆',
  repository: 'https://github.com/lijinxuan194723/sishi-yu-ni-xia-yan',
  branch: 'release/v2.0.5-complete',
});
export const RELEASE_SOURCE_URL = `${RELEASE_INFO.repository}/tree/${RELEASE_INFO.branch}`;
export const RELEASE_BUILDS_URL = `${RELEASE_INFO.repository}/actions/workflows/review-isolated205.yml`;
export function releaseSummary(): string {
  return `四时与你 ${RELEASE_INFO.version}（${RELEASE_INFO.channel}）\nAndroid versionCode：${RELEASE_INFO.androidVersionCode}\n代码分支：${RELEASE_INFO.branch}\n源码位置：${RELEASE_SOURCE_URL}\n构建位置：${RELEASE_BUILDS_URL}\n安装前先导出完整备份；不同包名的应用不会自动共享数据。`;
}
