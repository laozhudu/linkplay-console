package io.github.laozhudu.diagnostics

/**
 * 报告里的一段 —— 由"谁有状态谁自己渲染"的那个模块提供。
 *
 * @param id 唯一键，重复注册按覆盖处理（切换设备时同一个模块会重注册）
 * @param title 方括号里的标题
 * @param severity 这一段**当前**的健康度。是函数而不是固定值：
 *   注册只发生一次，而"设备全没应答"是运行中才出现的状态，
 *   注册时算一次就永远是注册那一刻的结论
 * @param render 返回多行文本，**自己负责缩进**（两空格）
 */
class Section(
    val id: String,
    val title: String,
    val severity: () -> Severity,
    val render: () -> String,
)
