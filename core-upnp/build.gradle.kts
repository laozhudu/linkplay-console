plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * 通用 UPnP 渲染器（DLNA）—— **与 LinkPlay 专用实现并存，不互相替代**。
 *
 * ## 两条轨分开的原因
 *
 * 三台 DOSS 音箱是 LinkPlay 私有方案（`httpapi.asp`、`ControlDeviceInfo`、`slave_list` 组网），
 * 这些命令在标准 UPnP 里根本不存在，必须保留（`core-protocol`）。
 *
 * 而电视盒子、别的品牌音箱只说标准 UPnP：AVTransport / RenderingControl / ConnectionManager。
 * 它们的差异不是"协议不同"，而是"支持的服务不同"，所以这一层只按**标准服务**建模。
 *
 * ## 为什么复用 core-protocol 的 SOAP 工具
 *
 * `SoapEnvelope` / `UpnpXml` / `DeviceDescription` 是纯协议层，与品牌无关。
 * 复制一份就是"同一个能力两套实现"—— 项目为此重构过三次。
 * 所以依赖方向是单向的：`core-upnp → core-protocol`，绝不回头。
 */
dependencies {
    implementation(project(":core-protocol"))
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    testLogging { events("passed", "failed", "skipped") }
}
