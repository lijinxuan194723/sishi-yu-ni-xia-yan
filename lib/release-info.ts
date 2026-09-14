/** Public build metadata only. Never include a model key or user record here. */
export const RELEASE_INFO = Object.freeze({
  version: '2.0.1-preview.6',
  androidVersionCode: 902006,
  channel: '预览版',
  base: 'v2.0.0 + 圆润开屏与独立聊天优化',
  repository: 'https://github.com/lijinxuan194723/sishi-yu-ni-xia-yan',
  branch: 'fix/rounded-native-startup',
});
export const RELEASE_SOURCE_URL = `${RELEASE_INFO.repository}/tree/${RELEASE_INFO.branch}`;
export const RELEASE_BUILDS_URL = `${RELEASE_INFO.repository}/actions/workflows/preview-apk.yml`;
export function releaseSummary(): string {
  return `四时与你 ${RELEASE_INFO.version}（${RELEASE_INFO.channel}）\nAndroid versionCode：${RELEASE_INFO.androidVersionCode}\n代码分支：${RELEASE_INFO.branch}\n源码位置：${RELEASE_SOURCE_URL}\n构建位置：${RELEASE_BUILDS_URL}\n预览版与正式版并存，不会自动读取正式版数据。`;
}
