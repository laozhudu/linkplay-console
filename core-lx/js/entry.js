/**
 * core-lx 的 musicSdk 入口。
 *
 * 作用：把洛雪五个平台的模块包成**回调式**接口，供 Kotlin 调用。
 *
 * ## 为什么是回调式而不是直接返回 Promise
 *
 * QuickJS 的 Java 绑定能调用 JS 函数，但拿不到 Promise 的最终值（没有 await）。
 * 所以这里收一个 reqId，做完之后主动通过 `__lx_native_call__` 把结果推回去，
 * Kotlin 侧按 reqId 匹配（和用户脚本的 requestKey/response 是同一套路子）。
 *
 * ## 通道
 *
 *   Kotlin -> JS : globalThis.lxSdkCall(reqId, action, payloadJson)
 *   JS -> Kotlin : __lx_native_call__(key, 'sdkResponse', {reqId, ok, data|error})
 */
import { nativeCall } from '@/bridge'
import wy from './src/utils/musicSdk/wy/index.js'
import kw from './src/utils/musicSdk/kw/index.js'
import tx from './src/utils/musicSdk/tx/index.js'
import mg from './src/utils/musicSdk/mg/index.js'
import kg from './src/utils/musicSdk/kg/index.js'

const MODULES = { wy, kw, tx, mg, kg }

/** 支持的平台，供 Kotlin 侧展示 */
const SOURCES = ['wy', 'kw', 'tx', 'mg', 'kg']

function mod(source) {
  const m = MODULES[source]
  if (!m) throw new Error(`不支持的平台: ${source}`)
  return m
}

/**
 * 每个 action 一个实现。统一返回可 JSON 序列化的值。
 *
 * 注意：**只做搜索/详情类操作，不做取播放直链** —— 洛雪 2.x 已把内置取流关掉
 * （`api-source-info.ts` 是空数组，`apis()` 直接抛 `Api is not found`），
 * 直链必须由用户自装脚本提供（见 LxUserApi）。
 */
const ACTIONS = {
  /** 按歌名搜。payload: {source, name, page, limit} */
  async search({ source, name, page = 1, limit = 30 }) {
    const r = await mod(source).musicSearch.search(name, page, limit)
    return { list: r.list || [], total: r.total || 0, allPage: r.allPage || 1 }
  },

  /** 热搜词。payload: {source} */
  async hotSearch({ source }) {
    const r = await mod(source).hotSearch.getList()
    return { list: r.list || r || [] }
  },

  /** 歌单搜索。payload: {source, name, page, limit} */
  async songListSearch({ source, name, page = 1, limit = 30 }) {
    const r = await mod(source).songList.search(name, page, limit)
    return { list: r.list || [], total: r.total || 0 }
  },

  /** 歌单详情。payload: {source, id, page, limit} */
  async songListDetail({ source, id, page = 1, limit = 1000 }) {
    const r = await mod(source).songList.getListDetail(id, page, limit)
    return { list: r.list || [], total: r.total || 0 }
  },

  /** 歌词。payload: {source, musicInfo} */
  async lyric({ source, musicInfo }) {
    const r = await mod(source).getLyric(musicInfo)
    return r || {}
  },

  /** 封面地址。payload: {source, musicInfo} */
  async pic({ source, musicInfo }) {
    const url = await mod(source).getPic(musicInfo)
    return { url: url || '' }
  },

  /** 排行榜列表。payload: {source} */
  async leaderboard({ source }) {
    const r = await mod(source).leaderboard.getBoards()
    return { boards: r.boards || [] }
  },

  /** 平台清单 */
  async sources() {
    return { sources: SOURCES }
  },
}

globalThis.lxSdkCall = (reqId, action, payloadJson) => {
  let payload = {}
  try {
    payload = payloadJson ? JSON.parse(payloadJson) : {}
  } catch (e) {
    nativeCall('sdkResponse', { reqId, ok: false, error: 'payload 不是合法 JSON' })
    return
  }
  const fn = ACTIONS[action]
  if (!fn) {
    nativeCall('sdkResponse', { reqId, ok: false, error: `不支持的 action: ${action}` })
    return
  }
  Promise.resolve()
    .then(() => fn(payload))
    .then((data) => nativeCall('sdkResponse', { reqId, ok: true, data }))
    .catch((e) => nativeCall('sdkResponse', { reqId, ok: false, error: String((e && e.message) || e) }))
}

globalThis.__lx_sdk_ready__ = true
