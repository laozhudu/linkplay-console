package io.github.laozhudu.linkplay.lx

/**
 * 给 QuickJS 补一个**最小 Node Buffer**。
 *
 * ## 为什么需要
 *
 * 真机实测（0.9.98，`docs/74` 预言的那个缺口）：
 *
 * ```
 * QuickJSException: 'Buffer' is not defined
 *   at <anonymous> (lx-music-sdk.bundle.js:7948)
 * ```
 *
 * 报错点在 `kw/decodeLyric.js` —— 酷我歌词是 `tp=content` + inflate + base64，
 * 那段代码直接用 `Buffer.from` / `Buffer.isBuffer` / `.subarray` / `.indexOf` / `.toString`。
 * QuickJS 只实现了 ECMAScript 标准，`Buffer` 是 Node 的全局对象，不存在。
 *
 * ## 为什么自己写而不是引 npm 的 buffer 包
 *
 * 引包要多一条构建链（npm install + 重新打 bundle + 体积变大），
 * 而这里需要的语义很小（`from / isBuffer / alloc / concat` + 几个实例方法）。
 * 手写的好处是**能直接拿去 Node 里和真实 Buffer 对照验证**——
 * 本轮就是那么验的：15 组用例（UTF-8 中文往返、base64 与 Node 输出一致、
 * subarray、indexOf 定位 CRLF、kw 歌词解码全流程）全过。
 *
 * ## 注入时机
 *
 * 必须在 bundle / preload **之前** evaluate。放在这里而不是 assets，
 * 是为了让 `LxMusicSdk` / `LxUserApi` 不依赖 `Context`。
 */
internal object LxBufferPolyfill {

    const val FILENAME = "lx-buffer-polyfill.js"

