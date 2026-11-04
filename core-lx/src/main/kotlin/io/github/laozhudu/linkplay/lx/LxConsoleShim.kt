package io.github.laozhudu.linkplay.lx

/**
 * 把原生 `console` 换成纯 JS 实现 —— **必须在 preload / bundle 之前注入**。
 *
 * ## 真机报错（0.9.110）
 *
 * ```
 * com.whl.quickjs.wrapper.QuickJSException:
 *   When invoke console stuff, you should be set a stdout of platform to console.stdout.
 *     at stdout (console.js:239)
 *     at print (console.js:266)
 *     at log (console.js:243)
 *     at <anonymous> (lx-preload.js:593)
 * ```
 *
 * `lx-preload.js` 最后一行是 `console.log('Preload finished.')`，而 quickjs-wrapper 的
 * 原生 console 要求宿主先往 `console.stdout` 设一个输出端 —— 没设就打一行日志都抛异常。
 *
 * ## 为什么用 JS 顶掉原生 console，而不是去设 wrapper 的 stdout
 *
 * 1. `LxEngine` 这层接口没有暴露 stdout 的设置（要改接口 + 三处实现 + 假引擎）；
 * 2. preload 末尾会 `freezeObjectProperty(globalThis)`，之后连替换 console 都做不到 ——
 *    必须**在它之前**动手；
 * 3. 用户脚本跑在冻结环境里，把 console 接到 native 桥等于往"被冻结的对象"里塞回调，
 *    是新的失败点。
 *
 * 所以：**先保证不崩**。日志不丢，先攒在 JS 里（最多 20 条），
 * `__lx_console_tail()` 能取出来 —— 以后要接进诊断随时能接，不必再改这里。
 */
object LxConsoleShim {

    const val FILENAME = "lx-console-shim.js"

    val SCRIPT: String = """
        (function (g) {
          var lines = [];
          var make = function (level) {
            return function () {
              try {
                var parts = [];
                for (var i = 0; i < arguments.length; i++) {
                  var a = arguments[i];
                  parts.push(typeof a === 'string' ? a : String(a));
                }
                lines.push(level + ': ' + parts.join(' '));
                if (lines.length > 20) lines.shift();
              } catch (e) {}
            };
          };
          g.console = {
            log: make('log'),
            info: make('info'),
            warn: make('warn'),
            error: make('error'),
            debug: make('debug'),
            trace: make('trace')
          };
          g.__lx_console_tail = function () { return lines.join('\n'); };
        })(typeof globalThis !== 'undefined' ? globalThis : this);
    """.trimIndent()
}
