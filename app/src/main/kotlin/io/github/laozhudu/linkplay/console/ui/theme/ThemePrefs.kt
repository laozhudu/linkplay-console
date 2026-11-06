package io.github.laozhudu.linkplay.console.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外观：跟随系统 / 始终浅色 / 始终深色。
 *
 * ## 为什么默认「跟随系统」
 *
 * 绝大多数人只在系统里设一次深浅，App 跟着走就对了。给一个默认值却不跟随系统，
 * 等于让用户在两个地方各设一次、还可能互相矛盾。
 *
 * ## 为什么用 `StateFlow` 而不是回调
 *
 * 切主题要**立刻生效**，包括状态栏图标（`LinkPlayTheme` 里的 `SideEffect` 依赖
 * `darkTheme` 这个布尔值）。用 `StateFlow` + `collectAsState`，改一处、整棵树跟着重绘，
 * 不需要任何页面自己去通知别处刷新。
 */
enum class ThemeMode(val id: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色"),
    ;

    companion object {
        fun of(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

/**
 * 主题偏好的唯一读写口。
 *
 * 用 `SharedPreferences` 而不是数据库：这是一行配置，读写频率低；
 * 而且**首帧就要用**（`setContent` 之前），数据库首次打开可能慢到能看见白屏。
 */
object ThemePrefs {

    private const val FILE = "linkplay_theme"
    private const val KEY_MODE = "mode"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _mode = MutableStateFlow(ThemeMode.System)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** 幂等：Activity 重建会再调一次，重复调没有副作用。 */
    fun init(context: Context) {
        val p = prefs ?: context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .also { prefs = it }
        _mode.value = ThemeMode.of(p.getString(KEY_MODE, null))
    }

    fun set(context: Context, mode: ThemeMode) {
        init(context)
        _mode.value = mode
        prefs?.edit()?.putString(KEY_MODE, mode.id)?.apply()
    }

    /**
     * 归一成"现在到底该不该用深色"。
     *
     * @param systemDark 系统当前的深色状态（Compose 的 `isSystemInDarkTheme()`）
     */
    fun resolve(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
}
