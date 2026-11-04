package io.github.laozhudu.linkplay.protocol.media

import java.io.OutputStream

/**
 * 单次连接的发送进度（`serve` 里 new，随连接走）。
 *
 * [written] 由 [CountingOutputStream] 每写一次累加，[total] 在发响应头前设定。
 * 两者一起才能在诊断里写出「已发 1.2MB / 共 3.4MB」——
 * 没有这个，"客户端断了"和"文件根本没读出来"是同一种现象。
 */
internal class Progress {
    @Volatile var written: Long = 0L
    @Volatile var total: Long = 0L
}

/**
 * 计数的输出流包装。
 *
 * 为什么需要：本地服务和转发服务都要统计"这一条连接到底发出去了多少"，
 * 但发送路径五花八门（整文件、Range 片段、内存 m3u、`copyTo` 透传）。
 * 如果每处各写一次 `written += n`，漏一处就是统计说谎 —— 而统计说谎比没有统计更坏。
 * 所以统一在流这一层数：**只要字节从 socket 出去，就一定被数到**。
 */
internal class CountingOutputStream(
    private val delegate: OutputStream,
    private val progress: Progress = Progress(),
) : OutputStream() {

    val written: Long get() = progress.written

    fun expect(total: Long) {
        progress.total = total
    }

    override fun write(b: Int) {
        delegate.write(b)
        progress.written += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        progress.written += len
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}
