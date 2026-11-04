/**
 * 替换洛雪的 `src/store/setting/state.ts`。
 *
 * musicSdk 只读这几个值（主要是 common.apiSource 用来判断走内置还是用户源）。
 * 我们永远走"内置 SDK 搜索"，所以固定成 user_api 前缀之外的值也不需要，
 * 这里给一份稳定默认值即可。
 */
const state = {
  setting: {
    'common.apiSource': 'builtin',
    'common.sourceNameType': 'alias',
    'player.playQuality': '320k',
    'download.savePath': '',
    'download.fileName': '歌名 - 歌手',
    'list.isShowSource': true,
  } as Record<string, any>,
  setSetting(patch: Record<string, any>) {
    Object.assign(state.setting, patch)
  },
}

export { state }
export default state