    val SCRIPT: String = """
/*
 * 最小 Node Buffer 垫片 —— 给 QuickJS 用。
 *
 * 真机实测（0.9.98）：bundle 在 kw 的歌词解码里用了 Buffer，
 * QuickJS 没有这个全局对象 → `'Buffer' is not defined at lx-music-sdk.bundle.js:7948`。
 *
 * 只实现 bundle 实际用到的部分：
 *   Buffer.from(string|Uint8Array|ArrayBuffer|Array, encoding?)
 *   Buffer.isBuffer / alloc / concat
 *   .length / .subarray / .slice / .indexOf / .equals / .toString(enc, start, end)
 *
 * 为什么不引 npm 的 buffer 包：那要多一条构建链（npm install + 打 bundle），
 * 而这里需要的语义很小，手写一份可以直接在 Node 里对照验证。
 */
(function () {
  var g = typeof globalThis !== "undefined" ? globalThis : this;
  if (g.Buffer) return;

  function utf8Encode(str) {
    var out = [];
    for (var i = 0; i < str.length; i++) {
      var c = str.charCodeAt(i);
      if (c < 0x80) {
        out.push(c);
      } else if (c < 0x800) {
        out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
      } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < str.length) {
        var c2 = str.charCodeAt(++i);
        var cp = 0x10000 + ((c - 0xd800) << 10) + (c2 - 0xdc00);
        out.push(
          0xf0 | (cp >> 18),
          0x80 | ((cp >> 12) & 0x3f),
          0x80 | ((cp >> 6) & 0x3f),
          0x80 | (cp & 0x3f)
        );
      } else {
        out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
      }
    }
    return new Uint8Array(out);
  }

  function utf8Decode(b, start, end) {
    var s = "";
    var i = start;
    while (i < end) {
      var c = b[i++];
      if (c < 0x80) {
        s += String.fromCharCode(c);
      } else if (c < 0xe0 && i <= end) {
        s += String.fromCharCode(((c & 0x1f) << 6) | (b[i++] & 0x3f));
      } else if (c < 0xf0 && i + 1 <= end) {
        s += String.fromCharCode(((c & 0x0f) << 12) | ((b[i++] & 0x3f) << 6) | (b[i++] & 0x3f));
      } else if (i + 2 <= end) {
        var cp =
          ((c & 0x07) << 18) | ((b[i++] & 0x3f) << 12) | ((b[i++] & 0x3f) << 6) | (b[i++] & 0x3f);
        cp -= 0x10000;
        s += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
      }
    }
    return s;
  }

  var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

  function base64Encode(b) {
    var s = "";
    for (var i = 0; i < b.length; i += 3) {
      var has1 = i + 1 < b.length;
      var has2 = i + 2 < b.length;
      var n = (b[i] << 16) | ((has1 ? b[i + 1] : 0) << 8) | (has2 ? b[i + 2] : 0);
      s += B64[(n >> 18) & 63] + B64[(n >> 12) & 63];
      s += has1 ? B64[(n >> 6) & 63] : "=";
      s += has2 ? B64[n & 63] : "=";
    }
    return s;
  }

  function base64Decode(str) {
    var t = String(str).replace(/[^A-Za-z0-9+/=]/g, "");
    var out = [];
    for (var i = 0; i < t.length; i += 4) {
      var c0 = B64.indexOf(t[i]);
      var c1 = B64.indexOf(t[i + 1]);
      var c2 = t[i + 2] === "=" ? -1 : B64.indexOf(t[i + 2]);
      var c3 = t[i + 3] === "=" ? -1 : B64.indexOf(t[i + 3]);
      if (c0 < 0 || c1 < 0) break;
      var n = (c0 << 18) | (c1 << 12) | ((c2 < 0 ? 0 : c2) << 6) | (c3 < 0 ? 0 : c3);
      out.push((n >> 16) & 255);
      if (c2 >= 0) out.push((n >> 8) & 255);
      if (c3 >= 0) out.push(n & 255);
    }
    return new Uint8Array(out);
  }

  function hexEncode(b) {
    var s = "";
    for (var i = 0; i < b.length; i++) s += (b[i] < 16 ? "0" : "") + b[i].toString(16);
    return s;
  }

  function hexDecode(str) {
    var t = String(str).replace(/[^0-9a-fA-F]/g, "");
    var out = new Uint8Array(Math.floor(t.length / 2));
    for (var i = 0; i < out.length; i++) out[i] = parseInt(t.substr(i * 2, 2), 16);
    return out;
  }

  function toBytes(input, encoding) {
    if (input == null) return new Uint8Array(0);
    if (input instanceof Uint8Array) return new Uint8Array(input);
    if (typeof ArrayBuffer !== "undefined" && input instanceof ArrayBuffer) {
      return new Uint8Array(input);
    }
    if (Array.isArray(input)) return new Uint8Array(input);
    if (typeof input === "string") {
      var enc = encoding ? String(encoding).toLowerCase() : "utf8";
      if (enc === "base64") return base64Decode(input);
      if (enc === "hex") return hexDecode(input);
      return utf8Encode(input);
    }
    if (typeof input.length === "number") {
      var u = new Uint8Array(input.length);
      for (var i = 0; i < input.length; i++) u[i] = input[i] & 0xff;
      return u;
    }
    return new Uint8Array(0);
  }

  /** 把 Uint8Array 包成"够用的 Buffer"：补 toString / subarray / indexOf 等方法。 */
  function wrap(u8) {
    var b = u8;
    b.__isBuffer = true;

    b.toString = function (encoding, start, end) {
      var enc = encoding ? String(encoding).toLowerCase() : "utf8";
      var s = start === undefined ? 0 : Math.max(0, start | 0);
      var e = end === undefined ? this.length : Math.min(this.length, end | 0);
      if (e < s) return "";
      if (enc === "base64") return base64Encode(this.subarray(s, e));
      if (enc === "hex") return hexEncode(this.subarray(s, e));
      if (enc === "ascii" || enc === "latin1" || enc === "binary") {
        var out = "";
        for (var i = s; i < e; i++) out += String.fromCharCode(this[i]);
        return out;
      }
      return utf8Decode(this, s, e);
    };

    b.subarray = function (a, z) {
      return wrap(Uint8Array.prototype.subarray.call(this, a, z));
    };

    b.slice = function (a, z) {
      return wrap(Uint8Array.prototype.slice.call(this, a, z));
    };

    b.equals = function (other) {
      var o = toBytes(other);
      if (o.length !== this.length) return false;
      for (var i = 0; i < this.length; i++) if (this[i] !== o[i]) return false;
      return true;
    };

    b.indexOf = function (needle, offset) {
      var from = offset === undefined ? 0 : Math.max(0, offset | 0);
      var n = typeof needle === "string" ? utf8Encode(needle) : toBytes(needle);
      if (n.length === 0) return from <= this.length ? from : -1;
      for (var i = from; i <= this.length - n.length; i++) {
        var hit = true;
        for (var j = 0; j < n.length; j++) {
          if (this[i + j] !== n[j]) {
            hit = false;
            break;
          }
        }
        if (hit) return i;
      }
      return -1;
    };

    b.toJSON = function () {
      return { type: "Buffer", data: Array.prototype.slice.call(this) };
    };

    return b;
  }

  var BufferShim = {
    from: function (input, encoding) {
      return wrap(toBytes(input, encoding));
    },
    isBuffer: function (x) {
      return !!(x && x.__isBuffer);
    },
    alloc: function (size, fill) {
      var u = new Uint8Array(size | 0);
      if (fill !== undefined) u.fill(fill & 0xff);
      return wrap(u);
    },
    concat: function (list) {
      var total = 0;
      var i;
      for (i = 0; i < list.length; i++) total += toBytes(list[i]).length;
      var out = new Uint8Array(total);
      var at = 0;
      for (i = 0; i < list.length; i++) {
        var part = toBytes(list[i]);
        out.set(part, at);
        at += part.length;
      }
      return wrap(out);
    },
    byteLength: function (input, encoding) {
      return toBytes(input, encoding).length;
    },
  };

  g.Buffer = BufferShim;
})();
"""
}
