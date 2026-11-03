package io.github.laozhudu.linkplay.protocol.player

/**
 * 设备切换的「代际号」。
 *
 * ## 解决的是什么问题
 *
 * 换设备时，上一台设备的请求可能还在飞（读 `description.xml`、拉快照、读队列）。
 * 它们返回时如果无条件写状态，就会把**上一台设备**的曲名/音量/队列盖到新设备界面上。
 * 用户实测的现象是：「切到另一台后先是报错串台，等一会儿又自己好了」——
 * 那次"自己好了"，就是旧设备的下一次轮询被新设备的数据盖过去。
 *
 * ## 怎么用
 *
 * ```kotlin
 * val token = gen.current()        // 发起请求前取票
 * ...suspend 请求...
 * if (gen.isStale(token)) return   // 回来先验票，过期就整份丢弃
 * ```
 *
 * 换设备时调 [bump]，之前发出的所有票立即作废。
 *
 * ## 为什么不用「取消协程」就够了
 *
 * HTTP 请求完全可能在 `cancel()` 落地的同一刻返回，取消不是同步屏障。
 * 票号比较是最后一道闸，成本只有一个 int 比较。
 */
class DeviceGeneration {

    @Volatile
    private var value: Int = 0

    /** 当前票号。请求发出前取一次。 */
    fun current(): Int = value

    /** 作废所有已发出的票，返回新票号。 */
    @Synchronized
    fun bump(): Int {
        value++
        return value
    }

    /** 这张票是否已经过期（期间发生过 [bump]）。 */
    fun isStale(token: Int): Boolean = token != value
}
