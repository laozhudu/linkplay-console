(() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __commonJS = (cb, mod2) => function __require() {
    try {
      return mod2 || (0, cb[__getOwnPropNames(cb)[0]])((mod2 = { exports: {} }).exports, mod2), mod2.exports;
    } catch (e) {
      throw mod2 = 0, e;
    }
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toESM = (mod2, isNodeMode, target) => (target = mod2 != null ? __create(__getProtoOf(mod2)) : {}, __copyProps(
    // If the importer is in node compatibility mode or this is not an ESM
    // file that has been converted to a CommonJS file using a Babel-
    // compatible transform (i.e. "__esModule" has not been set), then set
    // "default" to the CommonJS "module.exports" for node compatibility.
    isNodeMode || !mod2 || !mod2.__esModule ? __defProp(target, "default", { value: mod2, enumerable: true }) : target,
    mod2
  ));

  // node_modules/he/he.js
  var require_he = __commonJS({
    "node_modules/he/he.js"(exports, module) {
      (function(root) {
        var freeExports = typeof exports == "object" && exports;
        var freeModule = typeof module == "object" && module && module.exports == freeExports && module;
        var freeGlobal = typeof global == "object" && global;
        if (freeGlobal.global === freeGlobal || freeGlobal.window === freeGlobal) {
          root = freeGlobal;
        }
        var regexAstralSymbols = /[\uD800-\uDBFF][\uDC00-\uDFFF]/g;
        var regexAsciiWhitelist = /[\x01-\x7F]/g;
        var regexBmpWhitelist = /[\x01-\t\x0B\f\x0E-\x1F\x7F\x81\x8D\x8F\x90\x9D\xA0-\uFFFF]/g;
        var regexEncodeNonAscii = /<\u20D2|=\u20E5|>\u20D2|\u205F\u200A|\u219D\u0338|\u2202\u0338|\u2220\u20D2|\u2229\uFE00|\u222A\uFE00|\u223C\u20D2|\u223D\u0331|\u223E\u0333|\u2242\u0338|\u224B\u0338|\u224D\u20D2|\u224E\u0338|\u224F\u0338|\u2250\u0338|\u2261\u20E5|\u2264\u20D2|\u2265\u20D2|\u2266\u0338|\u2267\u0338|\u2268\uFE00|\u2269\uFE00|\u226A\u0338|\u226A\u20D2|\u226B\u0338|\u226B\u20D2|\u227F\u0338|\u2282\u20D2|\u2283\u20D2|\u228A\uFE00|\u228B\uFE00|\u228F\u0338|\u2290\u0338|\u2293\uFE00|\u2294\uFE00|\u22B4\u20D2|\u22B5\u20D2|\u22D8\u0338|\u22D9\u0338|\u22DA\uFE00|\u22DB\uFE00|\u22F5\u0338|\u22F9\u0338|\u2933\u0338|\u29CF\u0338|\u29D0\u0338|\u2A6D\u0338|\u2A70\u0338|\u2A7D\u0338|\u2A7E\u0338|\u2AA1\u0338|\u2AA2\u0338|\u2AAC\uFE00|\u2AAD\uFE00|\u2AAF\u0338|\u2AB0\u0338|\u2AC5\u0338|\u2AC6\u0338|\u2ACB\uFE00|\u2ACC\uFE00|\u2AFD\u20E5|[\xA0-\u0113\u0116-\u0122\u0124-\u012B\u012E-\u014D\u0150-\u017E\u0192\u01B5\u01F5\u0237\u02C6\u02C7\u02D8-\u02DD\u0311\u0391-\u03A1\u03A3-\u03A9\u03B1-\u03C9\u03D1\u03D2\u03D5\u03D6\u03DC\u03DD\u03F0\u03F1\u03F5\u03F6\u0401-\u040C\u040E-\u044F\u0451-\u045C\u045E\u045F\u2002-\u2005\u2007-\u2010\u2013-\u2016\u2018-\u201A\u201C-\u201E\u2020-\u2022\u2025\u2026\u2030-\u2035\u2039\u203A\u203E\u2041\u2043\u2044\u204F\u2057\u205F-\u2063\u20AC\u20DB\u20DC\u2102\u2105\u210A-\u2113\u2115-\u211E\u2122\u2124\u2127-\u2129\u212C\u212D\u212F-\u2131\u2133-\u2138\u2145-\u2148\u2153-\u215E\u2190-\u219B\u219D-\u21A7\u21A9-\u21AE\u21B0-\u21B3\u21B5-\u21B7\u21BA-\u21DB\u21DD\u21E4\u21E5\u21F5\u21FD-\u2205\u2207-\u2209\u220B\u220C\u220F-\u2214\u2216-\u2218\u221A\u221D-\u2238\u223A-\u2257\u2259\u225A\u225C\u225F-\u2262\u2264-\u228B\u228D-\u229B\u229D-\u22A5\u22A7-\u22B0\u22B2-\u22BB\u22BD-\u22DB\u22DE-\u22E3\u22E6-\u22F7\u22F9-\u22FE\u2305\u2306\u2308-\u2310\u2312\u2313\u2315\u2316\u231C-\u231F\u2322\u2323\u232D\u232E\u2336\u233D\u233F\u237C\u23B0\u23B1\u23B4-\u23B6\u23DC-\u23DF\u23E2\u23E7\u2423\u24C8\u2500\u2502\u250C\u2510\u2514\u2518\u251C\u2524\u252C\u2534\u253C\u2550-\u256C\u2580\u2584\u2588\u2591-\u2593\u25A1\u25AA\u25AB\u25AD\u25AE\u25B1\u25B3-\u25B5\u25B8\u25B9\u25BD-\u25BF\u25C2\u25C3\u25CA\u25CB\u25EC\u25EF\u25F8-\u25FC\u2605\u2606\u260E\u2640\u2642\u2660\u2663\u2665\u2666\u266A\u266D-\u266F\u2713\u2717\u2720\u2736\u2758\u2772\u2773\u27C8\u27C9\u27E6-\u27ED\u27F5-\u27FA\u27FC\u27FF\u2902-\u2905\u290C-\u2913\u2916\u2919-\u2920\u2923-\u292A\u2933\u2935-\u2939\u293C\u293D\u2945\u2948-\u294B\u294E-\u2976\u2978\u2979\u297B-\u297F\u2985\u2986\u298B-\u2996\u299A\u299C\u299D\u29A4-\u29B7\u29B9\u29BB\u29BC\u29BE-\u29C5\u29C9\u29CD-\u29D0\u29DC-\u29DE\u29E3-\u29E5\u29EB\u29F4\u29F6\u2A00-\u2A02\u2A04\u2A06\u2A0C\u2A0D\u2A10-\u2A17\u2A22-\u2A27\u2A29\u2A2A\u2A2D-\u2A31\u2A33-\u2A3C\u2A3F\u2A40\u2A42-\u2A4D\u2A50\u2A53-\u2A58\u2A5A-\u2A5D\u2A5F\u2A66\u2A6A\u2A6D-\u2A75\u2A77-\u2A9A\u2A9D-\u2AA2\u2AA4-\u2AB0\u2AB3-\u2AC8\u2ACB\u2ACC\u2ACF-\u2ADB\u2AE4\u2AE6-\u2AE9\u2AEB-\u2AF3\u2AFD\uFB00-\uFB04]|\uD835[\uDC9C\uDC9E\uDC9F\uDCA2\uDCA5\uDCA6\uDCA9-\uDCAC\uDCAE-\uDCB9\uDCBB\uDCBD-\uDCC3\uDCC5-\uDCCF\uDD04\uDD05\uDD07-\uDD0A\uDD0D-\uDD14\uDD16-\uDD1C\uDD1E-\uDD39\uDD3B-\uDD3E\uDD40-\uDD44\uDD46\uDD4A-\uDD50\uDD52-\uDD6B]/g;
        var encodeMap = { "\xAD": "shy", "\u200C": "zwnj", "\u200D": "zwj", "\u200E": "lrm", "\u2063": "ic", "\u2062": "it", "\u2061": "af", "\u200F": "rlm", "\u200B": "ZeroWidthSpace", "\u2060": "NoBreak", "\u0311": "DownBreve", "\u20DB": "tdot", "\u20DC": "DotDot", "	": "Tab", "\n": "NewLine", "\u2008": "puncsp", "\u205F": "MediumSpace", "\u2009": "thinsp", "\u200A": "hairsp", "\u2004": "emsp13", "\u2002": "ensp", "\u2005": "emsp14", "\u2003": "emsp", "\u2007": "numsp", "\xA0": "nbsp", "\u205F\u200A": "ThickSpace", "\u203E": "oline", "_": "lowbar", "\u2010": "dash", "\u2013": "ndash", "\u2014": "mdash", "\u2015": "horbar", ",": "comma", ";": "semi", "\u204F": "bsemi", ":": "colon", "\u2A74": "Colone", "!": "excl", "\xA1": "iexcl", "?": "quest", "\xBF": "iquest", ".": "period", "\u2025": "nldr", "\u2026": "mldr", "\xB7": "middot", "'": "apos", "\u2018": "lsquo", "\u2019": "rsquo", "\u201A": "sbquo", "\u2039": "lsaquo", "\u203A": "rsaquo", '"': "quot", "\u201C": "ldquo", "\u201D": "rdquo", "\u201E": "bdquo", "\xAB": "laquo", "\xBB": "raquo", "(": "lpar", ")": "rpar", "[": "lsqb", "]": "rsqb", "{": "lcub", "}": "rcub", "\u2308": "lceil", "\u2309": "rceil", "\u230A": "lfloor", "\u230B": "rfloor", "\u2985": "lopar", "\u2986": "ropar", "\u298B": "lbrke", "\u298C": "rbrke", "\u298D": "lbrkslu", "\u298E": "rbrksld", "\u298F": "lbrksld", "\u2990": "rbrkslu", "\u2991": "langd", "\u2992": "rangd", "\u2993": "lparlt", "\u2994": "rpargt", "\u2995": "gtlPar", "\u2996": "ltrPar", "\u27E6": "lobrk", "\u27E7": "robrk", "\u27E8": "lang", "\u27E9": "rang", "\u27EA": "Lang", "\u27EB": "Rang", "\u27EC": "loang", "\u27ED": "roang", "\u2772": "lbbrk", "\u2773": "rbbrk", "\u2016": "Vert", "\xA7": "sect", "\xB6": "para", "@": "commat", "*": "ast", "/": "sol", "undefined": null, "&": "amp", "#": "num", "%": "percnt", "\u2030": "permil", "\u2031": "pertenk", "\u2020": "dagger", "\u2021": "Dagger", "\u2022": "bull", "\u2043": "hybull", "\u2032": "prime", "\u2033": "Prime", "\u2034": "tprime", "\u2057": "qprime", "\u2035": "bprime", "\u2041": "caret", "`": "grave", "\xB4": "acute", "\u02DC": "tilde", "^": "Hat", "\xAF": "macr", "\u02D8": "breve", "\u02D9": "dot", "\xA8": "die", "\u02DA": "ring", "\u02DD": "dblac", "\xB8": "cedil", "\u02DB": "ogon", "\u02C6": "circ", "\u02C7": "caron", "\xB0": "deg", "\xA9": "copy", "\xAE": "reg", "\u2117": "copysr", "\u2118": "wp", "\u211E": "rx", "\u2127": "mho", "\u2129": "iiota", "\u2190": "larr", "\u219A": "nlarr", "\u2192": "rarr", "\u219B": "nrarr", "\u2191": "uarr", "\u2193": "darr", "\u2194": "harr", "\u21AE": "nharr", "\u2195": "varr", "\u2196": "nwarr", "\u2197": "nearr", "\u2198": "searr", "\u2199": "swarr", "\u219D": "rarrw", "\u219D\u0338": "nrarrw", "\u219E": "Larr", "\u219F": "Uarr", "\u21A0": "Rarr", "\u21A1": "Darr", "\u21A2": "larrtl", "\u21A3": "rarrtl", "\u21A4": "mapstoleft", "\u21A5": "mapstoup", "\u21A6": "map", "\u21A7": "mapstodown", "\u21A9": "larrhk", "\u21AA": "rarrhk", "\u21AB": "larrlp", "\u21AC": "rarrlp", "\u21AD": "harrw", "\u21B0": "lsh", "\u21B1": "rsh", "\u21B2": "ldsh", "\u21B3": "rdsh", "\u21B5": "crarr", "\u21B6": "cularr", "\u21B7": "curarr", "\u21BA": "olarr", "\u21BB": "orarr", "\u21BC": "lharu", "\u21BD": "lhard", "\u21BE": "uharr", "\u21BF": "uharl", "\u21C0": "rharu", "\u21C1": "rhard", "\u21C2": "dharr", "\u21C3": "dharl", "\u21C4": "rlarr", "\u21C5": "udarr", "\u21C6": "lrarr", "\u21C7": "llarr", "\u21C8": "uuarr", "\u21C9": "rrarr", "\u21CA": "ddarr", "\u21CB": "lrhar", "\u21CC": "rlhar", "\u21D0": "lArr", "\u21CD": "nlArr", "\u21D1": "uArr", "\u21D2": "rArr", "\u21CF": "nrArr", "\u21D3": "dArr", "\u21D4": "iff", "\u21CE": "nhArr", "\u21D5": "vArr", "\u21D6": "nwArr", "\u21D7": "neArr", "\u21D8": "seArr", "\u21D9": "swArr", "\u21DA": "lAarr", "\u21DB": "rAarr", "\u21DD": "zigrarr", "\u21E4": "larrb", "\u21E5": "rarrb", "\u21F5": "duarr", "\u21FD": "loarr", "\u21FE": "roarr", "\u21FF": "hoarr", "\u2200": "forall", "\u2201": "comp", "\u2202": "part", "\u2202\u0338": "npart", "\u2203": "exist", "\u2204": "nexist", "\u2205": "empty", "\u2207": "Del", "\u2208": "in", "\u2209": "notin", "\u220B": "ni", "\u220C": "notni", "\u03F6": "bepsi", "\u220F": "prod", "\u2210": "coprod", "\u2211": "sum", "+": "plus", "\xB1": "pm", "\xF7": "div", "\xD7": "times", "<": "lt", "\u226E": "nlt", "<\u20D2": "nvlt", "=": "equals", "\u2260": "ne", "=\u20E5": "bne", "\u2A75": "Equal", ">": "gt", "\u226F": "ngt", ">\u20D2": "nvgt", "\xAC": "not", "|": "vert", "\xA6": "brvbar", "\u2212": "minus", "\u2213": "mp", "\u2214": "plusdo", "\u2044": "frasl", "\u2216": "setmn", "\u2217": "lowast", "\u2218": "compfn", "\u221A": "Sqrt", "\u221D": "prop", "\u221E": "infin", "\u221F": "angrt", "\u2220": "ang", "\u2220\u20D2": "nang", "\u2221": "angmsd", "\u2222": "angsph", "\u2223": "mid", "\u2224": "nmid", "\u2225": "par", "\u2226": "npar", "\u2227": "and", "\u2228": "or", "\u2229": "cap", "\u2229\uFE00": "caps", "\u222A": "cup", "\u222A\uFE00": "cups", "\u222B": "int", "\u222C": "Int", "\u222D": "tint", "\u2A0C": "qint", "\u222E": "oint", "\u222F": "Conint", "\u2230": "Cconint", "\u2231": "cwint", "\u2232": "cwconint", "\u2233": "awconint", "\u2234": "there4", "\u2235": "becaus", "\u2236": "ratio", "\u2237": "Colon", "\u2238": "minusd", "\u223A": "mDDot", "\u223B": "homtht", "\u223C": "sim", "\u2241": "nsim", "\u223C\u20D2": "nvsim", "\u223D": "bsim", "\u223D\u0331": "race", "\u223E": "ac", "\u223E\u0333": "acE", "\u223F": "acd", "\u2240": "wr", "\u2242": "esim", "\u2242\u0338": "nesim", "\u2243": "sime", "\u2244": "nsime", "\u2245": "cong", "\u2247": "ncong", "\u2246": "simne", "\u2248": "ap", "\u2249": "nap", "\u224A": "ape", "\u224B": "apid", "\u224B\u0338": "napid", "\u224C": "bcong", "\u224D": "CupCap", "\u226D": "NotCupCap", "\u224D\u20D2": "nvap", "\u224E": "bump", "\u224E\u0338": "nbump", "\u224F": "bumpe", "\u224F\u0338": "nbumpe", "\u2250": "doteq", "\u2250\u0338": "nedot", "\u2251": "eDot", "\u2252": "efDot", "\u2253": "erDot", "\u2254": "colone", "\u2255": "ecolon", "\u2256": "ecir", "\u2257": "cire", "\u2259": "wedgeq", "\u225A": "veeeq", "\u225C": "trie", "\u225F": "equest", "\u2261": "equiv", "\u2262": "nequiv", "\u2261\u20E5": "bnequiv", "\u2264": "le", "\u2270": "nle", "\u2264\u20D2": "nvle", "\u2265": "ge", "\u2271": "nge", "\u2265\u20D2": "nvge", "\u2266": "lE", "\u2266\u0338": "nlE", "\u2267": "gE", "\u2267\u0338": "ngE", "\u2268\uFE00": "lvnE", "\u2268": "lnE", "\u2269": "gnE", "\u2269\uFE00": "gvnE", "\u226A": "ll", "\u226A\u0338": "nLtv", "\u226A\u20D2": "nLt", "\u226B": "gg", "\u226B\u0338": "nGtv", "\u226B\u20D2": "nGt", "\u226C": "twixt", "\u2272": "lsim", "\u2274": "nlsim", "\u2273": "gsim", "\u2275": "ngsim", "\u2276": "lg", "\u2278": "ntlg", "\u2277": "gl", "\u2279": "ntgl", "\u227A": "pr", "\u2280": "npr", "\u227B": "sc", "\u2281": "nsc", "\u227C": "prcue", "\u22E0": "nprcue", "\u227D": "sccue", "\u22E1": "nsccue", "\u227E": "prsim", "\u227F": "scsim", "\u227F\u0338": "NotSucceedsTilde", "\u2282": "sub", "\u2284": "nsub", "\u2282\u20D2": "vnsub", "\u2283": "sup", "\u2285": "nsup", "\u2283\u20D2": "vnsup", "\u2286": "sube", "\u2288": "nsube", "\u2287": "supe", "\u2289": "nsupe", "\u228A\uFE00": "vsubne", "\u228A": "subne", "\u228B\uFE00": "vsupne", "\u228B": "supne", "\u228D": "cupdot", "\u228E": "uplus", "\u228F": "sqsub", "\u228F\u0338": "NotSquareSubset", "\u2290": "sqsup", "\u2290\u0338": "NotSquareSuperset", "\u2291": "sqsube", "\u22E2": "nsqsube", "\u2292": "sqsupe", "\u22E3": "nsqsupe", "\u2293": "sqcap", "\u2293\uFE00": "sqcaps", "\u2294": "sqcup", "\u2294\uFE00": "sqcups", "\u2295": "oplus", "\u2296": "ominus", "\u2297": "otimes", "\u2298": "osol", "\u2299": "odot", "\u229A": "ocir", "\u229B": "oast", "\u229D": "odash", "\u229E": "plusb", "\u229F": "minusb", "\u22A0": "timesb", "\u22A1": "sdotb", "\u22A2": "vdash", "\u22AC": "nvdash", "\u22A3": "dashv", "\u22A4": "top", "\u22A5": "bot", "\u22A7": "models", "\u22A8": "vDash", "\u22AD": "nvDash", "\u22A9": "Vdash", "\u22AE": "nVdash", "\u22AA": "Vvdash", "\u22AB": "VDash", "\u22AF": "nVDash", "\u22B0": "prurel", "\u22B2": "vltri", "\u22EA": "nltri", "\u22B3": "vrtri", "\u22EB": "nrtri", "\u22B4": "ltrie", "\u22EC": "nltrie", "\u22B4\u20D2": "nvltrie", "\u22B5": "rtrie", "\u22ED": "nrtrie", "\u22B5\u20D2": "nvrtrie", "\u22B6": "origof", "\u22B7": "imof", "\u22B8": "mumap", "\u22B9": "hercon", "\u22BA": "intcal", "\u22BB": "veebar", "\u22BD": "barvee", "\u22BE": "angrtvb", "\u22BF": "lrtri", "\u22C0": "Wedge", "\u22C1": "Vee", "\u22C2": "xcap", "\u22C3": "xcup", "\u22C4": "diam", "\u22C5": "sdot", "\u22C6": "Star", "\u22C7": "divonx", "\u22C8": "bowtie", "\u22C9": "ltimes", "\u22CA": "rtimes", "\u22CB": "lthree", "\u22CC": "rthree", "\u22CD": "bsime", "\u22CE": "cuvee", "\u22CF": "cuwed", "\u22D0": "Sub", "\u22D1": "Sup", "\u22D2": "Cap", "\u22D3": "Cup", "\u22D4": "fork", "\u22D5": "epar", "\u22D6": "ltdot", "\u22D7": "gtdot", "\u22D8": "Ll", "\u22D8\u0338": "nLl", "\u22D9": "Gg", "\u22D9\u0338": "nGg", "\u22DA\uFE00": "lesg", "\u22DA": "leg", "\u22DB": "gel", "\u22DB\uFE00": "gesl", "\u22DE": "cuepr", "\u22DF": "cuesc", "\u22E6": "lnsim", "\u22E7": "gnsim", "\u22E8": "prnsim", "\u22E9": "scnsim", "\u22EE": "vellip", "\u22EF": "ctdot", "\u22F0": "utdot", "\u22F1": "dtdot", "\u22F2": "disin", "\u22F3": "isinsv", "\u22F4": "isins", "\u22F5": "isindot", "\u22F5\u0338": "notindot", "\u22F6": "notinvc", "\u22F7": "notinvb", "\u22F9": "isinE", "\u22F9\u0338": "notinE", "\u22FA": "nisd", "\u22FB": "xnis", "\u22FC": "nis", "\u22FD": "notnivc", "\u22FE": "notnivb", "\u2305": "barwed", "\u2306": "Barwed", "\u230C": "drcrop", "\u230D": "dlcrop", "\u230E": "urcrop", "\u230F": "ulcrop", "\u2310": "bnot", "\u2312": "profline", "\u2313": "profsurf", "\u2315": "telrec", "\u2316": "target", "\u231C": "ulcorn", "\u231D": "urcorn", "\u231E": "dlcorn", "\u231F": "drcorn", "\u2322": "frown", "\u2323": "smile", "\u232D": "cylcty", "\u232E": "profalar", "\u2336": "topbot", "\u233D": "ovbar", "\u233F": "solbar", "\u237C": "angzarr", "\u23B0": "lmoust", "\u23B1": "rmoust", "\u23B4": "tbrk", "\u23B5": "bbrk", "\u23B6": "bbrktbrk", "\u23DC": "OverParenthesis", "\u23DD": "UnderParenthesis", "\u23DE": "OverBrace", "\u23DF": "UnderBrace", "\u23E2": "trpezium", "\u23E7": "elinters", "\u2423": "blank", "\u2500": "boxh", "\u2502": "boxv", "\u250C": "boxdr", "\u2510": "boxdl", "\u2514": "boxur", "\u2518": "boxul", "\u251C": "boxvr", "\u2524": "boxvl", "\u252C": "boxhd", "\u2534": "boxhu", "\u253C": "boxvh", "\u2550": "boxH", "\u2551": "boxV", "\u2552": "boxdR", "\u2553": "boxDr", "\u2554": "boxDR", "\u2555": "boxdL", "\u2556": "boxDl", "\u2557": "boxDL", "\u2558": "boxuR", "\u2559": "boxUr", "\u255A": "boxUR", "\u255B": "boxuL", "\u255C": "boxUl", "\u255D": "boxUL", "\u255E": "boxvR", "\u255F": "boxVr", "\u2560": "boxVR", "\u2561": "boxvL", "\u2562": "boxVl", "\u2563": "boxVL", "\u2564": "boxHd", "\u2565": "boxhD", "\u2566": "boxHD", "\u2567": "boxHu", "\u2568": "boxhU", "\u2569": "boxHU", "\u256A": "boxvH", "\u256B": "boxVh", "\u256C": "boxVH", "\u2580": "uhblk", "\u2584": "lhblk", "\u2588": "block", "\u2591": "blk14", "\u2592": "blk12", "\u2593": "blk34", "\u25A1": "squ", "\u25AA": "squf", "\u25AB": "EmptyVerySmallSquare", "\u25AD": "rect", "\u25AE": "marker", "\u25B1": "fltns", "\u25B3": "xutri", "\u25B4": "utrif", "\u25B5": "utri", "\u25B8": "rtrif", "\u25B9": "rtri", "\u25BD": "xdtri", "\u25BE": "dtrif", "\u25BF": "dtri", "\u25C2": "ltrif", "\u25C3": "ltri", "\u25CA": "loz", "\u25CB": "cir", "\u25EC": "tridot", "\u25EF": "xcirc", "\u25F8": "ultri", "\u25F9": "urtri", "\u25FA": "lltri", "\u25FB": "EmptySmallSquare", "\u25FC": "FilledSmallSquare", "\u2605": "starf", "\u2606": "star", "\u260E": "phone", "\u2640": "female", "\u2642": "male", "\u2660": "spades", "\u2663": "clubs", "\u2665": "hearts", "\u2666": "diams", "\u266A": "sung", "\u2713": "check", "\u2717": "cross", "\u2720": "malt", "\u2736": "sext", "\u2758": "VerticalSeparator", "\u27C8": "bsolhsub", "\u27C9": "suphsol", "\u27F5": "xlarr", "\u27F6": "xrarr", "\u27F7": "xharr", "\u27F8": "xlArr", "\u27F9": "xrArr", "\u27FA": "xhArr", "\u27FC": "xmap", "\u27FF": "dzigrarr", "\u2902": "nvlArr", "\u2903": "nvrArr", "\u2904": "nvHarr", "\u2905": "Map", "\u290C": "lbarr", "\u290D": "rbarr", "\u290E": "lBarr", "\u290F": "rBarr", "\u2910": "RBarr", "\u2911": "DDotrahd", "\u2912": "UpArrowBar", "\u2913": "DownArrowBar", "\u2916": "Rarrtl", "\u2919": "latail", "\u291A": "ratail", "\u291B": "lAtail", "\u291C": "rAtail", "\u291D": "larrfs", "\u291E": "rarrfs", "\u291F": "larrbfs", "\u2920": "rarrbfs", "\u2923": "nwarhk", "\u2924": "nearhk", "\u2925": "searhk", "\u2926": "swarhk", "\u2927": "nwnear", "\u2928": "toea", "\u2929": "tosa", "\u292A": "swnwar", "\u2933": "rarrc", "\u2933\u0338": "nrarrc", "\u2935": "cudarrr", "\u2936": "ldca", "\u2937": "rdca", "\u2938": "cudarrl", "\u2939": "larrpl", "\u293C": "curarrm", "\u293D": "cularrp", "\u2945": "rarrpl", "\u2948": "harrcir", "\u2949": "Uarrocir", "\u294A": "lurdshar", "\u294B": "ldrushar", "\u294E": "LeftRightVector", "\u294F": "RightUpDownVector", "\u2950": "DownLeftRightVector", "\u2951": "LeftUpDownVector", "\u2952": "LeftVectorBar", "\u2953": "RightVectorBar", "\u2954": "RightUpVectorBar", "\u2955": "RightDownVectorBar", "\u2956": "DownLeftVectorBar", "\u2957": "DownRightVectorBar", "\u2958": "LeftUpVectorBar", "\u2959": "LeftDownVectorBar", "\u295A": "LeftTeeVector", "\u295B": "RightTeeVector", "\u295C": "RightUpTeeVector", "\u295D": "RightDownTeeVector", "\u295E": "DownLeftTeeVector", "\u295F": "DownRightTeeVector", "\u2960": "LeftUpTeeVector", "\u2961": "LeftDownTeeVector", "\u2962": "lHar", "\u2963": "uHar", "\u2964": "rHar", "\u2965": "dHar", "\u2966": "luruhar", "\u2967": "ldrdhar", "\u2968": "ruluhar", "\u2969": "rdldhar", "\u296A": "lharul", "\u296B": "llhard", "\u296C": "rharul", "\u296D": "lrhard", "\u296E": "udhar", "\u296F": "duhar", "\u2970": "RoundImplies", "\u2971": "erarr", "\u2972": "simrarr", "\u2973": "larrsim", "\u2974": "rarrsim", "\u2975": "rarrap", "\u2976": "ltlarr", "\u2978": "gtrarr", "\u2979": "subrarr", "\u297B": "suplarr", "\u297C": "lfisht", "\u297D": "rfisht", "\u297E": "ufisht", "\u297F": "dfisht", "\u299A": "vzigzag", "\u299C": "vangrt", "\u299D": "angrtvbd", "\u29A4": "ange", "\u29A5": "range", "\u29A6": "dwangle", "\u29A7": "uwangle", "\u29A8": "angmsdaa", "\u29A9": "angmsdab", "\u29AA": "angmsdac", "\u29AB": "angmsdad", "\u29AC": "angmsdae", "\u29AD": "angmsdaf", "\u29AE": "angmsdag", "\u29AF": "angmsdah", "\u29B0": "bemptyv", "\u29B1": "demptyv", "\u29B2": "cemptyv", "\u29B3": "raemptyv", "\u29B4": "laemptyv", "\u29B5": "ohbar", "\u29B6": "omid", "\u29B7": "opar", "\u29B9": "operp", "\u29BB": "olcross", "\u29BC": "odsold", "\u29BE": "olcir", "\u29BF": "ofcir", "\u29C0": "olt", "\u29C1": "ogt", "\u29C2": "cirscir", "\u29C3": "cirE", "\u29C4": "solb", "\u29C5": "bsolb", "\u29C9": "boxbox", "\u29CD": "trisb", "\u29CE": "rtriltri", "\u29CF": "LeftTriangleBar", "\u29CF\u0338": "NotLeftTriangleBar", "\u29D0": "RightTriangleBar", "\u29D0\u0338": "NotRightTriangleBar", "\u29DC": "iinfin", "\u29DD": "infintie", "\u29DE": "nvinfin", "\u29E3": "eparsl", "\u29E4": "smeparsl", "\u29E5": "eqvparsl", "\u29EB": "lozf", "\u29F4": "RuleDelayed", "\u29F6": "dsol", "\u2A00": "xodot", "\u2A01": "xoplus", "\u2A02": "xotime", "\u2A04": "xuplus", "\u2A06": "xsqcup", "\u2A0D": "fpartint", "\u2A10": "cirfnint", "\u2A11": "awint", "\u2A12": "rppolint", "\u2A13": "scpolint", "\u2A14": "npolint", "\u2A15": "pointint", "\u2A16": "quatint", "\u2A17": "intlarhk", "\u2A22": "pluscir", "\u2A23": "plusacir", "\u2A24": "simplus", "\u2A25": "plusdu", "\u2A26": "plussim", "\u2A27": "plustwo", "\u2A29": "mcomma", "\u2A2A": "minusdu", "\u2A2D": "loplus", "\u2A2E": "roplus", "\u2A2F": "Cross", "\u2A30": "timesd", "\u2A31": "timesbar", "\u2A33": "smashp", "\u2A34": "lotimes", "\u2A35": "rotimes", "\u2A36": "otimesas", "\u2A37": "Otimes", "\u2A38": "odiv", "\u2A39": "triplus", "\u2A3A": "triminus", "\u2A3B": "tritime", "\u2A3C": "iprod", "\u2A3F": "amalg", "\u2A40": "capdot", "\u2A42": "ncup", "\u2A43": "ncap", "\u2A44": "capand", "\u2A45": "cupor", "\u2A46": "cupcap", "\u2A47": "capcup", "\u2A48": "cupbrcap", "\u2A49": "capbrcup", "\u2A4A": "cupcup", "\u2A4B": "capcap", "\u2A4C": "ccups", "\u2A4D": "ccaps", "\u2A50": "ccupssm", "\u2A53": "And", "\u2A54": "Or", "\u2A55": "andand", "\u2A56": "oror", "\u2A57": "orslope", "\u2A58": "andslope", "\u2A5A": "andv", "\u2A5B": "orv", "\u2A5C": "andd", "\u2A5D": "ord", "\u2A5F": "wedbar", "\u2A66": "sdote", "\u2A6A": "simdot", "\u2A6D": "congdot", "\u2A6D\u0338": "ncongdot", "\u2A6E": "easter", "\u2A6F": "apacir", "\u2A70": "apE", "\u2A70\u0338": "napE", "\u2A71": "eplus", "\u2A72": "pluse", "\u2A73": "Esim", "\u2A77": "eDDot", "\u2A78": "equivDD", "\u2A79": "ltcir", "\u2A7A": "gtcir", "\u2A7B": "ltquest", "\u2A7C": "gtquest", "\u2A7D": "les", "\u2A7D\u0338": "nles", "\u2A7E": "ges", "\u2A7E\u0338": "nges", "\u2A7F": "lesdot", "\u2A80": "gesdot", "\u2A81": "lesdoto", "\u2A82": "gesdoto", "\u2A83": "lesdotor", "\u2A84": "gesdotol", "\u2A85": "lap", "\u2A86": "gap", "\u2A87": "lne", "\u2A88": "gne", "\u2A89": "lnap", "\u2A8A": "gnap", "\u2A8B": "lEg", "\u2A8C": "gEl", "\u2A8D": "lsime", "\u2A8E": "gsime", "\u2A8F": "lsimg", "\u2A90": "gsiml", "\u2A91": "lgE", "\u2A92": "glE", "\u2A93": "lesges", "\u2A94": "gesles", "\u2A95": "els", "\u2A96": "egs", "\u2A97": "elsdot", "\u2A98": "egsdot", "\u2A99": "el", "\u2A9A": "eg", "\u2A9D": "siml", "\u2A9E": "simg", "\u2A9F": "simlE", "\u2AA0": "simgE", "\u2AA1": "LessLess", "\u2AA1\u0338": "NotNestedLessLess", "\u2AA2": "GreaterGreater", "\u2AA2\u0338": "NotNestedGreaterGreater", "\u2AA4": "glj", "\u2AA5": "gla", "\u2AA6": "ltcc", "\u2AA7": "gtcc", "\u2AA8": "lescc", "\u2AA9": "gescc", "\u2AAA": "smt", "\u2AAB": "lat", "\u2AAC": "smte", "\u2AAC\uFE00": "smtes", "\u2AAD": "late", "\u2AAD\uFE00": "lates", "\u2AAE": "bumpE", "\u2AAF": "pre", "\u2AAF\u0338": "npre", "\u2AB0": "sce", "\u2AB0\u0338": "nsce", "\u2AB3": "prE", "\u2AB4": "scE", "\u2AB5": "prnE", "\u2AB6": "scnE", "\u2AB7": "prap", "\u2AB8": "scap", "\u2AB9": "prnap", "\u2ABA": "scnap", "\u2ABB": "Pr", "\u2ABC": "Sc", "\u2ABD": "subdot", "\u2ABE": "supdot", "\u2ABF": "subplus", "\u2AC0": "supplus", "\u2AC1": "submult", "\u2AC2": "supmult", "\u2AC3": "subedot", "\u2AC4": "supedot", "\u2AC5": "subE", "\u2AC5\u0338": "nsubE", "\u2AC6": "supE", "\u2AC6\u0338": "nsupE", "\u2AC7": "subsim", "\u2AC8": "supsim", "\u2ACB\uFE00": "vsubnE", "\u2ACB": "subnE", "\u2ACC\uFE00": "vsupnE", "\u2ACC": "supnE", "\u2ACF": "csub", "\u2AD0": "csup", "\u2AD1": "csube", "\u2AD2": "csupe", "\u2AD3": "subsup", "\u2AD4": "supsub", "\u2AD5": "subsub", "\u2AD6": "supsup", "\u2AD7": "suphsub", "\u2AD8": "supdsub", "\u2AD9": "forkv", "\u2ADA": "topfork", "\u2ADB": "mlcp", "\u2AE4": "Dashv", "\u2AE6": "Vdashl", "\u2AE7": "Barv", "\u2AE8": "vBar", "\u2AE9": "vBarv", "\u2AEB": "Vbar", "\u2AEC": "Not", "\u2AED": "bNot", "\u2AEE": "rnmid", "\u2AEF": "cirmid", "\u2AF0": "midcir", "\u2AF1": "topcir", "\u2AF2": "nhpar", "\u2AF3": "parsim", "\u2AFD": "parsl", "\u2AFD\u20E5": "nparsl", "\u266D": "flat", "\u266E": "natur", "\u266F": "sharp", "\xA4": "curren", "\xA2": "cent", "$": "dollar", "\xA3": "pound", "\xA5": "yen", "\u20AC": "euro", "\xB9": "sup1", "\xBD": "half", "\u2153": "frac13", "\xBC": "frac14", "\u2155": "frac15", "\u2159": "frac16", "\u215B": "frac18", "\xB2": "sup2", "\u2154": "frac23", "\u2156": "frac25", "\xB3": "sup3", "\xBE": "frac34", "\u2157": "frac35", "\u215C": "frac38", "\u2158": "frac45", "\u215A": "frac56", "\u215D": "frac58", "\u215E": "frac78", "\u{1D4B6}": "ascr", "\u{1D552}": "aopf", "\u{1D51E}": "afr", "\u{1D538}": "Aopf", "\u{1D504}": "Afr", "\u{1D49C}": "Ascr", "\xAA": "ordf", "\xE1": "aacute", "\xC1": "Aacute", "\xE0": "agrave", "\xC0": "Agrave", "\u0103": "abreve", "\u0102": "Abreve", "\xE2": "acirc", "\xC2": "Acirc", "\xE5": "aring", "\xC5": "angst", "\xE4": "auml", "\xC4": "Auml", "\xE3": "atilde", "\xC3": "Atilde", "\u0105": "aogon", "\u0104": "Aogon", "\u0101": "amacr", "\u0100": "Amacr", "\xE6": "aelig", "\xC6": "AElig", "\u{1D4B7}": "bscr", "\u{1D553}": "bopf", "\u{1D51F}": "bfr", "\u{1D539}": "Bopf", "\u212C": "Bscr", "\u{1D505}": "Bfr", "\u{1D520}": "cfr", "\u{1D4B8}": "cscr", "\u{1D554}": "copf", "\u212D": "Cfr", "\u{1D49E}": "Cscr", "\u2102": "Copf", "\u0107": "cacute", "\u0106": "Cacute", "\u0109": "ccirc", "\u0108": "Ccirc", "\u010D": "ccaron", "\u010C": "Ccaron", "\u010B": "cdot", "\u010A": "Cdot", "\xE7": "ccedil", "\xC7": "Ccedil", "\u2105": "incare", "\u{1D521}": "dfr", "\u2146": "dd", "\u{1D555}": "dopf", "\u{1D4B9}": "dscr", "\u{1D49F}": "Dscr", "\u{1D507}": "Dfr", "\u2145": "DD", "\u{1D53B}": "Dopf", "\u010F": "dcaron", "\u010E": "Dcaron", "\u0111": "dstrok", "\u0110": "Dstrok", "\xF0": "eth", "\xD0": "ETH", "\u2147": "ee", "\u212F": "escr", "\u{1D522}": "efr", "\u{1D556}": "eopf", "\u2130": "Escr", "\u{1D508}": "Efr", "\u{1D53C}": "Eopf", "\xE9": "eacute", "\xC9": "Eacute", "\xE8": "egrave", "\xC8": "Egrave", "\xEA": "ecirc", "\xCA": "Ecirc", "\u011B": "ecaron", "\u011A": "Ecaron", "\xEB": "euml", "\xCB": "Euml", "\u0117": "edot", "\u0116": "Edot", "\u0119": "eogon", "\u0118": "Eogon", "\u0113": "emacr", "\u0112": "Emacr", "\u{1D523}": "ffr", "\u{1D557}": "fopf", "\u{1D4BB}": "fscr", "\u{1D509}": "Ffr", "\u{1D53D}": "Fopf", "\u2131": "Fscr", "\uFB00": "fflig", "\uFB03": "ffilig", "\uFB04": "ffllig", "\uFB01": "filig", "fj": "fjlig", "\uFB02": "fllig", "\u0192": "fnof", "\u210A": "gscr", "\u{1D558}": "gopf", "\u{1D524}": "gfr", "\u{1D4A2}": "Gscr", "\u{1D53E}": "Gopf", "\u{1D50A}": "Gfr", "\u01F5": "gacute", "\u011F": "gbreve", "\u011E": "Gbreve", "\u011D": "gcirc", "\u011C": "Gcirc", "\u0121": "gdot", "\u0120": "Gdot", "\u0122": "Gcedil", "\u{1D525}": "hfr", "\u210E": "planckh", "\u{1D4BD}": "hscr", "\u{1D559}": "hopf", "\u210B": "Hscr", "\u210C": "Hfr", "\u210D": "Hopf", "\u0125": "hcirc", "\u0124": "Hcirc", "\u210F": "hbar", "\u0127": "hstrok", "\u0126": "Hstrok", "\u{1D55A}": "iopf", "\u{1D526}": "ifr", "\u{1D4BE}": "iscr", "\u2148": "ii", "\u{1D540}": "Iopf", "\u2110": "Iscr", "\u2111": "Im", "\xED": "iacute", "\xCD": "Iacute", "\xEC": "igrave", "\xCC": "Igrave", "\xEE": "icirc", "\xCE": "Icirc", "\xEF": "iuml", "\xCF": "Iuml", "\u0129": "itilde", "\u0128": "Itilde", "\u0130": "Idot", "\u012F": "iogon", "\u012E": "Iogon", "\u012B": "imacr", "\u012A": "Imacr", "\u0133": "ijlig", "\u0132": "IJlig", "\u0131": "imath", "\u{1D4BF}": "jscr", "\u{1D55B}": "jopf", "\u{1D527}": "jfr", "\u{1D4A5}": "Jscr", "\u{1D50D}": "Jfr", "\u{1D541}": "Jopf", "\u0135": "jcirc", "\u0134": "Jcirc", "\u0237": "jmath", "\u{1D55C}": "kopf", "\u{1D4C0}": "kscr", "\u{1D528}": "kfr", "\u{1D4A6}": "Kscr", "\u{1D542}": "Kopf", "\u{1D50E}": "Kfr", "\u0137": "kcedil", "\u0136": "Kcedil", "\u{1D529}": "lfr", "\u{1D4C1}": "lscr", "\u2113": "ell", "\u{1D55D}": "lopf", "\u2112": "Lscr", "\u{1D50F}": "Lfr", "\u{1D543}": "Lopf", "\u013A": "lacute", "\u0139": "Lacute", "\u013E": "lcaron", "\u013D": "Lcaron", "\u013C": "lcedil", "\u013B": "Lcedil", "\u0142": "lstrok", "\u0141": "Lstrok", "\u0140": "lmidot", "\u013F": "Lmidot", "\u{1D52A}": "mfr", "\u{1D55E}": "mopf", "\u{1D4C2}": "mscr", "\u{1D510}": "Mfr", "\u{1D544}": "Mopf", "\u2133": "Mscr", "\u{1D52B}": "nfr", "\u{1D55F}": "nopf", "\u{1D4C3}": "nscr", "\u2115": "Nopf", "\u{1D4A9}": "Nscr", "\u{1D511}": "Nfr", "\u0144": "nacute", "\u0143": "Nacute", "\u0148": "ncaron", "\u0147": "Ncaron", "\xF1": "ntilde", "\xD1": "Ntilde", "\u0146": "ncedil", "\u0145": "Ncedil", "\u2116": "numero", "\u014B": "eng", "\u014A": "ENG", "\u{1D560}": "oopf", "\u{1D52C}": "ofr", "\u2134": "oscr", "\u{1D4AA}": "Oscr", "\u{1D512}": "Ofr", "\u{1D546}": "Oopf", "\xBA": "ordm", "\xF3": "oacute", "\xD3": "Oacute", "\xF2": "ograve", "\xD2": "Ograve", "\xF4": "ocirc", "\xD4": "Ocirc", "\xF6": "ouml", "\xD6": "Ouml", "\u0151": "odblac", "\u0150": "Odblac", "\xF5": "otilde", "\xD5": "Otilde", "\xF8": "oslash", "\xD8": "Oslash", "\u014D": "omacr", "\u014C": "Omacr", "\u0153": "oelig", "\u0152": "OElig", "\u{1D52D}": "pfr", "\u{1D4C5}": "pscr", "\u{1D561}": "popf", "\u2119": "Popf", "\u{1D513}": "Pfr", "\u{1D4AB}": "Pscr", "\u{1D562}": "qopf", "\u{1D52E}": "qfr", "\u{1D4C6}": "qscr", "\u{1D4AC}": "Qscr", "\u{1D514}": "Qfr", "\u211A": "Qopf", "\u0138": "kgreen", "\u{1D52F}": "rfr", "\u{1D563}": "ropf", "\u{1D4C7}": "rscr", "\u211B": "Rscr", "\u211C": "Re", "\u211D": "Ropf", "\u0155": "racute", "\u0154": "Racute", "\u0159": "rcaron", "\u0158": "Rcaron", "\u0157": "rcedil", "\u0156": "Rcedil", "\u{1D564}": "sopf", "\u{1D4C8}": "sscr", "\u{1D530}": "sfr", "\u{1D54A}": "Sopf", "\u{1D516}": "Sfr", "\u{1D4AE}": "Sscr", "\u24C8": "oS", "\u015B": "sacute", "\u015A": "Sacute", "\u015D": "scirc", "\u015C": "Scirc", "\u0161": "scaron", "\u0160": "Scaron", "\u015F": "scedil", "\u015E": "Scedil", "\xDF": "szlig", "\u{1D531}": "tfr", "\u{1D4C9}": "tscr", "\u{1D565}": "topf", "\u{1D4AF}": "Tscr", "\u{1D517}": "Tfr", "\u{1D54B}": "Topf", "\u0165": "tcaron", "\u0164": "Tcaron", "\u0163": "tcedil", "\u0162": "Tcedil", "\u2122": "trade", "\u0167": "tstrok", "\u0166": "Tstrok", "\u{1D4CA}": "uscr", "\u{1D566}": "uopf", "\u{1D532}": "ufr", "\u{1D54C}": "Uopf", "\u{1D518}": "Ufr", "\u{1D4B0}": "Uscr", "\xFA": "uacute", "\xDA": "Uacute", "\xF9": "ugrave", "\xD9": "Ugrave", "\u016D": "ubreve", "\u016C": "Ubreve", "\xFB": "ucirc", "\xDB": "Ucirc", "\u016F": "uring", "\u016E": "Uring", "\xFC": "uuml", "\xDC": "Uuml", "\u0171": "udblac", "\u0170": "Udblac", "\u0169": "utilde", "\u0168": "Utilde", "\u0173": "uogon", "\u0172": "Uogon", "\u016B": "umacr", "\u016A": "Umacr", "\u{1D533}": "vfr", "\u{1D567}": "vopf", "\u{1D4CB}": "vscr", "\u{1D519}": "Vfr", "\u{1D54D}": "Vopf", "\u{1D4B1}": "Vscr", "\u{1D568}": "wopf", "\u{1D4CC}": "wscr", "\u{1D534}": "wfr", "\u{1D4B2}": "Wscr", "\u{1D54E}": "Wopf", "\u{1D51A}": "Wfr", "\u0175": "wcirc", "\u0174": "Wcirc", "\u{1D535}": "xfr", "\u{1D4CD}": "xscr", "\u{1D569}": "xopf", "\u{1D54F}": "Xopf", "\u{1D51B}": "Xfr", "\u{1D4B3}": "Xscr", "\u{1D536}": "yfr", "\u{1D4CE}": "yscr", "\u{1D56A}": "yopf", "\u{1D4B4}": "Yscr", "\u{1D51C}": "Yfr", "\u{1D550}": "Yopf", "\xFD": "yacute", "\xDD": "Yacute", "\u0177": "ycirc", "\u0176": "Ycirc", "\xFF": "yuml", "\u0178": "Yuml", "\u{1D4CF}": "zscr", "\u{1D537}": "zfr", "\u{1D56B}": "zopf", "\u2128": "Zfr", "\u2124": "Zopf", "\u{1D4B5}": "Zscr", "\u017A": "zacute", "\u0179": "Zacute", "\u017E": "zcaron", "\u017D": "Zcaron", "\u017C": "zdot", "\u017B": "Zdot", "\u01B5": "imped", "\xFE": "thorn", "\xDE": "THORN", "\u0149": "napos", "\u03B1": "alpha", "\u0391": "Alpha", "\u03B2": "beta", "\u0392": "Beta", "\u03B3": "gamma", "\u0393": "Gamma", "\u03B4": "delta", "\u0394": "Delta", "\u03B5": "epsi", "\u03F5": "epsiv", "\u0395": "Epsilon", "\u03DD": "gammad", "\u03DC": "Gammad", "\u03B6": "zeta", "\u0396": "Zeta", "\u03B7": "eta", "\u0397": "Eta", "\u03B8": "theta", "\u03D1": "thetav", "\u0398": "Theta", "\u03B9": "iota", "\u0399": "Iota", "\u03BA": "kappa", "\u03F0": "kappav", "\u039A": "Kappa", "\u03BB": "lambda", "\u039B": "Lambda", "\u03BC": "mu", "\xB5": "micro", "\u039C": "Mu", "\u03BD": "nu", "\u039D": "Nu", "\u03BE": "xi", "\u039E": "Xi", "\u03BF": "omicron", "\u039F": "Omicron", "\u03C0": "pi", "\u03D6": "piv", "\u03A0": "Pi", "\u03C1": "rho", "\u03F1": "rhov", "\u03A1": "Rho", "\u03C3": "sigma", "\u03A3": "Sigma", "\u03C2": "sigmaf", "\u03C4": "tau", "\u03A4": "Tau", "\u03C5": "upsi", "\u03A5": "Upsilon", "\u03D2": "Upsi", "\u03C6": "phi", "\u03D5": "phiv", "\u03A6": "Phi", "\u03C7": "chi", "\u03A7": "Chi", "\u03C8": "psi", "\u03A8": "Psi", "\u03C9": "omega", "\u03A9": "ohm", "\u0430": "acy", "\u0410": "Acy", "\u0431": "bcy", "\u0411": "Bcy", "\u0432": "vcy", "\u0412": "Vcy", "\u0433": "gcy", "\u0413": "Gcy", "\u0453": "gjcy", "\u0403": "GJcy", "\u0434": "dcy", "\u0414": "Dcy", "\u0452": "djcy", "\u0402": "DJcy", "\u0435": "iecy", "\u0415": "IEcy", "\u0451": "iocy", "\u0401": "IOcy", "\u0454": "jukcy", "\u0404": "Jukcy", "\u0436": "zhcy", "\u0416": "ZHcy", "\u0437": "zcy", "\u0417": "Zcy", "\u0455": "dscy", "\u0405": "DScy", "\u0438": "icy", "\u0418": "Icy", "\u0456": "iukcy", "\u0406": "Iukcy", "\u0457": "yicy", "\u0407": "YIcy", "\u0439": "jcy", "\u0419": "Jcy", "\u0458": "jsercy", "\u0408": "Jsercy", "\u043A": "kcy", "\u041A": "Kcy", "\u045C": "kjcy", "\u040C": "KJcy", "\u043B": "lcy", "\u041B": "Lcy", "\u0459": "ljcy", "\u0409": "LJcy", "\u043C": "mcy", "\u041C": "Mcy", "\u043D": "ncy", "\u041D": "Ncy", "\u045A": "njcy", "\u040A": "NJcy", "\u043E": "ocy", "\u041E": "Ocy", "\u043F": "pcy", "\u041F": "Pcy", "\u0440": "rcy", "\u0420": "Rcy", "\u0441": "scy", "\u0421": "Scy", "\u0442": "tcy", "\u0422": "Tcy", "\u045B": "tshcy", "\u040B": "TSHcy", "\u0443": "ucy", "\u0423": "Ucy", "\u045E": "ubrcy", "\u040E": "Ubrcy", "\u0444": "fcy", "\u0424": "Fcy", "\u0445": "khcy", "\u0425": "KHcy", "\u0446": "tscy", "\u0426": "TScy", "\u0447": "chcy", "\u0427": "CHcy", "\u045F": "dzcy", "\u040F": "DZcy", "\u0448": "shcy", "\u0428": "SHcy", "\u0449": "shchcy", "\u0429": "SHCHcy", "\u044A": "hardcy", "\u042A": "HARDcy", "\u044B": "ycy", "\u042B": "Ycy", "\u044C": "softcy", "\u042C": "SOFTcy", "\u044D": "ecy", "\u042D": "Ecy", "\u044E": "yucy", "\u042E": "YUcy", "\u044F": "yacy", "\u042F": "YAcy", "\u2135": "aleph", "\u2136": "beth", "\u2137": "gimel", "\u2138": "daleth" };
        var regexEscape = /["&'<>`]/g;
        var escapeMap = {
          '"': "&quot;",
          "&": "&amp;",
          "'": "&#x27;",
          "<": "&lt;",
          // See https://mathiasbynens.be/notes/ambiguous-ampersands: in HTML, the
          // following is not strictly necessary unless it’s part of a tag or an
          // unquoted attribute value. We’re only escaping it to support those
          // situations, and for XML support.
          ">": "&gt;",
          // In Internet Explorer ≤ 8, the backtick character can be used
          // to break out of (un)quoted attribute values or HTML comments.
          // See http://html5sec.org/#102, http://html5sec.org/#108, and
          // http://html5sec.org/#133.
          "`": "&#x60;"
        };
        var regexInvalidEntity = /&#(?:[xX][^a-fA-F0-9]|[^0-9xX])/;
        var regexInvalidRawCodePoint = /[\0-\x08\x0B\x0E-\x1F\x7F-\x9F\uFDD0-\uFDEF\uFFFE\uFFFF]|[\uD83F\uD87F\uD8BF\uD8FF\uD93F\uD97F\uD9BF\uD9FF\uDA3F\uDA7F\uDABF\uDAFF\uDB3F\uDB7F\uDBBF\uDBFF][\uDFFE\uDFFF]|[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?:[^\uD800-\uDBFF]|^)[\uDC00-\uDFFF]/;
        var regexDecode = /&(CounterClockwiseContourIntegral|DoubleLongLeftRightArrow|ClockwiseContourIntegral|NotNestedGreaterGreater|NotSquareSupersetEqual|DiacriticalDoubleAcute|NotRightTriangleEqual|NotSucceedsSlantEqual|NotPrecedesSlantEqual|CloseCurlyDoubleQuote|NegativeVeryThinSpace|DoubleContourIntegral|FilledVerySmallSquare|CapitalDifferentialD|OpenCurlyDoubleQuote|EmptyVerySmallSquare|NestedGreaterGreater|DoubleLongRightArrow|NotLeftTriangleEqual|NotGreaterSlantEqual|ReverseUpEquilibrium|DoubleLeftRightArrow|NotSquareSubsetEqual|NotDoubleVerticalBar|RightArrowLeftArrow|NotGreaterFullEqual|NotRightTriangleBar|SquareSupersetEqual|DownLeftRightVector|DoubleLongLeftArrow|leftrightsquigarrow|LeftArrowRightArrow|NegativeMediumSpace|blacktriangleright|RightDownVectorBar|PrecedesSlantEqual|RightDoubleBracket|SucceedsSlantEqual|NotLeftTriangleBar|RightTriangleEqual|SquareIntersection|RightDownTeeVector|ReverseEquilibrium|NegativeThickSpace|longleftrightarrow|Longleftrightarrow|LongLeftRightArrow|DownRightTeeVector|DownRightVectorBar|GreaterSlantEqual|SquareSubsetEqual|LeftDownVectorBar|LeftDoubleBracket|VerticalSeparator|rightleftharpoons|NotGreaterGreater|NotSquareSuperset|blacktriangleleft|blacktriangledown|NegativeThinSpace|LeftDownTeeVector|NotLessSlantEqual|leftrightharpoons|DoubleUpDownArrow|DoubleVerticalBar|LeftTriangleEqual|FilledSmallSquare|twoheadrightarrow|NotNestedLessLess|DownLeftTeeVector|DownLeftVectorBar|RightAngleBracket|NotTildeFullEqual|NotReverseElement|RightUpDownVector|DiacriticalTilde|NotSucceedsTilde|circlearrowright|NotPrecedesEqual|rightharpoondown|DoubleRightArrow|NotSucceedsEqual|NonBreakingSpace|NotRightTriangle|LessEqualGreater|RightUpTeeVector|LeftAngleBracket|GreaterFullEqual|DownArrowUpArrow|RightUpVectorBar|twoheadleftarrow|GreaterEqualLess|downharpoonright|RightTriangleBar|ntrianglerighteq|NotSupersetEqual|LeftUpDownVector|DiacriticalAcute|rightrightarrows|vartriangleright|UpArrowDownArrow|DiacriticalGrave|UnderParenthesis|EmptySmallSquare|LeftUpVectorBar|leftrightarrows|DownRightVector|downharpoonleft|trianglerighteq|ShortRightArrow|OverParenthesis|DoubleLeftArrow|DoubleDownArrow|NotSquareSubset|bigtriangledown|ntrianglelefteq|UpperRightArrow|curvearrowright|vartriangleleft|NotLeftTriangle|nleftrightarrow|LowerRightArrow|NotHumpDownHump|NotGreaterTilde|rightthreetimes|LeftUpTeeVector|NotGreaterEqual|straightepsilon|LeftTriangleBar|rightsquigarrow|ContourIntegral|rightleftarrows|CloseCurlyQuote|RightDownVector|LeftRightVector|nLeftrightarrow|leftharpoondown|circlearrowleft|SquareSuperset|OpenCurlyQuote|hookrightarrow|HorizontalLine|DiacriticalDot|NotLessGreater|ntriangleright|DoubleRightTee|InvisibleComma|InvisibleTimes|LowerLeftArrow|DownLeftVector|NotSubsetEqual|curvearrowleft|trianglelefteq|NotVerticalBar|TildeFullEqual|downdownarrows|NotGreaterLess|RightTeeVector|ZeroWidthSpace|looparrowright|LongRightArrow|doublebarwedge|ShortLeftArrow|ShortDownArrow|RightVectorBar|GreaterGreater|ReverseElement|rightharpoonup|LessSlantEqual|leftthreetimes|upharpoonright|rightarrowtail|LeftDownVector|Longrightarrow|NestedLessLess|UpperLeftArrow|nshortparallel|leftleftarrows|leftrightarrow|Leftrightarrow|LeftRightArrow|longrightarrow|upharpoonleft|RightArrowBar|ApplyFunction|LeftTeeVector|leftarrowtail|NotEqualTilde|varsubsetneqq|varsupsetneqq|RightTeeArrow|SucceedsEqual|SucceedsTilde|LeftVectorBar|SupersetEqual|hookleftarrow|DifferentialD|VerticalTilde|VeryThinSpace|blacktriangle|bigtriangleup|LessFullEqual|divideontimes|leftharpoonup|UpEquilibrium|ntriangleleft|RightTriangle|measuredangle|shortparallel|longleftarrow|Longleftarrow|LongLeftArrow|DoubleLeftTee|Poincareplane|PrecedesEqual|triangleright|DoubleUpArrow|RightUpVector|fallingdotseq|looparrowleft|PrecedesTilde|NotTildeEqual|NotTildeTilde|smallsetminus|Proportional|triangleleft|triangledown|UnderBracket|NotHumpEqual|exponentiale|ExponentialE|NotLessTilde|HilbertSpace|RightCeiling|blacklozenge|varsupsetneq|HumpDownHump|GreaterEqual|VerticalLine|LeftTeeArrow|NotLessEqual|DownTeeArrow|LeftTriangle|varsubsetneq|Intersection|NotCongruent|DownArrowBar|LeftUpVector|LeftArrowBar|risingdotseq|GreaterTilde|RoundImplies|SquareSubset|ShortUpArrow|NotSuperset|quaternions|precnapprox|backepsilon|preccurlyeq|OverBracket|blacksquare|MediumSpace|VerticalBar|circledcirc|circleddash|CircleMinus|CircleTimes|LessGreater|curlyeqprec|curlyeqsucc|diamondsuit|UpDownArrow|Updownarrow|RuleDelayed|Rrightarrow|updownarrow|RightVector|nRightarrow|nrightarrow|eqslantless|LeftCeiling|Equilibrium|SmallCircle|expectation|NotSucceeds|thickapprox|GreaterLess|SquareUnion|NotPrecedes|NotLessLess|straightphi|succnapprox|succcurlyeq|SubsetEqual|sqsupseteq|Proportion|Laplacetrf|ImaginaryI|supsetneqq|NotGreater|gtreqqless|NotElement|ThickSpace|TildeEqual|TildeTilde|Fouriertrf|rmoustache|EqualTilde|eqslantgtr|UnderBrace|LeftVector|UpArrowBar|nLeftarrow|nsubseteqq|subsetneqq|nsupseteqq|nleftarrow|succapprox|lessapprox|UpTeeArrow|upuparrows|curlywedge|lesseqqgtr|varepsilon|varnothing|RightFloor|complement|CirclePlus|sqsubseteq|Lleftarrow|circledast|RightArrow|Rightarrow|rightarrow|lmoustache|Bernoullis|precapprox|mapstoleft|mapstodown|longmapsto|dotsquare|downarrow|DoubleDot|nsubseteq|supsetneq|leftarrow|nsupseteq|subsetneq|ThinSpace|ngeqslant|subseteqq|HumpEqual|NotSubset|triangleq|NotCupCap|lesseqgtr|heartsuit|TripleDot|Leftarrow|Coproduct|Congruent|varpropto|complexes|gvertneqq|LeftArrow|LessTilde|supseteqq|MinusPlus|CircleDot|nleqslant|NotExists|gtreqless|nparallel|UnionPlus|LeftFloor|checkmark|CenterDot|centerdot|Mellintrf|gtrapprox|bigotimes|OverBrace|spadesuit|therefore|pitchfork|rationals|PlusMinus|Backslash|Therefore|DownBreve|backsimeq|backprime|DownArrow|nshortmid|Downarrow|lvertneqq|eqvparsl|imagline|imagpart|infintie|integers|Integral|intercal|LessLess|Uarrocir|intlarhk|sqsupset|angmsdaf|sqsubset|llcorner|vartheta|cupbrcap|lnapprox|Superset|SuchThat|succnsim|succneqq|angmsdag|biguplus|curlyvee|trpezium|Succeeds|NotTilde|bigwedge|angmsdah|angrtvbd|triminus|cwconint|fpartint|lrcorner|smeparsl|subseteq|urcorner|lurdshar|laemptyv|DDotrahd|approxeq|ldrushar|awconint|mapstoup|backcong|shortmid|triangle|geqslant|gesdotol|timesbar|circledR|circledS|setminus|multimap|naturals|scpolint|ncongdot|RightTee|boxminus|gnapprox|boxtimes|andslope|thicksim|angmsdaa|varsigma|cirfnint|rtriltri|angmsdab|rppolint|angmsdac|barwedge|drbkarow|clubsuit|thetasym|bsolhsub|capbrcup|dzigrarr|doteqdot|DotEqual|dotminus|UnderBar|NotEqual|realpart|otimesas|ulcorner|hksearow|hkswarow|parallel|PartialD|elinters|emptyset|plusacir|bbrktbrk|angmsdad|pointint|bigoplus|angmsdae|Precedes|bigsqcup|varkappa|notindot|supseteq|precneqq|precnsim|profalar|profline|profsurf|leqslant|lesdotor|raemptyv|subplus|notnivb|notnivc|subrarr|zigrarr|vzigzag|submult|subedot|Element|between|cirscir|larrbfs|larrsim|lotimes|lbrksld|lbrkslu|lozenge|ldrdhar|dbkarow|bigcirc|epsilon|simrarr|simplus|ltquest|Epsilon|luruhar|gtquest|maltese|npolint|eqcolon|npreceq|bigodot|ddagger|gtrless|bnequiv|harrcir|ddotseq|equivDD|backsim|demptyv|nsqsube|nsqsupe|Upsilon|nsubset|upsilon|minusdu|nsucceq|swarrow|nsupset|coloneq|searrow|boxplus|napprox|natural|asympeq|alefsym|congdot|nearrow|bigstar|diamond|supplus|tritime|LeftTee|nvinfin|triplus|NewLine|nvltrie|nvrtrie|nwarrow|nexists|Diamond|ruluhar|Implies|supmult|angzarr|suplarr|suphsub|questeq|because|digamma|Because|olcross|bemptyv|omicron|Omicron|rotimes|NoBreak|intprod|angrtvb|orderof|uwangle|suphsol|lesdoto|orslope|DownTee|realine|cudarrl|rdldhar|OverBar|supedot|lessdot|supdsub|topfork|succsim|rbrkslu|rbrksld|pertenk|cudarrr|isindot|planckh|lessgtr|pluscir|gesdoto|plussim|plustwo|lesssim|cularrp|rarrsim|Cayleys|notinva|notinvb|notinvc|UpArrow|Uparrow|uparrow|NotLess|dwangle|precsim|Product|curarrm|Cconint|dotplus|rarrbfs|ccupssm|Cedilla|cemptyv|notniva|quatint|frac35|frac38|frac45|frac56|frac58|frac78|tridot|xoplus|gacute|gammad|Gammad|lfisht|lfloor|bigcup|sqsupe|gbreve|Gbreve|lharul|sqsube|sqcups|Gcedil|apacir|llhard|lmidot|Lmidot|lmoust|andand|sqcaps|approx|Abreve|spades|circeq|tprime|divide|topcir|Assign|topbot|gesdot|divonx|xuplus|timesd|gesles|atilde|solbar|SOFTcy|loplus|timesb|lowast|lowbar|dlcorn|dlcrop|softcy|dollar|lparlt|thksim|lrhard|Atilde|lsaquo|smashp|bigvee|thinsp|wreath|bkarow|lsquor|lstrok|Lstrok|lthree|ltimes|ltlarr|DotDot|simdot|ltrPar|weierp|xsqcup|angmsd|sigmav|sigmaf|zeetrf|Zcaron|zcaron|mapsto|vsupne|thetav|cirmid|marker|mcomma|Zacute|vsubnE|there4|gtlPar|vsubne|bottom|gtrarr|SHCHcy|shchcy|midast|midcir|middot|minusb|minusd|gtrdot|bowtie|sfrown|mnplus|models|colone|seswar|Colone|mstpos|searhk|gtrsim|nacute|Nacute|boxbox|telrec|hairsp|Tcedil|nbumpe|scnsim|ncaron|Ncaron|ncedil|Ncedil|hamilt|Scedil|nearhk|hardcy|HARDcy|tcedil|Tcaron|commat|nequiv|nesear|tcaron|target|hearts|nexist|varrho|scedil|Scaron|scaron|hellip|Sacute|sacute|hercon|swnwar|compfn|rtimes|rthree|rsquor|rsaquo|zacute|wedgeq|homtht|barvee|barwed|Barwed|rpargt|horbar|conint|swarhk|roplus|nltrie|hslash|hstrok|Hstrok|rmoust|Conint|bprime|hybull|hyphen|iacute|Iacute|supsup|supsub|supsim|varphi|coprod|brvbar|agrave|Supset|supset|igrave|Igrave|notinE|Agrave|iiiint|iinfin|copysr|wedbar|Verbar|vangrt|becaus|incare|verbar|inodot|bullet|drcorn|intcal|drcrop|cularr|vellip|Utilde|bumpeq|cupcap|dstrok|Dstrok|CupCap|cupcup|cupdot|eacute|Eacute|supdot|iquest|easter|ecaron|Ecaron|ecolon|isinsv|utilde|itilde|Itilde|curarr|succeq|Bumpeq|cacute|ulcrop|nparsl|Cacute|nprcue|egrave|Egrave|nrarrc|nrarrw|subsup|subsub|nrtrie|jsercy|nsccue|Jsercy|kappav|kcedil|Kcedil|subsim|ulcorn|nsimeq|egsdot|veebar|kgreen|capand|elsdot|Subset|subset|curren|aacute|lacute|Lacute|emptyv|ntilde|Ntilde|lagran|lambda|Lambda|capcap|Ugrave|langle|subdot|emsp13|numero|emsp14|nvdash|nvDash|nVdash|nVDash|ugrave|ufisht|nvHarr|larrfs|nvlArr|larrhk|larrlp|larrpl|nvrArr|Udblac|nwarhk|larrtl|nwnear|oacute|Oacute|latail|lAtail|sstarf|lbrace|odblac|Odblac|lbrack|udblac|odsold|eparsl|lcaron|Lcaron|ograve|Ograve|lcedil|Lcedil|Aacute|ssmile|ssetmn|squarf|ldquor|capcup|ominus|cylcty|rharul|eqcirc|dagger|rfloor|rfisht|Dagger|daleth|equals|origof|capdot|equest|dcaron|Dcaron|rdquor|oslash|Oslash|otilde|Otilde|otimes|Otimes|urcrop|Ubreve|ubreve|Yacute|Uacute|uacute|Rcedil|rcedil|urcorn|parsim|Rcaron|Vdashl|rcaron|Tstrok|percnt|period|permil|Exists|yacute|rbrack|rbrace|phmmat|ccaron|Ccaron|planck|ccedil|plankv|tstrok|female|plusdo|plusdu|ffilig|plusmn|ffllig|Ccedil|rAtail|dfisht|bernou|ratail|Rarrtl|rarrtl|angsph|rarrpl|rarrlp|rarrhk|xwedge|xotime|forall|ForAll|Vvdash|vsupnE|preceq|bigcap|frac12|frac13|frac14|primes|rarrfs|prnsim|frac15|Square|frac16|square|lesdot|frac18|frac23|propto|prurel|rarrap|rangle|puncsp|frac25|Racute|qprime|racute|lesges|frac34|abreve|AElig|eqsim|utdot|setmn|urtri|Equal|Uring|seArr|uring|searr|dashv|Dashv|mumap|nabla|iogon|Iogon|sdote|sdotb|scsim|napid|napos|equiv|natur|Acirc|dblac|erarr|nbump|iprod|erDot|ucirc|awint|esdot|angrt|ncong|isinE|scnap|Scirc|scirc|ndash|isins|Ubrcy|nearr|neArr|isinv|nedot|ubrcy|acute|Ycirc|iukcy|Iukcy|xutri|nesim|caret|jcirc|Jcirc|caron|twixt|ddarr|sccue|exist|jmath|sbquo|ngeqq|angst|ccaps|lceil|ngsim|UpTee|delta|Delta|rtrif|nharr|nhArr|nhpar|rtrie|jukcy|Jukcy|kappa|rsquo|Kappa|nlarr|nlArr|TSHcy|rrarr|aogon|Aogon|fflig|xrarr|tshcy|ccirc|nleqq|filig|upsih|nless|dharl|nlsim|fjlig|ropar|nltri|dharr|robrk|roarr|fllig|fltns|roang|rnmid|subnE|subne|lAarr|trisb|Ccirc|acirc|ccups|blank|VDash|forkv|Vdash|langd|cedil|blk12|blk14|laquo|strns|diams|notin|vDash|larrb|blk34|block|disin|uplus|vdash|vBarv|aelig|starf|Wedge|check|xrArr|lates|lbarr|lBarr|notni|lbbrk|bcong|frasl|lbrke|frown|vrtri|vprop|vnsup|gamma|Gamma|wedge|xodot|bdquo|srarr|doteq|ldquo|boxdl|boxdL|gcirc|Gcirc|boxDl|boxDL|boxdr|boxdR|boxDr|TRADE|trade|rlhar|boxDR|vnsub|npart|vltri|rlarr|boxhd|boxhD|nprec|gescc|nrarr|nrArr|boxHd|boxHD|boxhu|boxhU|nrtri|boxHu|clubs|boxHU|times|colon|Colon|gimel|xlArr|Tilde|nsime|tilde|nsmid|nspar|THORN|thorn|xlarr|nsube|nsubE|thkap|xhArr|comma|nsucc|boxul|boxuL|nsupe|nsupE|gneqq|gnsim|boxUl|boxUL|grave|boxur|boxuR|boxUr|boxUR|lescc|angle|bepsi|boxvh|varpi|boxvH|numsp|Theta|gsime|gsiml|theta|boxVh|boxVH|boxvl|gtcir|gtdot|boxvL|boxVl|boxVL|crarr|cross|Cross|nvsim|boxvr|nwarr|nwArr|sqsup|dtdot|Uogon|lhard|lharu|dtrif|ocirc|Ocirc|lhblk|duarr|odash|sqsub|Hacek|sqcup|llarr|duhar|oelig|OElig|ofcir|boxvR|uogon|lltri|boxVr|csube|uuarr|ohbar|csupe|ctdot|olarr|olcir|harrw|oline|sqcap|omacr|Omacr|omega|Omega|boxVR|aleph|lneqq|lnsim|loang|loarr|rharu|lobrk|hcirc|operp|oplus|rhard|Hcirc|orarr|Union|order|ecirc|Ecirc|cuepr|szlig|cuesc|breve|reals|eDDot|Breve|hoarr|lopar|utrif|rdquo|Umacr|umacr|efDot|swArr|ultri|alpha|rceil|ovbar|swarr|Wcirc|wcirc|smtes|smile|bsemi|lrarr|aring|parsl|lrhar|bsime|uhblk|lrtri|cupor|Aring|uharr|uharl|slarr|rbrke|bsolb|lsime|rbbrk|RBarr|lsimg|phone|rBarr|rbarr|icirc|lsquo|Icirc|emacr|Emacr|ratio|simne|plusb|simlE|simgE|simeq|pluse|ltcir|ltdot|empty|xharr|xdtri|iexcl|Alpha|ltrie|rarrw|pound|ltrif|xcirc|bumpe|prcue|bumpE|asymp|amacr|cuvee|Sigma|sigma|iiint|udhar|iiota|ijlig|IJlig|supnE|imacr|Imacr|prime|Prime|image|prnap|eogon|Eogon|rarrc|mdash|mDDot|cuwed|imath|supne|imped|Amacr|udarr|prsim|micro|rarrb|cwint|raquo|infin|eplus|range|rangd|Ucirc|radic|minus|amalg|veeeq|rAarr|epsiv|ycirc|quest|sharp|quot|zwnj|Qscr|race|qscr|Qopf|qopf|qint|rang|Rang|Zscr|zscr|Zopf|zopf|rarr|rArr|Rarr|Pscr|pscr|prop|prod|prnE|prec|ZHcy|zhcy|prap|Zeta|zeta|Popf|popf|Zdot|plus|zdot|Yuml|yuml|phiv|YUcy|yucy|Yscr|yscr|perp|Yopf|yopf|part|para|YIcy|Ouml|rcub|yicy|YAcy|rdca|ouml|osol|Oscr|rdsh|yacy|real|oscr|xvee|andd|rect|andv|Xscr|oror|ordm|ordf|xscr|ange|aopf|Aopf|rHar|Xopf|opar|Oopf|xopf|xnis|rhov|oopf|omid|xmap|oint|apid|apos|ogon|ascr|Ascr|odot|odiv|xcup|xcap|ocir|oast|nvlt|nvle|nvgt|nvge|nvap|Wscr|wscr|auml|ntlg|ntgl|nsup|nsub|nsim|Nscr|nscr|nsce|Wopf|ring|npre|wopf|npar|Auml|Barv|bbrk|Nopf|nopf|nmid|nLtv|beta|ropf|Ropf|Beta|beth|nles|rpar|nleq|bnot|bNot|nldr|NJcy|rscr|Rscr|Vscr|vscr|rsqb|njcy|bopf|nisd|Bopf|rtri|Vopf|nGtv|ngtr|vopf|boxh|boxH|boxv|nges|ngeq|boxV|bscr|scap|Bscr|bsim|Vert|vert|bsol|bull|bump|caps|cdot|ncup|scnE|ncap|nbsp|napE|Cdot|cent|sdot|Vbar|nang|vBar|chcy|Mscr|mscr|sect|semi|CHcy|Mopf|mopf|sext|circ|cire|mldr|mlcp|cirE|comp|shcy|SHcy|vArr|varr|cong|copf|Copf|copy|COPY|malt|male|macr|lvnE|cscr|ltri|sime|ltcc|simg|Cscr|siml|csub|Uuml|lsqb|lsim|uuml|csup|Lscr|lscr|utri|smid|lpar|cups|smte|lozf|darr|Lopf|Uscr|solb|lopf|sopf|Sopf|lneq|uscr|spar|dArr|lnap|Darr|dash|Sqrt|LJcy|ljcy|lHar|dHar|Upsi|upsi|diam|lesg|djcy|DJcy|leqq|dopf|Dopf|dscr|Dscr|dscy|ldsh|ldca|squf|DScy|sscr|Sscr|dsol|lcub|late|star|Star|Uopf|Larr|lArr|larr|uopf|dtri|dzcy|sube|subE|Lang|lang|Kscr|kscr|Kopf|kopf|KJcy|kjcy|KHcy|khcy|DZcy|ecir|edot|eDot|Jscr|jscr|succ|Jopf|jopf|Edot|uHar|emsp|ensp|Iuml|iuml|eopf|isin|Iscr|iscr|Eopf|epar|sung|epsi|escr|sup1|sup2|sup3|Iota|iota|supe|supE|Iopf|iopf|IOcy|iocy|Escr|esim|Esim|imof|Uarr|QUOT|uArr|uarr|euml|IEcy|iecy|Idot|Euml|euro|excl|Hscr|hscr|Hopf|hopf|TScy|tscy|Tscr|hbar|tscr|flat|tbrk|fnof|hArr|harr|half|fopf|Fopf|tdot|gvnE|fork|trie|gtcc|fscr|Fscr|gdot|gsim|Gscr|gscr|Gopf|gopf|gneq|Gdot|tosa|gnap|Topf|topf|geqq|toea|GJcy|gjcy|tint|gesl|mid|Sfr|ggg|top|ges|gla|glE|glj|geq|gne|gEl|gel|gnE|Gcy|gcy|gap|Tfr|tfr|Tcy|tcy|Hat|Tau|Ffr|tau|Tab|hfr|Hfr|ffr|Fcy|fcy|icy|Icy|iff|ETH|eth|ifr|Ifr|Eta|eta|int|Int|Sup|sup|ucy|Ucy|Sum|sum|jcy|ENG|ufr|Ufr|eng|Jcy|jfr|els|ell|egs|Efr|efr|Jfr|uml|kcy|Kcy|Ecy|ecy|kfr|Kfr|lap|Sub|sub|lat|lcy|Lcy|leg|Dot|dot|lEg|leq|les|squ|div|die|lfr|Lfr|lgE|Dfr|dfr|Del|deg|Dcy|dcy|lne|lnE|sol|loz|smt|Cup|lrm|cup|lsh|Lsh|sim|shy|map|Map|mcy|Mcy|mfr|Mfr|mho|gfr|Gfr|sfr|cir|Chi|chi|nap|Cfr|vcy|Vcy|cfr|Scy|scy|ncy|Ncy|vee|Vee|Cap|cap|nfr|scE|sce|Nfr|nge|ngE|nGg|vfr|Vfr|ngt|bot|nGt|nis|niv|Rsh|rsh|nle|nlE|bne|Bfr|bfr|nLl|nlt|nLt|Bcy|bcy|not|Not|rlm|wfr|Wfr|npr|nsc|num|ocy|ast|Ocy|ofr|xfr|Xfr|Ofr|ogt|ohm|apE|olt|Rho|ape|rho|Rfr|rfr|ord|REG|ang|reg|orv|And|and|AMP|Rcy|amp|Afr|ycy|Ycy|yen|yfr|Yfr|rcy|par|pcy|Pcy|pfr|Pfr|phi|Phi|afr|Acy|acy|zcy|Zcy|piv|acE|acd|zfr|Zfr|pre|prE|psi|Psi|qfr|Qfr|zwj|Or|ge|Gg|gt|gg|el|oS|lt|Lt|LT|Re|lg|gl|eg|ne|Im|it|le|DD|wp|wr|nu|Nu|dd|lE|Sc|sc|pi|Pi|ee|af|ll|Ll|rx|gE|xi|pm|Xi|ic|pr|Pr|in|ni|mp|mu|ac|Mu|or|ap|Gt|GT|ii);|&(Aacute|Agrave|Atilde|Ccedil|Eacute|Egrave|Iacute|Igrave|Ntilde|Oacute|Ograve|Oslash|Otilde|Uacute|Ugrave|Yacute|aacute|agrave|atilde|brvbar|ccedil|curren|divide|eacute|egrave|frac12|frac14|frac34|iacute|igrave|iquest|middot|ntilde|oacute|ograve|oslash|otilde|plusmn|uacute|ugrave|yacute|AElig|Acirc|Aring|Ecirc|Icirc|Ocirc|THORN|Ucirc|acirc|acute|aelig|aring|cedil|ecirc|icirc|iexcl|laquo|micro|ocirc|pound|raquo|szlig|thorn|times|ucirc|Auml|COPY|Euml|Iuml|Ouml|QUOT|Uuml|auml|cent|copy|euml|iuml|macr|nbsp|ordf|ordm|ouml|para|quot|sect|sup1|sup2|sup3|uuml|yuml|AMP|ETH|REG|amp|deg|eth|not|reg|shy|uml|yen|GT|LT|gt|lt)(?!;)([=a-zA-Z0-9]?)|&#([0-9]+)(;?)|&#[xX]([a-fA-F0-9]+)(;?)|&([0-9a-zA-Z]+)/g;
        var decodeMap = { "aacute": "\xE1", "Aacute": "\xC1", "abreve": "\u0103", "Abreve": "\u0102", "ac": "\u223E", "acd": "\u223F", "acE": "\u223E\u0333", "acirc": "\xE2", "Acirc": "\xC2", "acute": "\xB4", "acy": "\u0430", "Acy": "\u0410", "aelig": "\xE6", "AElig": "\xC6", "af": "\u2061", "afr": "\u{1D51E}", "Afr": "\u{1D504}", "agrave": "\xE0", "Agrave": "\xC0", "alefsym": "\u2135", "aleph": "\u2135", "alpha": "\u03B1", "Alpha": "\u0391", "amacr": "\u0101", "Amacr": "\u0100", "amalg": "\u2A3F", "amp": "&", "AMP": "&", "and": "\u2227", "And": "\u2A53", "andand": "\u2A55", "andd": "\u2A5C", "andslope": "\u2A58", "andv": "\u2A5A", "ang": "\u2220", "ange": "\u29A4", "angle": "\u2220", "angmsd": "\u2221", "angmsdaa": "\u29A8", "angmsdab": "\u29A9", "angmsdac": "\u29AA", "angmsdad": "\u29AB", "angmsdae": "\u29AC", "angmsdaf": "\u29AD", "angmsdag": "\u29AE", "angmsdah": "\u29AF", "angrt": "\u221F", "angrtvb": "\u22BE", "angrtvbd": "\u299D", "angsph": "\u2222", "angst": "\xC5", "angzarr": "\u237C", "aogon": "\u0105", "Aogon": "\u0104", "aopf": "\u{1D552}", "Aopf": "\u{1D538}", "ap": "\u2248", "apacir": "\u2A6F", "ape": "\u224A", "apE": "\u2A70", "apid": "\u224B", "apos": "'", "ApplyFunction": "\u2061", "approx": "\u2248", "approxeq": "\u224A", "aring": "\xE5", "Aring": "\xC5", "ascr": "\u{1D4B6}", "Ascr": "\u{1D49C}", "Assign": "\u2254", "ast": "*", "asymp": "\u2248", "asympeq": "\u224D", "atilde": "\xE3", "Atilde": "\xC3", "auml": "\xE4", "Auml": "\xC4", "awconint": "\u2233", "awint": "\u2A11", "backcong": "\u224C", "backepsilon": "\u03F6", "backprime": "\u2035", "backsim": "\u223D", "backsimeq": "\u22CD", "Backslash": "\u2216", "Barv": "\u2AE7", "barvee": "\u22BD", "barwed": "\u2305", "Barwed": "\u2306", "barwedge": "\u2305", "bbrk": "\u23B5", "bbrktbrk": "\u23B6", "bcong": "\u224C", "bcy": "\u0431", "Bcy": "\u0411", "bdquo": "\u201E", "becaus": "\u2235", "because": "\u2235", "Because": "\u2235", "bemptyv": "\u29B0", "bepsi": "\u03F6", "bernou": "\u212C", "Bernoullis": "\u212C", "beta": "\u03B2", "Beta": "\u0392", "beth": "\u2136", "between": "\u226C", "bfr": "\u{1D51F}", "Bfr": "\u{1D505}", "bigcap": "\u22C2", "bigcirc": "\u25EF", "bigcup": "\u22C3", "bigodot": "\u2A00", "bigoplus": "\u2A01", "bigotimes": "\u2A02", "bigsqcup": "\u2A06", "bigstar": "\u2605", "bigtriangledown": "\u25BD", "bigtriangleup": "\u25B3", "biguplus": "\u2A04", "bigvee": "\u22C1", "bigwedge": "\u22C0", "bkarow": "\u290D", "blacklozenge": "\u29EB", "blacksquare": "\u25AA", "blacktriangle": "\u25B4", "blacktriangledown": "\u25BE", "blacktriangleleft": "\u25C2", "blacktriangleright": "\u25B8", "blank": "\u2423", "blk12": "\u2592", "blk14": "\u2591", "blk34": "\u2593", "block": "\u2588", "bne": "=\u20E5", "bnequiv": "\u2261\u20E5", "bnot": "\u2310", "bNot": "\u2AED", "bopf": "\u{1D553}", "Bopf": "\u{1D539}", "bot": "\u22A5", "bottom": "\u22A5", "bowtie": "\u22C8", "boxbox": "\u29C9", "boxdl": "\u2510", "boxdL": "\u2555", "boxDl": "\u2556", "boxDL": "\u2557", "boxdr": "\u250C", "boxdR": "\u2552", "boxDr": "\u2553", "boxDR": "\u2554", "boxh": "\u2500", "boxH": "\u2550", "boxhd": "\u252C", "boxhD": "\u2565", "boxHd": "\u2564", "boxHD": "\u2566", "boxhu": "\u2534", "boxhU": "\u2568", "boxHu": "\u2567", "boxHU": "\u2569", "boxminus": "\u229F", "boxplus": "\u229E", "boxtimes": "\u22A0", "boxul": "\u2518", "boxuL": "\u255B", "boxUl": "\u255C", "boxUL": "\u255D", "boxur": "\u2514", "boxuR": "\u2558", "boxUr": "\u2559", "boxUR": "\u255A", "boxv": "\u2502", "boxV": "\u2551", "boxvh": "\u253C", "boxvH": "\u256A", "boxVh": "\u256B", "boxVH": "\u256C", "boxvl": "\u2524", "boxvL": "\u2561", "boxVl": "\u2562", "boxVL": "\u2563", "boxvr": "\u251C", "boxvR": "\u255E", "boxVr": "\u255F", "boxVR": "\u2560", "bprime": "\u2035", "breve": "\u02D8", "Breve": "\u02D8", "brvbar": "\xA6", "bscr": "\u{1D4B7}", "Bscr": "\u212C", "bsemi": "\u204F", "bsim": "\u223D", "bsime": "\u22CD", "bsol": "\\", "bsolb": "\u29C5", "bsolhsub": "\u27C8", "bull": "\u2022", "bullet": "\u2022", "bump": "\u224E", "bumpe": "\u224F", "bumpE": "\u2AAE", "bumpeq": "\u224F", "Bumpeq": "\u224E", "cacute": "\u0107", "Cacute": "\u0106", "cap": "\u2229", "Cap": "\u22D2", "capand": "\u2A44", "capbrcup": "\u2A49", "capcap": "\u2A4B", "capcup": "\u2A47", "capdot": "\u2A40", "CapitalDifferentialD": "\u2145", "caps": "\u2229\uFE00", "caret": "\u2041", "caron": "\u02C7", "Cayleys": "\u212D", "ccaps": "\u2A4D", "ccaron": "\u010D", "Ccaron": "\u010C", "ccedil": "\xE7", "Ccedil": "\xC7", "ccirc": "\u0109", "Ccirc": "\u0108", "Cconint": "\u2230", "ccups": "\u2A4C", "ccupssm": "\u2A50", "cdot": "\u010B", "Cdot": "\u010A", "cedil": "\xB8", "Cedilla": "\xB8", "cemptyv": "\u29B2", "cent": "\xA2", "centerdot": "\xB7", "CenterDot": "\xB7", "cfr": "\u{1D520}", "Cfr": "\u212D", "chcy": "\u0447", "CHcy": "\u0427", "check": "\u2713", "checkmark": "\u2713", "chi": "\u03C7", "Chi": "\u03A7", "cir": "\u25CB", "circ": "\u02C6", "circeq": "\u2257", "circlearrowleft": "\u21BA", "circlearrowright": "\u21BB", "circledast": "\u229B", "circledcirc": "\u229A", "circleddash": "\u229D", "CircleDot": "\u2299", "circledR": "\xAE", "circledS": "\u24C8", "CircleMinus": "\u2296", "CirclePlus": "\u2295", "CircleTimes": "\u2297", "cire": "\u2257", "cirE": "\u29C3", "cirfnint": "\u2A10", "cirmid": "\u2AEF", "cirscir": "\u29C2", "ClockwiseContourIntegral": "\u2232", "CloseCurlyDoubleQuote": "\u201D", "CloseCurlyQuote": "\u2019", "clubs": "\u2663", "clubsuit": "\u2663", "colon": ":", "Colon": "\u2237", "colone": "\u2254", "Colone": "\u2A74", "coloneq": "\u2254", "comma": ",", "commat": "@", "comp": "\u2201", "compfn": "\u2218", "complement": "\u2201", "complexes": "\u2102", "cong": "\u2245", "congdot": "\u2A6D", "Congruent": "\u2261", "conint": "\u222E", "Conint": "\u222F", "ContourIntegral": "\u222E", "copf": "\u{1D554}", "Copf": "\u2102", "coprod": "\u2210", "Coproduct": "\u2210", "copy": "\xA9", "COPY": "\xA9", "copysr": "\u2117", "CounterClockwiseContourIntegral": "\u2233", "crarr": "\u21B5", "cross": "\u2717", "Cross": "\u2A2F", "cscr": "\u{1D4B8}", "Cscr": "\u{1D49E}", "csub": "\u2ACF", "csube": "\u2AD1", "csup": "\u2AD0", "csupe": "\u2AD2", "ctdot": "\u22EF", "cudarrl": "\u2938", "cudarrr": "\u2935", "cuepr": "\u22DE", "cuesc": "\u22DF", "cularr": "\u21B6", "cularrp": "\u293D", "cup": "\u222A", "Cup": "\u22D3", "cupbrcap": "\u2A48", "cupcap": "\u2A46", "CupCap": "\u224D", "cupcup": "\u2A4A", "cupdot": "\u228D", "cupor": "\u2A45", "cups": "\u222A\uFE00", "curarr": "\u21B7", "curarrm": "\u293C", "curlyeqprec": "\u22DE", "curlyeqsucc": "\u22DF", "curlyvee": "\u22CE", "curlywedge": "\u22CF", "curren": "\xA4", "curvearrowleft": "\u21B6", "curvearrowright": "\u21B7", "cuvee": "\u22CE", "cuwed": "\u22CF", "cwconint": "\u2232", "cwint": "\u2231", "cylcty": "\u232D", "dagger": "\u2020", "Dagger": "\u2021", "daleth": "\u2138", "darr": "\u2193", "dArr": "\u21D3", "Darr": "\u21A1", "dash": "\u2010", "dashv": "\u22A3", "Dashv": "\u2AE4", "dbkarow": "\u290F", "dblac": "\u02DD", "dcaron": "\u010F", "Dcaron": "\u010E", "dcy": "\u0434", "Dcy": "\u0414", "dd": "\u2146", "DD": "\u2145", "ddagger": "\u2021", "ddarr": "\u21CA", "DDotrahd": "\u2911", "ddotseq": "\u2A77", "deg": "\xB0", "Del": "\u2207", "delta": "\u03B4", "Delta": "\u0394", "demptyv": "\u29B1", "dfisht": "\u297F", "dfr": "\u{1D521}", "Dfr": "\u{1D507}", "dHar": "\u2965", "dharl": "\u21C3", "dharr": "\u21C2", "DiacriticalAcute": "\xB4", "DiacriticalDot": "\u02D9", "DiacriticalDoubleAcute": "\u02DD", "DiacriticalGrave": "`", "DiacriticalTilde": "\u02DC", "diam": "\u22C4", "diamond": "\u22C4", "Diamond": "\u22C4", "diamondsuit": "\u2666", "diams": "\u2666", "die": "\xA8", "DifferentialD": "\u2146", "digamma": "\u03DD", "disin": "\u22F2", "div": "\xF7", "divide": "\xF7", "divideontimes": "\u22C7", "divonx": "\u22C7", "djcy": "\u0452", "DJcy": "\u0402", "dlcorn": "\u231E", "dlcrop": "\u230D", "dollar": "$", "dopf": "\u{1D555}", "Dopf": "\u{1D53B}", "dot": "\u02D9", "Dot": "\xA8", "DotDot": "\u20DC", "doteq": "\u2250", "doteqdot": "\u2251", "DotEqual": "\u2250", "dotminus": "\u2238", "dotplus": "\u2214", "dotsquare": "\u22A1", "doublebarwedge": "\u2306", "DoubleContourIntegral": "\u222F", "DoubleDot": "\xA8", "DoubleDownArrow": "\u21D3", "DoubleLeftArrow": "\u21D0", "DoubleLeftRightArrow": "\u21D4", "DoubleLeftTee": "\u2AE4", "DoubleLongLeftArrow": "\u27F8", "DoubleLongLeftRightArrow": "\u27FA", "DoubleLongRightArrow": "\u27F9", "DoubleRightArrow": "\u21D2", "DoubleRightTee": "\u22A8", "DoubleUpArrow": "\u21D1", "DoubleUpDownArrow": "\u21D5", "DoubleVerticalBar": "\u2225", "downarrow": "\u2193", "Downarrow": "\u21D3", "DownArrow": "\u2193", "DownArrowBar": "\u2913", "DownArrowUpArrow": "\u21F5", "DownBreve": "\u0311", "downdownarrows": "\u21CA", "downharpoonleft": "\u21C3", "downharpoonright": "\u21C2", "DownLeftRightVector": "\u2950", "DownLeftTeeVector": "\u295E", "DownLeftVector": "\u21BD", "DownLeftVectorBar": "\u2956", "DownRightTeeVector": "\u295F", "DownRightVector": "\u21C1", "DownRightVectorBar": "\u2957", "DownTee": "\u22A4", "DownTeeArrow": "\u21A7", "drbkarow": "\u2910", "drcorn": "\u231F", "drcrop": "\u230C", "dscr": "\u{1D4B9}", "Dscr": "\u{1D49F}", "dscy": "\u0455", "DScy": "\u0405", "dsol": "\u29F6", "dstrok": "\u0111", "Dstrok": "\u0110", "dtdot": "\u22F1", "dtri": "\u25BF", "dtrif": "\u25BE", "duarr": "\u21F5", "duhar": "\u296F", "dwangle": "\u29A6", "dzcy": "\u045F", "DZcy": "\u040F", "dzigrarr": "\u27FF", "eacute": "\xE9", "Eacute": "\xC9", "easter": "\u2A6E", "ecaron": "\u011B", "Ecaron": "\u011A", "ecir": "\u2256", "ecirc": "\xEA", "Ecirc": "\xCA", "ecolon": "\u2255", "ecy": "\u044D", "Ecy": "\u042D", "eDDot": "\u2A77", "edot": "\u0117", "eDot": "\u2251", "Edot": "\u0116", "ee": "\u2147", "efDot": "\u2252", "efr": "\u{1D522}", "Efr": "\u{1D508}", "eg": "\u2A9A", "egrave": "\xE8", "Egrave": "\xC8", "egs": "\u2A96", "egsdot": "\u2A98", "el": "\u2A99", "Element": "\u2208", "elinters": "\u23E7", "ell": "\u2113", "els": "\u2A95", "elsdot": "\u2A97", "emacr": "\u0113", "Emacr": "\u0112", "empty": "\u2205", "emptyset": "\u2205", "EmptySmallSquare": "\u25FB", "emptyv": "\u2205", "EmptyVerySmallSquare": "\u25AB", "emsp": "\u2003", "emsp13": "\u2004", "emsp14": "\u2005", "eng": "\u014B", "ENG": "\u014A", "ensp": "\u2002", "eogon": "\u0119", "Eogon": "\u0118", "eopf": "\u{1D556}", "Eopf": "\u{1D53C}", "epar": "\u22D5", "eparsl": "\u29E3", "eplus": "\u2A71", "epsi": "\u03B5", "epsilon": "\u03B5", "Epsilon": "\u0395", "epsiv": "\u03F5", "eqcirc": "\u2256", "eqcolon": "\u2255", "eqsim": "\u2242", "eqslantgtr": "\u2A96", "eqslantless": "\u2A95", "Equal": "\u2A75", "equals": "=", "EqualTilde": "\u2242", "equest": "\u225F", "Equilibrium": "\u21CC", "equiv": "\u2261", "equivDD": "\u2A78", "eqvparsl": "\u29E5", "erarr": "\u2971", "erDot": "\u2253", "escr": "\u212F", "Escr": "\u2130", "esdot": "\u2250", "esim": "\u2242", "Esim": "\u2A73", "eta": "\u03B7", "Eta": "\u0397", "eth": "\xF0", "ETH": "\xD0", "euml": "\xEB", "Euml": "\xCB", "euro": "\u20AC", "excl": "!", "exist": "\u2203", "Exists": "\u2203", "expectation": "\u2130", "exponentiale": "\u2147", "ExponentialE": "\u2147", "fallingdotseq": "\u2252", "fcy": "\u0444", "Fcy": "\u0424", "female": "\u2640", "ffilig": "\uFB03", "fflig": "\uFB00", "ffllig": "\uFB04", "ffr": "\u{1D523}", "Ffr": "\u{1D509}", "filig": "\uFB01", "FilledSmallSquare": "\u25FC", "FilledVerySmallSquare": "\u25AA", "fjlig": "fj", "flat": "\u266D", "fllig": "\uFB02", "fltns": "\u25B1", "fnof": "\u0192", "fopf": "\u{1D557}", "Fopf": "\u{1D53D}", "forall": "\u2200", "ForAll": "\u2200", "fork": "\u22D4", "forkv": "\u2AD9", "Fouriertrf": "\u2131", "fpartint": "\u2A0D", "frac12": "\xBD", "frac13": "\u2153", "frac14": "\xBC", "frac15": "\u2155", "frac16": "\u2159", "frac18": "\u215B", "frac23": "\u2154", "frac25": "\u2156", "frac34": "\xBE", "frac35": "\u2157", "frac38": "\u215C", "frac45": "\u2158", "frac56": "\u215A", "frac58": "\u215D", "frac78": "\u215E", "frasl": "\u2044", "frown": "\u2322", "fscr": "\u{1D4BB}", "Fscr": "\u2131", "gacute": "\u01F5", "gamma": "\u03B3", "Gamma": "\u0393", "gammad": "\u03DD", "Gammad": "\u03DC", "gap": "\u2A86", "gbreve": "\u011F", "Gbreve": "\u011E", "Gcedil": "\u0122", "gcirc": "\u011D", "Gcirc": "\u011C", "gcy": "\u0433", "Gcy": "\u0413", "gdot": "\u0121", "Gdot": "\u0120", "ge": "\u2265", "gE": "\u2267", "gel": "\u22DB", "gEl": "\u2A8C", "geq": "\u2265", "geqq": "\u2267", "geqslant": "\u2A7E", "ges": "\u2A7E", "gescc": "\u2AA9", "gesdot": "\u2A80", "gesdoto": "\u2A82", "gesdotol": "\u2A84", "gesl": "\u22DB\uFE00", "gesles": "\u2A94", "gfr": "\u{1D524}", "Gfr": "\u{1D50A}", "gg": "\u226B", "Gg": "\u22D9", "ggg": "\u22D9", "gimel": "\u2137", "gjcy": "\u0453", "GJcy": "\u0403", "gl": "\u2277", "gla": "\u2AA5", "glE": "\u2A92", "glj": "\u2AA4", "gnap": "\u2A8A", "gnapprox": "\u2A8A", "gne": "\u2A88", "gnE": "\u2269", "gneq": "\u2A88", "gneqq": "\u2269", "gnsim": "\u22E7", "gopf": "\u{1D558}", "Gopf": "\u{1D53E}", "grave": "`", "GreaterEqual": "\u2265", "GreaterEqualLess": "\u22DB", "GreaterFullEqual": "\u2267", "GreaterGreater": "\u2AA2", "GreaterLess": "\u2277", "GreaterSlantEqual": "\u2A7E", "GreaterTilde": "\u2273", "gscr": "\u210A", "Gscr": "\u{1D4A2}", "gsim": "\u2273", "gsime": "\u2A8E", "gsiml": "\u2A90", "gt": ">", "Gt": "\u226B", "GT": ">", "gtcc": "\u2AA7", "gtcir": "\u2A7A", "gtdot": "\u22D7", "gtlPar": "\u2995", "gtquest": "\u2A7C", "gtrapprox": "\u2A86", "gtrarr": "\u2978", "gtrdot": "\u22D7", "gtreqless": "\u22DB", "gtreqqless": "\u2A8C", "gtrless": "\u2277", "gtrsim": "\u2273", "gvertneqq": "\u2269\uFE00", "gvnE": "\u2269\uFE00", "Hacek": "\u02C7", "hairsp": "\u200A", "half": "\xBD", "hamilt": "\u210B", "hardcy": "\u044A", "HARDcy": "\u042A", "harr": "\u2194", "hArr": "\u21D4", "harrcir": "\u2948", "harrw": "\u21AD", "Hat": "^", "hbar": "\u210F", "hcirc": "\u0125", "Hcirc": "\u0124", "hearts": "\u2665", "heartsuit": "\u2665", "hellip": "\u2026", "hercon": "\u22B9", "hfr": "\u{1D525}", "Hfr": "\u210C", "HilbertSpace": "\u210B", "hksearow": "\u2925", "hkswarow": "\u2926", "hoarr": "\u21FF", "homtht": "\u223B", "hookleftarrow": "\u21A9", "hookrightarrow": "\u21AA", "hopf": "\u{1D559}", "Hopf": "\u210D", "horbar": "\u2015", "HorizontalLine": "\u2500", "hscr": "\u{1D4BD}", "Hscr": "\u210B", "hslash": "\u210F", "hstrok": "\u0127", "Hstrok": "\u0126", "HumpDownHump": "\u224E", "HumpEqual": "\u224F", "hybull": "\u2043", "hyphen": "\u2010", "iacute": "\xED", "Iacute": "\xCD", "ic": "\u2063", "icirc": "\xEE", "Icirc": "\xCE", "icy": "\u0438", "Icy": "\u0418", "Idot": "\u0130", "iecy": "\u0435", "IEcy": "\u0415", "iexcl": "\xA1", "iff": "\u21D4", "ifr": "\u{1D526}", "Ifr": "\u2111", "igrave": "\xEC", "Igrave": "\xCC", "ii": "\u2148", "iiiint": "\u2A0C", "iiint": "\u222D", "iinfin": "\u29DC", "iiota": "\u2129", "ijlig": "\u0133", "IJlig": "\u0132", "Im": "\u2111", "imacr": "\u012B", "Imacr": "\u012A", "image": "\u2111", "ImaginaryI": "\u2148", "imagline": "\u2110", "imagpart": "\u2111", "imath": "\u0131", "imof": "\u22B7", "imped": "\u01B5", "Implies": "\u21D2", "in": "\u2208", "incare": "\u2105", "infin": "\u221E", "infintie": "\u29DD", "inodot": "\u0131", "int": "\u222B", "Int": "\u222C", "intcal": "\u22BA", "integers": "\u2124", "Integral": "\u222B", "intercal": "\u22BA", "Intersection": "\u22C2", "intlarhk": "\u2A17", "intprod": "\u2A3C", "InvisibleComma": "\u2063", "InvisibleTimes": "\u2062", "iocy": "\u0451", "IOcy": "\u0401", "iogon": "\u012F", "Iogon": "\u012E", "iopf": "\u{1D55A}", "Iopf": "\u{1D540}", "iota": "\u03B9", "Iota": "\u0399", "iprod": "\u2A3C", "iquest": "\xBF", "iscr": "\u{1D4BE}", "Iscr": "\u2110", "isin": "\u2208", "isindot": "\u22F5", "isinE": "\u22F9", "isins": "\u22F4", "isinsv": "\u22F3", "isinv": "\u2208", "it": "\u2062", "itilde": "\u0129", "Itilde": "\u0128", "iukcy": "\u0456", "Iukcy": "\u0406", "iuml": "\xEF", "Iuml": "\xCF", "jcirc": "\u0135", "Jcirc": "\u0134", "jcy": "\u0439", "Jcy": "\u0419", "jfr": "\u{1D527}", "Jfr": "\u{1D50D}", "jmath": "\u0237", "jopf": "\u{1D55B}", "Jopf": "\u{1D541}", "jscr": "\u{1D4BF}", "Jscr": "\u{1D4A5}", "jsercy": "\u0458", "Jsercy": "\u0408", "jukcy": "\u0454", "Jukcy": "\u0404", "kappa": "\u03BA", "Kappa": "\u039A", "kappav": "\u03F0", "kcedil": "\u0137", "Kcedil": "\u0136", "kcy": "\u043A", "Kcy": "\u041A", "kfr": "\u{1D528}", "Kfr": "\u{1D50E}", "kgreen": "\u0138", "khcy": "\u0445", "KHcy": "\u0425", "kjcy": "\u045C", "KJcy": "\u040C", "kopf": "\u{1D55C}", "Kopf": "\u{1D542}", "kscr": "\u{1D4C0}", "Kscr": "\u{1D4A6}", "lAarr": "\u21DA", "lacute": "\u013A", "Lacute": "\u0139", "laemptyv": "\u29B4", "lagran": "\u2112", "lambda": "\u03BB", "Lambda": "\u039B", "lang": "\u27E8", "Lang": "\u27EA", "langd": "\u2991", "langle": "\u27E8", "lap": "\u2A85", "Laplacetrf": "\u2112", "laquo": "\xAB", "larr": "\u2190", "lArr": "\u21D0", "Larr": "\u219E", "larrb": "\u21E4", "larrbfs": "\u291F", "larrfs": "\u291D", "larrhk": "\u21A9", "larrlp": "\u21AB", "larrpl": "\u2939", "larrsim": "\u2973", "larrtl": "\u21A2", "lat": "\u2AAB", "latail": "\u2919", "lAtail": "\u291B", "late": "\u2AAD", "lates": "\u2AAD\uFE00", "lbarr": "\u290C", "lBarr": "\u290E", "lbbrk": "\u2772", "lbrace": "{", "lbrack": "[", "lbrke": "\u298B", "lbrksld": "\u298F", "lbrkslu": "\u298D", "lcaron": "\u013E", "Lcaron": "\u013D", "lcedil": "\u013C", "Lcedil": "\u013B", "lceil": "\u2308", "lcub": "{", "lcy": "\u043B", "Lcy": "\u041B", "ldca": "\u2936", "ldquo": "\u201C", "ldquor": "\u201E", "ldrdhar": "\u2967", "ldrushar": "\u294B", "ldsh": "\u21B2", "le": "\u2264", "lE": "\u2266", "LeftAngleBracket": "\u27E8", "leftarrow": "\u2190", "Leftarrow": "\u21D0", "LeftArrow": "\u2190", "LeftArrowBar": "\u21E4", "LeftArrowRightArrow": "\u21C6", "leftarrowtail": "\u21A2", "LeftCeiling": "\u2308", "LeftDoubleBracket": "\u27E6", "LeftDownTeeVector": "\u2961", "LeftDownVector": "\u21C3", "LeftDownVectorBar": "\u2959", "LeftFloor": "\u230A", "leftharpoondown": "\u21BD", "leftharpoonup": "\u21BC", "leftleftarrows": "\u21C7", "leftrightarrow": "\u2194", "Leftrightarrow": "\u21D4", "LeftRightArrow": "\u2194", "leftrightarrows": "\u21C6", "leftrightharpoons": "\u21CB", "leftrightsquigarrow": "\u21AD", "LeftRightVector": "\u294E", "LeftTee": "\u22A3", "LeftTeeArrow": "\u21A4", "LeftTeeVector": "\u295A", "leftthreetimes": "\u22CB", "LeftTriangle": "\u22B2", "LeftTriangleBar": "\u29CF", "LeftTriangleEqual": "\u22B4", "LeftUpDownVector": "\u2951", "LeftUpTeeVector": "\u2960", "LeftUpVector": "\u21BF", "LeftUpVectorBar": "\u2958", "LeftVector": "\u21BC", "LeftVectorBar": "\u2952", "leg": "\u22DA", "lEg": "\u2A8B", "leq": "\u2264", "leqq": "\u2266", "leqslant": "\u2A7D", "les": "\u2A7D", "lescc": "\u2AA8", "lesdot": "\u2A7F", "lesdoto": "\u2A81", "lesdotor": "\u2A83", "lesg": "\u22DA\uFE00", "lesges": "\u2A93", "lessapprox": "\u2A85", "lessdot": "\u22D6", "lesseqgtr": "\u22DA", "lesseqqgtr": "\u2A8B", "LessEqualGreater": "\u22DA", "LessFullEqual": "\u2266", "LessGreater": "\u2276", "lessgtr": "\u2276", "LessLess": "\u2AA1", "lesssim": "\u2272", "LessSlantEqual": "\u2A7D", "LessTilde": "\u2272", "lfisht": "\u297C", "lfloor": "\u230A", "lfr": "\u{1D529}", "Lfr": "\u{1D50F}", "lg": "\u2276", "lgE": "\u2A91", "lHar": "\u2962", "lhard": "\u21BD", "lharu": "\u21BC", "lharul": "\u296A", "lhblk": "\u2584", "ljcy": "\u0459", "LJcy": "\u0409", "ll": "\u226A", "Ll": "\u22D8", "llarr": "\u21C7", "llcorner": "\u231E", "Lleftarrow": "\u21DA", "llhard": "\u296B", "lltri": "\u25FA", "lmidot": "\u0140", "Lmidot": "\u013F", "lmoust": "\u23B0", "lmoustache": "\u23B0", "lnap": "\u2A89", "lnapprox": "\u2A89", "lne": "\u2A87", "lnE": "\u2268", "lneq": "\u2A87", "lneqq": "\u2268", "lnsim": "\u22E6", "loang": "\u27EC", "loarr": "\u21FD", "lobrk": "\u27E6", "longleftarrow": "\u27F5", "Longleftarrow": "\u27F8", "LongLeftArrow": "\u27F5", "longleftrightarrow": "\u27F7", "Longleftrightarrow": "\u27FA", "LongLeftRightArrow": "\u27F7", "longmapsto": "\u27FC", "longrightarrow": "\u27F6", "Longrightarrow": "\u27F9", "LongRightArrow": "\u27F6", "looparrowleft": "\u21AB", "looparrowright": "\u21AC", "lopar": "\u2985", "lopf": "\u{1D55D}", "Lopf": "\u{1D543}", "loplus": "\u2A2D", "lotimes": "\u2A34", "lowast": "\u2217", "lowbar": "_", "LowerLeftArrow": "\u2199", "LowerRightArrow": "\u2198", "loz": "\u25CA", "lozenge": "\u25CA", "lozf": "\u29EB", "lpar": "(", "lparlt": "\u2993", "lrarr": "\u21C6", "lrcorner": "\u231F", "lrhar": "\u21CB", "lrhard": "\u296D", "lrm": "\u200E", "lrtri": "\u22BF", "lsaquo": "\u2039", "lscr": "\u{1D4C1}", "Lscr": "\u2112", "lsh": "\u21B0", "Lsh": "\u21B0", "lsim": "\u2272", "lsime": "\u2A8D", "lsimg": "\u2A8F", "lsqb": "[", "lsquo": "\u2018", "lsquor": "\u201A", "lstrok": "\u0142", "Lstrok": "\u0141", "lt": "<", "Lt": "\u226A", "LT": "<", "ltcc": "\u2AA6", "ltcir": "\u2A79", "ltdot": "\u22D6", "lthree": "\u22CB", "ltimes": "\u22C9", "ltlarr": "\u2976", "ltquest": "\u2A7B", "ltri": "\u25C3", "ltrie": "\u22B4", "ltrif": "\u25C2", "ltrPar": "\u2996", "lurdshar": "\u294A", "luruhar": "\u2966", "lvertneqq": "\u2268\uFE00", "lvnE": "\u2268\uFE00", "macr": "\xAF", "male": "\u2642", "malt": "\u2720", "maltese": "\u2720", "map": "\u21A6", "Map": "\u2905", "mapsto": "\u21A6", "mapstodown": "\u21A7", "mapstoleft": "\u21A4", "mapstoup": "\u21A5", "marker": "\u25AE", "mcomma": "\u2A29", "mcy": "\u043C", "Mcy": "\u041C", "mdash": "\u2014", "mDDot": "\u223A", "measuredangle": "\u2221", "MediumSpace": "\u205F", "Mellintrf": "\u2133", "mfr": "\u{1D52A}", "Mfr": "\u{1D510}", "mho": "\u2127", "micro": "\xB5", "mid": "\u2223", "midast": "*", "midcir": "\u2AF0", "middot": "\xB7", "minus": "\u2212", "minusb": "\u229F", "minusd": "\u2238", "minusdu": "\u2A2A", "MinusPlus": "\u2213", "mlcp": "\u2ADB", "mldr": "\u2026", "mnplus": "\u2213", "models": "\u22A7", "mopf": "\u{1D55E}", "Mopf": "\u{1D544}", "mp": "\u2213", "mscr": "\u{1D4C2}", "Mscr": "\u2133", "mstpos": "\u223E", "mu": "\u03BC", "Mu": "\u039C", "multimap": "\u22B8", "mumap": "\u22B8", "nabla": "\u2207", "nacute": "\u0144", "Nacute": "\u0143", "nang": "\u2220\u20D2", "nap": "\u2249", "napE": "\u2A70\u0338", "napid": "\u224B\u0338", "napos": "\u0149", "napprox": "\u2249", "natur": "\u266E", "natural": "\u266E", "naturals": "\u2115", "nbsp": "\xA0", "nbump": "\u224E\u0338", "nbumpe": "\u224F\u0338", "ncap": "\u2A43", "ncaron": "\u0148", "Ncaron": "\u0147", "ncedil": "\u0146", "Ncedil": "\u0145", "ncong": "\u2247", "ncongdot": "\u2A6D\u0338", "ncup": "\u2A42", "ncy": "\u043D", "Ncy": "\u041D", "ndash": "\u2013", "ne": "\u2260", "nearhk": "\u2924", "nearr": "\u2197", "neArr": "\u21D7", "nearrow": "\u2197", "nedot": "\u2250\u0338", "NegativeMediumSpace": "\u200B", "NegativeThickSpace": "\u200B", "NegativeThinSpace": "\u200B", "NegativeVeryThinSpace": "\u200B", "nequiv": "\u2262", "nesear": "\u2928", "nesim": "\u2242\u0338", "NestedGreaterGreater": "\u226B", "NestedLessLess": "\u226A", "NewLine": "\n", "nexist": "\u2204", "nexists": "\u2204", "nfr": "\u{1D52B}", "Nfr": "\u{1D511}", "nge": "\u2271", "ngE": "\u2267\u0338", "ngeq": "\u2271", "ngeqq": "\u2267\u0338", "ngeqslant": "\u2A7E\u0338", "nges": "\u2A7E\u0338", "nGg": "\u22D9\u0338", "ngsim": "\u2275", "ngt": "\u226F", "nGt": "\u226B\u20D2", "ngtr": "\u226F", "nGtv": "\u226B\u0338", "nharr": "\u21AE", "nhArr": "\u21CE", "nhpar": "\u2AF2", "ni": "\u220B", "nis": "\u22FC", "nisd": "\u22FA", "niv": "\u220B", "njcy": "\u045A", "NJcy": "\u040A", "nlarr": "\u219A", "nlArr": "\u21CD", "nldr": "\u2025", "nle": "\u2270", "nlE": "\u2266\u0338", "nleftarrow": "\u219A", "nLeftarrow": "\u21CD", "nleftrightarrow": "\u21AE", "nLeftrightarrow": "\u21CE", "nleq": "\u2270", "nleqq": "\u2266\u0338", "nleqslant": "\u2A7D\u0338", "nles": "\u2A7D\u0338", "nless": "\u226E", "nLl": "\u22D8\u0338", "nlsim": "\u2274", "nlt": "\u226E", "nLt": "\u226A\u20D2", "nltri": "\u22EA", "nltrie": "\u22EC", "nLtv": "\u226A\u0338", "nmid": "\u2224", "NoBreak": "\u2060", "NonBreakingSpace": "\xA0", "nopf": "\u{1D55F}", "Nopf": "\u2115", "not": "\xAC", "Not": "\u2AEC", "NotCongruent": "\u2262", "NotCupCap": "\u226D", "NotDoubleVerticalBar": "\u2226", "NotElement": "\u2209", "NotEqual": "\u2260", "NotEqualTilde": "\u2242\u0338", "NotExists": "\u2204", "NotGreater": "\u226F", "NotGreaterEqual": "\u2271", "NotGreaterFullEqual": "\u2267\u0338", "NotGreaterGreater": "\u226B\u0338", "NotGreaterLess": "\u2279", "NotGreaterSlantEqual": "\u2A7E\u0338", "NotGreaterTilde": "\u2275", "NotHumpDownHump": "\u224E\u0338", "NotHumpEqual": "\u224F\u0338", "notin": "\u2209", "notindot": "\u22F5\u0338", "notinE": "\u22F9\u0338", "notinva": "\u2209", "notinvb": "\u22F7", "notinvc": "\u22F6", "NotLeftTriangle": "\u22EA", "NotLeftTriangleBar": "\u29CF\u0338", "NotLeftTriangleEqual": "\u22EC", "NotLess": "\u226E", "NotLessEqual": "\u2270", "NotLessGreater": "\u2278", "NotLessLess": "\u226A\u0338", "NotLessSlantEqual": "\u2A7D\u0338", "NotLessTilde": "\u2274", "NotNestedGreaterGreater": "\u2AA2\u0338", "NotNestedLessLess": "\u2AA1\u0338", "notni": "\u220C", "notniva": "\u220C", "notnivb": "\u22FE", "notnivc": "\u22FD", "NotPrecedes": "\u2280", "NotPrecedesEqual": "\u2AAF\u0338", "NotPrecedesSlantEqual": "\u22E0", "NotReverseElement": "\u220C", "NotRightTriangle": "\u22EB", "NotRightTriangleBar": "\u29D0\u0338", "NotRightTriangleEqual": "\u22ED", "NotSquareSubset": "\u228F\u0338", "NotSquareSubsetEqual": "\u22E2", "NotSquareSuperset": "\u2290\u0338", "NotSquareSupersetEqual": "\u22E3", "NotSubset": "\u2282\u20D2", "NotSubsetEqual": "\u2288", "NotSucceeds": "\u2281", "NotSucceedsEqual": "\u2AB0\u0338", "NotSucceedsSlantEqual": "\u22E1", "NotSucceedsTilde": "\u227F\u0338", "NotSuperset": "\u2283\u20D2", "NotSupersetEqual": "\u2289", "NotTilde": "\u2241", "NotTildeEqual": "\u2244", "NotTildeFullEqual": "\u2247", "NotTildeTilde": "\u2249", "NotVerticalBar": "\u2224", "npar": "\u2226", "nparallel": "\u2226", "nparsl": "\u2AFD\u20E5", "npart": "\u2202\u0338", "npolint": "\u2A14", "npr": "\u2280", "nprcue": "\u22E0", "npre": "\u2AAF\u0338", "nprec": "\u2280", "npreceq": "\u2AAF\u0338", "nrarr": "\u219B", "nrArr": "\u21CF", "nrarrc": "\u2933\u0338", "nrarrw": "\u219D\u0338", "nrightarrow": "\u219B", "nRightarrow": "\u21CF", "nrtri": "\u22EB", "nrtrie": "\u22ED", "nsc": "\u2281", "nsccue": "\u22E1", "nsce": "\u2AB0\u0338", "nscr": "\u{1D4C3}", "Nscr": "\u{1D4A9}", "nshortmid": "\u2224", "nshortparallel": "\u2226", "nsim": "\u2241", "nsime": "\u2244", "nsimeq": "\u2244", "nsmid": "\u2224", "nspar": "\u2226", "nsqsube": "\u22E2", "nsqsupe": "\u22E3", "nsub": "\u2284", "nsube": "\u2288", "nsubE": "\u2AC5\u0338", "nsubset": "\u2282\u20D2", "nsubseteq": "\u2288", "nsubseteqq": "\u2AC5\u0338", "nsucc": "\u2281", "nsucceq": "\u2AB0\u0338", "nsup": "\u2285", "nsupe": "\u2289", "nsupE": "\u2AC6\u0338", "nsupset": "\u2283\u20D2", "nsupseteq": "\u2289", "nsupseteqq": "\u2AC6\u0338", "ntgl": "\u2279", "ntilde": "\xF1", "Ntilde": "\xD1", "ntlg": "\u2278", "ntriangleleft": "\u22EA", "ntrianglelefteq": "\u22EC", "ntriangleright": "\u22EB", "ntrianglerighteq": "\u22ED", "nu": "\u03BD", "Nu": "\u039D", "num": "#", "numero": "\u2116", "numsp": "\u2007", "nvap": "\u224D\u20D2", "nvdash": "\u22AC", "nvDash": "\u22AD", "nVdash": "\u22AE", "nVDash": "\u22AF", "nvge": "\u2265\u20D2", "nvgt": ">\u20D2", "nvHarr": "\u2904", "nvinfin": "\u29DE", "nvlArr": "\u2902", "nvle": "\u2264\u20D2", "nvlt": "<\u20D2", "nvltrie": "\u22B4\u20D2", "nvrArr": "\u2903", "nvrtrie": "\u22B5\u20D2", "nvsim": "\u223C\u20D2", "nwarhk": "\u2923", "nwarr": "\u2196", "nwArr": "\u21D6", "nwarrow": "\u2196", "nwnear": "\u2927", "oacute": "\xF3", "Oacute": "\xD3", "oast": "\u229B", "ocir": "\u229A", "ocirc": "\xF4", "Ocirc": "\xD4", "ocy": "\u043E", "Ocy": "\u041E", "odash": "\u229D", "odblac": "\u0151", "Odblac": "\u0150", "odiv": "\u2A38", "odot": "\u2299", "odsold": "\u29BC", "oelig": "\u0153", "OElig": "\u0152", "ofcir": "\u29BF", "ofr": "\u{1D52C}", "Ofr": "\u{1D512}", "ogon": "\u02DB", "ograve": "\xF2", "Ograve": "\xD2", "ogt": "\u29C1", "ohbar": "\u29B5", "ohm": "\u03A9", "oint": "\u222E", "olarr": "\u21BA", "olcir": "\u29BE", "olcross": "\u29BB", "oline": "\u203E", "olt": "\u29C0", "omacr": "\u014D", "Omacr": "\u014C", "omega": "\u03C9", "Omega": "\u03A9", "omicron": "\u03BF", "Omicron": "\u039F", "omid": "\u29B6", "ominus": "\u2296", "oopf": "\u{1D560}", "Oopf": "\u{1D546}", "opar": "\u29B7", "OpenCurlyDoubleQuote": "\u201C", "OpenCurlyQuote": "\u2018", "operp": "\u29B9", "oplus": "\u2295", "or": "\u2228", "Or": "\u2A54", "orarr": "\u21BB", "ord": "\u2A5D", "order": "\u2134", "orderof": "\u2134", "ordf": "\xAA", "ordm": "\xBA", "origof": "\u22B6", "oror": "\u2A56", "orslope": "\u2A57", "orv": "\u2A5B", "oS": "\u24C8", "oscr": "\u2134", "Oscr": "\u{1D4AA}", "oslash": "\xF8", "Oslash": "\xD8", "osol": "\u2298", "otilde": "\xF5", "Otilde": "\xD5", "otimes": "\u2297", "Otimes": "\u2A37", "otimesas": "\u2A36", "ouml": "\xF6", "Ouml": "\xD6", "ovbar": "\u233D", "OverBar": "\u203E", "OverBrace": "\u23DE", "OverBracket": "\u23B4", "OverParenthesis": "\u23DC", "par": "\u2225", "para": "\xB6", "parallel": "\u2225", "parsim": "\u2AF3", "parsl": "\u2AFD", "part": "\u2202", "PartialD": "\u2202", "pcy": "\u043F", "Pcy": "\u041F", "percnt": "%", "period": ".", "permil": "\u2030", "perp": "\u22A5", "pertenk": "\u2031", "pfr": "\u{1D52D}", "Pfr": "\u{1D513}", "phi": "\u03C6", "Phi": "\u03A6", "phiv": "\u03D5", "phmmat": "\u2133", "phone": "\u260E", "pi": "\u03C0", "Pi": "\u03A0", "pitchfork": "\u22D4", "piv": "\u03D6", "planck": "\u210F", "planckh": "\u210E", "plankv": "\u210F", "plus": "+", "plusacir": "\u2A23", "plusb": "\u229E", "pluscir": "\u2A22", "plusdo": "\u2214", "plusdu": "\u2A25", "pluse": "\u2A72", "PlusMinus": "\xB1", "plusmn": "\xB1", "plussim": "\u2A26", "plustwo": "\u2A27", "pm": "\xB1", "Poincareplane": "\u210C", "pointint": "\u2A15", "popf": "\u{1D561}", "Popf": "\u2119", "pound": "\xA3", "pr": "\u227A", "Pr": "\u2ABB", "prap": "\u2AB7", "prcue": "\u227C", "pre": "\u2AAF", "prE": "\u2AB3", "prec": "\u227A", "precapprox": "\u2AB7", "preccurlyeq": "\u227C", "Precedes": "\u227A", "PrecedesEqual": "\u2AAF", "PrecedesSlantEqual": "\u227C", "PrecedesTilde": "\u227E", "preceq": "\u2AAF", "precnapprox": "\u2AB9", "precneqq": "\u2AB5", "precnsim": "\u22E8", "precsim": "\u227E", "prime": "\u2032", "Prime": "\u2033", "primes": "\u2119", "prnap": "\u2AB9", "prnE": "\u2AB5", "prnsim": "\u22E8", "prod": "\u220F", "Product": "\u220F", "profalar": "\u232E", "profline": "\u2312", "profsurf": "\u2313", "prop": "\u221D", "Proportion": "\u2237", "Proportional": "\u221D", "propto": "\u221D", "prsim": "\u227E", "prurel": "\u22B0", "pscr": "\u{1D4C5}", "Pscr": "\u{1D4AB}", "psi": "\u03C8", "Psi": "\u03A8", "puncsp": "\u2008", "qfr": "\u{1D52E}", "Qfr": "\u{1D514}", "qint": "\u2A0C", "qopf": "\u{1D562}", "Qopf": "\u211A", "qprime": "\u2057", "qscr": "\u{1D4C6}", "Qscr": "\u{1D4AC}", "quaternions": "\u210D", "quatint": "\u2A16", "quest": "?", "questeq": "\u225F", "quot": '"', "QUOT": '"', "rAarr": "\u21DB", "race": "\u223D\u0331", "racute": "\u0155", "Racute": "\u0154", "radic": "\u221A", "raemptyv": "\u29B3", "rang": "\u27E9", "Rang": "\u27EB", "rangd": "\u2992", "range": "\u29A5", "rangle": "\u27E9", "raquo": "\xBB", "rarr": "\u2192", "rArr": "\u21D2", "Rarr": "\u21A0", "rarrap": "\u2975", "rarrb": "\u21E5", "rarrbfs": "\u2920", "rarrc": "\u2933", "rarrfs": "\u291E", "rarrhk": "\u21AA", "rarrlp": "\u21AC", "rarrpl": "\u2945", "rarrsim": "\u2974", "rarrtl": "\u21A3", "Rarrtl": "\u2916", "rarrw": "\u219D", "ratail": "\u291A", "rAtail": "\u291C", "ratio": "\u2236", "rationals": "\u211A", "rbarr": "\u290D", "rBarr": "\u290F", "RBarr": "\u2910", "rbbrk": "\u2773", "rbrace": "}", "rbrack": "]", "rbrke": "\u298C", "rbrksld": "\u298E", "rbrkslu": "\u2990", "rcaron": "\u0159", "Rcaron": "\u0158", "rcedil": "\u0157", "Rcedil": "\u0156", "rceil": "\u2309", "rcub": "}", "rcy": "\u0440", "Rcy": "\u0420", "rdca": "\u2937", "rdldhar": "\u2969", "rdquo": "\u201D", "rdquor": "\u201D", "rdsh": "\u21B3", "Re": "\u211C", "real": "\u211C", "realine": "\u211B", "realpart": "\u211C", "reals": "\u211D", "rect": "\u25AD", "reg": "\xAE", "REG": "\xAE", "ReverseElement": "\u220B", "ReverseEquilibrium": "\u21CB", "ReverseUpEquilibrium": "\u296F", "rfisht": "\u297D", "rfloor": "\u230B", "rfr": "\u{1D52F}", "Rfr": "\u211C", "rHar": "\u2964", "rhard": "\u21C1", "rharu": "\u21C0", "rharul": "\u296C", "rho": "\u03C1", "Rho": "\u03A1", "rhov": "\u03F1", "RightAngleBracket": "\u27E9", "rightarrow": "\u2192", "Rightarrow": "\u21D2", "RightArrow": "\u2192", "RightArrowBar": "\u21E5", "RightArrowLeftArrow": "\u21C4", "rightarrowtail": "\u21A3", "RightCeiling": "\u2309", "RightDoubleBracket": "\u27E7", "RightDownTeeVector": "\u295D", "RightDownVector": "\u21C2", "RightDownVectorBar": "\u2955", "RightFloor": "\u230B", "rightharpoondown": "\u21C1", "rightharpoonup": "\u21C0", "rightleftarrows": "\u21C4", "rightleftharpoons": "\u21CC", "rightrightarrows": "\u21C9", "rightsquigarrow": "\u219D", "RightTee": "\u22A2", "RightTeeArrow": "\u21A6", "RightTeeVector": "\u295B", "rightthreetimes": "\u22CC", "RightTriangle": "\u22B3", "RightTriangleBar": "\u29D0", "RightTriangleEqual": "\u22B5", "RightUpDownVector": "\u294F", "RightUpTeeVector": "\u295C", "RightUpVector": "\u21BE", "RightUpVectorBar": "\u2954", "RightVector": "\u21C0", "RightVectorBar": "\u2953", "ring": "\u02DA", "risingdotseq": "\u2253", "rlarr": "\u21C4", "rlhar": "\u21CC", "rlm": "\u200F", "rmoust": "\u23B1", "rmoustache": "\u23B1", "rnmid": "\u2AEE", "roang": "\u27ED", "roarr": "\u21FE", "robrk": "\u27E7", "ropar": "\u2986", "ropf": "\u{1D563}", "Ropf": "\u211D", "roplus": "\u2A2E", "rotimes": "\u2A35", "RoundImplies": "\u2970", "rpar": ")", "rpargt": "\u2994", "rppolint": "\u2A12", "rrarr": "\u21C9", "Rrightarrow": "\u21DB", "rsaquo": "\u203A", "rscr": "\u{1D4C7}", "Rscr": "\u211B", "rsh": "\u21B1", "Rsh": "\u21B1", "rsqb": "]", "rsquo": "\u2019", "rsquor": "\u2019", "rthree": "\u22CC", "rtimes": "\u22CA", "rtri": "\u25B9", "rtrie": "\u22B5", "rtrif": "\u25B8", "rtriltri": "\u29CE", "RuleDelayed": "\u29F4", "ruluhar": "\u2968", "rx": "\u211E", "sacute": "\u015B", "Sacute": "\u015A", "sbquo": "\u201A", "sc": "\u227B", "Sc": "\u2ABC", "scap": "\u2AB8", "scaron": "\u0161", "Scaron": "\u0160", "sccue": "\u227D", "sce": "\u2AB0", "scE": "\u2AB4", "scedil": "\u015F", "Scedil": "\u015E", "scirc": "\u015D", "Scirc": "\u015C", "scnap": "\u2ABA", "scnE": "\u2AB6", "scnsim": "\u22E9", "scpolint": "\u2A13", "scsim": "\u227F", "scy": "\u0441", "Scy": "\u0421", "sdot": "\u22C5", "sdotb": "\u22A1", "sdote": "\u2A66", "searhk": "\u2925", "searr": "\u2198", "seArr": "\u21D8", "searrow": "\u2198", "sect": "\xA7", "semi": ";", "seswar": "\u2929", "setminus": "\u2216", "setmn": "\u2216", "sext": "\u2736", "sfr": "\u{1D530}", "Sfr": "\u{1D516}", "sfrown": "\u2322", "sharp": "\u266F", "shchcy": "\u0449", "SHCHcy": "\u0429", "shcy": "\u0448", "SHcy": "\u0428", "ShortDownArrow": "\u2193", "ShortLeftArrow": "\u2190", "shortmid": "\u2223", "shortparallel": "\u2225", "ShortRightArrow": "\u2192", "ShortUpArrow": "\u2191", "shy": "\xAD", "sigma": "\u03C3", "Sigma": "\u03A3", "sigmaf": "\u03C2", "sigmav": "\u03C2", "sim": "\u223C", "simdot": "\u2A6A", "sime": "\u2243", "simeq": "\u2243", "simg": "\u2A9E", "simgE": "\u2AA0", "siml": "\u2A9D", "simlE": "\u2A9F", "simne": "\u2246", "simplus": "\u2A24", "simrarr": "\u2972", "slarr": "\u2190", "SmallCircle": "\u2218", "smallsetminus": "\u2216", "smashp": "\u2A33", "smeparsl": "\u29E4", "smid": "\u2223", "smile": "\u2323", "smt": "\u2AAA", "smte": "\u2AAC", "smtes": "\u2AAC\uFE00", "softcy": "\u044C", "SOFTcy": "\u042C", "sol": "/", "solb": "\u29C4", "solbar": "\u233F", "sopf": "\u{1D564}", "Sopf": "\u{1D54A}", "spades": "\u2660", "spadesuit": "\u2660", "spar": "\u2225", "sqcap": "\u2293", "sqcaps": "\u2293\uFE00", "sqcup": "\u2294", "sqcups": "\u2294\uFE00", "Sqrt": "\u221A", "sqsub": "\u228F", "sqsube": "\u2291", "sqsubset": "\u228F", "sqsubseteq": "\u2291", "sqsup": "\u2290", "sqsupe": "\u2292", "sqsupset": "\u2290", "sqsupseteq": "\u2292", "squ": "\u25A1", "square": "\u25A1", "Square": "\u25A1", "SquareIntersection": "\u2293", "SquareSubset": "\u228F", "SquareSubsetEqual": "\u2291", "SquareSuperset": "\u2290", "SquareSupersetEqual": "\u2292", "SquareUnion": "\u2294", "squarf": "\u25AA", "squf": "\u25AA", "srarr": "\u2192", "sscr": "\u{1D4C8}", "Sscr": "\u{1D4AE}", "ssetmn": "\u2216", "ssmile": "\u2323", "sstarf": "\u22C6", "star": "\u2606", "Star": "\u22C6", "starf": "\u2605", "straightepsilon": "\u03F5", "straightphi": "\u03D5", "strns": "\xAF", "sub": "\u2282", "Sub": "\u22D0", "subdot": "\u2ABD", "sube": "\u2286", "subE": "\u2AC5", "subedot": "\u2AC3", "submult": "\u2AC1", "subne": "\u228A", "subnE": "\u2ACB", "subplus": "\u2ABF", "subrarr": "\u2979", "subset": "\u2282", "Subset": "\u22D0", "subseteq": "\u2286", "subseteqq": "\u2AC5", "SubsetEqual": "\u2286", "subsetneq": "\u228A", "subsetneqq": "\u2ACB", "subsim": "\u2AC7", "subsub": "\u2AD5", "subsup": "\u2AD3", "succ": "\u227B", "succapprox": "\u2AB8", "succcurlyeq": "\u227D", "Succeeds": "\u227B", "SucceedsEqual": "\u2AB0", "SucceedsSlantEqual": "\u227D", "SucceedsTilde": "\u227F", "succeq": "\u2AB0", "succnapprox": "\u2ABA", "succneqq": "\u2AB6", "succnsim": "\u22E9", "succsim": "\u227F", "SuchThat": "\u220B", "sum": "\u2211", "Sum": "\u2211", "sung": "\u266A", "sup": "\u2283", "Sup": "\u22D1", "sup1": "\xB9", "sup2": "\xB2", "sup3": "\xB3", "supdot": "\u2ABE", "supdsub": "\u2AD8", "supe": "\u2287", "supE": "\u2AC6", "supedot": "\u2AC4", "Superset": "\u2283", "SupersetEqual": "\u2287", "suphsol": "\u27C9", "suphsub": "\u2AD7", "suplarr": "\u297B", "supmult": "\u2AC2", "supne": "\u228B", "supnE": "\u2ACC", "supplus": "\u2AC0", "supset": "\u2283", "Supset": "\u22D1", "supseteq": "\u2287", "supseteqq": "\u2AC6", "supsetneq": "\u228B", "supsetneqq": "\u2ACC", "supsim": "\u2AC8", "supsub": "\u2AD4", "supsup": "\u2AD6", "swarhk": "\u2926", "swarr": "\u2199", "swArr": "\u21D9", "swarrow": "\u2199", "swnwar": "\u292A", "szlig": "\xDF", "Tab": "	", "target": "\u2316", "tau": "\u03C4", "Tau": "\u03A4", "tbrk": "\u23B4", "tcaron": "\u0165", "Tcaron": "\u0164", "tcedil": "\u0163", "Tcedil": "\u0162", "tcy": "\u0442", "Tcy": "\u0422", "tdot": "\u20DB", "telrec": "\u2315", "tfr": "\u{1D531}", "Tfr": "\u{1D517}", "there4": "\u2234", "therefore": "\u2234", "Therefore": "\u2234", "theta": "\u03B8", "Theta": "\u0398", "thetasym": "\u03D1", "thetav": "\u03D1", "thickapprox": "\u2248", "thicksim": "\u223C", "ThickSpace": "\u205F\u200A", "thinsp": "\u2009", "ThinSpace": "\u2009", "thkap": "\u2248", "thksim": "\u223C", "thorn": "\xFE", "THORN": "\xDE", "tilde": "\u02DC", "Tilde": "\u223C", "TildeEqual": "\u2243", "TildeFullEqual": "\u2245", "TildeTilde": "\u2248", "times": "\xD7", "timesb": "\u22A0", "timesbar": "\u2A31", "timesd": "\u2A30", "tint": "\u222D", "toea": "\u2928", "top": "\u22A4", "topbot": "\u2336", "topcir": "\u2AF1", "topf": "\u{1D565}", "Topf": "\u{1D54B}", "topfork": "\u2ADA", "tosa": "\u2929", "tprime": "\u2034", "trade": "\u2122", "TRADE": "\u2122", "triangle": "\u25B5", "triangledown": "\u25BF", "triangleleft": "\u25C3", "trianglelefteq": "\u22B4", "triangleq": "\u225C", "triangleright": "\u25B9", "trianglerighteq": "\u22B5", "tridot": "\u25EC", "trie": "\u225C", "triminus": "\u2A3A", "TripleDot": "\u20DB", "triplus": "\u2A39", "trisb": "\u29CD", "tritime": "\u2A3B", "trpezium": "\u23E2", "tscr": "\u{1D4C9}", "Tscr": "\u{1D4AF}", "tscy": "\u0446", "TScy": "\u0426", "tshcy": "\u045B", "TSHcy": "\u040B", "tstrok": "\u0167", "Tstrok": "\u0166", "twixt": "\u226C", "twoheadleftarrow": "\u219E", "twoheadrightarrow": "\u21A0", "uacute": "\xFA", "Uacute": "\xDA", "uarr": "\u2191", "uArr": "\u21D1", "Uarr": "\u219F", "Uarrocir": "\u2949", "ubrcy": "\u045E", "Ubrcy": "\u040E", "ubreve": "\u016D", "Ubreve": "\u016C", "ucirc": "\xFB", "Ucirc": "\xDB", "ucy": "\u0443", "Ucy": "\u0423", "udarr": "\u21C5", "udblac": "\u0171", "Udblac": "\u0170", "udhar": "\u296E", "ufisht": "\u297E", "ufr": "\u{1D532}", "Ufr": "\u{1D518}", "ugrave": "\xF9", "Ugrave": "\xD9", "uHar": "\u2963", "uharl": "\u21BF", "uharr": "\u21BE", "uhblk": "\u2580", "ulcorn": "\u231C", "ulcorner": "\u231C", "ulcrop": "\u230F", "ultri": "\u25F8", "umacr": "\u016B", "Umacr": "\u016A", "uml": "\xA8", "UnderBar": "_", "UnderBrace": "\u23DF", "UnderBracket": "\u23B5", "UnderParenthesis": "\u23DD", "Union": "\u22C3", "UnionPlus": "\u228E", "uogon": "\u0173", "Uogon": "\u0172", "uopf": "\u{1D566}", "Uopf": "\u{1D54C}", "uparrow": "\u2191", "Uparrow": "\u21D1", "UpArrow": "\u2191", "UpArrowBar": "\u2912", "UpArrowDownArrow": "\u21C5", "updownarrow": "\u2195", "Updownarrow": "\u21D5", "UpDownArrow": "\u2195", "UpEquilibrium": "\u296E", "upharpoonleft": "\u21BF", "upharpoonright": "\u21BE", "uplus": "\u228E", "UpperLeftArrow": "\u2196", "UpperRightArrow": "\u2197", "upsi": "\u03C5", "Upsi": "\u03D2", "upsih": "\u03D2", "upsilon": "\u03C5", "Upsilon": "\u03A5", "UpTee": "\u22A5", "UpTeeArrow": "\u21A5", "upuparrows": "\u21C8", "urcorn": "\u231D", "urcorner": "\u231D", "urcrop": "\u230E", "uring": "\u016F", "Uring": "\u016E", "urtri": "\u25F9", "uscr": "\u{1D4CA}", "Uscr": "\u{1D4B0}", "utdot": "\u22F0", "utilde": "\u0169", "Utilde": "\u0168", "utri": "\u25B5", "utrif": "\u25B4", "uuarr": "\u21C8", "uuml": "\xFC", "Uuml": "\xDC", "uwangle": "\u29A7", "vangrt": "\u299C", "varepsilon": "\u03F5", "varkappa": "\u03F0", "varnothing": "\u2205", "varphi": "\u03D5", "varpi": "\u03D6", "varpropto": "\u221D", "varr": "\u2195", "vArr": "\u21D5", "varrho": "\u03F1", "varsigma": "\u03C2", "varsubsetneq": "\u228A\uFE00", "varsubsetneqq": "\u2ACB\uFE00", "varsupsetneq": "\u228B\uFE00", "varsupsetneqq": "\u2ACC\uFE00", "vartheta": "\u03D1", "vartriangleleft": "\u22B2", "vartriangleright": "\u22B3", "vBar": "\u2AE8", "Vbar": "\u2AEB", "vBarv": "\u2AE9", "vcy": "\u0432", "Vcy": "\u0412", "vdash": "\u22A2", "vDash": "\u22A8", "Vdash": "\u22A9", "VDash": "\u22AB", "Vdashl": "\u2AE6", "vee": "\u2228", "Vee": "\u22C1", "veebar": "\u22BB", "veeeq": "\u225A", "vellip": "\u22EE", "verbar": "|", "Verbar": "\u2016", "vert": "|", "Vert": "\u2016", "VerticalBar": "\u2223", "VerticalLine": "|", "VerticalSeparator": "\u2758", "VerticalTilde": "\u2240", "VeryThinSpace": "\u200A", "vfr": "\u{1D533}", "Vfr": "\u{1D519}", "vltri": "\u22B2", "vnsub": "\u2282\u20D2", "vnsup": "\u2283\u20D2", "vopf": "\u{1D567}", "Vopf": "\u{1D54D}", "vprop": "\u221D", "vrtri": "\u22B3", "vscr": "\u{1D4CB}", "Vscr": "\u{1D4B1}", "vsubne": "\u228A\uFE00", "vsubnE": "\u2ACB\uFE00", "vsupne": "\u228B\uFE00", "vsupnE": "\u2ACC\uFE00", "Vvdash": "\u22AA", "vzigzag": "\u299A", "wcirc": "\u0175", "Wcirc": "\u0174", "wedbar": "\u2A5F", "wedge": "\u2227", "Wedge": "\u22C0", "wedgeq": "\u2259", "weierp": "\u2118", "wfr": "\u{1D534}", "Wfr": "\u{1D51A}", "wopf": "\u{1D568}", "Wopf": "\u{1D54E}", "wp": "\u2118", "wr": "\u2240", "wreath": "\u2240", "wscr": "\u{1D4CC}", "Wscr": "\u{1D4B2}", "xcap": "\u22C2", "xcirc": "\u25EF", "xcup": "\u22C3", "xdtri": "\u25BD", "xfr": "\u{1D535}", "Xfr": "\u{1D51B}", "xharr": "\u27F7", "xhArr": "\u27FA", "xi": "\u03BE", "Xi": "\u039E", "xlarr": "\u27F5", "xlArr": "\u27F8", "xmap": "\u27FC", "xnis": "\u22FB", "xodot": "\u2A00", "xopf": "\u{1D569}", "Xopf": "\u{1D54F}", "xoplus": "\u2A01", "xotime": "\u2A02", "xrarr": "\u27F6", "xrArr": "\u27F9", "xscr": "\u{1D4CD}", "Xscr": "\u{1D4B3}", "xsqcup": "\u2A06", "xuplus": "\u2A04", "xutri": "\u25B3", "xvee": "\u22C1", "xwedge": "\u22C0", "yacute": "\xFD", "Yacute": "\xDD", "yacy": "\u044F", "YAcy": "\u042F", "ycirc": "\u0177", "Ycirc": "\u0176", "ycy": "\u044B", "Ycy": "\u042B", "yen": "\xA5", "yfr": "\u{1D536}", "Yfr": "\u{1D51C}", "yicy": "\u0457", "YIcy": "\u0407", "yopf": "\u{1D56A}", "Yopf": "\u{1D550}", "yscr": "\u{1D4CE}", "Yscr": "\u{1D4B4}", "yucy": "\u044E", "YUcy": "\u042E", "yuml": "\xFF", "Yuml": "\u0178", "zacute": "\u017A", "Zacute": "\u0179", "zcaron": "\u017E", "Zcaron": "\u017D", "zcy": "\u0437", "Zcy": "\u0417", "zdot": "\u017C", "Zdot": "\u017B", "zeetrf": "\u2128", "ZeroWidthSpace": "\u200B", "zeta": "\u03B6", "Zeta": "\u0396", "zfr": "\u{1D537}", "Zfr": "\u2128", "zhcy": "\u0436", "ZHcy": "\u0416", "zigrarr": "\u21DD", "zopf": "\u{1D56B}", "Zopf": "\u2124", "zscr": "\u{1D4CF}", "Zscr": "\u{1D4B5}", "zwj": "\u200D", "zwnj": "\u200C" };
        var decodeMapLegacy = { "aacute": "\xE1", "Aacute": "\xC1", "acirc": "\xE2", "Acirc": "\xC2", "acute": "\xB4", "aelig": "\xE6", "AElig": "\xC6", "agrave": "\xE0", "Agrave": "\xC0", "amp": "&", "AMP": "&", "aring": "\xE5", "Aring": "\xC5", "atilde": "\xE3", "Atilde": "\xC3", "auml": "\xE4", "Auml": "\xC4", "brvbar": "\xA6", "ccedil": "\xE7", "Ccedil": "\xC7", "cedil": "\xB8", "cent": "\xA2", "copy": "\xA9", "COPY": "\xA9", "curren": "\xA4", "deg": "\xB0", "divide": "\xF7", "eacute": "\xE9", "Eacute": "\xC9", "ecirc": "\xEA", "Ecirc": "\xCA", "egrave": "\xE8", "Egrave": "\xC8", "eth": "\xF0", "ETH": "\xD0", "euml": "\xEB", "Euml": "\xCB", "frac12": "\xBD", "frac14": "\xBC", "frac34": "\xBE", "gt": ">", "GT": ">", "iacute": "\xED", "Iacute": "\xCD", "icirc": "\xEE", "Icirc": "\xCE", "iexcl": "\xA1", "igrave": "\xEC", "Igrave": "\xCC", "iquest": "\xBF", "iuml": "\xEF", "Iuml": "\xCF", "laquo": "\xAB", "lt": "<", "LT": "<", "macr": "\xAF", "micro": "\xB5", "middot": "\xB7", "nbsp": "\xA0", "not": "\xAC", "ntilde": "\xF1", "Ntilde": "\xD1", "oacute": "\xF3", "Oacute": "\xD3", "ocirc": "\xF4", "Ocirc": "\xD4", "ograve": "\xF2", "Ograve": "\xD2", "ordf": "\xAA", "ordm": "\xBA", "oslash": "\xF8", "Oslash": "\xD8", "otilde": "\xF5", "Otilde": "\xD5", "ouml": "\xF6", "Ouml": "\xD6", "para": "\xB6", "plusmn": "\xB1", "pound": "\xA3", "quot": '"', "QUOT": '"', "raquo": "\xBB", "reg": "\xAE", "REG": "\xAE", "sect": "\xA7", "shy": "\xAD", "sup1": "\xB9", "sup2": "\xB2", "sup3": "\xB3", "szlig": "\xDF", "thorn": "\xFE", "THORN": "\xDE", "times": "\xD7", "uacute": "\xFA", "Uacute": "\xDA", "ucirc": "\xFB", "Ucirc": "\xDB", "ugrave": "\xF9", "Ugrave": "\xD9", "uml": "\xA8", "uuml": "\xFC", "Uuml": "\xDC", "yacute": "\xFD", "Yacute": "\xDD", "yen": "\xA5", "yuml": "\xFF" };
        var decodeMapNumeric = { "0": "\uFFFD", "128": "\u20AC", "130": "\u201A", "131": "\u0192", "132": "\u201E", "133": "\u2026", "134": "\u2020", "135": "\u2021", "136": "\u02C6", "137": "\u2030", "138": "\u0160", "139": "\u2039", "140": "\u0152", "142": "\u017D", "145": "\u2018", "146": "\u2019", "147": "\u201C", "148": "\u201D", "149": "\u2022", "150": "\u2013", "151": "\u2014", "152": "\u02DC", "153": "\u2122", "154": "\u0161", "155": "\u203A", "156": "\u0153", "158": "\u017E", "159": "\u0178" };
        var invalidReferenceCodePoints = [1, 2, 3, 4, 5, 6, 7, 8, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 64976, 64977, 64978, 64979, 64980, 64981, 64982, 64983, 64984, 64985, 64986, 64987, 64988, 64989, 64990, 64991, 64992, 64993, 64994, 64995, 64996, 64997, 64998, 64999, 65e3, 65001, 65002, 65003, 65004, 65005, 65006, 65007, 65534, 65535, 131070, 131071, 196606, 196607, 262142, 262143, 327678, 327679, 393214, 393215, 458750, 458751, 524286, 524287, 589822, 589823, 655358, 655359, 720894, 720895, 786430, 786431, 851966, 851967, 917502, 917503, 983038, 983039, 1048574, 1048575, 1114110, 1114111];
        var stringFromCharCode = String.fromCharCode;
        var object = {};
        var hasOwnProperty = object.hasOwnProperty;
        var has = function(object2, propertyName) {
          return hasOwnProperty.call(object2, propertyName);
        };
        var contains = function(array, value) {
          var index = -1;
          var length = array.length;
          while (++index < length) {
            if (array[index] == value) {
              return true;
            }
          }
          return false;
        };
        var merge = function(options, defaults) {
          if (!options) {
            return defaults;
          }
          var result = {};
          var key2;
          for (key2 in defaults) {
            result[key2] = has(options, key2) ? options[key2] : defaults[key2];
          }
          return result;
        };
        var codePointToSymbol = function(codePoint, strict) {
          var output = "";
          if (codePoint >= 55296 && codePoint <= 57343 || codePoint > 1114111) {
            if (strict) {
              parseError("character reference outside the permissible Unicode range");
            }
            return "\uFFFD";
          }
          if (has(decodeMapNumeric, codePoint)) {
            if (strict) {
              parseError("disallowed character reference");
            }
            return decodeMapNumeric[codePoint];
          }
          if (strict && contains(invalidReferenceCodePoints, codePoint)) {
            parseError("disallowed character reference");
          }
          if (codePoint > 65535) {
            codePoint -= 65536;
            output += stringFromCharCode(codePoint >>> 10 & 1023 | 55296);
            codePoint = 56320 | codePoint & 1023;
          }
          output += stringFromCharCode(codePoint);
          return output;
        };
        var hexEscape = function(codePoint) {
          return "&#x" + codePoint.toString(16).toUpperCase() + ";";
        };
        var decEscape = function(codePoint) {
          return "&#" + codePoint + ";";
        };
        var parseError = function(message) {
          throw Error("Parse error: " + message);
        };
        var encode = function(string, options) {
          options = merge(options, encode.options);
          var strict = options.strict;
          if (strict && regexInvalidRawCodePoint.test(string)) {
            parseError("forbidden code point");
          }
          var encodeEverything = options.encodeEverything;
          var useNamedReferences = options.useNamedReferences;
          var allowUnsafeSymbols = options.allowUnsafeSymbols;
          var escapeCodePoint = options.decimal ? decEscape : hexEscape;
          var escapeBmpSymbol = function(symbol) {
            return escapeCodePoint(symbol.charCodeAt(0));
          };
          if (encodeEverything) {
            string = string.replace(regexAsciiWhitelist, function(symbol) {
              if (useNamedReferences && has(encodeMap, symbol)) {
                return "&" + encodeMap[symbol] + ";";
              }
              return escapeBmpSymbol(symbol);
            });
            if (useNamedReferences) {
              string = string.replace(/&gt;\u20D2/g, "&nvgt;").replace(/&lt;\u20D2/g, "&nvlt;").replace(/&#x66;&#x6A;/g, "&fjlig;");
            }
            if (useNamedReferences) {
              string = string.replace(regexEncodeNonAscii, function(string2) {
                return "&" + encodeMap[string2] + ";";
              });
            }
          } else if (useNamedReferences) {
            if (!allowUnsafeSymbols) {
              string = string.replace(regexEscape, function(string2) {
                return "&" + encodeMap[string2] + ";";
              });
            }
            string = string.replace(/&gt;\u20D2/g, "&nvgt;").replace(/&lt;\u20D2/g, "&nvlt;");
            string = string.replace(regexEncodeNonAscii, function(string2) {
              return "&" + encodeMap[string2] + ";";
            });
          } else if (!allowUnsafeSymbols) {
            string = string.replace(regexEscape, escapeBmpSymbol);
          }
          return string.replace(regexAstralSymbols, function($0) {
            var high = $0.charCodeAt(0);
            var low = $0.charCodeAt(1);
            var codePoint = (high - 55296) * 1024 + low - 56320 + 65536;
            return escapeCodePoint(codePoint);
          }).replace(regexBmpWhitelist, escapeBmpSymbol);
        };
        encode.options = {
          "allowUnsafeSymbols": false,
          "encodeEverything": false,
          "strict": false,
          "useNamedReferences": false,
          "decimal": false
        };
        var decode = function(html, options) {
          options = merge(options, decode.options);
          var strict = options.strict;
          if (strict && regexInvalidEntity.test(html)) {
            parseError("malformed character reference");
          }
          return html.replace(regexDecode, function($0, $1, $2, $3, $4, $5, $6, $7, $8) {
            var codePoint;
            var semicolon;
            var decDigits;
            var hexDigits;
            var reference;
            var next;
            if ($1) {
              reference = $1;
              return decodeMap[reference];
            }
            if ($2) {
              reference = $2;
              next = $3;
              if (next && options.isAttributeValue) {
                if (strict && next == "=") {
                  parseError("`&` did not start a character reference");
                }
                return $0;
              } else {
                if (strict) {
                  parseError(
                    "named character reference was not terminated by a semicolon"
                  );
                }
                return decodeMapLegacy[reference] + (next || "");
              }
            }
            if ($4) {
              decDigits = $4;
              semicolon = $5;
              if (strict && !semicolon) {
                parseError("character reference was not terminated by a semicolon");
              }
              codePoint = parseInt(decDigits, 10);
              return codePointToSymbol(codePoint, strict);
            }
            if ($6) {
              hexDigits = $6;
              semicolon = $7;
              if (strict && !semicolon) {
                parseError("character reference was not terminated by a semicolon");
              }
              codePoint = parseInt(hexDigits, 16);
              return codePointToSymbol(codePoint, strict);
            }
            if (strict) {
              parseError(
                "named character reference was not terminated by a semicolon"
              );
            }
            return $0;
          });
        };
        decode.options = {
          "isAttributeValue": false,
          "strict": false
        };
        var escape2 = function(string) {
          return string.replace(regexEscape, function($0) {
            return escapeMap[$0];
          });
        };
        var he2 = {
          "version": "1.2.0",
          "encode": encode,
          "decode": decode,
          "escape": escape2,
          "unescape": decode
        };
        if (typeof define == "function" && typeof define.amd == "object" && define.amd) {
          define(function() {
            return he2;
          });
        } else if (freeExports && !freeExports.nodeType) {
          if (freeModule) {
            freeModule.exports = he2;
          } else {
            for (var key in he2) {
              has(he2, key) && (freeExports[key] = he2[key]);
            }
          }
        } else {
          root.he = he2;
        }
      })(exports);
    }
  });

  // node_modules/pako/lib/utils/common.js
  var require_common = __commonJS({
    "node_modules/pako/lib/utils/common.js"(exports) {
      "use strict";
      var TYPED_OK = typeof Uint8Array !== "undefined" && typeof Uint16Array !== "undefined" && typeof Int32Array !== "undefined";
      function _has(obj, key) {
        return Object.prototype.hasOwnProperty.call(obj, key);
      }
      exports.assign = function(obj) {
        var sources2 = Array.prototype.slice.call(arguments, 1);
        while (sources2.length) {
          var source = sources2.shift();
          if (!source) {
            continue;
          }
          if (typeof source !== "object") {
            throw new TypeError(source + "must be non-object");
          }
          for (var p in source) {
            if (_has(source, p)) {
              obj[p] = source[p];
            }
          }
        }
        return obj;
      };
      exports.shrinkBuf = function(buf, size) {
        if (buf.length === size) {
          return buf;
        }
        if (buf.subarray) {
          return buf.subarray(0, size);
        }
        buf.length = size;
        return buf;
      };
      var fnTyped = {
        arraySet: function(dest, src, src_offs, len, dest_offs) {
          if (src.subarray && dest.subarray) {
            dest.set(src.subarray(src_offs, src_offs + len), dest_offs);
            return;
          }
          for (var i = 0; i < len; i++) {
            dest[dest_offs + i] = src[src_offs + i];
          }
        },
        // Join array of chunks to single array.
        flattenChunks: function(chunks) {
          var i, l, len, pos, chunk, result;
          len = 0;
          for (i = 0, l = chunks.length; i < l; i++) {
            len += chunks[i].length;
          }
          result = new Uint8Array(len);
          pos = 0;
          for (i = 0, l = chunks.length; i < l; i++) {
            chunk = chunks[i];
            result.set(chunk, pos);
            pos += chunk.length;
          }
          return result;
        }
      };
      var fnUntyped = {
        arraySet: function(dest, src, src_offs, len, dest_offs) {
          for (var i = 0; i < len; i++) {
            dest[dest_offs + i] = src[src_offs + i];
          }
        },
        // Join array of chunks to single array.
        flattenChunks: function(chunks) {
          return [].concat.apply([], chunks);
        }
      };
      exports.setTyped = function(on) {
        if (on) {
          exports.Buf8 = Uint8Array;
          exports.Buf16 = Uint16Array;
          exports.Buf32 = Int32Array;
          exports.assign(exports, fnTyped);
        } else {
          exports.Buf8 = Array;
          exports.Buf16 = Array;
          exports.Buf32 = Array;
          exports.assign(exports, fnUntyped);
        }
      };
      exports.setTyped(TYPED_OK);
    }
  });

  // node_modules/pako/lib/zlib/trees.js
  var require_trees = __commonJS({
    "node_modules/pako/lib/zlib/trees.js"(exports) {
      "use strict";
      var utils = require_common();
      var Z_FIXED = 4;
      var Z_BINARY = 0;
      var Z_TEXT = 1;
      var Z_UNKNOWN = 2;
      function zero(buf) {
        var len = buf.length;
        while (--len >= 0) {
          buf[len] = 0;
        }
      }
      var STORED_BLOCK = 0;
      var STATIC_TREES = 1;
      var DYN_TREES = 2;
      var MIN_MATCH = 3;
      var MAX_MATCH = 258;
      var LENGTH_CODES = 29;
      var LITERALS = 256;
      var L_CODES = LITERALS + 1 + LENGTH_CODES;
      var D_CODES = 30;
      var BL_CODES = 19;
      var HEAP_SIZE = 2 * L_CODES + 1;
      var MAX_BITS = 15;
      var Buf_size = 16;
      var MAX_BL_BITS = 7;
      var END_BLOCK = 256;
      var REP_3_6 = 16;
      var REPZ_3_10 = 17;
      var REPZ_11_138 = 18;
      var extra_lbits = (
        /* extra bits for each length code */
        [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0]
      );
      var extra_dbits = (
        /* extra bits for each distance code */
        [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13]
      );
      var extra_blbits = (
        /* extra bits for each bit length code */
        [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 7]
      );
      var bl_order = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15];
      var DIST_CODE_LEN = 512;
      var static_ltree = new Array((L_CODES + 2) * 2);
      zero(static_ltree);
      var static_dtree = new Array(D_CODES * 2);
      zero(static_dtree);
      var _dist_code = new Array(DIST_CODE_LEN);
      zero(_dist_code);
      var _length_code = new Array(MAX_MATCH - MIN_MATCH + 1);
      zero(_length_code);
      var base_length = new Array(LENGTH_CODES);
      zero(base_length);
      var base_dist = new Array(D_CODES);
      zero(base_dist);
      function StaticTreeDesc(static_tree, extra_bits, extra_base, elems, max_length) {
        this.static_tree = static_tree;
        this.extra_bits = extra_bits;
        this.extra_base = extra_base;
        this.elems = elems;
        this.max_length = max_length;
        this.has_stree = static_tree && static_tree.length;
      }
      var static_l_desc;
      var static_d_desc;
      var static_bl_desc;
      function TreeDesc(dyn_tree, stat_desc) {
        this.dyn_tree = dyn_tree;
        this.max_code = 0;
        this.stat_desc = stat_desc;
      }
      function d_code(dist) {
        return dist < 256 ? _dist_code[dist] : _dist_code[256 + (dist >>> 7)];
      }
      function put_short(s, w) {
        s.pending_buf[s.pending++] = w & 255;
        s.pending_buf[s.pending++] = w >>> 8 & 255;
      }
      function send_bits(s, value, length) {
        if (s.bi_valid > Buf_size - length) {
          s.bi_buf |= value << s.bi_valid & 65535;
          put_short(s, s.bi_buf);
          s.bi_buf = value >> Buf_size - s.bi_valid;
          s.bi_valid += length - Buf_size;
        } else {
          s.bi_buf |= value << s.bi_valid & 65535;
          s.bi_valid += length;
        }
      }
      function send_code(s, c, tree) {
        send_bits(
          s,
          tree[c * 2],
          tree[c * 2 + 1]
          /*.Len*/
        );
      }
      function bi_reverse(code, len) {
        var res = 0;
        do {
          res |= code & 1;
          code >>>= 1;
          res <<= 1;
        } while (--len > 0);
        return res >>> 1;
      }
      function bi_flush(s) {
        if (s.bi_valid === 16) {
          put_short(s, s.bi_buf);
          s.bi_buf = 0;
          s.bi_valid = 0;
        } else if (s.bi_valid >= 8) {
          s.pending_buf[s.pending++] = s.bi_buf & 255;
          s.bi_buf >>= 8;
          s.bi_valid -= 8;
        }
      }
      function gen_bitlen(s, desc) {
        var tree = desc.dyn_tree;
        var max_code = desc.max_code;
        var stree = desc.stat_desc.static_tree;
        var has_stree = desc.stat_desc.has_stree;
        var extra = desc.stat_desc.extra_bits;
        var base = desc.stat_desc.extra_base;
        var max_length = desc.stat_desc.max_length;
        var h;
        var n, m;
        var bits;
        var xbits;
        var f;
        var overflow = 0;
        for (bits = 0; bits <= MAX_BITS; bits++) {
          s.bl_count[bits] = 0;
        }
        tree[s.heap[s.heap_max] * 2 + 1] = 0;
        for (h = s.heap_max + 1; h < HEAP_SIZE; h++) {
          n = s.heap[h];
          bits = tree[tree[n * 2 + 1] * 2 + 1] + 1;
          if (bits > max_length) {
            bits = max_length;
            overflow++;
          }
          tree[n * 2 + 1] = bits;
          if (n > max_code) {
            continue;
          }
          s.bl_count[bits]++;
          xbits = 0;
          if (n >= base) {
            xbits = extra[n - base];
          }
          f = tree[n * 2];
          s.opt_len += f * (bits + xbits);
          if (has_stree) {
            s.static_len += f * (stree[n * 2 + 1] + xbits);
          }
        }
        if (overflow === 0) {
          return;
        }
        do {
          bits = max_length - 1;
          while (s.bl_count[bits] === 0) {
            bits--;
          }
          s.bl_count[bits]--;
          s.bl_count[bits + 1] += 2;
          s.bl_count[max_length]--;
          overflow -= 2;
        } while (overflow > 0);
        for (bits = max_length; bits !== 0; bits--) {
          n = s.bl_count[bits];
          while (n !== 0) {
            m = s.heap[--h];
            if (m > max_code) {
              continue;
            }
            if (tree[m * 2 + 1] !== bits) {
              s.opt_len += (bits - tree[m * 2 + 1]) * tree[m * 2];
              tree[m * 2 + 1] = bits;
            }
            n--;
          }
        }
      }
      function gen_codes(tree, max_code, bl_count) {
        var next_code = new Array(MAX_BITS + 1);
        var code = 0;
        var bits;
        var n;
        for (bits = 1; bits <= MAX_BITS; bits++) {
          next_code[bits] = code = code + bl_count[bits - 1] << 1;
        }
        for (n = 0; n <= max_code; n++) {
          var len = tree[n * 2 + 1];
          if (len === 0) {
            continue;
          }
          tree[n * 2] = bi_reverse(next_code[len]++, len);
        }
      }
      function tr_static_init() {
        var n;
        var bits;
        var length;
        var code;
        var dist;
        var bl_count = new Array(MAX_BITS + 1);
        length = 0;
        for (code = 0; code < LENGTH_CODES - 1; code++) {
          base_length[code] = length;
          for (n = 0; n < 1 << extra_lbits[code]; n++) {
            _length_code[length++] = code;
          }
        }
        _length_code[length - 1] = code;
        dist = 0;
        for (code = 0; code < 16; code++) {
          base_dist[code] = dist;
          for (n = 0; n < 1 << extra_dbits[code]; n++) {
            _dist_code[dist++] = code;
          }
        }
        dist >>= 7;
        for (; code < D_CODES; code++) {
          base_dist[code] = dist << 7;
          for (n = 0; n < 1 << extra_dbits[code] - 7; n++) {
            _dist_code[256 + dist++] = code;
          }
        }
        for (bits = 0; bits <= MAX_BITS; bits++) {
          bl_count[bits] = 0;
        }
        n = 0;
        while (n <= 143) {
          static_ltree[n * 2 + 1] = 8;
          n++;
          bl_count[8]++;
        }
        while (n <= 255) {
          static_ltree[n * 2 + 1] = 9;
          n++;
          bl_count[9]++;
        }
        while (n <= 279) {
          static_ltree[n * 2 + 1] = 7;
          n++;
          bl_count[7]++;
        }
        while (n <= 287) {
          static_ltree[n * 2 + 1] = 8;
          n++;
          bl_count[8]++;
        }
        gen_codes(static_ltree, L_CODES + 1, bl_count);
        for (n = 0; n < D_CODES; n++) {
          static_dtree[n * 2 + 1] = 5;
          static_dtree[n * 2] = bi_reverse(n, 5);
        }
        static_l_desc = new StaticTreeDesc(static_ltree, extra_lbits, LITERALS + 1, L_CODES, MAX_BITS);
        static_d_desc = new StaticTreeDesc(static_dtree, extra_dbits, 0, D_CODES, MAX_BITS);
        static_bl_desc = new StaticTreeDesc(new Array(0), extra_blbits, 0, BL_CODES, MAX_BL_BITS);
      }
      function init_block(s) {
        var n;
        for (n = 0; n < L_CODES; n++) {
          s.dyn_ltree[n * 2] = 0;
        }
        for (n = 0; n < D_CODES; n++) {
          s.dyn_dtree[n * 2] = 0;
        }
        for (n = 0; n < BL_CODES; n++) {
          s.bl_tree[n * 2] = 0;
        }
        s.dyn_ltree[END_BLOCK * 2] = 1;
        s.opt_len = s.static_len = 0;
        s.last_lit = s.matches = 0;
      }
      function bi_windup(s) {
        if (s.bi_valid > 8) {
          put_short(s, s.bi_buf);
        } else if (s.bi_valid > 0) {
          s.pending_buf[s.pending++] = s.bi_buf;
        }
        s.bi_buf = 0;
        s.bi_valid = 0;
      }
      function copy_block(s, buf, len, header) {
        bi_windup(s);
        if (header) {
          put_short(s, len);
          put_short(s, ~len);
        }
        utils.arraySet(s.pending_buf, s.window, buf, len, s.pending);
        s.pending += len;
      }
      function smaller(tree, n, m, depth) {
        var _n2 = n * 2;
        var _m2 = m * 2;
        return tree[_n2] < tree[_m2] || tree[_n2] === tree[_m2] && depth[n] <= depth[m];
      }
      function pqdownheap(s, tree, k) {
        var v = s.heap[k];
        var j = k << 1;
        while (j <= s.heap_len) {
          if (j < s.heap_len && smaller(tree, s.heap[j + 1], s.heap[j], s.depth)) {
            j++;
          }
          if (smaller(tree, v, s.heap[j], s.depth)) {
            break;
          }
          s.heap[k] = s.heap[j];
          k = j;
          j <<= 1;
        }
        s.heap[k] = v;
      }
      function compress_block(s, ltree, dtree) {
        var dist;
        var lc;
        var lx = 0;
        var code;
        var extra;
        if (s.last_lit !== 0) {
          do {
            dist = s.pending_buf[s.d_buf + lx * 2] << 8 | s.pending_buf[s.d_buf + lx * 2 + 1];
            lc = s.pending_buf[s.l_buf + lx];
            lx++;
            if (dist === 0) {
              send_code(s, lc, ltree);
            } else {
              code = _length_code[lc];
              send_code(s, code + LITERALS + 1, ltree);
              extra = extra_lbits[code];
              if (extra !== 0) {
                lc -= base_length[code];
                send_bits(s, lc, extra);
              }
              dist--;
              code = d_code(dist);
              send_code(s, code, dtree);
              extra = extra_dbits[code];
              if (extra !== 0) {
                dist -= base_dist[code];
                send_bits(s, dist, extra);
              }
            }
          } while (lx < s.last_lit);
        }
        send_code(s, END_BLOCK, ltree);
      }
      function build_tree(s, desc) {
        var tree = desc.dyn_tree;
        var stree = desc.stat_desc.static_tree;
        var has_stree = desc.stat_desc.has_stree;
        var elems = desc.stat_desc.elems;
        var n, m;
        var max_code = -1;
        var node;
        s.heap_len = 0;
        s.heap_max = HEAP_SIZE;
        for (n = 0; n < elems; n++) {
          if (tree[n * 2] !== 0) {
            s.heap[++s.heap_len] = max_code = n;
            s.depth[n] = 0;
          } else {
            tree[n * 2 + 1] = 0;
          }
        }
        while (s.heap_len < 2) {
          node = s.heap[++s.heap_len] = max_code < 2 ? ++max_code : 0;
          tree[node * 2] = 1;
          s.depth[node] = 0;
          s.opt_len--;
          if (has_stree) {
            s.static_len -= stree[node * 2 + 1];
          }
        }
        desc.max_code = max_code;
        for (n = s.heap_len >> 1; n >= 1; n--) {
          pqdownheap(s, tree, n);
        }
        node = elems;
        do {
          n = s.heap[
            1
            /*SMALLEST*/
          ];
          s.heap[
            1
            /*SMALLEST*/
          ] = s.heap[s.heap_len--];
          pqdownheap(
            s,
            tree,
            1
            /*SMALLEST*/
          );
          m = s.heap[
            1
            /*SMALLEST*/
          ];
          s.heap[--s.heap_max] = n;
          s.heap[--s.heap_max] = m;
          tree[node * 2] = tree[n * 2] + tree[m * 2];
          s.depth[node] = (s.depth[n] >= s.depth[m] ? s.depth[n] : s.depth[m]) + 1;
          tree[n * 2 + 1] = tree[m * 2 + 1] = node;
          s.heap[
            1
            /*SMALLEST*/
          ] = node++;
          pqdownheap(
            s,
            tree,
            1
            /*SMALLEST*/
          );
        } while (s.heap_len >= 2);
        s.heap[--s.heap_max] = s.heap[
          1
          /*SMALLEST*/
        ];
        gen_bitlen(s, desc);
        gen_codes(tree, max_code, s.bl_count);
      }
      function scan_tree(s, tree, max_code) {
        var n;
        var prevlen = -1;
        var curlen;
        var nextlen = tree[0 * 2 + 1];
        var count = 0;
        var max_count = 7;
        var min_count = 4;
        if (nextlen === 0) {
          max_count = 138;
          min_count = 3;
        }
        tree[(max_code + 1) * 2 + 1] = 65535;
        for (n = 0; n <= max_code; n++) {
          curlen = nextlen;
          nextlen = tree[(n + 1) * 2 + 1];
          if (++count < max_count && curlen === nextlen) {
            continue;
          } else if (count < min_count) {
            s.bl_tree[curlen * 2] += count;
          } else if (curlen !== 0) {
            if (curlen !== prevlen) {
              s.bl_tree[curlen * 2]++;
            }
            s.bl_tree[REP_3_6 * 2]++;
          } else if (count <= 10) {
            s.bl_tree[REPZ_3_10 * 2]++;
          } else {
            s.bl_tree[REPZ_11_138 * 2]++;
          }
          count = 0;
          prevlen = curlen;
          if (nextlen === 0) {
            max_count = 138;
            min_count = 3;
          } else if (curlen === nextlen) {
            max_count = 6;
            min_count = 3;
          } else {
            max_count = 7;
            min_count = 4;
          }
        }
      }
      function send_tree(s, tree, max_code) {
        var n;
        var prevlen = -1;
        var curlen;
        var nextlen = tree[0 * 2 + 1];
        var count = 0;
        var max_count = 7;
        var min_count = 4;
        if (nextlen === 0) {
          max_count = 138;
          min_count = 3;
        }
        for (n = 0; n <= max_code; n++) {
          curlen = nextlen;
          nextlen = tree[(n + 1) * 2 + 1];
          if (++count < max_count && curlen === nextlen) {
            continue;
          } else if (count < min_count) {
            do {
              send_code(s, curlen, s.bl_tree);
            } while (--count !== 0);
          } else if (curlen !== 0) {
            if (curlen !== prevlen) {
              send_code(s, curlen, s.bl_tree);
              count--;
            }
            send_code(s, REP_3_6, s.bl_tree);
            send_bits(s, count - 3, 2);
          } else if (count <= 10) {
            send_code(s, REPZ_3_10, s.bl_tree);
            send_bits(s, count - 3, 3);
          } else {
            send_code(s, REPZ_11_138, s.bl_tree);
            send_bits(s, count - 11, 7);
          }
          count = 0;
          prevlen = curlen;
          if (nextlen === 0) {
            max_count = 138;
            min_count = 3;
          } else if (curlen === nextlen) {
            max_count = 6;
            min_count = 3;
          } else {
            max_count = 7;
            min_count = 4;
          }
        }
      }
      function build_bl_tree(s) {
        var max_blindex;
        scan_tree(s, s.dyn_ltree, s.l_desc.max_code);
        scan_tree(s, s.dyn_dtree, s.d_desc.max_code);
        build_tree(s, s.bl_desc);
        for (max_blindex = BL_CODES - 1; max_blindex >= 3; max_blindex--) {
          if (s.bl_tree[bl_order[max_blindex] * 2 + 1] !== 0) {
            break;
          }
        }
        s.opt_len += 3 * (max_blindex + 1) + 5 + 5 + 4;
        return max_blindex;
      }
      function send_all_trees(s, lcodes, dcodes, blcodes) {
        var rank;
        send_bits(s, lcodes - 257, 5);
        send_bits(s, dcodes - 1, 5);
        send_bits(s, blcodes - 4, 4);
        for (rank = 0; rank < blcodes; rank++) {
          send_bits(s, s.bl_tree[bl_order[rank] * 2 + 1], 3);
        }
        send_tree(s, s.dyn_ltree, lcodes - 1);
        send_tree(s, s.dyn_dtree, dcodes - 1);
      }
      function detect_data_type(s) {
        var black_mask = 4093624447;
        var n;
        for (n = 0; n <= 31; n++, black_mask >>>= 1) {
          if (black_mask & 1 && s.dyn_ltree[n * 2] !== 0) {
            return Z_BINARY;
          }
        }
        if (s.dyn_ltree[9 * 2] !== 0 || s.dyn_ltree[10 * 2] !== 0 || s.dyn_ltree[13 * 2] !== 0) {
          return Z_TEXT;
        }
        for (n = 32; n < LITERALS; n++) {
          if (s.dyn_ltree[n * 2] !== 0) {
            return Z_TEXT;
          }
        }
        return Z_BINARY;
      }
      var static_init_done = false;
      function _tr_init(s) {
        if (!static_init_done) {
          tr_static_init();
          static_init_done = true;
        }
        s.l_desc = new TreeDesc(s.dyn_ltree, static_l_desc);
        s.d_desc = new TreeDesc(s.dyn_dtree, static_d_desc);
        s.bl_desc = new TreeDesc(s.bl_tree, static_bl_desc);
        s.bi_buf = 0;
        s.bi_valid = 0;
        init_block(s);
      }
      function _tr_stored_block(s, buf, stored_len, last) {
        send_bits(s, (STORED_BLOCK << 1) + (last ? 1 : 0), 3);
        copy_block(s, buf, stored_len, true);
      }
      function _tr_align(s) {
        send_bits(s, STATIC_TREES << 1, 3);
        send_code(s, END_BLOCK, static_ltree);
        bi_flush(s);
      }
      function _tr_flush_block(s, buf, stored_len, last) {
        var opt_lenb, static_lenb;
        var max_blindex = 0;
        if (s.level > 0) {
          if (s.strm.data_type === Z_UNKNOWN) {
            s.strm.data_type = detect_data_type(s);
          }
          build_tree(s, s.l_desc);
          build_tree(s, s.d_desc);
          max_blindex = build_bl_tree(s);
          opt_lenb = s.opt_len + 3 + 7 >>> 3;
          static_lenb = s.static_len + 3 + 7 >>> 3;
          if (static_lenb <= opt_lenb) {
            opt_lenb = static_lenb;
          }
        } else {
          opt_lenb = static_lenb = stored_len + 5;
        }
        if (stored_len + 4 <= opt_lenb && buf !== -1) {
          _tr_stored_block(s, buf, stored_len, last);
        } else if (s.strategy === Z_FIXED || static_lenb === opt_lenb) {
          send_bits(s, (STATIC_TREES << 1) + (last ? 1 : 0), 3);
          compress_block(s, static_ltree, static_dtree);
        } else {
          send_bits(s, (DYN_TREES << 1) + (last ? 1 : 0), 3);
          send_all_trees(s, s.l_desc.max_code + 1, s.d_desc.max_code + 1, max_blindex + 1);
          compress_block(s, s.dyn_ltree, s.dyn_dtree);
        }
        init_block(s);
        if (last) {
          bi_windup(s);
        }
      }
      function _tr_tally(s, dist, lc) {
        s.pending_buf[s.d_buf + s.last_lit * 2] = dist >>> 8 & 255;
        s.pending_buf[s.d_buf + s.last_lit * 2 + 1] = dist & 255;
        s.pending_buf[s.l_buf + s.last_lit] = lc & 255;
        s.last_lit++;
        if (dist === 0) {
          s.dyn_ltree[lc * 2]++;
        } else {
          s.matches++;
          dist--;
          s.dyn_ltree[(_length_code[lc] + LITERALS + 1) * 2]++;
          s.dyn_dtree[d_code(dist) * 2]++;
        }
        return s.last_lit === s.lit_bufsize - 1;
      }
      exports._tr_init = _tr_init;
      exports._tr_stored_block = _tr_stored_block;
      exports._tr_flush_block = _tr_flush_block;
      exports._tr_tally = _tr_tally;
      exports._tr_align = _tr_align;
    }
  });

  // node_modules/pako/lib/zlib/adler32.js
  var require_adler32 = __commonJS({
    "node_modules/pako/lib/zlib/adler32.js"(exports, module) {
      "use strict";
      function adler32(adler, buf, len, pos) {
        var s1 = adler & 65535 | 0, s2 = adler >>> 16 & 65535 | 0, n = 0;
        while (len !== 0) {
          n = len > 2e3 ? 2e3 : len;
          len -= n;
          do {
            s1 = s1 + buf[pos++] | 0;
            s2 = s2 + s1 | 0;
          } while (--n);
          s1 %= 65521;
          s2 %= 65521;
        }
        return s1 | s2 << 16 | 0;
      }
      module.exports = adler32;
    }
  });

  // node_modules/pako/lib/zlib/crc32.js
  var require_crc32 = __commonJS({
    "node_modules/pako/lib/zlib/crc32.js"(exports, module) {
      "use strict";
      function makeTable() {
        var c, table = [];
        for (var n = 0; n < 256; n++) {
          c = n;
          for (var k = 0; k < 8; k++) {
            c = c & 1 ? 3988292384 ^ c >>> 1 : c >>> 1;
          }
          table[n] = c;
        }
        return table;
      }
      var crcTable = makeTable();
      function crc32(crc, buf, len, pos) {
        var t = crcTable, end = pos + len;
        crc ^= -1;
        for (var i = pos; i < end; i++) {
          crc = crc >>> 8 ^ t[(crc ^ buf[i]) & 255];
        }
        return crc ^ -1;
      }
      module.exports = crc32;
    }
  });

  // node_modules/pako/lib/zlib/messages.js
  var require_messages = __commonJS({
    "node_modules/pako/lib/zlib/messages.js"(exports, module) {
      "use strict";
      module.exports = {
        2: "need dictionary",
        /* Z_NEED_DICT       2  */
        1: "stream end",
        /* Z_STREAM_END      1  */
        0: "",
        /* Z_OK              0  */
        "-1": "file error",
        /* Z_ERRNO         (-1) */
        "-2": "stream error",
        /* Z_STREAM_ERROR  (-2) */
        "-3": "data error",
        /* Z_DATA_ERROR    (-3) */
        "-4": "insufficient memory",
        /* Z_MEM_ERROR     (-4) */
        "-5": "buffer error",
        /* Z_BUF_ERROR     (-5) */
        "-6": "incompatible version"
        /* Z_VERSION_ERROR (-6) */
      };
    }
  });

  // node_modules/pako/lib/zlib/deflate.js
  var require_deflate = __commonJS({
    "node_modules/pako/lib/zlib/deflate.js"(exports) {
      "use strict";
      var utils = require_common();
      var trees = require_trees();
      var adler32 = require_adler32();
      var crc32 = require_crc32();
      var msg = require_messages();
      var Z_NO_FLUSH = 0;
      var Z_PARTIAL_FLUSH = 1;
      var Z_FULL_FLUSH = 3;
      var Z_FINISH = 4;
      var Z_BLOCK = 5;
      var Z_OK = 0;
      var Z_STREAM_END = 1;
      var Z_STREAM_ERROR = -2;
      var Z_DATA_ERROR = -3;
      var Z_BUF_ERROR = -5;
      var Z_DEFAULT_COMPRESSION = -1;
      var Z_FILTERED = 1;
      var Z_HUFFMAN_ONLY = 2;
      var Z_RLE = 3;
      var Z_FIXED = 4;
      var Z_DEFAULT_STRATEGY = 0;
      var Z_UNKNOWN = 2;
      var Z_DEFLATED = 8;
      var MAX_MEM_LEVEL = 9;
      var MAX_WBITS = 15;
      var DEF_MEM_LEVEL = 8;
      var LENGTH_CODES = 29;
      var LITERALS = 256;
      var L_CODES = LITERALS + 1 + LENGTH_CODES;
      var D_CODES = 30;
      var BL_CODES = 19;
      var HEAP_SIZE = 2 * L_CODES + 1;
      var MAX_BITS = 15;
      var MIN_MATCH = 3;
      var MAX_MATCH = 258;
      var MIN_LOOKAHEAD = MAX_MATCH + MIN_MATCH + 1;
      var PRESET_DICT = 32;
      var INIT_STATE = 42;
      var EXTRA_STATE = 69;
      var NAME_STATE = 73;
      var COMMENT_STATE = 91;
      var HCRC_STATE = 103;
      var BUSY_STATE = 113;
      var FINISH_STATE = 666;
      var BS_NEED_MORE = 1;
      var BS_BLOCK_DONE = 2;
      var BS_FINISH_STARTED = 3;
      var BS_FINISH_DONE = 4;
      var OS_CODE = 3;
      function err(strm, errorCode) {
        strm.msg = msg[errorCode];
        return errorCode;
      }
      function rank(f) {
        return (f << 1) - (f > 4 ? 9 : 0);
      }
      function zero(buf) {
        var len = buf.length;
        while (--len >= 0) {
          buf[len] = 0;
        }
      }
      function flush_pending(strm) {
        var s = strm.state;
        var len = s.pending;
        if (len > strm.avail_out) {
          len = strm.avail_out;
        }
        if (len === 0) {
          return;
        }
        utils.arraySet(strm.output, s.pending_buf, s.pending_out, len, strm.next_out);
        strm.next_out += len;
        s.pending_out += len;
        strm.total_out += len;
        strm.avail_out -= len;
        s.pending -= len;
        if (s.pending === 0) {
          s.pending_out = 0;
        }
      }
      function flush_block_only(s, last) {
        trees._tr_flush_block(s, s.block_start >= 0 ? s.block_start : -1, s.strstart - s.block_start, last);
        s.block_start = s.strstart;
        flush_pending(s.strm);
      }
      function put_byte(s, b) {
        s.pending_buf[s.pending++] = b;
      }
      function putShortMSB(s, b) {
        s.pending_buf[s.pending++] = b >>> 8 & 255;
        s.pending_buf[s.pending++] = b & 255;
      }
      function read_buf(strm, buf, start, size) {
        var len = strm.avail_in;
        if (len > size) {
          len = size;
        }
        if (len === 0) {
          return 0;
        }
        strm.avail_in -= len;
        utils.arraySet(buf, strm.input, strm.next_in, len, start);
        if (strm.state.wrap === 1) {
          strm.adler = adler32(strm.adler, buf, len, start);
        } else if (strm.state.wrap === 2) {
          strm.adler = crc32(strm.adler, buf, len, start);
        }
        strm.next_in += len;
        strm.total_in += len;
        return len;
      }
      function longest_match(s, cur_match) {
        var chain_length = s.max_chain_length;
        var scan = s.strstart;
        var match;
        var len;
        var best_len = s.prev_length;
        var nice_match = s.nice_match;
        var limit = s.strstart > s.w_size - MIN_LOOKAHEAD ? s.strstart - (s.w_size - MIN_LOOKAHEAD) : 0;
        var _win = s.window;
        var wmask = s.w_mask;
        var prev = s.prev;
        var strend = s.strstart + MAX_MATCH;
        var scan_end1 = _win[scan + best_len - 1];
        var scan_end = _win[scan + best_len];
        if (s.prev_length >= s.good_match) {
          chain_length >>= 2;
        }
        if (nice_match > s.lookahead) {
          nice_match = s.lookahead;
        }
        do {
          match = cur_match;
          if (_win[match + best_len] !== scan_end || _win[match + best_len - 1] !== scan_end1 || _win[match] !== _win[scan] || _win[++match] !== _win[scan + 1]) {
            continue;
          }
          scan += 2;
          match++;
          do {
          } while (_win[++scan] === _win[++match] && _win[++scan] === _win[++match] && _win[++scan] === _win[++match] && _win[++scan] === _win[++match] && _win[++scan] === _win[++match] && _win[++scan] === _win[++match] && _win[++scan] === _win[++match] && _win[++scan] === _win[++match] && scan < strend);
          len = MAX_MATCH - (strend - scan);
          scan = strend - MAX_MATCH;
          if (len > best_len) {
            s.match_start = cur_match;
            best_len = len;
            if (len >= nice_match) {
              break;
            }
            scan_end1 = _win[scan + best_len - 1];
            scan_end = _win[scan + best_len];
          }
        } while ((cur_match = prev[cur_match & wmask]) > limit && --chain_length !== 0);
        if (best_len <= s.lookahead) {
          return best_len;
        }
        return s.lookahead;
      }
      function fill_window(s) {
        var _w_size = s.w_size;
        var p, n, m, more, str;
        do {
          more = s.window_size - s.lookahead - s.strstart;
          if (s.strstart >= _w_size + (_w_size - MIN_LOOKAHEAD)) {
            utils.arraySet(s.window, s.window, _w_size, _w_size, 0);
            s.match_start -= _w_size;
            s.strstart -= _w_size;
            s.block_start -= _w_size;
            n = s.hash_size;
            p = n;
            do {
              m = s.head[--p];
              s.head[p] = m >= _w_size ? m - _w_size : 0;
            } while (--n);
            n = _w_size;
            p = n;
            do {
              m = s.prev[--p];
              s.prev[p] = m >= _w_size ? m - _w_size : 0;
            } while (--n);
            more += _w_size;
          }
          if (s.strm.avail_in === 0) {
            break;
          }
          n = read_buf(s.strm, s.window, s.strstart + s.lookahead, more);
          s.lookahead += n;
          if (s.lookahead + s.insert >= MIN_MATCH) {
            str = s.strstart - s.insert;
            s.ins_h = s.window[str];
            s.ins_h = (s.ins_h << s.hash_shift ^ s.window[str + 1]) & s.hash_mask;
            while (s.insert) {
              s.ins_h = (s.ins_h << s.hash_shift ^ s.window[str + MIN_MATCH - 1]) & s.hash_mask;
              s.prev[str & s.w_mask] = s.head[s.ins_h];
              s.head[s.ins_h] = str;
              str++;
              s.insert--;
              if (s.lookahead + s.insert < MIN_MATCH) {
                break;
              }
            }
          }
        } while (s.lookahead < MIN_LOOKAHEAD && s.strm.avail_in !== 0);
      }
      function deflate_stored(s, flush) {
        var max_block_size = 65535;
        if (max_block_size > s.pending_buf_size - 5) {
          max_block_size = s.pending_buf_size - 5;
        }
        for (; ; ) {
          if (s.lookahead <= 1) {
            fill_window(s);
            if (s.lookahead === 0 && flush === Z_NO_FLUSH) {
              return BS_NEED_MORE;
            }
            if (s.lookahead === 0) {
              break;
            }
          }
          s.strstart += s.lookahead;
          s.lookahead = 0;
          var max_start = s.block_start + max_block_size;
          if (s.strstart === 0 || s.strstart >= max_start) {
            s.lookahead = s.strstart - max_start;
            s.strstart = max_start;
            flush_block_only(s, false);
            if (s.strm.avail_out === 0) {
              return BS_NEED_MORE;
            }
          }
          if (s.strstart - s.block_start >= s.w_size - MIN_LOOKAHEAD) {
            flush_block_only(s, false);
            if (s.strm.avail_out === 0) {
              return BS_NEED_MORE;
            }
          }
        }
        s.insert = 0;
        if (flush === Z_FINISH) {
          flush_block_only(s, true);
          if (s.strm.avail_out === 0) {
            return BS_FINISH_STARTED;
          }
          return BS_FINISH_DONE;
        }
        if (s.strstart > s.block_start) {
          flush_block_only(s, false);
          if (s.strm.avail_out === 0) {
            return BS_NEED_MORE;
          }
        }
        return BS_NEED_MORE;
      }
      function deflate_fast(s, flush) {
        var hash_head;
        var bflush;
        for (; ; ) {
          if (s.lookahead < MIN_LOOKAHEAD) {
            fill_window(s);
            if (s.lookahead < MIN_LOOKAHEAD && flush === Z_NO_FLUSH) {
              return BS_NEED_MORE;
            }
            if (s.lookahead === 0) {
              break;
            }
          }
          hash_head = 0;
          if (s.lookahead >= MIN_MATCH) {
            s.ins_h = (s.ins_h << s.hash_shift ^ s.window[s.strstart + MIN_MATCH - 1]) & s.hash_mask;
            hash_head = s.prev[s.strstart & s.w_mask] = s.head[s.ins_h];
            s.head[s.ins_h] = s.strstart;
          }
          if (hash_head !== 0 && s.strstart - hash_head <= s.w_size - MIN_LOOKAHEAD) {
            s.match_length = longest_match(s, hash_head);
          }
          if (s.match_length >= MIN_MATCH) {
            bflush = trees._tr_tally(s, s.strstart - s.match_start, s.match_length - MIN_MATCH);
            s.lookahead -= s.match_length;
            if (s.match_length <= s.max_lazy_match && s.lookahead >= MIN_MATCH) {
              s.match_length--;
              do {
                s.strstart++;
                s.ins_h = (s.ins_h << s.hash_shift ^ s.window[s.strstart + MIN_MATCH - 1]) & s.hash_mask;
                hash_head = s.prev[s.strstart & s.w_mask] = s.head[s.ins_h];
                s.head[s.ins_h] = s.strstart;
              } while (--s.match_length !== 0);
              s.strstart++;
            } else {
              s.strstart += s.match_length;
              s.match_length = 0;
              s.ins_h = s.window[s.strstart];
              s.ins_h = (s.ins_h << s.hash_shift ^ s.window[s.strstart + 1]) & s.hash_mask;
            }
          } else {
            bflush = trees._tr_tally(s, 0, s.window[s.strstart]);
            s.lookahead--;
            s.strstart++;
          }
          if (bflush) {
            flush_block_only(s, false);
            if (s.strm.avail_out === 0) {
              return BS_NEED_MORE;
            }
          }
        }
        s.insert = s.strstart < MIN_MATCH - 1 ? s.strstart : MIN_MATCH - 1;
        if (flush === Z_FINISH) {
          flush_block_only(s, true);
          if (s.strm.avail_out === 0) {
            return BS_FINISH_STARTED;
          }
          return BS_FINISH_DONE;
        }
        if (s.last_lit) {
          flush_block_only(s, false);
          if (s.strm.avail_out === 0) {
            return BS_NEED_MORE;
          }
        }
        return BS_BLOCK_DONE;
      }
      function deflate_slow(s, flush) {
        var hash_head;
        var bflush;
        var max_insert;
        for (; ; ) {
          if (s.lookahead < MIN_LOOKAHEAD) {
            fill_window(s);
            if (s.lookahead < MIN_LOOKAHEAD && flush === Z_NO_FLUSH) {
              return BS_NEED_MORE;
            }
            if (s.lookahead === 0) {
              break;
            }
          }
          hash_head = 0;
          if (s.lookahead >= MIN_MATCH) {
            s.ins_h = (s.ins_h << s.hash_shift ^ s.window[s.strstart + MIN_MATCH - 1]) & s.hash_mask;
            hash_head = s.prev[s.strstart & s.w_mask] = s.head[s.ins_h];
            s.head[s.ins_h] = s.strstart;
          }
          s.prev_length = s.match_length;
          s.prev_match = s.match_start;
          s.match_length = MIN_MATCH - 1;
          if (hash_head !== 0 && s.prev_length < s.max_lazy_match && s.strstart - hash_head <= s.w_size - MIN_LOOKAHEAD) {
            s.match_length = longest_match(s, hash_head);
            if (s.match_length <= 5 && (s.strategy === Z_FILTERED || s.match_length === MIN_MATCH && s.strstart - s.match_start > 4096)) {
              s.match_length = MIN_MATCH - 1;
            }
          }
          if (s.prev_length >= MIN_MATCH && s.match_length <= s.prev_length) {
            max_insert = s.strstart + s.lookahead - MIN_MATCH;
            bflush = trees._tr_tally(s, s.strstart - 1 - s.prev_match, s.prev_length - MIN_MATCH);
            s.lookahead -= s.prev_length - 1;
            s.prev_length -= 2;
            do {
              if (++s.strstart <= max_insert) {
                s.ins_h = (s.ins_h << s.hash_shift ^ s.window[s.strstart + MIN_MATCH - 1]) & s.hash_mask;
                hash_head = s.prev[s.strstart & s.w_mask] = s.head[s.ins_h];
                s.head[s.ins_h] = s.strstart;
              }
            } while (--s.prev_length !== 0);
            s.match_available = 0;
            s.match_length = MIN_MATCH - 1;
            s.strstart++;
            if (bflush) {
              flush_block_only(s, false);
              if (s.strm.avail_out === 0) {
                return BS_NEED_MORE;
              }
            }
          } else if (s.match_available) {
            bflush = trees._tr_tally(s, 0, s.window[s.strstart - 1]);
            if (bflush) {
              flush_block_only(s, false);
            }
            s.strstart++;
            s.lookahead--;
            if (s.strm.avail_out === 0) {
              return BS_NEED_MORE;
            }
          } else {
            s.match_available = 1;
            s.strstart++;
            s.lookahead--;
          }
        }
        if (s.match_available) {
          bflush = trees._tr_tally(s, 0, s.window[s.strstart - 1]);
          s.match_available = 0;
        }
        s.insert = s.strstart < MIN_MATCH - 1 ? s.strstart : MIN_MATCH - 1;
        if (flush === Z_FINISH) {
          flush_block_only(s, true);
          if (s.strm.avail_out === 0) {
            return BS_FINISH_STARTED;
          }
          return BS_FINISH_DONE;
        }
        if (s.last_lit) {
          flush_block_only(s, false);
          if (s.strm.avail_out === 0) {
            return BS_NEED_MORE;
          }
        }
        return BS_BLOCK_DONE;
      }
      function deflate_rle(s, flush) {
        var bflush;
        var prev;
        var scan, strend;
        var _win = s.window;
        for (; ; ) {
          if (s.lookahead <= MAX_MATCH) {
            fill_window(s);
            if (s.lookahead <= MAX_MATCH && flush === Z_NO_FLUSH) {
              return BS_NEED_MORE;
            }
            if (s.lookahead === 0) {
              break;
            }
          }
          s.match_length = 0;
          if (s.lookahead >= MIN_MATCH && s.strstart > 0) {
            scan = s.strstart - 1;
            prev = _win[scan];
            if (prev === _win[++scan] && prev === _win[++scan] && prev === _win[++scan]) {
              strend = s.strstart + MAX_MATCH;
              do {
              } while (prev === _win[++scan] && prev === _win[++scan] && prev === _win[++scan] && prev === _win[++scan] && prev === _win[++scan] && prev === _win[++scan] && prev === _win[++scan] && prev === _win[++scan] && scan < strend);
              s.match_length = MAX_MATCH - (strend - scan);
              if (s.match_length > s.lookahead) {
                s.match_length = s.lookahead;
              }
            }
          }
          if (s.match_length >= MIN_MATCH) {
            bflush = trees._tr_tally(s, 1, s.match_length - MIN_MATCH);
            s.lookahead -= s.match_length;
            s.strstart += s.match_length;
            s.match_length = 0;
          } else {
            bflush = trees._tr_tally(s, 0, s.window[s.strstart]);
            s.lookahead--;
            s.strstart++;
          }
          if (bflush) {
            flush_block_only(s, false);
            if (s.strm.avail_out === 0) {
              return BS_NEED_MORE;
            }
          }
        }
        s.insert = 0;
        if (flush === Z_FINISH) {
          flush_block_only(s, true);
          if (s.strm.avail_out === 0) {
            return BS_FINISH_STARTED;
          }
          return BS_FINISH_DONE;
        }
        if (s.last_lit) {
          flush_block_only(s, false);
          if (s.strm.avail_out === 0) {
            return BS_NEED_MORE;
          }
        }
        return BS_BLOCK_DONE;
      }
      function deflate_huff(s, flush) {
        var bflush;
        for (; ; ) {
          if (s.lookahead === 0) {
            fill_window(s);
            if (s.lookahead === 0) {
              if (flush === Z_NO_FLUSH) {
                return BS_NEED_MORE;
              }
              break;
            }
          }
          s.match_length = 0;
          bflush = trees._tr_tally(s, 0, s.window[s.strstart]);
          s.lookahead--;
          s.strstart++;
          if (bflush) {
            flush_block_only(s, false);
            if (s.strm.avail_out === 0) {
              return BS_NEED_MORE;
            }
          }
        }
        s.insert = 0;
        if (flush === Z_FINISH) {
          flush_block_only(s, true);
          if (s.strm.avail_out === 0) {
            return BS_FINISH_STARTED;
          }
          return BS_FINISH_DONE;
        }
        if (s.last_lit) {
          flush_block_only(s, false);
          if (s.strm.avail_out === 0) {
            return BS_NEED_MORE;
          }
        }
        return BS_BLOCK_DONE;
      }
      function Config(good_length, max_lazy, nice_length, max_chain, func) {
        this.good_length = good_length;
        this.max_lazy = max_lazy;
        this.nice_length = nice_length;
        this.max_chain = max_chain;
        this.func = func;
      }
      var configuration_table;
      configuration_table = [
        /*      good lazy nice chain */
        new Config(0, 0, 0, 0, deflate_stored),
        /* 0 store only */
        new Config(4, 4, 8, 4, deflate_fast),
        /* 1 max speed, no lazy matches */
        new Config(4, 5, 16, 8, deflate_fast),
        /* 2 */
        new Config(4, 6, 32, 32, deflate_fast),
        /* 3 */
        new Config(4, 4, 16, 16, deflate_slow),
        /* 4 lazy matches */
        new Config(8, 16, 32, 32, deflate_slow),
        /* 5 */
        new Config(8, 16, 128, 128, deflate_slow),
        /* 6 */
        new Config(8, 32, 128, 256, deflate_slow),
        /* 7 */
        new Config(32, 128, 258, 1024, deflate_slow),
        /* 8 */
        new Config(32, 258, 258, 4096, deflate_slow)
        /* 9 max compression */
      ];
      function lm_init(s) {
        s.window_size = 2 * s.w_size;
        zero(s.head);
        s.max_lazy_match = configuration_table[s.level].max_lazy;
        s.good_match = configuration_table[s.level].good_length;
        s.nice_match = configuration_table[s.level].nice_length;
        s.max_chain_length = configuration_table[s.level].max_chain;
        s.strstart = 0;
        s.block_start = 0;
        s.lookahead = 0;
        s.insert = 0;
        s.match_length = s.prev_length = MIN_MATCH - 1;
        s.match_available = 0;
        s.ins_h = 0;
      }
      function DeflateState() {
        this.strm = null;
        this.status = 0;
        this.pending_buf = null;
        this.pending_buf_size = 0;
        this.pending_out = 0;
        this.pending = 0;
        this.wrap = 0;
        this.gzhead = null;
        this.gzindex = 0;
        this.method = Z_DEFLATED;
        this.last_flush = -1;
        this.w_size = 0;
        this.w_bits = 0;
        this.w_mask = 0;
        this.window = null;
        this.window_size = 0;
        this.prev = null;
        this.head = null;
        this.ins_h = 0;
        this.hash_size = 0;
        this.hash_bits = 0;
        this.hash_mask = 0;
        this.hash_shift = 0;
        this.block_start = 0;
        this.match_length = 0;
        this.prev_match = 0;
        this.match_available = 0;
        this.strstart = 0;
        this.match_start = 0;
        this.lookahead = 0;
        this.prev_length = 0;
        this.max_chain_length = 0;
        this.max_lazy_match = 0;
        this.level = 0;
        this.strategy = 0;
        this.good_match = 0;
        this.nice_match = 0;
        this.dyn_ltree = new utils.Buf16(HEAP_SIZE * 2);
        this.dyn_dtree = new utils.Buf16((2 * D_CODES + 1) * 2);
        this.bl_tree = new utils.Buf16((2 * BL_CODES + 1) * 2);
        zero(this.dyn_ltree);
        zero(this.dyn_dtree);
        zero(this.bl_tree);
        this.l_desc = null;
        this.d_desc = null;
        this.bl_desc = null;
        this.bl_count = new utils.Buf16(MAX_BITS + 1);
        this.heap = new utils.Buf16(2 * L_CODES + 1);
        zero(this.heap);
        this.heap_len = 0;
        this.heap_max = 0;
        this.depth = new utils.Buf16(2 * L_CODES + 1);
        zero(this.depth);
        this.l_buf = 0;
        this.lit_bufsize = 0;
        this.last_lit = 0;
        this.d_buf = 0;
        this.opt_len = 0;
        this.static_len = 0;
        this.matches = 0;
        this.insert = 0;
        this.bi_buf = 0;
        this.bi_valid = 0;
      }
      function deflateResetKeep(strm) {
        var s;
        if (!strm || !strm.state) {
          return err(strm, Z_STREAM_ERROR);
        }
        strm.total_in = strm.total_out = 0;
        strm.data_type = Z_UNKNOWN;
        s = strm.state;
        s.pending = 0;
        s.pending_out = 0;
        if (s.wrap < 0) {
          s.wrap = -s.wrap;
        }
        s.status = s.wrap ? INIT_STATE : BUSY_STATE;
        strm.adler = s.wrap === 2 ? 0 : 1;
        s.last_flush = Z_NO_FLUSH;
        trees._tr_init(s);
        return Z_OK;
      }
      function deflateReset(strm) {
        var ret = deflateResetKeep(strm);
        if (ret === Z_OK) {
          lm_init(strm.state);
        }
        return ret;
      }
      function deflateSetHeader(strm, head) {
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        if (strm.state.wrap !== 2) {
          return Z_STREAM_ERROR;
        }
        strm.state.gzhead = head;
        return Z_OK;
      }
      function deflateInit2(strm, level, method, windowBits, memLevel, strategy) {
        if (!strm) {
          return Z_STREAM_ERROR;
        }
        var wrap = 1;
        if (level === Z_DEFAULT_COMPRESSION) {
          level = 6;
        }
        if (windowBits < 0) {
          wrap = 0;
          windowBits = -windowBits;
        } else if (windowBits > 15) {
          wrap = 2;
          windowBits -= 16;
        }
        if (memLevel < 1 || memLevel > MAX_MEM_LEVEL || method !== Z_DEFLATED || windowBits < 8 || windowBits > 15 || level < 0 || level > 9 || strategy < 0 || strategy > Z_FIXED) {
          return err(strm, Z_STREAM_ERROR);
        }
        if (windowBits === 8) {
          windowBits = 9;
        }
        var s = new DeflateState();
        strm.state = s;
        s.strm = strm;
        s.wrap = wrap;
        s.gzhead = null;
        s.w_bits = windowBits;
        s.w_size = 1 << s.w_bits;
        s.w_mask = s.w_size - 1;
        s.hash_bits = memLevel + 7;
        s.hash_size = 1 << s.hash_bits;
        s.hash_mask = s.hash_size - 1;
        s.hash_shift = ~~((s.hash_bits + MIN_MATCH - 1) / MIN_MATCH);
        s.window = new utils.Buf8(s.w_size * 2);
        s.head = new utils.Buf16(s.hash_size);
        s.prev = new utils.Buf16(s.w_size);
        s.lit_bufsize = 1 << memLevel + 6;
        s.pending_buf_size = s.lit_bufsize * 4;
        s.pending_buf = new utils.Buf8(s.pending_buf_size);
        s.d_buf = 1 * s.lit_bufsize;
        s.l_buf = (1 + 2) * s.lit_bufsize;
        s.level = level;
        s.strategy = strategy;
        s.method = method;
        return deflateReset(strm);
      }
      function deflateInit(strm, level) {
        return deflateInit2(strm, level, Z_DEFLATED, MAX_WBITS, DEF_MEM_LEVEL, Z_DEFAULT_STRATEGY);
      }
      function deflate(strm, flush) {
        var old_flush, s;
        var beg, val;
        if (!strm || !strm.state || flush > Z_BLOCK || flush < 0) {
          return strm ? err(strm, Z_STREAM_ERROR) : Z_STREAM_ERROR;
        }
        s = strm.state;
        if (!strm.output || !strm.input && strm.avail_in !== 0 || s.status === FINISH_STATE && flush !== Z_FINISH) {
          return err(strm, strm.avail_out === 0 ? Z_BUF_ERROR : Z_STREAM_ERROR);
        }
        s.strm = strm;
        old_flush = s.last_flush;
        s.last_flush = flush;
        if (s.status === INIT_STATE) {
          if (s.wrap === 2) {
            strm.adler = 0;
            put_byte(s, 31);
            put_byte(s, 139);
            put_byte(s, 8);
            if (!s.gzhead) {
              put_byte(s, 0);
              put_byte(s, 0);
              put_byte(s, 0);
              put_byte(s, 0);
              put_byte(s, 0);
              put_byte(s, s.level === 9 ? 2 : s.strategy >= Z_HUFFMAN_ONLY || s.level < 2 ? 4 : 0);
              put_byte(s, OS_CODE);
              s.status = BUSY_STATE;
            } else {
              put_byte(
                s,
                (s.gzhead.text ? 1 : 0) + (s.gzhead.hcrc ? 2 : 0) + (!s.gzhead.extra ? 0 : 4) + (!s.gzhead.name ? 0 : 8) + (!s.gzhead.comment ? 0 : 16)
              );
              put_byte(s, s.gzhead.time & 255);
              put_byte(s, s.gzhead.time >> 8 & 255);
              put_byte(s, s.gzhead.time >> 16 & 255);
              put_byte(s, s.gzhead.time >> 24 & 255);
              put_byte(s, s.level === 9 ? 2 : s.strategy >= Z_HUFFMAN_ONLY || s.level < 2 ? 4 : 0);
              put_byte(s, s.gzhead.os & 255);
              if (s.gzhead.extra && s.gzhead.extra.length) {
                put_byte(s, s.gzhead.extra.length & 255);
                put_byte(s, s.gzhead.extra.length >> 8 & 255);
              }
              if (s.gzhead.hcrc) {
                strm.adler = crc32(strm.adler, s.pending_buf, s.pending, 0);
              }
              s.gzindex = 0;
              s.status = EXTRA_STATE;
            }
          } else {
            var header = Z_DEFLATED + (s.w_bits - 8 << 4) << 8;
            var level_flags = -1;
            if (s.strategy >= Z_HUFFMAN_ONLY || s.level < 2) {
              level_flags = 0;
            } else if (s.level < 6) {
              level_flags = 1;
            } else if (s.level === 6) {
              level_flags = 2;
            } else {
              level_flags = 3;
            }
            header |= level_flags << 6;
            if (s.strstart !== 0) {
              header |= PRESET_DICT;
            }
            header += 31 - header % 31;
            s.status = BUSY_STATE;
            putShortMSB(s, header);
            if (s.strstart !== 0) {
              putShortMSB(s, strm.adler >>> 16);
              putShortMSB(s, strm.adler & 65535);
            }
            strm.adler = 1;
          }
        }
        if (s.status === EXTRA_STATE) {
          if (s.gzhead.extra) {
            beg = s.pending;
            while (s.gzindex < (s.gzhead.extra.length & 65535)) {
              if (s.pending === s.pending_buf_size) {
                if (s.gzhead.hcrc && s.pending > beg) {
                  strm.adler = crc32(strm.adler, s.pending_buf, s.pending - beg, beg);
                }
                flush_pending(strm);
                beg = s.pending;
                if (s.pending === s.pending_buf_size) {
                  break;
                }
              }
              put_byte(s, s.gzhead.extra[s.gzindex] & 255);
              s.gzindex++;
            }
            if (s.gzhead.hcrc && s.pending > beg) {
              strm.adler = crc32(strm.adler, s.pending_buf, s.pending - beg, beg);
            }
            if (s.gzindex === s.gzhead.extra.length) {
              s.gzindex = 0;
              s.status = NAME_STATE;
            }
          } else {
            s.status = NAME_STATE;
          }
        }
        if (s.status === NAME_STATE) {
          if (s.gzhead.name) {
            beg = s.pending;
            do {
              if (s.pending === s.pending_buf_size) {
                if (s.gzhead.hcrc && s.pending > beg) {
                  strm.adler = crc32(strm.adler, s.pending_buf, s.pending - beg, beg);
                }
                flush_pending(strm);
                beg = s.pending;
                if (s.pending === s.pending_buf_size) {
                  val = 1;
                  break;
                }
              }
              if (s.gzindex < s.gzhead.name.length) {
                val = s.gzhead.name.charCodeAt(s.gzindex++) & 255;
              } else {
                val = 0;
              }
              put_byte(s, val);
            } while (val !== 0);
            if (s.gzhead.hcrc && s.pending > beg) {
              strm.adler = crc32(strm.adler, s.pending_buf, s.pending - beg, beg);
            }
            if (val === 0) {
              s.gzindex = 0;
              s.status = COMMENT_STATE;
            }
          } else {
            s.status = COMMENT_STATE;
          }
        }
        if (s.status === COMMENT_STATE) {
          if (s.gzhead.comment) {
            beg = s.pending;
            do {
              if (s.pending === s.pending_buf_size) {
                if (s.gzhead.hcrc && s.pending > beg) {
                  strm.adler = crc32(strm.adler, s.pending_buf, s.pending - beg, beg);
                }
                flush_pending(strm);
                beg = s.pending;
                if (s.pending === s.pending_buf_size) {
                  val = 1;
                  break;
                }
              }
              if (s.gzindex < s.gzhead.comment.length) {
                val = s.gzhead.comment.charCodeAt(s.gzindex++) & 255;
              } else {
                val = 0;
              }
              put_byte(s, val);
            } while (val !== 0);
            if (s.gzhead.hcrc && s.pending > beg) {
              strm.adler = crc32(strm.adler, s.pending_buf, s.pending - beg, beg);
            }
            if (val === 0) {
              s.status = HCRC_STATE;
            }
          } else {
            s.status = HCRC_STATE;
          }
        }
        if (s.status === HCRC_STATE) {
          if (s.gzhead.hcrc) {
            if (s.pending + 2 > s.pending_buf_size) {
              flush_pending(strm);
            }
            if (s.pending + 2 <= s.pending_buf_size) {
              put_byte(s, strm.adler & 255);
              put_byte(s, strm.adler >> 8 & 255);
              strm.adler = 0;
              s.status = BUSY_STATE;
            }
          } else {
            s.status = BUSY_STATE;
          }
        }
        if (s.pending !== 0) {
          flush_pending(strm);
          if (strm.avail_out === 0) {
            s.last_flush = -1;
            return Z_OK;
          }
        } else if (strm.avail_in === 0 && rank(flush) <= rank(old_flush) && flush !== Z_FINISH) {
          return err(strm, Z_BUF_ERROR);
        }
        if (s.status === FINISH_STATE && strm.avail_in !== 0) {
          return err(strm, Z_BUF_ERROR);
        }
        if (strm.avail_in !== 0 || s.lookahead !== 0 || flush !== Z_NO_FLUSH && s.status !== FINISH_STATE) {
          var bstate = s.strategy === Z_HUFFMAN_ONLY ? deflate_huff(s, flush) : s.strategy === Z_RLE ? deflate_rle(s, flush) : configuration_table[s.level].func(s, flush);
          if (bstate === BS_FINISH_STARTED || bstate === BS_FINISH_DONE) {
            s.status = FINISH_STATE;
          }
          if (bstate === BS_NEED_MORE || bstate === BS_FINISH_STARTED) {
            if (strm.avail_out === 0) {
              s.last_flush = -1;
            }
            return Z_OK;
          }
          if (bstate === BS_BLOCK_DONE) {
            if (flush === Z_PARTIAL_FLUSH) {
              trees._tr_align(s);
            } else if (flush !== Z_BLOCK) {
              trees._tr_stored_block(s, 0, 0, false);
              if (flush === Z_FULL_FLUSH) {
                zero(s.head);
                if (s.lookahead === 0) {
                  s.strstart = 0;
                  s.block_start = 0;
                  s.insert = 0;
                }
              }
            }
            flush_pending(strm);
            if (strm.avail_out === 0) {
              s.last_flush = -1;
              return Z_OK;
            }
          }
        }
        if (flush !== Z_FINISH) {
          return Z_OK;
        }
        if (s.wrap <= 0) {
          return Z_STREAM_END;
        }
        if (s.wrap === 2) {
          put_byte(s, strm.adler & 255);
          put_byte(s, strm.adler >> 8 & 255);
          put_byte(s, strm.adler >> 16 & 255);
          put_byte(s, strm.adler >> 24 & 255);
          put_byte(s, strm.total_in & 255);
          put_byte(s, strm.total_in >> 8 & 255);
          put_byte(s, strm.total_in >> 16 & 255);
          put_byte(s, strm.total_in >> 24 & 255);
        } else {
          putShortMSB(s, strm.adler >>> 16);
          putShortMSB(s, strm.adler & 65535);
        }
        flush_pending(strm);
        if (s.wrap > 0) {
          s.wrap = -s.wrap;
        }
        return s.pending !== 0 ? Z_OK : Z_STREAM_END;
      }
      function deflateEnd(strm) {
        var status;
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        status = strm.state.status;
        if (status !== INIT_STATE && status !== EXTRA_STATE && status !== NAME_STATE && status !== COMMENT_STATE && status !== HCRC_STATE && status !== BUSY_STATE && status !== FINISH_STATE) {
          return err(strm, Z_STREAM_ERROR);
        }
        strm.state = null;
        return status === BUSY_STATE ? err(strm, Z_DATA_ERROR) : Z_OK;
      }
      function deflateSetDictionary(strm, dictionary) {
        var dictLength = dictionary.length;
        var s;
        var str, n;
        var wrap;
        var avail;
        var next;
        var input;
        var tmpDict;
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        s = strm.state;
        wrap = s.wrap;
        if (wrap === 2 || wrap === 1 && s.status !== INIT_STATE || s.lookahead) {
          return Z_STREAM_ERROR;
        }
        if (wrap === 1) {
          strm.adler = adler32(strm.adler, dictionary, dictLength, 0);
        }
        s.wrap = 0;
        if (dictLength >= s.w_size) {
          if (wrap === 0) {
            zero(s.head);
            s.strstart = 0;
            s.block_start = 0;
            s.insert = 0;
          }
          tmpDict = new utils.Buf8(s.w_size);
          utils.arraySet(tmpDict, dictionary, dictLength - s.w_size, s.w_size, 0);
          dictionary = tmpDict;
          dictLength = s.w_size;
        }
        avail = strm.avail_in;
        next = strm.next_in;
        input = strm.input;
        strm.avail_in = dictLength;
        strm.next_in = 0;
        strm.input = dictionary;
        fill_window(s);
        while (s.lookahead >= MIN_MATCH) {
          str = s.strstart;
          n = s.lookahead - (MIN_MATCH - 1);
          do {
            s.ins_h = (s.ins_h << s.hash_shift ^ s.window[str + MIN_MATCH - 1]) & s.hash_mask;
            s.prev[str & s.w_mask] = s.head[s.ins_h];
            s.head[s.ins_h] = str;
            str++;
          } while (--n);
          s.strstart = str;
          s.lookahead = MIN_MATCH - 1;
          fill_window(s);
        }
        s.strstart += s.lookahead;
        s.block_start = s.strstart;
        s.insert = s.lookahead;
        s.lookahead = 0;
        s.match_length = s.prev_length = MIN_MATCH - 1;
        s.match_available = 0;
        strm.next_in = next;
        strm.input = input;
        strm.avail_in = avail;
        s.wrap = wrap;
        return Z_OK;
      }
      exports.deflateInit = deflateInit;
      exports.deflateInit2 = deflateInit2;
      exports.deflateReset = deflateReset;
      exports.deflateResetKeep = deflateResetKeep;
      exports.deflateSetHeader = deflateSetHeader;
      exports.deflate = deflate;
      exports.deflateEnd = deflateEnd;
      exports.deflateSetDictionary = deflateSetDictionary;
      exports.deflateInfo = "pako deflate (from Nodeca project)";
    }
  });

  // node_modules/pako/lib/utils/strings.js
  var require_strings = __commonJS({
    "node_modules/pako/lib/utils/strings.js"(exports) {
      "use strict";
      var utils = require_common();
      var STR_APPLY_OK = true;
      var STR_APPLY_UIA_OK = true;
      try {
        String.fromCharCode.apply(null, [0]);
      } catch (__) {
        STR_APPLY_OK = false;
      }
      try {
        String.fromCharCode.apply(null, new Uint8Array(1));
      } catch (__) {
        STR_APPLY_UIA_OK = false;
      }
      var _utf8len = new utils.Buf8(256);
      for (q = 0; q < 256; q++) {
        _utf8len[q] = q >= 252 ? 6 : q >= 248 ? 5 : q >= 240 ? 4 : q >= 224 ? 3 : q >= 192 ? 2 : 1;
      }
      var q;
      _utf8len[254] = _utf8len[254] = 1;
      exports.string2buf = function(str) {
        var buf, c, c2, m_pos, i, str_len = str.length, buf_len = 0;
        for (m_pos = 0; m_pos < str_len; m_pos++) {
          c = str.charCodeAt(m_pos);
          if ((c & 64512) === 55296 && m_pos + 1 < str_len) {
            c2 = str.charCodeAt(m_pos + 1);
            if ((c2 & 64512) === 56320) {
              c = 65536 + (c - 55296 << 10) + (c2 - 56320);
              m_pos++;
            }
          }
          buf_len += c < 128 ? 1 : c < 2048 ? 2 : c < 65536 ? 3 : 4;
        }
        buf = new utils.Buf8(buf_len);
        for (i = 0, m_pos = 0; i < buf_len; m_pos++) {
          c = str.charCodeAt(m_pos);
          if ((c & 64512) === 55296 && m_pos + 1 < str_len) {
            c2 = str.charCodeAt(m_pos + 1);
            if ((c2 & 64512) === 56320) {
              c = 65536 + (c - 55296 << 10) + (c2 - 56320);
              m_pos++;
            }
          }
          if (c < 128) {
            buf[i++] = c;
          } else if (c < 2048) {
            buf[i++] = 192 | c >>> 6;
            buf[i++] = 128 | c & 63;
          } else if (c < 65536) {
            buf[i++] = 224 | c >>> 12;
            buf[i++] = 128 | c >>> 6 & 63;
            buf[i++] = 128 | c & 63;
          } else {
            buf[i++] = 240 | c >>> 18;
            buf[i++] = 128 | c >>> 12 & 63;
            buf[i++] = 128 | c >>> 6 & 63;
            buf[i++] = 128 | c & 63;
          }
        }
        return buf;
      };
      function buf2binstring(buf, len) {
        if (len < 65534) {
          if (buf.subarray && STR_APPLY_UIA_OK || !buf.subarray && STR_APPLY_OK) {
            return String.fromCharCode.apply(null, utils.shrinkBuf(buf, len));
          }
        }
        var result = "";
        for (var i = 0; i < len; i++) {
          result += String.fromCharCode(buf[i]);
        }
        return result;
      }
      exports.buf2binstring = function(buf) {
        return buf2binstring(buf, buf.length);
      };
      exports.binstring2buf = function(str) {
        var buf = new utils.Buf8(str.length);
        for (var i = 0, len = buf.length; i < len; i++) {
          buf[i] = str.charCodeAt(i);
        }
        return buf;
      };
      exports.buf2string = function(buf, max) {
        var i, out, c, c_len;
        var len = max || buf.length;
        var utf16buf = new Array(len * 2);
        for (out = 0, i = 0; i < len; ) {
          c = buf[i++];
          if (c < 128) {
            utf16buf[out++] = c;
            continue;
          }
          c_len = _utf8len[c];
          if (c_len > 4) {
            utf16buf[out++] = 65533;
            i += c_len - 1;
            continue;
          }
          c &= c_len === 2 ? 31 : c_len === 3 ? 15 : 7;
          while (c_len > 1 && i < len) {
            c = c << 6 | buf[i++] & 63;
            c_len--;
          }
          if (c_len > 1) {
            utf16buf[out++] = 65533;
            continue;
          }
          if (c < 65536) {
            utf16buf[out++] = c;
          } else {
            c -= 65536;
            utf16buf[out++] = 55296 | c >> 10 & 1023;
            utf16buf[out++] = 56320 | c & 1023;
          }
        }
        return buf2binstring(utf16buf, out);
      };
      exports.utf8border = function(buf, max) {
        var pos;
        max = max || buf.length;
        if (max > buf.length) {
          max = buf.length;
        }
        pos = max - 1;
        while (pos >= 0 && (buf[pos] & 192) === 128) {
          pos--;
        }
        if (pos < 0) {
          return max;
        }
        if (pos === 0) {
          return max;
        }
        return pos + _utf8len[buf[pos]] > max ? pos : max;
      };
    }
  });

  // node_modules/pako/lib/zlib/zstream.js
  var require_zstream = __commonJS({
    "node_modules/pako/lib/zlib/zstream.js"(exports, module) {
      "use strict";
      function ZStream() {
        this.input = null;
        this.next_in = 0;
        this.avail_in = 0;
        this.total_in = 0;
        this.output = null;
        this.next_out = 0;
        this.avail_out = 0;
        this.total_out = 0;
        this.msg = "";
        this.state = null;
        this.data_type = 2;
        this.adler = 0;
      }
      module.exports = ZStream;
    }
  });

  // node_modules/pako/lib/deflate.js
  var require_deflate2 = __commonJS({
    "node_modules/pako/lib/deflate.js"(exports) {
      "use strict";
      var zlib_deflate = require_deflate();
      var utils = require_common();
      var strings = require_strings();
      var msg = require_messages();
      var ZStream = require_zstream();
      var toString = Object.prototype.toString;
      var Z_NO_FLUSH = 0;
      var Z_FINISH = 4;
      var Z_OK = 0;
      var Z_STREAM_END = 1;
      var Z_SYNC_FLUSH = 2;
      var Z_DEFAULT_COMPRESSION = -1;
      var Z_DEFAULT_STRATEGY = 0;
      var Z_DEFLATED = 8;
      function Deflate(options) {
        if (!(this instanceof Deflate)) return new Deflate(options);
        this.options = utils.assign({
          level: Z_DEFAULT_COMPRESSION,
          method: Z_DEFLATED,
          chunkSize: 16384,
          windowBits: 15,
          memLevel: 8,
          strategy: Z_DEFAULT_STRATEGY,
          to: ""
        }, options || {});
        var opt = this.options;
        if (opt.raw && opt.windowBits > 0) {
          opt.windowBits = -opt.windowBits;
        } else if (opt.gzip && opt.windowBits > 0 && opt.windowBits < 16) {
          opt.windowBits += 16;
        }
        this.err = 0;
        this.msg = "";
        this.ended = false;
        this.chunks = [];
        this.strm = new ZStream();
        this.strm.avail_out = 0;
        var status = zlib_deflate.deflateInit2(
          this.strm,
          opt.level,
          opt.method,
          opt.windowBits,
          opt.memLevel,
          opt.strategy
        );
        if (status !== Z_OK) {
          throw new Error(msg[status]);
        }
        if (opt.header) {
          zlib_deflate.deflateSetHeader(this.strm, opt.header);
        }
        if (opt.dictionary) {
          var dict;
          if (typeof opt.dictionary === "string") {
            dict = strings.string2buf(opt.dictionary);
          } else if (toString.call(opt.dictionary) === "[object ArrayBuffer]") {
            dict = new Uint8Array(opt.dictionary);
          } else {
            dict = opt.dictionary;
          }
          status = zlib_deflate.deflateSetDictionary(this.strm, dict);
          if (status !== Z_OK) {
            throw new Error(msg[status]);
          }
          this._dict_set = true;
        }
      }
      Deflate.prototype.push = function(data, mode) {
        var strm = this.strm;
        var chunkSize = this.options.chunkSize;
        var status, _mode;
        if (this.ended) {
          return false;
        }
        _mode = mode === ~~mode ? mode : mode === true ? Z_FINISH : Z_NO_FLUSH;
        if (typeof data === "string") {
          strm.input = strings.string2buf(data);
        } else if (toString.call(data) === "[object ArrayBuffer]") {
          strm.input = new Uint8Array(data);
        } else {
          strm.input = data;
        }
        strm.next_in = 0;
        strm.avail_in = strm.input.length;
        do {
          if (strm.avail_out === 0) {
            strm.output = new utils.Buf8(chunkSize);
            strm.next_out = 0;
            strm.avail_out = chunkSize;
          }
          status = zlib_deflate.deflate(strm, _mode);
          if (status !== Z_STREAM_END && status !== Z_OK) {
            this.onEnd(status);
            this.ended = true;
            return false;
          }
          if (strm.avail_out === 0 || strm.avail_in === 0 && (_mode === Z_FINISH || _mode === Z_SYNC_FLUSH)) {
            if (this.options.to === "string") {
              this.onData(strings.buf2binstring(utils.shrinkBuf(strm.output, strm.next_out)));
            } else {
              this.onData(utils.shrinkBuf(strm.output, strm.next_out));
            }
          }
        } while ((strm.avail_in > 0 || strm.avail_out === 0) && status !== Z_STREAM_END);
        if (_mode === Z_FINISH) {
          status = zlib_deflate.deflateEnd(this.strm);
          this.onEnd(status);
          this.ended = true;
          return status === Z_OK;
        }
        if (_mode === Z_SYNC_FLUSH) {
          this.onEnd(Z_OK);
          strm.avail_out = 0;
          return true;
        }
        return true;
      };
      Deflate.prototype.onData = function(chunk) {
        this.chunks.push(chunk);
      };
      Deflate.prototype.onEnd = function(status) {
        if (status === Z_OK) {
          if (this.options.to === "string") {
            this.result = this.chunks.join("");
          } else {
            this.result = utils.flattenChunks(this.chunks);
          }
        }
        this.chunks = [];
        this.err = status;
        this.msg = this.strm.msg;
      };
      function deflate(input, options) {
        var deflator = new Deflate(options);
        deflator.push(input, true);
        if (deflator.err) {
          throw deflator.msg || msg[deflator.err];
        }
        return deflator.result;
      }
      function deflateRaw(input, options) {
        options = options || {};
        options.raw = true;
        return deflate(input, options);
      }
      function gzip(input, options) {
        options = options || {};
        options.gzip = true;
        return deflate(input, options);
      }
      exports.Deflate = Deflate;
      exports.deflate = deflate;
      exports.deflateRaw = deflateRaw;
      exports.gzip = gzip;
    }
  });

  // node_modules/pako/lib/zlib/inffast.js
  var require_inffast = __commonJS({
    "node_modules/pako/lib/zlib/inffast.js"(exports, module) {
      "use strict";
      var BAD = 30;
      var TYPE = 12;
      module.exports = function inflate_fast(strm, start) {
        var state2;
        var _in;
        var last;
        var _out;
        var beg;
        var end;
        var dmax;
        var wsize;
        var whave;
        var wnext;
        var s_window;
        var hold;
        var bits;
        var lcode;
        var dcode;
        var lmask;
        var dmask;
        var here;
        var op;
        var len;
        var dist;
        var from;
        var from_source;
        var input, output;
        state2 = strm.state;
        _in = strm.next_in;
        input = strm.input;
        last = _in + (strm.avail_in - 5);
        _out = strm.next_out;
        output = strm.output;
        beg = _out - (start - strm.avail_out);
        end = _out + (strm.avail_out - 257);
        dmax = state2.dmax;
        wsize = state2.wsize;
        whave = state2.whave;
        wnext = state2.wnext;
        s_window = state2.window;
        hold = state2.hold;
        bits = state2.bits;
        lcode = state2.lencode;
        dcode = state2.distcode;
        lmask = (1 << state2.lenbits) - 1;
        dmask = (1 << state2.distbits) - 1;
        top:
          do {
            if (bits < 15) {
              hold += input[_in++] << bits;
              bits += 8;
              hold += input[_in++] << bits;
              bits += 8;
            }
            here = lcode[hold & lmask];
            dolen:
              for (; ; ) {
                op = here >>> 24;
                hold >>>= op;
                bits -= op;
                op = here >>> 16 & 255;
                if (op === 0) {
                  output[_out++] = here & 65535;
                } else if (op & 16) {
                  len = here & 65535;
                  op &= 15;
                  if (op) {
                    if (bits < op) {
                      hold += input[_in++] << bits;
                      bits += 8;
                    }
                    len += hold & (1 << op) - 1;
                    hold >>>= op;
                    bits -= op;
                  }
                  if (bits < 15) {
                    hold += input[_in++] << bits;
                    bits += 8;
                    hold += input[_in++] << bits;
                    bits += 8;
                  }
                  here = dcode[hold & dmask];
                  dodist:
                    for (; ; ) {
                      op = here >>> 24;
                      hold >>>= op;
                      bits -= op;
                      op = here >>> 16 & 255;
                      if (op & 16) {
                        dist = here & 65535;
                        op &= 15;
                        if (bits < op) {
                          hold += input[_in++] << bits;
                          bits += 8;
                          if (bits < op) {
                            hold += input[_in++] << bits;
                            bits += 8;
                          }
                        }
                        dist += hold & (1 << op) - 1;
                        if (dist > dmax) {
                          strm.msg = "invalid distance too far back";
                          state2.mode = BAD;
                          break top;
                        }
                        hold >>>= op;
                        bits -= op;
                        op = _out - beg;
                        if (dist > op) {
                          op = dist - op;
                          if (op > whave) {
                            if (state2.sane) {
                              strm.msg = "invalid distance too far back";
                              state2.mode = BAD;
                              break top;
                            }
                          }
                          from = 0;
                          from_source = s_window;
                          if (wnext === 0) {
                            from += wsize - op;
                            if (op < len) {
                              len -= op;
                              do {
                                output[_out++] = s_window[from++];
                              } while (--op);
                              from = _out - dist;
                              from_source = output;
                            }
                          } else if (wnext < op) {
                            from += wsize + wnext - op;
                            op -= wnext;
                            if (op < len) {
                              len -= op;
                              do {
                                output[_out++] = s_window[from++];
                              } while (--op);
                              from = 0;
                              if (wnext < len) {
                                op = wnext;
                                len -= op;
                                do {
                                  output[_out++] = s_window[from++];
                                } while (--op);
                                from = _out - dist;
                                from_source = output;
                              }
                            }
                          } else {
                            from += wnext - op;
                            if (op < len) {
                              len -= op;
                              do {
                                output[_out++] = s_window[from++];
                              } while (--op);
                              from = _out - dist;
                              from_source = output;
                            }
                          }
                          while (len > 2) {
                            output[_out++] = from_source[from++];
                            output[_out++] = from_source[from++];
                            output[_out++] = from_source[from++];
                            len -= 3;
                          }
                          if (len) {
                            output[_out++] = from_source[from++];
                            if (len > 1) {
                              output[_out++] = from_source[from++];
                            }
                          }
                        } else {
                          from = _out - dist;
                          do {
                            output[_out++] = output[from++];
                            output[_out++] = output[from++];
                            output[_out++] = output[from++];
                            len -= 3;
                          } while (len > 2);
                          if (len) {
                            output[_out++] = output[from++];
                            if (len > 1) {
                              output[_out++] = output[from++];
                            }
                          }
                        }
                      } else if ((op & 64) === 0) {
                        here = dcode[(here & 65535) + (hold & (1 << op) - 1)];
                        continue dodist;
                      } else {
                        strm.msg = "invalid distance code";
                        state2.mode = BAD;
                        break top;
                      }
                      break;
                    }
                } else if ((op & 64) === 0) {
                  here = lcode[(here & 65535) + (hold & (1 << op) - 1)];
                  continue dolen;
                } else if (op & 32) {
                  state2.mode = TYPE;
                  break top;
                } else {
                  strm.msg = "invalid literal/length code";
                  state2.mode = BAD;
                  break top;
                }
                break;
              }
          } while (_in < last && _out < end);
        len = bits >> 3;
        _in -= len;
        bits -= len << 3;
        hold &= (1 << bits) - 1;
        strm.next_in = _in;
        strm.next_out = _out;
        strm.avail_in = _in < last ? 5 + (last - _in) : 5 - (_in - last);
        strm.avail_out = _out < end ? 257 + (end - _out) : 257 - (_out - end);
        state2.hold = hold;
        state2.bits = bits;
        return;
      };
    }
  });

  // node_modules/pako/lib/zlib/inftrees.js
  var require_inftrees = __commonJS({
    "node_modules/pako/lib/zlib/inftrees.js"(exports, module) {
      "use strict";
      var utils = require_common();
      var MAXBITS = 15;
      var ENOUGH_LENS = 852;
      var ENOUGH_DISTS = 592;
      var CODES = 0;
      var LENS = 1;
      var DISTS = 2;
      var lbase = [
        /* Length codes 257..285 base */
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10,
        11,
        13,
        15,
        17,
        19,
        23,
        27,
        31,
        35,
        43,
        51,
        59,
        67,
        83,
        99,
        115,
        131,
        163,
        195,
        227,
        258,
        0,
        0
      ];
      var lext = [
        /* Length codes 257..285 extra */
        16,
        16,
        16,
        16,
        16,
        16,
        16,
        16,
        17,
        17,
        17,
        17,
        18,
        18,
        18,
        18,
        19,
        19,
        19,
        19,
        20,
        20,
        20,
        20,
        21,
        21,
        21,
        21,
        16,
        72,
        78
      ];
      var dbase = [
        /* Distance codes 0..29 base */
        1,
        2,
        3,
        4,
        5,
        7,
        9,
        13,
        17,
        25,
        33,
        49,
        65,
        97,
        129,
        193,
        257,
        385,
        513,
        769,
        1025,
        1537,
        2049,
        3073,
        4097,
        6145,
        8193,
        12289,
        16385,
        24577,
        0,
        0
      ];
      var dext = [
        /* Distance codes 0..29 extra */
        16,
        16,
        16,
        16,
        17,
        17,
        18,
        18,
        19,
        19,
        20,
        20,
        21,
        21,
        22,
        22,
        23,
        23,
        24,
        24,
        25,
        25,
        26,
        26,
        27,
        27,
        28,
        28,
        29,
        29,
        64,
        64
      ];
      module.exports = function inflate_table(type, lens, lens_index, codes, table, table_index, work, opts) {
        var bits = opts.bits;
        var len = 0;
        var sym = 0;
        var min = 0, max = 0;
        var root = 0;
        var curr = 0;
        var drop = 0;
        var left = 0;
        var used = 0;
        var huff = 0;
        var incr;
        var fill;
        var low;
        var mask;
        var next;
        var base = null;
        var base_index = 0;
        var end;
        var count = new utils.Buf16(MAXBITS + 1);
        var offs = new utils.Buf16(MAXBITS + 1);
        var extra = null;
        var extra_index = 0;
        var here_bits, here_op, here_val;
        for (len = 0; len <= MAXBITS; len++) {
          count[len] = 0;
        }
        for (sym = 0; sym < codes; sym++) {
          count[lens[lens_index + sym]]++;
        }
        root = bits;
        for (max = MAXBITS; max >= 1; max--) {
          if (count[max] !== 0) {
            break;
          }
        }
        if (root > max) {
          root = max;
        }
        if (max === 0) {
          table[table_index++] = 1 << 24 | 64 << 16 | 0;
          table[table_index++] = 1 << 24 | 64 << 16 | 0;
          opts.bits = 1;
          return 0;
        }
        for (min = 1; min < max; min++) {
          if (count[min] !== 0) {
            break;
          }
        }
        if (root < min) {
          root = min;
        }
        left = 1;
        for (len = 1; len <= MAXBITS; len++) {
          left <<= 1;
          left -= count[len];
          if (left < 0) {
            return -1;
          }
        }
        if (left > 0 && (type === CODES || max !== 1)) {
          return -1;
        }
        offs[1] = 0;
        for (len = 1; len < MAXBITS; len++) {
          offs[len + 1] = offs[len] + count[len];
        }
        for (sym = 0; sym < codes; sym++) {
          if (lens[lens_index + sym] !== 0) {
            work[offs[lens[lens_index + sym]]++] = sym;
          }
        }
        if (type === CODES) {
          base = extra = work;
          end = 19;
        } else if (type === LENS) {
          base = lbase;
          base_index -= 257;
          extra = lext;
          extra_index -= 257;
          end = 256;
        } else {
          base = dbase;
          extra = dext;
          end = -1;
        }
        huff = 0;
        sym = 0;
        len = min;
        next = table_index;
        curr = root;
        drop = 0;
        low = -1;
        used = 1 << root;
        mask = used - 1;
        if (type === LENS && used > ENOUGH_LENS || type === DISTS && used > ENOUGH_DISTS) {
          return 1;
        }
        for (; ; ) {
          here_bits = len - drop;
          if (work[sym] < end) {
            here_op = 0;
            here_val = work[sym];
          } else if (work[sym] > end) {
            here_op = extra[extra_index + work[sym]];
            here_val = base[base_index + work[sym]];
          } else {
            here_op = 32 + 64;
            here_val = 0;
          }
          incr = 1 << len - drop;
          fill = 1 << curr;
          min = fill;
          do {
            fill -= incr;
            table[next + (huff >> drop) + fill] = here_bits << 24 | here_op << 16 | here_val | 0;
          } while (fill !== 0);
          incr = 1 << len - 1;
          while (huff & incr) {
            incr >>= 1;
          }
          if (incr !== 0) {
            huff &= incr - 1;
            huff += incr;
          } else {
            huff = 0;
          }
          sym++;
          if (--count[len] === 0) {
            if (len === max) {
              break;
            }
            len = lens[lens_index + work[sym]];
          }
          if (len > root && (huff & mask) !== low) {
            if (drop === 0) {
              drop = root;
            }
            next += min;
            curr = len - drop;
            left = 1 << curr;
            while (curr + drop < max) {
              left -= count[curr + drop];
              if (left <= 0) {
                break;
              }
              curr++;
              left <<= 1;
            }
            used += 1 << curr;
            if (type === LENS && used > ENOUGH_LENS || type === DISTS && used > ENOUGH_DISTS) {
              return 1;
            }
            low = huff & mask;
            table[low] = root << 24 | curr << 16 | next - table_index | 0;
          }
        }
        if (huff !== 0) {
          table[next + huff] = len - drop << 24 | 64 << 16 | 0;
        }
        opts.bits = root;
        return 0;
      };
    }
  });

  // node_modules/pako/lib/zlib/inflate.js
  var require_inflate = __commonJS({
    "node_modules/pako/lib/zlib/inflate.js"(exports) {
      "use strict";
      var utils = require_common();
      var adler32 = require_adler32();
      var crc32 = require_crc32();
      var inflate_fast = require_inffast();
      var inflate_table = require_inftrees();
      var CODES = 0;
      var LENS = 1;
      var DISTS = 2;
      var Z_FINISH = 4;
      var Z_BLOCK = 5;
      var Z_TREES = 6;
      var Z_OK = 0;
      var Z_STREAM_END = 1;
      var Z_NEED_DICT = 2;
      var Z_STREAM_ERROR = -2;
      var Z_DATA_ERROR = -3;
      var Z_MEM_ERROR = -4;
      var Z_BUF_ERROR = -5;
      var Z_DEFLATED = 8;
      var HEAD = 1;
      var FLAGS = 2;
      var TIME = 3;
      var OS = 4;
      var EXLEN = 5;
      var EXTRA = 6;
      var NAME = 7;
      var COMMENT = 8;
      var HCRC = 9;
      var DICTID = 10;
      var DICT = 11;
      var TYPE = 12;
      var TYPEDO = 13;
      var STORED = 14;
      var COPY_ = 15;
      var COPY = 16;
      var TABLE = 17;
      var LENLENS = 18;
      var CODELENS = 19;
      var LEN_ = 20;
      var LEN = 21;
      var LENEXT = 22;
      var DIST = 23;
      var DISTEXT = 24;
      var MATCH = 25;
      var LIT = 26;
      var CHECK = 27;
      var LENGTH = 28;
      var DONE = 29;
      var BAD = 30;
      var MEM = 31;
      var SYNC = 32;
      var ENOUGH_LENS = 852;
      var ENOUGH_DISTS = 592;
      var MAX_WBITS = 15;
      var DEF_WBITS = MAX_WBITS;
      function zswap32(q) {
        return (q >>> 24 & 255) + (q >>> 8 & 65280) + ((q & 65280) << 8) + ((q & 255) << 24);
      }
      function InflateState() {
        this.mode = 0;
        this.last = false;
        this.wrap = 0;
        this.havedict = false;
        this.flags = 0;
        this.dmax = 0;
        this.check = 0;
        this.total = 0;
        this.head = null;
        this.wbits = 0;
        this.wsize = 0;
        this.whave = 0;
        this.wnext = 0;
        this.window = null;
        this.hold = 0;
        this.bits = 0;
        this.length = 0;
        this.offset = 0;
        this.extra = 0;
        this.lencode = null;
        this.distcode = null;
        this.lenbits = 0;
        this.distbits = 0;
        this.ncode = 0;
        this.nlen = 0;
        this.ndist = 0;
        this.have = 0;
        this.next = null;
        this.lens = new utils.Buf16(320);
        this.work = new utils.Buf16(288);
        this.lendyn = null;
        this.distdyn = null;
        this.sane = 0;
        this.back = 0;
        this.was = 0;
      }
      function inflateResetKeep(strm) {
        var state2;
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        state2 = strm.state;
        strm.total_in = strm.total_out = state2.total = 0;
        strm.msg = "";
        if (state2.wrap) {
          strm.adler = state2.wrap & 1;
        }
        state2.mode = HEAD;
        state2.last = 0;
        state2.havedict = 0;
        state2.dmax = 32768;
        state2.head = null;
        state2.hold = 0;
        state2.bits = 0;
        state2.lencode = state2.lendyn = new utils.Buf32(ENOUGH_LENS);
        state2.distcode = state2.distdyn = new utils.Buf32(ENOUGH_DISTS);
        state2.sane = 1;
        state2.back = -1;
        return Z_OK;
      }
      function inflateReset(strm) {
        var state2;
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        state2 = strm.state;
        state2.wsize = 0;
        state2.whave = 0;
        state2.wnext = 0;
        return inflateResetKeep(strm);
      }
      function inflateReset2(strm, windowBits) {
        var wrap;
        var state2;
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        state2 = strm.state;
        if (windowBits < 0) {
          wrap = 0;
          windowBits = -windowBits;
        } else {
          wrap = (windowBits >> 4) + 1;
          if (windowBits < 48) {
            windowBits &= 15;
          }
        }
        if (windowBits && (windowBits < 8 || windowBits > 15)) {
          return Z_STREAM_ERROR;
        }
        if (state2.window !== null && state2.wbits !== windowBits) {
          state2.window = null;
        }
        state2.wrap = wrap;
        state2.wbits = windowBits;
        return inflateReset(strm);
      }
      function inflateInit2(strm, windowBits) {
        var ret;
        var state2;
        if (!strm) {
          return Z_STREAM_ERROR;
        }
        state2 = new InflateState();
        strm.state = state2;
        state2.window = null;
        ret = inflateReset2(strm, windowBits);
        if (ret !== Z_OK) {
          strm.state = null;
        }
        return ret;
      }
      function inflateInit(strm) {
        return inflateInit2(strm, DEF_WBITS);
      }
      var virgin = true;
      var lenfix;
      var distfix;
      function fixedtables(state2) {
        if (virgin) {
          var sym;
          lenfix = new utils.Buf32(512);
          distfix = new utils.Buf32(32);
          sym = 0;
          while (sym < 144) {
            state2.lens[sym++] = 8;
          }
          while (sym < 256) {
            state2.lens[sym++] = 9;
          }
          while (sym < 280) {
            state2.lens[sym++] = 7;
          }
          while (sym < 288) {
            state2.lens[sym++] = 8;
          }
          inflate_table(LENS, state2.lens, 0, 288, lenfix, 0, state2.work, { bits: 9 });
          sym = 0;
          while (sym < 32) {
            state2.lens[sym++] = 5;
          }
          inflate_table(DISTS, state2.lens, 0, 32, distfix, 0, state2.work, { bits: 5 });
          virgin = false;
        }
        state2.lencode = lenfix;
        state2.lenbits = 9;
        state2.distcode = distfix;
        state2.distbits = 5;
      }
      function updatewindow(strm, src, end, copy) {
        var dist;
        var state2 = strm.state;
        if (state2.window === null) {
          state2.wsize = 1 << state2.wbits;
          state2.wnext = 0;
          state2.whave = 0;
          state2.window = new utils.Buf8(state2.wsize);
        }
        if (copy >= state2.wsize) {
          utils.arraySet(state2.window, src, end - state2.wsize, state2.wsize, 0);
          state2.wnext = 0;
          state2.whave = state2.wsize;
        } else {
          dist = state2.wsize - state2.wnext;
          if (dist > copy) {
            dist = copy;
          }
          utils.arraySet(state2.window, src, end - copy, dist, state2.wnext);
          copy -= dist;
          if (copy) {
            utils.arraySet(state2.window, src, end - copy, copy, 0);
            state2.wnext = copy;
            state2.whave = state2.wsize;
          } else {
            state2.wnext += dist;
            if (state2.wnext === state2.wsize) {
              state2.wnext = 0;
            }
            if (state2.whave < state2.wsize) {
              state2.whave += dist;
            }
          }
        }
        return 0;
      }
      function inflate3(strm, flush) {
        var state2;
        var input, output;
        var next;
        var put;
        var have, left;
        var hold;
        var bits;
        var _in, _out;
        var copy;
        var from;
        var from_source;
        var here = 0;
        var here_bits, here_op, here_val;
        var last_bits, last_op, last_val;
        var len;
        var ret;
        var hbuf = new utils.Buf8(4);
        var opts;
        var n;
        var order = (
          /* permutation of code lengths */
          [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15]
        );
        if (!strm || !strm.state || !strm.output || !strm.input && strm.avail_in !== 0) {
          return Z_STREAM_ERROR;
        }
        state2 = strm.state;
        if (state2.mode === TYPE) {
          state2.mode = TYPEDO;
        }
        put = strm.next_out;
        output = strm.output;
        left = strm.avail_out;
        next = strm.next_in;
        input = strm.input;
        have = strm.avail_in;
        hold = state2.hold;
        bits = state2.bits;
        _in = have;
        _out = left;
        ret = Z_OK;
        inf_leave:
          for (; ; ) {
            switch (state2.mode) {
              case HEAD:
                if (state2.wrap === 0) {
                  state2.mode = TYPEDO;
                  break;
                }
                while (bits < 16) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                if (state2.wrap & 2 && hold === 35615) {
                  state2.check = 0;
                  hbuf[0] = hold & 255;
                  hbuf[1] = hold >>> 8 & 255;
                  state2.check = crc32(state2.check, hbuf, 2, 0);
                  hold = 0;
                  bits = 0;
                  state2.mode = FLAGS;
                  break;
                }
                state2.flags = 0;
                if (state2.head) {
                  state2.head.done = false;
                }
                if (!(state2.wrap & 1) || /* check if zlib header allowed */
                (((hold & 255) << 8) + (hold >> 8)) % 31) {
                  strm.msg = "incorrect header check";
                  state2.mode = BAD;
                  break;
                }
                if ((hold & 15) !== Z_DEFLATED) {
                  strm.msg = "unknown compression method";
                  state2.mode = BAD;
                  break;
                }
                hold >>>= 4;
                bits -= 4;
                len = (hold & 15) + 8;
                if (state2.wbits === 0) {
                  state2.wbits = len;
                } else if (len > state2.wbits) {
                  strm.msg = "invalid window size";
                  state2.mode = BAD;
                  break;
                }
                state2.dmax = 1 << len;
                strm.adler = state2.check = 1;
                state2.mode = hold & 512 ? DICTID : TYPE;
                hold = 0;
                bits = 0;
                break;
              case FLAGS:
                while (bits < 16) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                state2.flags = hold;
                if ((state2.flags & 255) !== Z_DEFLATED) {
                  strm.msg = "unknown compression method";
                  state2.mode = BAD;
                  break;
                }
                if (state2.flags & 57344) {
                  strm.msg = "unknown header flags set";
                  state2.mode = BAD;
                  break;
                }
                if (state2.head) {
                  state2.head.text = hold >> 8 & 1;
                }
                if (state2.flags & 512) {
                  hbuf[0] = hold & 255;
                  hbuf[1] = hold >>> 8 & 255;
                  state2.check = crc32(state2.check, hbuf, 2, 0);
                }
                hold = 0;
                bits = 0;
                state2.mode = TIME;
              /* falls through */
              case TIME:
                while (bits < 32) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                if (state2.head) {
                  state2.head.time = hold;
                }
                if (state2.flags & 512) {
                  hbuf[0] = hold & 255;
                  hbuf[1] = hold >>> 8 & 255;
                  hbuf[2] = hold >>> 16 & 255;
                  hbuf[3] = hold >>> 24 & 255;
                  state2.check = crc32(state2.check, hbuf, 4, 0);
                }
                hold = 0;
                bits = 0;
                state2.mode = OS;
              /* falls through */
              case OS:
                while (bits < 16) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                if (state2.head) {
                  state2.head.xflags = hold & 255;
                  state2.head.os = hold >> 8;
                }
                if (state2.flags & 512) {
                  hbuf[0] = hold & 255;
                  hbuf[1] = hold >>> 8 & 255;
                  state2.check = crc32(state2.check, hbuf, 2, 0);
                }
                hold = 0;
                bits = 0;
                state2.mode = EXLEN;
              /* falls through */
              case EXLEN:
                if (state2.flags & 1024) {
                  while (bits < 16) {
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  state2.length = hold;
                  if (state2.head) {
                    state2.head.extra_len = hold;
                  }
                  if (state2.flags & 512) {
                    hbuf[0] = hold & 255;
                    hbuf[1] = hold >>> 8 & 255;
                    state2.check = crc32(state2.check, hbuf, 2, 0);
                  }
                  hold = 0;
                  bits = 0;
                } else if (state2.head) {
                  state2.head.extra = null;
                }
                state2.mode = EXTRA;
              /* falls through */
              case EXTRA:
                if (state2.flags & 1024) {
                  copy = state2.length;
                  if (copy > have) {
                    copy = have;
                  }
                  if (copy) {
                    if (state2.head) {
                      len = state2.head.extra_len - state2.length;
                      if (!state2.head.extra) {
                        state2.head.extra = new Array(state2.head.extra_len);
                      }
                      utils.arraySet(
                        state2.head.extra,
                        input,
                        next,
                        // extra field is limited to 65536 bytes
                        // - no need for additional size check
                        copy,
                        /*len + copy > state.head.extra_max - len ? state.head.extra_max : copy,*/
                        len
                      );
                    }
                    if (state2.flags & 512) {
                      state2.check = crc32(state2.check, input, copy, next);
                    }
                    have -= copy;
                    next += copy;
                    state2.length -= copy;
                  }
                  if (state2.length) {
                    break inf_leave;
                  }
                }
                state2.length = 0;
                state2.mode = NAME;
              /* falls through */
              case NAME:
                if (state2.flags & 2048) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  copy = 0;
                  do {
                    len = input[next + copy++];
                    if (state2.head && len && state2.length < 65536) {
                      state2.head.name += String.fromCharCode(len);
                    }
                  } while (len && copy < have);
                  if (state2.flags & 512) {
                    state2.check = crc32(state2.check, input, copy, next);
                  }
                  have -= copy;
                  next += copy;
                  if (len) {
                    break inf_leave;
                  }
                } else if (state2.head) {
                  state2.head.name = null;
                }
                state2.length = 0;
                state2.mode = COMMENT;
              /* falls through */
              case COMMENT:
                if (state2.flags & 4096) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  copy = 0;
                  do {
                    len = input[next + copy++];
                    if (state2.head && len && state2.length < 65536) {
                      state2.head.comment += String.fromCharCode(len);
                    }
                  } while (len && copy < have);
                  if (state2.flags & 512) {
                    state2.check = crc32(state2.check, input, copy, next);
                  }
                  have -= copy;
                  next += copy;
                  if (len) {
                    break inf_leave;
                  }
                } else if (state2.head) {
                  state2.head.comment = null;
                }
                state2.mode = HCRC;
              /* falls through */
              case HCRC:
                if (state2.flags & 512) {
                  while (bits < 16) {
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  if (hold !== (state2.check & 65535)) {
                    strm.msg = "header crc mismatch";
                    state2.mode = BAD;
                    break;
                  }
                  hold = 0;
                  bits = 0;
                }
                if (state2.head) {
                  state2.head.hcrc = state2.flags >> 9 & 1;
                  state2.head.done = true;
                }
                strm.adler = state2.check = 0;
                state2.mode = TYPE;
                break;
              case DICTID:
                while (bits < 32) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                strm.adler = state2.check = zswap32(hold);
                hold = 0;
                bits = 0;
                state2.mode = DICT;
              /* falls through */
              case DICT:
                if (state2.havedict === 0) {
                  strm.next_out = put;
                  strm.avail_out = left;
                  strm.next_in = next;
                  strm.avail_in = have;
                  state2.hold = hold;
                  state2.bits = bits;
                  return Z_NEED_DICT;
                }
                strm.adler = state2.check = 1;
                state2.mode = TYPE;
              /* falls through */
              case TYPE:
                if (flush === Z_BLOCK || flush === Z_TREES) {
                  break inf_leave;
                }
              /* falls through */
              case TYPEDO:
                if (state2.last) {
                  hold >>>= bits & 7;
                  bits -= bits & 7;
                  state2.mode = CHECK;
                  break;
                }
                while (bits < 3) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                state2.last = hold & 1;
                hold >>>= 1;
                bits -= 1;
                switch (hold & 3) {
                  case 0:
                    state2.mode = STORED;
                    break;
                  case 1:
                    fixedtables(state2);
                    state2.mode = LEN_;
                    if (flush === Z_TREES) {
                      hold >>>= 2;
                      bits -= 2;
                      break inf_leave;
                    }
                    break;
                  case 2:
                    state2.mode = TABLE;
                    break;
                  case 3:
                    strm.msg = "invalid block type";
                    state2.mode = BAD;
                }
                hold >>>= 2;
                bits -= 2;
                break;
              case STORED:
                hold >>>= bits & 7;
                bits -= bits & 7;
                while (bits < 32) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                if ((hold & 65535) !== (hold >>> 16 ^ 65535)) {
                  strm.msg = "invalid stored block lengths";
                  state2.mode = BAD;
                  break;
                }
                state2.length = hold & 65535;
                hold = 0;
                bits = 0;
                state2.mode = COPY_;
                if (flush === Z_TREES) {
                  break inf_leave;
                }
              /* falls through */
              case COPY_:
                state2.mode = COPY;
              /* falls through */
              case COPY:
                copy = state2.length;
                if (copy) {
                  if (copy > have) {
                    copy = have;
                  }
                  if (copy > left) {
                    copy = left;
                  }
                  if (copy === 0) {
                    break inf_leave;
                  }
                  utils.arraySet(output, input, next, copy, put);
                  have -= copy;
                  next += copy;
                  left -= copy;
                  put += copy;
                  state2.length -= copy;
                  break;
                }
                state2.mode = TYPE;
                break;
              case TABLE:
                while (bits < 14) {
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                state2.nlen = (hold & 31) + 257;
                hold >>>= 5;
                bits -= 5;
                state2.ndist = (hold & 31) + 1;
                hold >>>= 5;
                bits -= 5;
                state2.ncode = (hold & 15) + 4;
                hold >>>= 4;
                bits -= 4;
                if (state2.nlen > 286 || state2.ndist > 30) {
                  strm.msg = "too many length or distance symbols";
                  state2.mode = BAD;
                  break;
                }
                state2.have = 0;
                state2.mode = LENLENS;
              /* falls through */
              case LENLENS:
                while (state2.have < state2.ncode) {
                  while (bits < 3) {
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  state2.lens[order[state2.have++]] = hold & 7;
                  hold >>>= 3;
                  bits -= 3;
                }
                while (state2.have < 19) {
                  state2.lens[order[state2.have++]] = 0;
                }
                state2.lencode = state2.lendyn;
                state2.lenbits = 7;
                opts = { bits: state2.lenbits };
                ret = inflate_table(CODES, state2.lens, 0, 19, state2.lencode, 0, state2.work, opts);
                state2.lenbits = opts.bits;
                if (ret) {
                  strm.msg = "invalid code lengths set";
                  state2.mode = BAD;
                  break;
                }
                state2.have = 0;
                state2.mode = CODELENS;
              /* falls through */
              case CODELENS:
                while (state2.have < state2.nlen + state2.ndist) {
                  for (; ; ) {
                    here = state2.lencode[hold & (1 << state2.lenbits) - 1];
                    here_bits = here >>> 24;
                    here_op = here >>> 16 & 255;
                    here_val = here & 65535;
                    if (here_bits <= bits) {
                      break;
                    }
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  if (here_val < 16) {
                    hold >>>= here_bits;
                    bits -= here_bits;
                    state2.lens[state2.have++] = here_val;
                  } else {
                    if (here_val === 16) {
                      n = here_bits + 2;
                      while (bits < n) {
                        if (have === 0) {
                          break inf_leave;
                        }
                        have--;
                        hold += input[next++] << bits;
                        bits += 8;
                      }
                      hold >>>= here_bits;
                      bits -= here_bits;
                      if (state2.have === 0) {
                        strm.msg = "invalid bit length repeat";
                        state2.mode = BAD;
                        break;
                      }
                      len = state2.lens[state2.have - 1];
                      copy = 3 + (hold & 3);
                      hold >>>= 2;
                      bits -= 2;
                    } else if (here_val === 17) {
                      n = here_bits + 3;
                      while (bits < n) {
                        if (have === 0) {
                          break inf_leave;
                        }
                        have--;
                        hold += input[next++] << bits;
                        bits += 8;
                      }
                      hold >>>= here_bits;
                      bits -= here_bits;
                      len = 0;
                      copy = 3 + (hold & 7);
                      hold >>>= 3;
                      bits -= 3;
                    } else {
                      n = here_bits + 7;
                      while (bits < n) {
                        if (have === 0) {
                          break inf_leave;
                        }
                        have--;
                        hold += input[next++] << bits;
                        bits += 8;
                      }
                      hold >>>= here_bits;
                      bits -= here_bits;
                      len = 0;
                      copy = 11 + (hold & 127);
                      hold >>>= 7;
                      bits -= 7;
                    }
                    if (state2.have + copy > state2.nlen + state2.ndist) {
                      strm.msg = "invalid bit length repeat";
                      state2.mode = BAD;
                      break;
                    }
                    while (copy--) {
                      state2.lens[state2.have++] = len;
                    }
                  }
                }
                if (state2.mode === BAD) {
                  break;
                }
                if (state2.lens[256] === 0) {
                  strm.msg = "invalid code -- missing end-of-block";
                  state2.mode = BAD;
                  break;
                }
                state2.lenbits = 9;
                opts = { bits: state2.lenbits };
                ret = inflate_table(LENS, state2.lens, 0, state2.nlen, state2.lencode, 0, state2.work, opts);
                state2.lenbits = opts.bits;
                if (ret) {
                  strm.msg = "invalid literal/lengths set";
                  state2.mode = BAD;
                  break;
                }
                state2.distbits = 6;
                state2.distcode = state2.distdyn;
                opts = { bits: state2.distbits };
                ret = inflate_table(DISTS, state2.lens, state2.nlen, state2.ndist, state2.distcode, 0, state2.work, opts);
                state2.distbits = opts.bits;
                if (ret) {
                  strm.msg = "invalid distances set";
                  state2.mode = BAD;
                  break;
                }
                state2.mode = LEN_;
                if (flush === Z_TREES) {
                  break inf_leave;
                }
              /* falls through */
              case LEN_:
                state2.mode = LEN;
              /* falls through */
              case LEN:
                if (have >= 6 && left >= 258) {
                  strm.next_out = put;
                  strm.avail_out = left;
                  strm.next_in = next;
                  strm.avail_in = have;
                  state2.hold = hold;
                  state2.bits = bits;
                  inflate_fast(strm, _out);
                  put = strm.next_out;
                  output = strm.output;
                  left = strm.avail_out;
                  next = strm.next_in;
                  input = strm.input;
                  have = strm.avail_in;
                  hold = state2.hold;
                  bits = state2.bits;
                  if (state2.mode === TYPE) {
                    state2.back = -1;
                  }
                  break;
                }
                state2.back = 0;
                for (; ; ) {
                  here = state2.lencode[hold & (1 << state2.lenbits) - 1];
                  here_bits = here >>> 24;
                  here_op = here >>> 16 & 255;
                  here_val = here & 65535;
                  if (here_bits <= bits) {
                    break;
                  }
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                if (here_op && (here_op & 240) === 0) {
                  last_bits = here_bits;
                  last_op = here_op;
                  last_val = here_val;
                  for (; ; ) {
                    here = state2.lencode[last_val + ((hold & (1 << last_bits + last_op) - 1) >> last_bits)];
                    here_bits = here >>> 24;
                    here_op = here >>> 16 & 255;
                    here_val = here & 65535;
                    if (last_bits + here_bits <= bits) {
                      break;
                    }
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  hold >>>= last_bits;
                  bits -= last_bits;
                  state2.back += last_bits;
                }
                hold >>>= here_bits;
                bits -= here_bits;
                state2.back += here_bits;
                state2.length = here_val;
                if (here_op === 0) {
                  state2.mode = LIT;
                  break;
                }
                if (here_op & 32) {
                  state2.back = -1;
                  state2.mode = TYPE;
                  break;
                }
                if (here_op & 64) {
                  strm.msg = "invalid literal/length code";
                  state2.mode = BAD;
                  break;
                }
                state2.extra = here_op & 15;
                state2.mode = LENEXT;
              /* falls through */
              case LENEXT:
                if (state2.extra) {
                  n = state2.extra;
                  while (bits < n) {
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  state2.length += hold & (1 << state2.extra) - 1;
                  hold >>>= state2.extra;
                  bits -= state2.extra;
                  state2.back += state2.extra;
                }
                state2.was = state2.length;
                state2.mode = DIST;
              /* falls through */
              case DIST:
                for (; ; ) {
                  here = state2.distcode[hold & (1 << state2.distbits) - 1];
                  here_bits = here >>> 24;
                  here_op = here >>> 16 & 255;
                  here_val = here & 65535;
                  if (here_bits <= bits) {
                    break;
                  }
                  if (have === 0) {
                    break inf_leave;
                  }
                  have--;
                  hold += input[next++] << bits;
                  bits += 8;
                }
                if ((here_op & 240) === 0) {
                  last_bits = here_bits;
                  last_op = here_op;
                  last_val = here_val;
                  for (; ; ) {
                    here = state2.distcode[last_val + ((hold & (1 << last_bits + last_op) - 1) >> last_bits)];
                    here_bits = here >>> 24;
                    here_op = here >>> 16 & 255;
                    here_val = here & 65535;
                    if (last_bits + here_bits <= bits) {
                      break;
                    }
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  hold >>>= last_bits;
                  bits -= last_bits;
                  state2.back += last_bits;
                }
                hold >>>= here_bits;
                bits -= here_bits;
                state2.back += here_bits;
                if (here_op & 64) {
                  strm.msg = "invalid distance code";
                  state2.mode = BAD;
                  break;
                }
                state2.offset = here_val;
                state2.extra = here_op & 15;
                state2.mode = DISTEXT;
              /* falls through */
              case DISTEXT:
                if (state2.extra) {
                  n = state2.extra;
                  while (bits < n) {
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  state2.offset += hold & (1 << state2.extra) - 1;
                  hold >>>= state2.extra;
                  bits -= state2.extra;
                  state2.back += state2.extra;
                }
                if (state2.offset > state2.dmax) {
                  strm.msg = "invalid distance too far back";
                  state2.mode = BAD;
                  break;
                }
                state2.mode = MATCH;
              /* falls through */
              case MATCH:
                if (left === 0) {
                  break inf_leave;
                }
                copy = _out - left;
                if (state2.offset > copy) {
                  copy = state2.offset - copy;
                  if (copy > state2.whave) {
                    if (state2.sane) {
                      strm.msg = "invalid distance too far back";
                      state2.mode = BAD;
                      break;
                    }
                  }
                  if (copy > state2.wnext) {
                    copy -= state2.wnext;
                    from = state2.wsize - copy;
                  } else {
                    from = state2.wnext - copy;
                  }
                  if (copy > state2.length) {
                    copy = state2.length;
                  }
                  from_source = state2.window;
                } else {
                  from_source = output;
                  from = put - state2.offset;
                  copy = state2.length;
                }
                if (copy > left) {
                  copy = left;
                }
                left -= copy;
                state2.length -= copy;
                do {
                  output[put++] = from_source[from++];
                } while (--copy);
                if (state2.length === 0) {
                  state2.mode = LEN;
                }
                break;
              case LIT:
                if (left === 0) {
                  break inf_leave;
                }
                output[put++] = state2.length;
                left--;
                state2.mode = LEN;
                break;
              case CHECK:
                if (state2.wrap) {
                  while (bits < 32) {
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold |= input[next++] << bits;
                    bits += 8;
                  }
                  _out -= left;
                  strm.total_out += _out;
                  state2.total += _out;
                  if (_out) {
                    strm.adler = state2.check = /*UPDATE(state.check, put - _out, _out);*/
                    state2.flags ? crc32(state2.check, output, _out, put - _out) : adler32(state2.check, output, _out, put - _out);
                  }
                  _out = left;
                  if ((state2.flags ? hold : zswap32(hold)) !== state2.check) {
                    strm.msg = "incorrect data check";
                    state2.mode = BAD;
                    break;
                  }
                  hold = 0;
                  bits = 0;
                }
                state2.mode = LENGTH;
              /* falls through */
              case LENGTH:
                if (state2.wrap && state2.flags) {
                  while (bits < 32) {
                    if (have === 0) {
                      break inf_leave;
                    }
                    have--;
                    hold += input[next++] << bits;
                    bits += 8;
                  }
                  if (hold !== (state2.total & 4294967295)) {
                    strm.msg = "incorrect length check";
                    state2.mode = BAD;
                    break;
                  }
                  hold = 0;
                  bits = 0;
                }
                state2.mode = DONE;
              /* falls through */
              case DONE:
                ret = Z_STREAM_END;
                break inf_leave;
              case BAD:
                ret = Z_DATA_ERROR;
                break inf_leave;
              case MEM:
                return Z_MEM_ERROR;
              case SYNC:
              /* falls through */
              default:
                return Z_STREAM_ERROR;
            }
          }
        strm.next_out = put;
        strm.avail_out = left;
        strm.next_in = next;
        strm.avail_in = have;
        state2.hold = hold;
        state2.bits = bits;
        if (state2.wsize || _out !== strm.avail_out && state2.mode < BAD && (state2.mode < CHECK || flush !== Z_FINISH)) {
          if (updatewindow(strm, strm.output, strm.next_out, _out - strm.avail_out)) {
            state2.mode = MEM;
            return Z_MEM_ERROR;
          }
        }
        _in -= strm.avail_in;
        _out -= strm.avail_out;
        strm.total_in += _in;
        strm.total_out += _out;
        state2.total += _out;
        if (state2.wrap && _out) {
          strm.adler = state2.check = /*UPDATE(state.check, strm.next_out - _out, _out);*/
          state2.flags ? crc32(state2.check, output, _out, strm.next_out - _out) : adler32(state2.check, output, _out, strm.next_out - _out);
        }
        strm.data_type = state2.bits + (state2.last ? 64 : 0) + (state2.mode === TYPE ? 128 : 0) + (state2.mode === LEN_ || state2.mode === COPY_ ? 256 : 0);
        if ((_in === 0 && _out === 0 || flush === Z_FINISH) && ret === Z_OK) {
          ret = Z_BUF_ERROR;
        }
        return ret;
      }
      function inflateEnd(strm) {
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        var state2 = strm.state;
        if (state2.window) {
          state2.window = null;
        }
        strm.state = null;
        return Z_OK;
      }
      function inflateGetHeader(strm, head) {
        var state2;
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        state2 = strm.state;
        if ((state2.wrap & 2) === 0) {
          return Z_STREAM_ERROR;
        }
        state2.head = head;
        head.done = false;
        return Z_OK;
      }
      function inflateSetDictionary(strm, dictionary) {
        var dictLength = dictionary.length;
        var state2;
        var dictid;
        var ret;
        if (!strm || !strm.state) {
          return Z_STREAM_ERROR;
        }
        state2 = strm.state;
        if (state2.wrap !== 0 && state2.mode !== DICT) {
          return Z_STREAM_ERROR;
        }
        if (state2.mode === DICT) {
          dictid = 1;
          dictid = adler32(dictid, dictionary, dictLength, 0);
          if (dictid !== state2.check) {
            return Z_DATA_ERROR;
          }
        }
        ret = updatewindow(strm, dictionary, dictLength, dictLength);
        if (ret) {
          state2.mode = MEM;
          return Z_MEM_ERROR;
        }
        state2.havedict = 1;
        return Z_OK;
      }
      exports.inflateReset = inflateReset;
      exports.inflateReset2 = inflateReset2;
      exports.inflateResetKeep = inflateResetKeep;
      exports.inflateInit = inflateInit;
      exports.inflateInit2 = inflateInit2;
      exports.inflate = inflate3;
      exports.inflateEnd = inflateEnd;
      exports.inflateGetHeader = inflateGetHeader;
      exports.inflateSetDictionary = inflateSetDictionary;
      exports.inflateInfo = "pako inflate (from Nodeca project)";
    }
  });

  // node_modules/pako/lib/zlib/constants.js
  var require_constants = __commonJS({
    "node_modules/pako/lib/zlib/constants.js"(exports, module) {
      "use strict";
      module.exports = {
        /* Allowed flush values; see deflate() and inflate() below for details */
        Z_NO_FLUSH: 0,
        Z_PARTIAL_FLUSH: 1,
        Z_SYNC_FLUSH: 2,
        Z_FULL_FLUSH: 3,
        Z_FINISH: 4,
        Z_BLOCK: 5,
        Z_TREES: 6,
        /* Return codes for the compression/decompression functions. Negative values
        * are errors, positive values are used for special but normal events.
        */
        Z_OK: 0,
        Z_STREAM_END: 1,
        Z_NEED_DICT: 2,
        Z_ERRNO: -1,
        Z_STREAM_ERROR: -2,
        Z_DATA_ERROR: -3,
        //Z_MEM_ERROR:     -4,
        Z_BUF_ERROR: -5,
        //Z_VERSION_ERROR: -6,
        /* compression levels */
        Z_NO_COMPRESSION: 0,
        Z_BEST_SPEED: 1,
        Z_BEST_COMPRESSION: 9,
        Z_DEFAULT_COMPRESSION: -1,
        Z_FILTERED: 1,
        Z_HUFFMAN_ONLY: 2,
        Z_RLE: 3,
        Z_FIXED: 4,
        Z_DEFAULT_STRATEGY: 0,
        /* Possible values of the data_type field (though see inflate()) */
        Z_BINARY: 0,
        Z_TEXT: 1,
        //Z_ASCII:                1, // = Z_TEXT (deprecated)
        Z_UNKNOWN: 2,
        /* The deflate compression method */
        Z_DEFLATED: 8
        //Z_NULL:                 null // Use -1 or null inline, depending on var type
      };
    }
  });

  // node_modules/pako/lib/zlib/gzheader.js
  var require_gzheader = __commonJS({
    "node_modules/pako/lib/zlib/gzheader.js"(exports, module) {
      "use strict";
      function GZheader() {
        this.text = 0;
        this.time = 0;
        this.xflags = 0;
        this.os = 0;
        this.extra = null;
        this.extra_len = 0;
        this.name = "";
        this.comment = "";
        this.hcrc = 0;
        this.done = false;
      }
      module.exports = GZheader;
    }
  });

  // node_modules/pako/lib/inflate.js
  var require_inflate2 = __commonJS({
    "node_modules/pako/lib/inflate.js"(exports) {
      "use strict";
      var zlib_inflate = require_inflate();
      var utils = require_common();
      var strings = require_strings();
      var c = require_constants();
      var msg = require_messages();
      var ZStream = require_zstream();
      var GZheader = require_gzheader();
      var toString = Object.prototype.toString;
      function Inflate(options) {
        if (!(this instanceof Inflate)) return new Inflate(options);
        this.options = utils.assign({
          chunkSize: 16384,
          windowBits: 0,
          to: ""
        }, options || {});
        var opt = this.options;
        if (opt.raw && opt.windowBits >= 0 && opt.windowBits < 16) {
          opt.windowBits = -opt.windowBits;
          if (opt.windowBits === 0) {
            opt.windowBits = -15;
          }
        }
        if (opt.windowBits >= 0 && opt.windowBits < 16 && !(options && options.windowBits)) {
          opt.windowBits += 32;
        }
        if (opt.windowBits > 15 && opt.windowBits < 48) {
          if ((opt.windowBits & 15) === 0) {
            opt.windowBits |= 15;
          }
        }
        this.err = 0;
        this.msg = "";
        this.ended = false;
        this.chunks = [];
        this.strm = new ZStream();
        this.strm.avail_out = 0;
        var status = zlib_inflate.inflateInit2(
          this.strm,
          opt.windowBits
        );
        if (status !== c.Z_OK) {
          throw new Error(msg[status]);
        }
        this.header = new GZheader();
        zlib_inflate.inflateGetHeader(this.strm, this.header);
        if (opt.dictionary) {
          if (typeof opt.dictionary === "string") {
            opt.dictionary = strings.string2buf(opt.dictionary);
          } else if (toString.call(opt.dictionary) === "[object ArrayBuffer]") {
            opt.dictionary = new Uint8Array(opt.dictionary);
          }
          if (opt.raw) {
            status = zlib_inflate.inflateSetDictionary(this.strm, opt.dictionary);
            if (status !== c.Z_OK) {
              throw new Error(msg[status]);
            }
          }
        }
      }
      Inflate.prototype.push = function(data, mode) {
        var strm = this.strm;
        var chunkSize = this.options.chunkSize;
        var dictionary = this.options.dictionary;
        var status, _mode;
        var next_out_utf8, tail, utf8str;
        var allowBufError = false;
        if (this.ended) {
          return false;
        }
        _mode = mode === ~~mode ? mode : mode === true ? c.Z_FINISH : c.Z_NO_FLUSH;
        if (typeof data === "string") {
          strm.input = strings.binstring2buf(data);
        } else if (toString.call(data) === "[object ArrayBuffer]") {
          strm.input = new Uint8Array(data);
        } else {
          strm.input = data;
        }
        strm.next_in = 0;
        strm.avail_in = strm.input.length;
        do {
          if (strm.avail_out === 0) {
            strm.output = new utils.Buf8(chunkSize);
            strm.next_out = 0;
            strm.avail_out = chunkSize;
          }
          status = zlib_inflate.inflate(strm, c.Z_NO_FLUSH);
          if (status === c.Z_NEED_DICT && dictionary) {
            status = zlib_inflate.inflateSetDictionary(this.strm, dictionary);
          }
          if (status === c.Z_BUF_ERROR && allowBufError === true) {
            status = c.Z_OK;
            allowBufError = false;
          }
          if (status !== c.Z_STREAM_END && status !== c.Z_OK) {
            this.onEnd(status);
            this.ended = true;
            return false;
          }
          if (strm.next_out) {
            if (strm.avail_out === 0 || status === c.Z_STREAM_END || strm.avail_in === 0 && (_mode === c.Z_FINISH || _mode === c.Z_SYNC_FLUSH)) {
              if (this.options.to === "string") {
                next_out_utf8 = strings.utf8border(strm.output, strm.next_out);
                tail = strm.next_out - next_out_utf8;
                utf8str = strings.buf2string(strm.output, next_out_utf8);
                strm.next_out = tail;
                strm.avail_out = chunkSize - tail;
                if (tail) {
                  utils.arraySet(strm.output, strm.output, next_out_utf8, tail, 0);
                }
                this.onData(utf8str);
              } else {
                this.onData(utils.shrinkBuf(strm.output, strm.next_out));
              }
            }
          }
          if (strm.avail_in === 0 && strm.avail_out === 0) {
            allowBufError = true;
          }
        } while ((strm.avail_in > 0 || strm.avail_out === 0) && status !== c.Z_STREAM_END);
        if (status === c.Z_STREAM_END) {
          _mode = c.Z_FINISH;
        }
        if (_mode === c.Z_FINISH) {
          status = zlib_inflate.inflateEnd(this.strm);
          this.onEnd(status);
          this.ended = true;
          return status === c.Z_OK;
        }
        if (_mode === c.Z_SYNC_FLUSH) {
          this.onEnd(c.Z_OK);
          strm.avail_out = 0;
          return true;
        }
        return true;
      };
      Inflate.prototype.onData = function(chunk) {
        this.chunks.push(chunk);
      };
      Inflate.prototype.onEnd = function(status) {
        if (status === c.Z_OK) {
          if (this.options.to === "string") {
            this.result = this.chunks.join("");
          } else {
            this.result = utils.flattenChunks(this.chunks);
          }
        }
        this.chunks = [];
        this.err = status;
        this.msg = this.strm.msg;
      };
      function inflate3(input, options) {
        var inflator = new Inflate(options);
        inflator.push(input, true);
        if (inflator.err) {
          throw inflator.msg || msg[inflator.err];
        }
        return inflator.result;
      }
      function inflateRaw(input, options) {
        options = options || {};
        options.raw = true;
        return inflate3(input, options);
      }
      exports.Inflate = Inflate;
      exports.inflate = inflate3;
      exports.inflateRaw = inflateRaw;
      exports.ungzip = inflate3;
    }
  });

  // node_modules/pako/index.js
  var require_pako = __commonJS({
    "node_modules/pako/index.js"(exports, module) {
      "use strict";
      var assign = require_common().assign;
      var deflate = require_deflate2();
      var inflate3 = require_inflate2();
      var constants2 = require_constants();
      var pako2 = {};
      assign(pako2, deflate, inflate3, constants2);
      module.exports = pako2;
    }
  });

  // src/utils/musicSdk/kg/vendors/infSign.min.js
  var require_infSign_min = __commonJS({
    "src/utils/musicSdk/kg/vendors/infSign.min.js"(exports, module) {
      !(function(t, n) {
        "object" == typeof exports && "undefined" != typeof module ? module.exports = n() : "function" == typeof define && define.amd ? define(n) : (t = t || self, t.infSign = n());
      })(exports, function() {
        "use strict";
        function t(t2, n2, r2) {
          return n2 in t2 ? Object.defineProperty(t2, n2, { value: r2, enumerable: true, configurable: true, writable: true }) : t2[n2] = r2, t2;
        }
        function n(t2, n2) {
          var r2 = Object.keys(t2);
          if (Object.getOwnPropertySymbols) {
            var e2 = Object.getOwnPropertySymbols(t2);
            n2 && (e2 = e2.filter(function(n3) {
              return Object.getOwnPropertyDescriptor(t2, n3).enumerable;
            })), r2.push.apply(r2, e2);
          }
          return r2;
        }
        function r(r2) {
          for (var e2 = 1; e2 < arguments.length; e2++) {
            var o2 = null != arguments[e2] ? arguments[e2] : {};
            e2 % 2 ? n(o2, true).forEach(function(n2) {
              t(r2, n2, o2[n2]);
            }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(r2, Object.getOwnPropertyDescriptors(o2)) : n(o2).forEach(function(t2) {
              Object.defineProperty(r2, t2, Object.getOwnPropertyDescriptor(o2, t2));
            });
          }
          return r2;
        }
        function e(t2, n2) {
          return n2 = { exports: {} }, t2(n2, n2.exports), n2.exports;
        }
        function o(t2) {
          return !!t2.constructor && "function" == typeof t2.constructor.isBuffer && t2.constructor.isBuffer(t2);
        }
        function i(t2) {
          return "function" == typeof t2.readFloatLE && "function" == typeof t2.slice && o(t2.slice(0, 0));
        }
        function c() {
          var t2, n2 = arguments.length > 0 && void 0 !== arguments[0] ? arguments[0] : {}, e2 = arguments.length > 1 && void 0 !== arguments[1] ? arguments[1] : "", o2 = arguments.length > 2 && void 0 !== arguments[2] ? arguments[2] : {}, i2 = false, c2 = false, a2 = "json", l2 = r({}, n2), u2 = s.isInClient();
          "function" == typeof o2 ? t2 = o2 : (t2 = o2.callback, i2 = o2.useH5 || false, a2 = o2.postType || "json", c2 = o2.isCDN || false), e2 && ("[object Object]" != Object.prototype.toString.call(e2) ? u2 = false : "urlencoded" == a2 && (u2 = false));
          var f2 = function() {
            var n3 = (/* @__PURE__ */ new Date()).getTime(), i3 = [], s2 = [], u3 = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt", f3 = { srcappid: "2919", clientver: "20000", clienttime: n3, mid: n3, uuid: n3, dfid: "-" };
            c2 && (delete f3.clienttime, delete f3.mid, delete f3.uuid, delete f3.dfid), l2 = r({}, f3, {}, l2);
            for (var g2 in l2) i3.push(g2);
            if (i3.sort(), i3.forEach(function(t3) {
              s2.push(t3 + "=" + l2[t3]);
            }), e2) if ("[object Object]" == Object.prototype.toString.call(e2)) if ("json" == a2) s2.push(JSON.stringify(e2));
            else {
              var b = [];
              for (var g2 in e2) b.push(g2 + "=" + e2[g2]);
              s2.push(b.join("&"));
            }
            else s2.push(e2);
            s2.unshift(u3), s2.push(u3), l2.signature = d(s2.join("")), o2.log && (console.log("H5\u7B7E\u540D\u524D\u53C2\u6570", s2), console.log("H5\u7B7E\u540D\u540E\u8FD4\u56DE", l2)), e2 ? t2 && t2(l2, "[object Object]" == Object.prototype.toString.call(e2) && "json" == a2 ? JSON.stringify(e2) : e2) : t2 && t2(l2);
          };
          if (u2 && !i2) {
            var g = false;
            s.mobileCall(764, { get: l2, post: e2 }, function(n3) {
              return !g && (g = true, n3 && n3.status ? (delete n3.status, o2.log && (console.log("\u5BA2\u6237\u7AEF\u7B7E\u540D\u524D\u53C2\u6570", { get: l2, post: e2 }), console.log("\u5BA2\u6237\u7AEF\u7B7E\u540D\u540E\u8FD4\u56DE", r({}, l2, {}, n3))), l2 = r({}, l2, {}, n3), e2 ? t2 && t2(l2, "[object Object]" == Object.prototype.toString.call(e2) && "json" == a2 ? JSON.stringify(e2) : e2) : t2 && t2(l2), false) : (u2 = false, void f2()));
            });
          } else u2 = false, f2();
        }
        "undefined" != typeof globalThis ? globalThis : "undefined" != typeof window ? window : "undefined" != typeof global ? global : "undefined" != typeof self && self;
        var s = e(function(t2, n2) {
          !(function(n3, r2) {
            t2.exports = (function() {
              var t3 = { str2Json: function(t4) {
                var n4 = {};
                if ("[object String]" === Object.prototype.toString.call(t4)) try {
                  n4 = JSON.parse(t4);
                } catch (t5) {
                  n4 = {};
                }
                return n4;
              }, json2Str: function(t4) {
                var n4 = t4;
                if ("string" != typeof t4) try {
                  n4 = JSON.stringify(t4);
                } catch (t5) {
                  n4 = "";
                }
                return n4;
              }, _extend: function(t4, n4) {
                if (n4) for (var r3 in t4) n4.hasOwnProperty(r3) || (n4[r3] = t4[r3]);
                return n4;
              }, formatURL: { browser: "", url: "" }, formatSong: { filename: "", filesize: "", hash: "", bitrate: "", extname: "", duration: "", mvhash: "", m4afilesize: "", "320hash": "", "320filesize": "", sqhash: "", sqfilesize: 0, feetype: 0, isfirst: 0 }, formatMV: { filename: "", singername: "", hash: "", imgurl: "" }, formatShare: { shareName: "", topicName: "", hash: "", listID: "", type: "", suid: "", slid: "", imgUrl: "", filename: "", duration: "", shareData: { linkUrl: "", picUrl: "", content: "", title: "" } }, cbNum: 0, isIOS: false, isKugouAndroid: false, isAndroid: true, loadUrl: function(t4) {
                var n4 = document.createElement("iframe");
                n4.setAttribute("src", t4), n4.setAttribute("style", "display:none;"), n4.setAttribute("height", "0px"), n4.setAttribute("width", "0px"), n4.setAttribute("frameborder", "0"), document.body.appendChild(n4), n4.parentNode.removeChild(n4), n4 = null;
              }, callCmd: function(n4) {
                var r3 = t3;
                if (r3.isKugouAndroid) {
                  var e2 = {}, o2 = "";
                  if (n4.cmd && (e2.cmd = n4.cmd), n4.jsonStr && (e2.jsonStr = n4.jsonStr), n4.callback && (o2 = "kgandroidmobilecall" + ++r3.cbNum + Math.random().toString().substr(2, 9), e2.callback = o2, window[o2] = function(t4, e3) {
                    void 0 !== t4 && ("[object String]" === Object.prototype.toString.call(t4) ? (t4 = "#" === e3 ? decodeURIComponent(t4) : decodeURIComponent(decodeURIComponent(t4)), n4.callback(r3.str2Json(t4))) : n4.callback(t4));
                  }), n4.AndroidCallback) {
                    var i2 = r3.str2Json(n4.jsonStr);
                    i2.AndroidCallback = o2, n4.jsonStr = r3.json2Str(i2), n4.jsonStr && (e2.jsonStr = n4.jsonStr);
                  }
                  var c2 = encodeURIComponent(JSON.stringify(e2)), s2 = "kugoujsbridge://start.kugou_jsbridge/?".concat(c2);
                  r3.loadUrl(s2);
                } else if (r3.isAndroid) {
                  var a2 = "", l2 = "";
                  if (n4.jsonStr) {
                    if (n4.callback && "" !== n4.callback && true === n4.AndroidCallback) {
                      l2 = "kgmobilecall" + ++r3.cbNum + Math.random().toString().substr(2, 9), window[l2] = function(t4, e3) {
                        void 0 !== t4 && ("[object String]" === Object.prototype.toString.call(t4) ? (t4 = "#" === e3 ? decodeURIComponent(t4) : decodeURIComponent(decodeURIComponent(t4)), n4.callback(r3.str2Json(t4))) : n4.callback(t4));
                      };
                      var u2 = r3.str2Json(n4.jsonStr);
                      u2.AndroidCallback = l2, n4.jsonStr = r3.json2Str(u2);
                    }
                    try {
                      a2 = external.superCall(n4.cmd, n4.jsonStr);
                    } catch (t4) {
                    }
                  } else try {
                    a2 = external.superCall(n4.cmd);
                  } catch (t4) {
                  }
                  n4.callback && "" !== n4.callback && "AndroidCallback" != a2 && (a2 = r3.str2Json(a2), n4.callback(a2));
                } else {
                  var f2 = "", d2 = "";
                  n4.callback && (d2 = "kgmobilecall" + ++r3.cbNum + Math.random().toString().substr(2, 9), window[d2] = function(t4) {
                    void 0 !== t4 && n4.callback && ("[object String]" === Object.prototype.toString.call(t4) ? n4.callback(r3.str2Json(t4)) : n4.callback(t4));
                  }), d2 && "" != d2 && n4.jsonStr && (f2 = 'kugouurl://start.music/?{"cmd":' + n4.cmd + ', "jsonStr":' + n4.jsonStr + ', "callback":"' + d2 + '"}'), d2 && "" != d2 && !n4.jsonStr && (f2 = 'kugouurl://start.music/?{"cmd":' + n4.cmd + ', "callback":"' + d2 + '"}'), "" == d2 && n4.jsonStr && (f2 = 'kugouurl://start.music/?{"cmd":' + n4.cmd + ', "jsonStr":' + n4.jsonStr + "}"), "" != d2 || n4.jsonStr || (f2 = 'kugouurl://start.music/?{"cmd":' + n4.cmd + "}"), r3.loadUrl(f2);
                }
              }, formartData: function(n4, r3) {
                n4 && 123 == n4 && r3 && (r3 = t3._extend(t3.formatURL, r3)), n4 && 123 == n4 && r3 && (r3 = t3._extend(t3.formatURL, r3));
              } };
              return { isIOS: t3.isIOS, isKugouAndroid: t3.isKugouAndroid, isAndroid: t3.isAndroid, isInClient: function() {
                return !(!t3.isAndroid && !t3.isKugouAndroid && !t3.isIOS);
              }, mobileCall: function(n4, r3, e2) {
                var o2 = "";
                if (r3 && (o2 = t3.json2Str(r3)), !n4) return console.error("\u8BF7\u8F93\u5165\u547D\u4EE4\u53F7\uFF01"), false;
                var i2 = {};
                n4 && (i2.cmd = n4), "" != o2 && (i2.jsonStr = o2), e2 && (i2.callback = e2), n4 && 186 == n4 && e2 && (i2.AndroidCallback = true), t3.callCmd(i2);
              }, KgWebMobileCall: function(t4, n4) {
                if (t4) try {
                  var r3 = t4.split(".");
                  r3.reduce(function(e2, o2) {
                    if (e2[o2]) {
                      if (o2 === r3[r3.length - 1]) {
                        var i2 = e2[o2];
                        return "function" == typeof i2 ? (e2[o2] = function(t5) {
                          i2 && i2(t5), n4 && n4(t5);
                        }, e2[o2]) : (console.error("\u8BF7\u68C0\u67E5\uFF0C\u5F53\u524D\u73AF\u5883\u53D8\u91CF\u5DF2\u6CE8\u518C\u4E86\u5BF9\u8C61\uFF1A" + t4 + "\uFF0C\u4E14\u8BE5\u5BF9\u8C61\u4E0D\u662F\u65B9\u6CD5"), null);
                      }
                      return e2[o2];
                    }
                    return o2 === r3[r3.length - 1] ? e2[o2] = function(t5) {
                      n4 && n4(t5);
                    } : e2[o2] = new Object(), e2[o2];
                  }, window);
                } catch (t5) {
                }
              } };
            })();
          })();
        }), a = e(function(t2) {
          !(function() {
            var n2 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", r2 = { rotl: function(t3, n3) {
              return t3 << n3 | t3 >>> 32 - n3;
            }, rotr: function(t3, n3) {
              return t3 << 32 - n3 | t3 >>> n3;
            }, endian: function(t3) {
              if (t3.constructor == Number) return 16711935 & r2.rotl(t3, 8) | 4278255360 & r2.rotl(t3, 24);
              for (var n3 = 0; n3 < t3.length; n3++) t3[n3] = r2.endian(t3[n3]);
              return t3;
            }, randomBytes: function(t3) {
              for (var n3 = []; t3 > 0; t3--) n3.push(Math.floor(256 * Math.random()));
              return n3;
            }, bytesToWords: function(t3) {
              for (var n3 = [], r3 = 0, e2 = 0; r3 < t3.length; r3++, e2 += 8) n3[e2 >>> 5] |= t3[r3] << 24 - e2 % 32;
              return n3;
            }, wordsToBytes: function(t3) {
              for (var n3 = [], r3 = 0; r3 < 32 * t3.length; r3 += 8) n3.push(t3[r3 >>> 5] >>> 24 - r3 % 32 & 255);
              return n3;
            }, bytesToHex: function(t3) {
              for (var n3 = [], r3 = 0; r3 < t3.length; r3++) n3.push((t3[r3] >>> 4).toString(16)), n3.push((15 & t3[r3]).toString(16));
              return n3.join("");
            }, hexToBytes: function(t3) {
              for (var n3 = [], r3 = 0; r3 < t3.length; r3 += 2) n3.push(parseInt(t3.substr(r3, 2), 16));
              return n3;
            }, bytesToBase64: function(t3) {
              for (var r3 = [], e2 = 0; e2 < t3.length; e2 += 3) for (var o2 = t3[e2] << 16 | t3[e2 + 1] << 8 | t3[e2 + 2], i2 = 0; i2 < 4; i2++) 8 * e2 + 6 * i2 <= 8 * t3.length ? r3.push(n2.charAt(o2 >>> 6 * (3 - i2) & 63)) : r3.push("=");
              return r3.join("");
            }, base64ToBytes: function(t3) {
              t3 = t3.replace(/[^A-Z0-9+\/]/gi, "");
              for (var r3 = [], e2 = 0, o2 = 0; e2 < t3.length; o2 = ++e2 % 4) 0 != o2 && r3.push((n2.indexOf(t3.charAt(e2 - 1)) & Math.pow(2, -2 * o2 + 8) - 1) << 2 * o2 | n2.indexOf(t3.charAt(e2)) >>> 6 - 2 * o2);
              return r3;
            } };
            t2.exports = r2;
          })();
        }), l = { utf8: { stringToBytes: function(t2) {
          return l.bin.stringToBytes(unescape(encodeURIComponent(t2)));
        }, bytesToString: function(t2) {
          return decodeURIComponent(escape(l.bin.bytesToString(t2)));
        } }, bin: { stringToBytes: function(t2) {
          for (var n2 = [], r2 = 0; r2 < t2.length; r2++) n2.push(255 & t2.charCodeAt(r2));
          return n2;
        }, bytesToString: function(t2) {
          for (var n2 = [], r2 = 0; r2 < t2.length; r2++) n2.push(String.fromCharCode(t2[r2]));
          return n2.join("");
        } } }, u = l, f = function(t2) {
          return null != t2 && (o(t2) || i(t2) || !!t2._isBuffer);
        }, d = e(function(t2) {
          !(function() {
            var n2 = a, r2 = u.utf8, e2 = f, o2 = u.bin, i2 = function(t3, c2) {
              t3.constructor == String ? t3 = c2 && "binary" === c2.encoding ? o2.stringToBytes(t3) : r2.stringToBytes(t3) : e2(t3) ? t3 = Array.prototype.slice.call(t3, 0) : Array.isArray(t3) || (t3 = t3.toString());
              for (var s2 = n2.bytesToWords(t3), a2 = 8 * t3.length, l2 = 1732584193, u2 = -271733879, f2 = -1732584194, d2 = 271733878, g = 0; g < s2.length; g++) s2[g] = 16711935 & (s2[g] << 8 | s2[g] >>> 24) | 4278255360 & (s2[g] << 24 | s2[g] >>> 8);
              s2[a2 >>> 5] |= 128 << a2 % 32, s2[14 + (a2 + 64 >>> 9 << 4)] = a2;
              for (var b = i2._ff, p = i2._gg, h = i2._hh, m = i2._ii, g = 0; g < s2.length; g += 16) {
                var y = l2, j = u2, S = f2, v = d2;
                u2 = m(u2 = m(u2 = m(u2 = m(u2 = h(u2 = h(u2 = h(u2 = h(u2 = p(u2 = p(u2 = p(u2 = p(u2 = b(u2 = b(u2 = b(u2 = b(u2, f2 = b(f2, d2 = b(d2, l2 = b(l2, u2, f2, d2, s2[g + 0], 7, -680876936), u2, f2, s2[g + 1], 12, -389564586), l2, u2, s2[g + 2], 17, 606105819), d2, l2, s2[g + 3], 22, -1044525330), f2 = b(f2, d2 = b(d2, l2 = b(l2, u2, f2, d2, s2[g + 4], 7, -176418897), u2, f2, s2[g + 5], 12, 1200080426), l2, u2, s2[g + 6], 17, -1473231341), d2, l2, s2[g + 7], 22, -45705983), f2 = b(f2, d2 = b(d2, l2 = b(l2, u2, f2, d2, s2[g + 8], 7, 1770035416), u2, f2, s2[g + 9], 12, -1958414417), l2, u2, s2[g + 10], 17, -42063), d2, l2, s2[g + 11], 22, -1990404162), f2 = b(f2, d2 = b(d2, l2 = b(l2, u2, f2, d2, s2[g + 12], 7, 1804603682), u2, f2, s2[g + 13], 12, -40341101), l2, u2, s2[g + 14], 17, -1502002290), d2, l2, s2[g + 15], 22, 1236535329), f2 = p(f2, d2 = p(d2, l2 = p(l2, u2, f2, d2, s2[g + 1], 5, -165796510), u2, f2, s2[g + 6], 9, -1069501632), l2, u2, s2[g + 11], 14, 643717713), d2, l2, s2[g + 0], 20, -373897302), f2 = p(f2, d2 = p(d2, l2 = p(l2, u2, f2, d2, s2[g + 5], 5, -701558691), u2, f2, s2[g + 10], 9, 38016083), l2, u2, s2[g + 15], 14, -660478335), d2, l2, s2[g + 4], 20, -405537848), f2 = p(f2, d2 = p(d2, l2 = p(l2, u2, f2, d2, s2[g + 9], 5, 568446438), u2, f2, s2[g + 14], 9, -1019803690), l2, u2, s2[g + 3], 14, -187363961), d2, l2, s2[g + 8], 20, 1163531501), f2 = p(f2, d2 = p(d2, l2 = p(l2, u2, f2, d2, s2[g + 13], 5, -1444681467), u2, f2, s2[g + 2], 9, -51403784), l2, u2, s2[g + 7], 14, 1735328473), d2, l2, s2[g + 12], 20, -1926607734), f2 = h(f2, d2 = h(d2, l2 = h(l2, u2, f2, d2, s2[g + 5], 4, -378558), u2, f2, s2[g + 8], 11, -2022574463), l2, u2, s2[g + 11], 16, 1839030562), d2, l2, s2[g + 14], 23, -35309556), f2 = h(f2, d2 = h(d2, l2 = h(l2, u2, f2, d2, s2[g + 1], 4, -1530992060), u2, f2, s2[g + 4], 11, 1272893353), l2, u2, s2[g + 7], 16, -155497632), d2, l2, s2[g + 10], 23, -1094730640), f2 = h(f2, d2 = h(d2, l2 = h(l2, u2, f2, d2, s2[g + 13], 4, 681279174), u2, f2, s2[g + 0], 11, -358537222), l2, u2, s2[g + 3], 16, -722521979), d2, l2, s2[g + 6], 23, 76029189), f2 = h(f2, d2 = h(d2, l2 = h(l2, u2, f2, d2, s2[g + 9], 4, -640364487), u2, f2, s2[g + 12], 11, -421815835), l2, u2, s2[g + 15], 16, 530742520), d2, l2, s2[g + 2], 23, -995338651), f2 = m(f2, d2 = m(d2, l2 = m(l2, u2, f2, d2, s2[g + 0], 6, -198630844), u2, f2, s2[g + 7], 10, 1126891415), l2, u2, s2[g + 14], 15, -1416354905), d2, l2, s2[g + 5], 21, -57434055), f2 = m(f2, d2 = m(d2, l2 = m(l2, u2, f2, d2, s2[g + 12], 6, 1700485571), u2, f2, s2[g + 3], 10, -1894986606), l2, u2, s2[g + 10], 15, -1051523), d2, l2, s2[g + 1], 21, -2054922799), f2 = m(f2, d2 = m(d2, l2 = m(l2, u2, f2, d2, s2[g + 8], 6, 1873313359), u2, f2, s2[g + 15], 10, -30611744), l2, u2, s2[g + 6], 15, -1560198380), d2, l2, s2[g + 13], 21, 1309151649), f2 = m(f2, d2 = m(d2, l2 = m(l2, u2, f2, d2, s2[g + 4], 6, -145523070), u2, f2, s2[g + 11], 10, -1120210379), l2, u2, s2[g + 2], 15, 718787259), d2, l2, s2[g + 9], 21, -343485551), l2 = l2 + y >>> 0, u2 = u2 + j >>> 0, f2 = f2 + S >>> 0, d2 = d2 + v >>> 0;
              }
              return n2.endian([l2, u2, f2, d2]);
            };
            i2._ff = function(t3, n3, r3, e3, o3, i3, c2) {
              var s2 = t3 + (n3 & r3 | ~n3 & e3) + (o3 >>> 0) + c2;
              return (s2 << i3 | s2 >>> 32 - i3) + n3;
            }, i2._gg = function(t3, n3, r3, e3, o3, i3, c2) {
              var s2 = t3 + (n3 & e3 | r3 & ~e3) + (o3 >>> 0) + c2;
              return (s2 << i3 | s2 >>> 32 - i3) + n3;
            }, i2._hh = function(t3, n3, r3, e3, o3, i3, c2) {
              var s2 = t3 + (n3 ^ r3 ^ e3) + (o3 >>> 0) + c2;
              return (s2 << i3 | s2 >>> 32 - i3) + n3;
            }, i2._ii = function(t3, n3, r3, e3, o3, i3, c2) {
              var s2 = t3 + (r3 ^ (n3 | ~e3)) + (o3 >>> 0) + c2;
              return (s2 << i3 | s2 >>> 32 - i3) + n3;
            }, i2._blocksize = 16, i2._digestsize = 16, t2.exports = function(t3, r3) {
              if (void 0 === t3 || null === t3) throw new Error("Illegal argument " + t3);
              var e3 = n2.wordsToBytes(i2(t3, r3));
              return r3 && r3.asBytes ? e3 : r3 && r3.asString ? o2.bytesToString(e3) : n2.bytesToHex(e3);
            };
          })();
        });
        return c;
      });
    }
  });

  // node_modules/base64-js/index.js
  var require_base64_js = __commonJS({
    "node_modules/base64-js/index.js"(exports) {
      "use strict";
      exports.byteLength = byteLength;
      exports.toByteArray = toByteArray;
      exports.fromByteArray = fromByteArray;
      var lookup = [];
      var revLookup = [];
      var Arr = typeof Uint8Array !== "undefined" ? Uint8Array : Array;
      var code = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
      for (i = 0, len = code.length; i < len; ++i) {
        lookup[i] = code[i];
        revLookup[code.charCodeAt(i)] = i;
      }
      var i;
      var len;
      revLookup["-".charCodeAt(0)] = 62;
      revLookup["_".charCodeAt(0)] = 63;
      function getLens(b64) {
        var len2 = b64.length;
        if (len2 % 4 > 0) {
          throw new Error("Invalid string. Length must be a multiple of 4");
        }
        var validLen = b64.indexOf("=");
        if (validLen === -1) validLen = len2;
        var placeHoldersLen = validLen === len2 ? 0 : 4 - validLen % 4;
        return [validLen, placeHoldersLen];
      }
      function byteLength(b64) {
        var lens = getLens(b64);
        var validLen = lens[0];
        var placeHoldersLen = lens[1];
        return (validLen + placeHoldersLen) * 3 / 4 - placeHoldersLen;
      }
      function _byteLength(b64, validLen, placeHoldersLen) {
        return (validLen + placeHoldersLen) * 3 / 4 - placeHoldersLen;
      }
      function toByteArray(b64) {
        var tmp;
        var lens = getLens(b64);
        var validLen = lens[0];
        var placeHoldersLen = lens[1];
        var arr = new Arr(_byteLength(b64, validLen, placeHoldersLen));
        var curByte = 0;
        var len2 = placeHoldersLen > 0 ? validLen - 4 : validLen;
        var i2;
        for (i2 = 0; i2 < len2; i2 += 4) {
          tmp = revLookup[b64.charCodeAt(i2)] << 18 | revLookup[b64.charCodeAt(i2 + 1)] << 12 | revLookup[b64.charCodeAt(i2 + 2)] << 6 | revLookup[b64.charCodeAt(i2 + 3)];
          arr[curByte++] = tmp >> 16 & 255;
          arr[curByte++] = tmp >> 8 & 255;
          arr[curByte++] = tmp & 255;
        }
        if (placeHoldersLen === 2) {
          tmp = revLookup[b64.charCodeAt(i2)] << 2 | revLookup[b64.charCodeAt(i2 + 1)] >> 4;
          arr[curByte++] = tmp & 255;
        }
        if (placeHoldersLen === 1) {
          tmp = revLookup[b64.charCodeAt(i2)] << 10 | revLookup[b64.charCodeAt(i2 + 1)] << 4 | revLookup[b64.charCodeAt(i2 + 2)] >> 2;
          arr[curByte++] = tmp >> 8 & 255;
          arr[curByte++] = tmp & 255;
        }
        return arr;
      }
      function tripletToBase64(num) {
        return lookup[num >> 18 & 63] + lookup[num >> 12 & 63] + lookup[num >> 6 & 63] + lookup[num & 63];
      }
      function encodeChunk(uint8, start, end) {
        var tmp;
        var output = [];
        for (var i2 = start; i2 < end; i2 += 3) {
          tmp = (uint8[i2] << 16 & 16711680) + (uint8[i2 + 1] << 8 & 65280) + (uint8[i2 + 2] & 255);
          output.push(tripletToBase64(tmp));
        }
        return output.join("");
      }
      function fromByteArray(uint8) {
        var tmp;
        var len2 = uint8.length;
        var extraBytes = len2 % 3;
        var parts = [];
        var maxChunkLength = 16383;
        for (var i2 = 0, len22 = len2 - extraBytes; i2 < len22; i2 += maxChunkLength) {
          parts.push(encodeChunk(uint8, i2, i2 + maxChunkLength > len22 ? len22 : i2 + maxChunkLength));
        }
        if (extraBytes === 1) {
          tmp = uint8[len2 - 1];
          parts.push(
            lookup[tmp >> 2] + lookup[tmp << 4 & 63] + "=="
          );
        } else if (extraBytes === 2) {
          tmp = (uint8[len2 - 2] << 8) + uint8[len2 - 1];
          parts.push(
            lookup[tmp >> 10] + lookup[tmp >> 4 & 63] + lookup[tmp << 2 & 63] + "="
          );
        }
        return parts.join("");
      }
    }
  });

  // node_modules/ieee754/index.js
  var require_ieee754 = __commonJS({
    "node_modules/ieee754/index.js"(exports) {
      exports.read = function(buffer, offset, isLE, mLen, nBytes) {
        var e, m;
        var eLen = nBytes * 8 - mLen - 1;
        var eMax = (1 << eLen) - 1;
        var eBias = eMax >> 1;
        var nBits = -7;
        var i = isLE ? nBytes - 1 : 0;
        var d = isLE ? -1 : 1;
        var s = buffer[offset + i];
        i += d;
        e = s & (1 << -nBits) - 1;
        s >>= -nBits;
        nBits += eLen;
        for (; nBits > 0; e = e * 256 + buffer[offset + i], i += d, nBits -= 8) {
        }
        m = e & (1 << -nBits) - 1;
        e >>= -nBits;
        nBits += mLen;
        for (; nBits > 0; m = m * 256 + buffer[offset + i], i += d, nBits -= 8) {
        }
        if (e === 0) {
          e = 1 - eBias;
        } else if (e === eMax) {
          return m ? NaN : (s ? -1 : 1) * Infinity;
        } else {
          m = m + Math.pow(2, mLen);
          e = e - eBias;
        }
        return (s ? -1 : 1) * m * Math.pow(2, e - mLen);
      };
      exports.write = function(buffer, value, offset, isLE, mLen, nBytes) {
        var e, m, c;
        var eLen = nBytes * 8 - mLen - 1;
        var eMax = (1 << eLen) - 1;
        var eBias = eMax >> 1;
        var rt = mLen === 23 ? Math.pow(2, -24) - Math.pow(2, -77) : 0;
        var i = isLE ? 0 : nBytes - 1;
        var d = isLE ? 1 : -1;
        var s = value < 0 || value === 0 && 1 / value < 0 ? 1 : 0;
        value = Math.abs(value);
        if (isNaN(value) || value === Infinity) {
          m = isNaN(value) ? 1 : 0;
          e = eMax;
        } else {
          e = Math.floor(Math.log(value) / Math.LN2);
          if (value * (c = Math.pow(2, -e)) < 1) {
            e--;
            c *= 2;
          }
          if (e + eBias >= 1) {
            value += rt / c;
          } else {
            value += rt * Math.pow(2, 1 - eBias);
          }
          if (value * c >= 2) {
            e++;
            c /= 2;
          }
          if (e + eBias >= eMax) {
            m = 0;
            e = eMax;
          } else if (e + eBias >= 1) {
            m = (value * c - 1) * Math.pow(2, mLen);
            e = e + eBias;
          } else {
            m = value * Math.pow(2, eBias - 1) * Math.pow(2, mLen);
            e = 0;
          }
        }
        for (; mLen >= 8; buffer[offset + i] = m & 255, i += d, m /= 256, mLen -= 8) {
        }
        e = e << mLen | m;
        eLen += mLen;
        for (; eLen > 0; buffer[offset + i] = e & 255, i += d, e /= 256, eLen -= 8) {
        }
        buffer[offset + i - d] |= s * 128;
      };
    }
  });

  // node_modules/buffer/index.js
  var require_buffer = __commonJS({
    "node_modules/buffer/index.js"(exports) {
      "use strict";
      var base64 = require_base64_js();
      var ieee754 = require_ieee754();
      var customInspectSymbol = typeof Symbol === "function" && typeof Symbol["for"] === "function" ? Symbol["for"]("nodejs.util.inspect.custom") : null;
      exports.Buffer = Buffer3;
      exports.SlowBuffer = SlowBuffer;
      exports.INSPECT_MAX_BYTES = 50;
      var K_MAX_LENGTH = 2147483647;
      exports.kMaxLength = K_MAX_LENGTH;
      Buffer3.TYPED_ARRAY_SUPPORT = typedArraySupport();
      if (!Buffer3.TYPED_ARRAY_SUPPORT && typeof console !== "undefined" && typeof console.error === "function") {
        console.error(
          "This browser lacks typed array (Uint8Array) support which is required by `buffer` v5.x. Use `buffer` v4.x if you require old browser support."
        );
      }
      function typedArraySupport() {
        try {
          const arr = new Uint8Array(1);
          const proto = { foo: function() {
            return 42;
          } };
          Object.setPrototypeOf(proto, Uint8Array.prototype);
          Object.setPrototypeOf(arr, proto);
          return arr.foo() === 42;
        } catch (e) {
          return false;
        }
      }
      Object.defineProperty(Buffer3.prototype, "parent", {
        enumerable: true,
        get: function() {
          if (!Buffer3.isBuffer(this)) return void 0;
          return this.buffer;
        }
      });
      Object.defineProperty(Buffer3.prototype, "offset", {
        enumerable: true,
        get: function() {
          if (!Buffer3.isBuffer(this)) return void 0;
          return this.byteOffset;
        }
      });
      function createBuffer(length) {
        if (length > K_MAX_LENGTH) {
          throw new RangeError('The value "' + length + '" is invalid for option "size"');
        }
        const buf = new Uint8Array(length);
        Object.setPrototypeOf(buf, Buffer3.prototype);
        return buf;
      }
      function Buffer3(arg, encodingOrOffset, length) {
        if (typeof arg === "number") {
          if (typeof encodingOrOffset === "string") {
            throw new TypeError(
              'The "string" argument must be of type string. Received type number'
            );
          }
          return allocUnsafe(arg);
        }
        return from(arg, encodingOrOffset, length);
      }
      Buffer3.poolSize = 8192;
      function from(value, encodingOrOffset, length) {
        if (typeof value === "string") {
          return fromString(value, encodingOrOffset);
        }
        if (ArrayBuffer.isView(value)) {
          return fromArrayView(value);
        }
        if (value == null) {
          throw new TypeError(
            "The first argument must be one of type string, Buffer, ArrayBuffer, Array, or Array-like Object. Received type " + typeof value
          );
        }
        if (isInstance(value, ArrayBuffer) || value && isInstance(value.buffer, ArrayBuffer)) {
          return fromArrayBuffer(value, encodingOrOffset, length);
        }
        if (typeof SharedArrayBuffer !== "undefined" && (isInstance(value, SharedArrayBuffer) || value && isInstance(value.buffer, SharedArrayBuffer))) {
          return fromArrayBuffer(value, encodingOrOffset, length);
        }
        if (typeof value === "number") {
          throw new TypeError(
            'The "value" argument must not be of type number. Received type number'
          );
        }
        const valueOf = value.valueOf && value.valueOf();
        if (valueOf != null && valueOf !== value) {
          return Buffer3.from(valueOf, encodingOrOffset, length);
        }
        const b = fromObject(value);
        if (b) return b;
        if (typeof Symbol !== "undefined" && Symbol.toPrimitive != null && typeof value[Symbol.toPrimitive] === "function") {
          return Buffer3.from(value[Symbol.toPrimitive]("string"), encodingOrOffset, length);
        }
        throw new TypeError(
          "The first argument must be one of type string, Buffer, ArrayBuffer, Array, or Array-like Object. Received type " + typeof value
        );
      }
      Buffer3.from = function(value, encodingOrOffset, length) {
        return from(value, encodingOrOffset, length);
      };
      Object.setPrototypeOf(Buffer3.prototype, Uint8Array.prototype);
      Object.setPrototypeOf(Buffer3, Uint8Array);
      function assertSize(size) {
        if (typeof size !== "number") {
          throw new TypeError('"size" argument must be of type number');
        } else if (size < 0) {
          throw new RangeError('The value "' + size + '" is invalid for option "size"');
        }
      }
      function alloc(size, fill, encoding) {
        assertSize(size);
        if (size <= 0) {
          return createBuffer(size);
        }
        if (fill !== void 0) {
          return typeof encoding === "string" ? createBuffer(size).fill(fill, encoding) : createBuffer(size).fill(fill);
        }
        return createBuffer(size);
      }
      Buffer3.alloc = function(size, fill, encoding) {
        return alloc(size, fill, encoding);
      };
      function allocUnsafe(size) {
        assertSize(size);
        return createBuffer(size < 0 ? 0 : checked(size) | 0);
      }
      Buffer3.allocUnsafe = function(size) {
        return allocUnsafe(size);
      };
      Buffer3.allocUnsafeSlow = function(size) {
        return allocUnsafe(size);
      };
      function fromString(string, encoding) {
        if (typeof encoding !== "string" || encoding === "") {
          encoding = "utf8";
        }
        if (!Buffer3.isEncoding(encoding)) {
          throw new TypeError("Unknown encoding: " + encoding);
        }
        const length = byteLength(string, encoding) | 0;
        let buf = createBuffer(length);
        const actual = buf.write(string, encoding);
        if (actual !== length) {
          buf = buf.slice(0, actual);
        }
        return buf;
      }
      function fromArrayLike(array) {
        const length = array.length < 0 ? 0 : checked(array.length) | 0;
        const buf = createBuffer(length);
        for (let i = 0; i < length; i += 1) {
          buf[i] = array[i] & 255;
        }
        return buf;
      }
      function fromArrayView(arrayView) {
        if (isInstance(arrayView, Uint8Array)) {
          const copy = new Uint8Array(arrayView);
          return fromArrayBuffer(copy.buffer, copy.byteOffset, copy.byteLength);
        }
        return fromArrayLike(arrayView);
      }
      function fromArrayBuffer(array, byteOffset, length) {
        if (byteOffset < 0 || array.byteLength < byteOffset) {
          throw new RangeError('"offset" is outside of buffer bounds');
        }
        if (array.byteLength < byteOffset + (length || 0)) {
          throw new RangeError('"length" is outside of buffer bounds');
        }
        let buf;
        if (byteOffset === void 0 && length === void 0) {
          buf = new Uint8Array(array);
        } else if (length === void 0) {
          buf = new Uint8Array(array, byteOffset);
        } else {
          buf = new Uint8Array(array, byteOffset, length);
        }
        Object.setPrototypeOf(buf, Buffer3.prototype);
        return buf;
      }
      function fromObject(obj) {
        if (Buffer3.isBuffer(obj)) {
          const len = checked(obj.length) | 0;
          const buf = createBuffer(len);
          if (buf.length === 0) {
            return buf;
          }
          obj.copy(buf, 0, 0, len);
          return buf;
        }
        if (obj.length !== void 0) {
          if (typeof obj.length !== "number" || numberIsNaN(obj.length)) {
            return createBuffer(0);
          }
          return fromArrayLike(obj);
        }
        if (obj.type === "Buffer" && Array.isArray(obj.data)) {
          return fromArrayLike(obj.data);
        }
      }
      function checked(length) {
        if (length >= K_MAX_LENGTH) {
          throw new RangeError("Attempt to allocate Buffer larger than maximum size: 0x" + K_MAX_LENGTH.toString(16) + " bytes");
        }
        return length | 0;
      }
      function SlowBuffer(length) {
        if (+length != length) {
          length = 0;
        }
        return Buffer3.alloc(+length);
      }
      Buffer3.isBuffer = function isBuffer(b) {
        return b != null && b._isBuffer === true && b !== Buffer3.prototype;
      };
      Buffer3.compare = function compare(a, b) {
        if (isInstance(a, Uint8Array)) a = Buffer3.from(a, a.offset, a.byteLength);
        if (isInstance(b, Uint8Array)) b = Buffer3.from(b, b.offset, b.byteLength);
        if (!Buffer3.isBuffer(a) || !Buffer3.isBuffer(b)) {
          throw new TypeError(
            'The "buf1", "buf2" arguments must be one of type Buffer or Uint8Array'
          );
        }
        if (a === b) return 0;
        let x = a.length;
        let y = b.length;
        for (let i = 0, len = Math.min(x, y); i < len; ++i) {
          if (a[i] !== b[i]) {
            x = a[i];
            y = b[i];
            break;
          }
        }
        if (x < y) return -1;
        if (y < x) return 1;
        return 0;
      };
      Buffer3.isEncoding = function isEncoding(encoding) {
        switch (String(encoding).toLowerCase()) {
          case "hex":
          case "utf8":
          case "utf-8":
          case "ascii":
          case "latin1":
          case "binary":
          case "base64":
          case "ucs2":
          case "ucs-2":
          case "utf16le":
          case "utf-16le":
            return true;
          default:
            return false;
        }
      };
      Buffer3.concat = function concat(list, length) {
        if (!Array.isArray(list)) {
          throw new TypeError('"list" argument must be an Array of Buffers');
        }
        if (list.length === 0) {
          return Buffer3.alloc(0);
        }
        let i;
        if (length === void 0) {
          length = 0;
          for (i = 0; i < list.length; ++i) {
            length += list[i].length;
          }
        }
        const buffer = Buffer3.allocUnsafe(length);
        let pos = 0;
        for (i = 0; i < list.length; ++i) {
          let buf = list[i];
          if (isInstance(buf, Uint8Array)) {
            if (pos + buf.length > buffer.length) {
              if (!Buffer3.isBuffer(buf)) buf = Buffer3.from(buf);
              buf.copy(buffer, pos);
            } else {
              Uint8Array.prototype.set.call(
                buffer,
                buf,
                pos
              );
            }
          } else if (!Buffer3.isBuffer(buf)) {
            throw new TypeError('"list" argument must be an Array of Buffers');
          } else {
            buf.copy(buffer, pos);
          }
          pos += buf.length;
        }
        return buffer;
      };
      function byteLength(string, encoding) {
        if (Buffer3.isBuffer(string)) {
          return string.length;
        }
        if (ArrayBuffer.isView(string) || isInstance(string, ArrayBuffer)) {
          return string.byteLength;
        }
        if (typeof string !== "string") {
          throw new TypeError(
            'The "string" argument must be one of type string, Buffer, or ArrayBuffer. Received type ' + typeof string
          );
        }
        const len = string.length;
        const mustMatch = arguments.length > 2 && arguments[2] === true;
        if (!mustMatch && len === 0) return 0;
        let loweredCase = false;
        for (; ; ) {
          switch (encoding) {
            case "ascii":
            case "latin1":
            case "binary":
              return len;
            case "utf8":
            case "utf-8":
              return utf8ToBytes(string).length;
            case "ucs2":
            case "ucs-2":
            case "utf16le":
            case "utf-16le":
              return len * 2;
            case "hex":
              return len >>> 1;
            case "base64":
              return base64ToBytes(string).length;
            default:
              if (loweredCase) {
                return mustMatch ? -1 : utf8ToBytes(string).length;
              }
              encoding = ("" + encoding).toLowerCase();
              loweredCase = true;
          }
        }
      }
      Buffer3.byteLength = byteLength;
      function slowToString(encoding, start, end) {
        let loweredCase = false;
        if (start === void 0 || start < 0) {
          start = 0;
        }
        if (start > this.length) {
          return "";
        }
        if (end === void 0 || end > this.length) {
          end = this.length;
        }
        if (end <= 0) {
          return "";
        }
        end >>>= 0;
        start >>>= 0;
        if (end <= start) {
          return "";
        }
        if (!encoding) encoding = "utf8";
        while (true) {
          switch (encoding) {
            case "hex":
              return hexSlice(this, start, end);
            case "utf8":
            case "utf-8":
              return utf8Slice(this, start, end);
            case "ascii":
              return asciiSlice(this, start, end);
            case "latin1":
            case "binary":
              return latin1Slice(this, start, end);
            case "base64":
              return base64Slice(this, start, end);
            case "ucs2":
            case "ucs-2":
            case "utf16le":
            case "utf-16le":
              return utf16leSlice(this, start, end);
            default:
              if (loweredCase) throw new TypeError("Unknown encoding: " + encoding);
              encoding = (encoding + "").toLowerCase();
              loweredCase = true;
          }
        }
      }
      Buffer3.prototype._isBuffer = true;
      function swap(b, n, m) {
        const i = b[n];
        b[n] = b[m];
        b[m] = i;
      }
      Buffer3.prototype.swap16 = function swap16() {
        const len = this.length;
        if (len % 2 !== 0) {
          throw new RangeError("Buffer size must be a multiple of 16-bits");
        }
        for (let i = 0; i < len; i += 2) {
          swap(this, i, i + 1);
        }
        return this;
      };
      Buffer3.prototype.swap32 = function swap32() {
        const len = this.length;
        if (len % 4 !== 0) {
          throw new RangeError("Buffer size must be a multiple of 32-bits");
        }
        for (let i = 0; i < len; i += 4) {
          swap(this, i, i + 3);
          swap(this, i + 1, i + 2);
        }
        return this;
      };
      Buffer3.prototype.swap64 = function swap64() {
        const len = this.length;
        if (len % 8 !== 0) {
          throw new RangeError("Buffer size must be a multiple of 64-bits");
        }
        for (let i = 0; i < len; i += 8) {
          swap(this, i, i + 7);
          swap(this, i + 1, i + 6);
          swap(this, i + 2, i + 5);
          swap(this, i + 3, i + 4);
        }
        return this;
      };
      Buffer3.prototype.toString = function toString() {
        const length = this.length;
        if (length === 0) return "";
        if (arguments.length === 0) return utf8Slice(this, 0, length);
        return slowToString.apply(this, arguments);
      };
      Buffer3.prototype.toLocaleString = Buffer3.prototype.toString;
      Buffer3.prototype.equals = function equals(b) {
        if (!Buffer3.isBuffer(b)) throw new TypeError("Argument must be a Buffer");
        if (this === b) return true;
        return Buffer3.compare(this, b) === 0;
      };
      Buffer3.prototype.inspect = function inspect() {
        let str = "";
        const max = exports.INSPECT_MAX_BYTES;
        str = this.toString("hex", 0, max).replace(/(.{2})/g, "$1 ").trim();
        if (this.length > max) str += " ... ";
        return "<Buffer " + str + ">";
      };
      if (customInspectSymbol) {
        Buffer3.prototype[customInspectSymbol] = Buffer3.prototype.inspect;
      }
      Buffer3.prototype.compare = function compare(target, start, end, thisStart, thisEnd) {
        if (isInstance(target, Uint8Array)) {
          target = Buffer3.from(target, target.offset, target.byteLength);
        }
        if (!Buffer3.isBuffer(target)) {
          throw new TypeError(
            'The "target" argument must be one of type Buffer or Uint8Array. Received type ' + typeof target
          );
        }
        if (start === void 0) {
          start = 0;
        }
        if (end === void 0) {
          end = target ? target.length : 0;
        }
        if (thisStart === void 0) {
          thisStart = 0;
        }
        if (thisEnd === void 0) {
          thisEnd = this.length;
        }
        if (start < 0 || end > target.length || thisStart < 0 || thisEnd > this.length) {
          throw new RangeError("out of range index");
        }
        if (thisStart >= thisEnd && start >= end) {
          return 0;
        }
        if (thisStart >= thisEnd) {
          return -1;
        }
        if (start >= end) {
          return 1;
        }
        start >>>= 0;
        end >>>= 0;
        thisStart >>>= 0;
        thisEnd >>>= 0;
        if (this === target) return 0;
        let x = thisEnd - thisStart;
        let y = end - start;
        const len = Math.min(x, y);
        const thisCopy = this.slice(thisStart, thisEnd);
        const targetCopy = target.slice(start, end);
        for (let i = 0; i < len; ++i) {
          if (thisCopy[i] !== targetCopy[i]) {
            x = thisCopy[i];
            y = targetCopy[i];
            break;
          }
        }
        if (x < y) return -1;
        if (y < x) return 1;
        return 0;
      };
      function bidirectionalIndexOf(buffer, val, byteOffset, encoding, dir) {
        if (buffer.length === 0) return -1;
        if (typeof byteOffset === "string") {
          encoding = byteOffset;
          byteOffset = 0;
        } else if (byteOffset > 2147483647) {
          byteOffset = 2147483647;
        } else if (byteOffset < -2147483648) {
          byteOffset = -2147483648;
        }
        byteOffset = +byteOffset;
        if (numberIsNaN(byteOffset)) {
          byteOffset = dir ? 0 : buffer.length - 1;
        }
        if (byteOffset < 0) byteOffset = buffer.length + byteOffset;
        if (byteOffset >= buffer.length) {
          if (dir) return -1;
          else byteOffset = buffer.length - 1;
        } else if (byteOffset < 0) {
          if (dir) byteOffset = 0;
          else return -1;
        }
        if (typeof val === "string") {
          val = Buffer3.from(val, encoding);
        }
        if (Buffer3.isBuffer(val)) {
          if (val.length === 0) {
            return -1;
          }
          return arrayIndexOf(buffer, val, byteOffset, encoding, dir);
        } else if (typeof val === "number") {
          val = val & 255;
          if (typeof Uint8Array.prototype.indexOf === "function") {
            if (dir) {
              return Uint8Array.prototype.indexOf.call(buffer, val, byteOffset);
            } else {
              return Uint8Array.prototype.lastIndexOf.call(buffer, val, byteOffset);
            }
          }
          return arrayIndexOf(buffer, [val], byteOffset, encoding, dir);
        }
        throw new TypeError("val must be string, number or Buffer");
      }
      function arrayIndexOf(arr, val, byteOffset, encoding, dir) {
        let indexSize = 1;
        let arrLength = arr.length;
        let valLength = val.length;
        if (encoding !== void 0) {
          encoding = String(encoding).toLowerCase();
          if (encoding === "ucs2" || encoding === "ucs-2" || encoding === "utf16le" || encoding === "utf-16le") {
            if (arr.length < 2 || val.length < 2) {
              return -1;
            }
            indexSize = 2;
            arrLength /= 2;
            valLength /= 2;
            byteOffset /= 2;
          }
        }
        function read(buf, i2) {
          if (indexSize === 1) {
            return buf[i2];
          } else {
            return buf.readUInt16BE(i2 * indexSize);
          }
        }
        let i;
        if (dir) {
          let foundIndex = -1;
          for (i = byteOffset; i < arrLength; i++) {
            if (read(arr, i) === read(val, foundIndex === -1 ? 0 : i - foundIndex)) {
              if (foundIndex === -1) foundIndex = i;
              if (i - foundIndex + 1 === valLength) return foundIndex * indexSize;
            } else {
              if (foundIndex !== -1) i -= i - foundIndex;
              foundIndex = -1;
            }
          }
        } else {
          if (byteOffset + valLength > arrLength) byteOffset = arrLength - valLength;
          for (i = byteOffset; i >= 0; i--) {
            let found = true;
            for (let j = 0; j < valLength; j++) {
              if (read(arr, i + j) !== read(val, j)) {
                found = false;
                break;
              }
            }
            if (found) return i;
          }
        }
        return -1;
      }
      Buffer3.prototype.includes = function includes(val, byteOffset, encoding) {
        return this.indexOf(val, byteOffset, encoding) !== -1;
      };
      Buffer3.prototype.indexOf = function indexOf(val, byteOffset, encoding) {
        return bidirectionalIndexOf(this, val, byteOffset, encoding, true);
      };
      Buffer3.prototype.lastIndexOf = function lastIndexOf(val, byteOffset, encoding) {
        return bidirectionalIndexOf(this, val, byteOffset, encoding, false);
      };
      function hexWrite(buf, string, offset, length) {
        offset = Number(offset) || 0;
        const remaining = buf.length - offset;
        if (!length) {
          length = remaining;
        } else {
          length = Number(length);
          if (length > remaining) {
            length = remaining;
          }
        }
        const strLen = string.length;
        if (length > strLen / 2) {
          length = strLen / 2;
        }
        let i;
        for (i = 0; i < length; ++i) {
          const parsed = parseInt(string.substr(i * 2, 2), 16);
          if (numberIsNaN(parsed)) return i;
          buf[offset + i] = parsed;
        }
        return i;
      }
      function utf8Write(buf, string, offset, length) {
        return blitBuffer(utf8ToBytes(string, buf.length - offset), buf, offset, length);
      }
      function asciiWrite(buf, string, offset, length) {
        return blitBuffer(asciiToBytes(string), buf, offset, length);
      }
      function base64Write(buf, string, offset, length) {
        return blitBuffer(base64ToBytes(string), buf, offset, length);
      }
      function ucs2Write(buf, string, offset, length) {
        return blitBuffer(utf16leToBytes(string, buf.length - offset), buf, offset, length);
      }
      Buffer3.prototype.write = function write(string, offset, length, encoding) {
        if (offset === void 0) {
          encoding = "utf8";
          length = this.length;
          offset = 0;
        } else if (length === void 0 && typeof offset === "string") {
          encoding = offset;
          length = this.length;
          offset = 0;
        } else if (isFinite(offset)) {
          offset = offset >>> 0;
          if (isFinite(length)) {
            length = length >>> 0;
            if (encoding === void 0) encoding = "utf8";
          } else {
            encoding = length;
            length = void 0;
          }
        } else {
          throw new Error(
            "Buffer.write(string, encoding, offset[, length]) is no longer supported"
          );
        }
        const remaining = this.length - offset;
        if (length === void 0 || length > remaining) length = remaining;
        if (string.length > 0 && (length < 0 || offset < 0) || offset > this.length) {
          throw new RangeError("Attempt to write outside buffer bounds");
        }
        if (!encoding) encoding = "utf8";
        let loweredCase = false;
        for (; ; ) {
          switch (encoding) {
            case "hex":
              return hexWrite(this, string, offset, length);
            case "utf8":
            case "utf-8":
              return utf8Write(this, string, offset, length);
            case "ascii":
            case "latin1":
            case "binary":
              return asciiWrite(this, string, offset, length);
            case "base64":
              return base64Write(this, string, offset, length);
            case "ucs2":
            case "ucs-2":
            case "utf16le":
            case "utf-16le":
              return ucs2Write(this, string, offset, length);
            default:
              if (loweredCase) throw new TypeError("Unknown encoding: " + encoding);
              encoding = ("" + encoding).toLowerCase();
              loweredCase = true;
          }
        }
      };
      Buffer3.prototype.toJSON = function toJSON() {
        return {
          type: "Buffer",
          data: Array.prototype.slice.call(this._arr || this, 0)
        };
      };
      function base64Slice(buf, start, end) {
        if (start === 0 && end === buf.length) {
          return base64.fromByteArray(buf);
        } else {
          return base64.fromByteArray(buf.slice(start, end));
        }
      }
      function utf8Slice(buf, start, end) {
        end = Math.min(buf.length, end);
        const res = [];
        let i = start;
        while (i < end) {
          const firstByte = buf[i];
          let codePoint = null;
          let bytesPerSequence = firstByte > 239 ? 4 : firstByte > 223 ? 3 : firstByte > 191 ? 2 : 1;
          if (i + bytesPerSequence <= end) {
            let secondByte, thirdByte, fourthByte, tempCodePoint;
            switch (bytesPerSequence) {
              case 1:
                if (firstByte < 128) {
                  codePoint = firstByte;
                }
                break;
              case 2:
                secondByte = buf[i + 1];
                if ((secondByte & 192) === 128) {
                  tempCodePoint = (firstByte & 31) << 6 | secondByte & 63;
                  if (tempCodePoint > 127) {
                    codePoint = tempCodePoint;
                  }
                }
                break;
              case 3:
                secondByte = buf[i + 1];
                thirdByte = buf[i + 2];
                if ((secondByte & 192) === 128 && (thirdByte & 192) === 128) {
                  tempCodePoint = (firstByte & 15) << 12 | (secondByte & 63) << 6 | thirdByte & 63;
                  if (tempCodePoint > 2047 && (tempCodePoint < 55296 || tempCodePoint > 57343)) {
                    codePoint = tempCodePoint;
                  }
                }
                break;
              case 4:
                secondByte = buf[i + 1];
                thirdByte = buf[i + 2];
                fourthByte = buf[i + 3];
                if ((secondByte & 192) === 128 && (thirdByte & 192) === 128 && (fourthByte & 192) === 128) {
                  tempCodePoint = (firstByte & 15) << 18 | (secondByte & 63) << 12 | (thirdByte & 63) << 6 | fourthByte & 63;
                  if (tempCodePoint > 65535 && tempCodePoint < 1114112) {
                    codePoint = tempCodePoint;
                  }
                }
            }
          }
          if (codePoint === null) {
            codePoint = 65533;
            bytesPerSequence = 1;
          } else if (codePoint > 65535) {
            codePoint -= 65536;
            res.push(codePoint >>> 10 & 1023 | 55296);
            codePoint = 56320 | codePoint & 1023;
          }
          res.push(codePoint);
          i += bytesPerSequence;
        }
        return decodeCodePointsArray(res);
      }
      var MAX_ARGUMENTS_LENGTH = 4096;
      function decodeCodePointsArray(codePoints) {
        const len = codePoints.length;
        if (len <= MAX_ARGUMENTS_LENGTH) {
          return String.fromCharCode.apply(String, codePoints);
        }
        let res = "";
        let i = 0;
        while (i < len) {
          res += String.fromCharCode.apply(
            String,
            codePoints.slice(i, i += MAX_ARGUMENTS_LENGTH)
          );
        }
        return res;
      }
      function asciiSlice(buf, start, end) {
        let ret = "";
        end = Math.min(buf.length, end);
        for (let i = start; i < end; ++i) {
          ret += String.fromCharCode(buf[i] & 127);
        }
        return ret;
      }
      function latin1Slice(buf, start, end) {
        let ret = "";
        end = Math.min(buf.length, end);
        for (let i = start; i < end; ++i) {
          ret += String.fromCharCode(buf[i]);
        }
        return ret;
      }
      function hexSlice(buf, start, end) {
        const len = buf.length;
        if (!start || start < 0) start = 0;
        if (!end || end < 0 || end > len) end = len;
        let out = "";
        for (let i = start; i < end; ++i) {
          out += hexSliceLookupTable[buf[i]];
        }
        return out;
      }
      function utf16leSlice(buf, start, end) {
        const bytes = buf.slice(start, end);
        let res = "";
        for (let i = 0; i < bytes.length - 1; i += 2) {
          res += String.fromCharCode(bytes[i] + bytes[i + 1] * 256);
        }
        return res;
      }
      Buffer3.prototype.slice = function slice(start, end) {
        const len = this.length;
        start = ~~start;
        end = end === void 0 ? len : ~~end;
        if (start < 0) {
          start += len;
          if (start < 0) start = 0;
        } else if (start > len) {
          start = len;
        }
        if (end < 0) {
          end += len;
          if (end < 0) end = 0;
        } else if (end > len) {
          end = len;
        }
        if (end < start) end = start;
        const newBuf = this.subarray(start, end);
        Object.setPrototypeOf(newBuf, Buffer3.prototype);
        return newBuf;
      };
      function checkOffset(offset, ext, length) {
        if (offset % 1 !== 0 || offset < 0) throw new RangeError("offset is not uint");
        if (offset + ext > length) throw new RangeError("Trying to access beyond buffer length");
      }
      Buffer3.prototype.readUintLE = Buffer3.prototype.readUIntLE = function readUIntLE(offset, byteLength2, noAssert) {
        offset = offset >>> 0;
        byteLength2 = byteLength2 >>> 0;
        if (!noAssert) checkOffset(offset, byteLength2, this.length);
        let val = this[offset];
        let mul = 1;
        let i = 0;
        while (++i < byteLength2 && (mul *= 256)) {
          val += this[offset + i] * mul;
        }
        return val;
      };
      Buffer3.prototype.readUintBE = Buffer3.prototype.readUIntBE = function readUIntBE(offset, byteLength2, noAssert) {
        offset = offset >>> 0;
        byteLength2 = byteLength2 >>> 0;
        if (!noAssert) {
          checkOffset(offset, byteLength2, this.length);
        }
        let val = this[offset + --byteLength2];
        let mul = 1;
        while (byteLength2 > 0 && (mul *= 256)) {
          val += this[offset + --byteLength2] * mul;
        }
        return val;
      };
      Buffer3.prototype.readUint8 = Buffer3.prototype.readUInt8 = function readUInt8(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 1, this.length);
        return this[offset];
      };
      Buffer3.prototype.readUint16LE = Buffer3.prototype.readUInt16LE = function readUInt16LE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 2, this.length);
        return this[offset] | this[offset + 1] << 8;
      };
      Buffer3.prototype.readUint16BE = Buffer3.prototype.readUInt16BE = function readUInt16BE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 2, this.length);
        return this[offset] << 8 | this[offset + 1];
      };
      Buffer3.prototype.readUint32LE = Buffer3.prototype.readUInt32LE = function readUInt32LE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 4, this.length);
        return (this[offset] | this[offset + 1] << 8 | this[offset + 2] << 16) + this[offset + 3] * 16777216;
      };
      Buffer3.prototype.readUint32BE = Buffer3.prototype.readUInt32BE = function readUInt32BE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 4, this.length);
        return this[offset] * 16777216 + (this[offset + 1] << 16 | this[offset + 2] << 8 | this[offset + 3]);
      };
      Buffer3.prototype.readBigUInt64LE = defineBigIntMethod(function readBigUInt64LE(offset) {
        offset = offset >>> 0;
        validateNumber(offset, "offset");
        const first = this[offset];
        const last = this[offset + 7];
        if (first === void 0 || last === void 0) {
          boundsError(offset, this.length - 8);
        }
        const lo = first + this[++offset] * 2 ** 8 + this[++offset] * 2 ** 16 + this[++offset] * 2 ** 24;
        const hi = this[++offset] + this[++offset] * 2 ** 8 + this[++offset] * 2 ** 16 + last * 2 ** 24;
        return BigInt(lo) + (BigInt(hi) << BigInt(32));
      });
      Buffer3.prototype.readBigUInt64BE = defineBigIntMethod(function readBigUInt64BE(offset) {
        offset = offset >>> 0;
        validateNumber(offset, "offset");
        const first = this[offset];
        const last = this[offset + 7];
        if (first === void 0 || last === void 0) {
          boundsError(offset, this.length - 8);
        }
        const hi = first * 2 ** 24 + this[++offset] * 2 ** 16 + this[++offset] * 2 ** 8 + this[++offset];
        const lo = this[++offset] * 2 ** 24 + this[++offset] * 2 ** 16 + this[++offset] * 2 ** 8 + last;
        return (BigInt(hi) << BigInt(32)) + BigInt(lo);
      });
      Buffer3.prototype.readIntLE = function readIntLE(offset, byteLength2, noAssert) {
        offset = offset >>> 0;
        byteLength2 = byteLength2 >>> 0;
        if (!noAssert) checkOffset(offset, byteLength2, this.length);
        let val = this[offset];
        let mul = 1;
        let i = 0;
        while (++i < byteLength2 && (mul *= 256)) {
          val += this[offset + i] * mul;
        }
        mul *= 128;
        if (val >= mul) val -= Math.pow(2, 8 * byteLength2);
        return val;
      };
      Buffer3.prototype.readIntBE = function readIntBE(offset, byteLength2, noAssert) {
        offset = offset >>> 0;
        byteLength2 = byteLength2 >>> 0;
        if (!noAssert) checkOffset(offset, byteLength2, this.length);
        let i = byteLength2;
        let mul = 1;
        let val = this[offset + --i];
        while (i > 0 && (mul *= 256)) {
          val += this[offset + --i] * mul;
        }
        mul *= 128;
        if (val >= mul) val -= Math.pow(2, 8 * byteLength2);
        return val;
      };
      Buffer3.prototype.readInt8 = function readInt8(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 1, this.length);
        if (!(this[offset] & 128)) return this[offset];
        return (255 - this[offset] + 1) * -1;
      };
      Buffer3.prototype.readInt16LE = function readInt16LE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 2, this.length);
        const val = this[offset] | this[offset + 1] << 8;
        return val & 32768 ? val | 4294901760 : val;
      };
      Buffer3.prototype.readInt16BE = function readInt16BE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 2, this.length);
        const val = this[offset + 1] | this[offset] << 8;
        return val & 32768 ? val | 4294901760 : val;
      };
      Buffer3.prototype.readInt32LE = function readInt32LE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 4, this.length);
        return this[offset] | this[offset + 1] << 8 | this[offset + 2] << 16 | this[offset + 3] << 24;
      };
      Buffer3.prototype.readInt32BE = function readInt32BE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 4, this.length);
        return this[offset] << 24 | this[offset + 1] << 16 | this[offset + 2] << 8 | this[offset + 3];
      };
      Buffer3.prototype.readBigInt64LE = defineBigIntMethod(function readBigInt64LE(offset) {
        offset = offset >>> 0;
        validateNumber(offset, "offset");
        const first = this[offset];
        const last = this[offset + 7];
        if (first === void 0 || last === void 0) {
          boundsError(offset, this.length - 8);
        }
        const val = this[offset + 4] + this[offset + 5] * 2 ** 8 + this[offset + 6] * 2 ** 16 + (last << 24);
        return (BigInt(val) << BigInt(32)) + BigInt(first + this[++offset] * 2 ** 8 + this[++offset] * 2 ** 16 + this[++offset] * 2 ** 24);
      });
      Buffer3.prototype.readBigInt64BE = defineBigIntMethod(function readBigInt64BE(offset) {
        offset = offset >>> 0;
        validateNumber(offset, "offset");
        const first = this[offset];
        const last = this[offset + 7];
        if (first === void 0 || last === void 0) {
          boundsError(offset, this.length - 8);
        }
        const val = (first << 24) + // Overflow
        this[++offset] * 2 ** 16 + this[++offset] * 2 ** 8 + this[++offset];
        return (BigInt(val) << BigInt(32)) + BigInt(this[++offset] * 2 ** 24 + this[++offset] * 2 ** 16 + this[++offset] * 2 ** 8 + last);
      });
      Buffer3.prototype.readFloatLE = function readFloatLE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 4, this.length);
        return ieee754.read(this, offset, true, 23, 4);
      };
      Buffer3.prototype.readFloatBE = function readFloatBE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 4, this.length);
        return ieee754.read(this, offset, false, 23, 4);
      };
      Buffer3.prototype.readDoubleLE = function readDoubleLE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 8, this.length);
        return ieee754.read(this, offset, true, 52, 8);
      };
      Buffer3.prototype.readDoubleBE = function readDoubleBE(offset, noAssert) {
        offset = offset >>> 0;
        if (!noAssert) checkOffset(offset, 8, this.length);
        return ieee754.read(this, offset, false, 52, 8);
      };
      function checkInt(buf, value, offset, ext, max, min) {
        if (!Buffer3.isBuffer(buf)) throw new TypeError('"buffer" argument must be a Buffer instance');
        if (value > max || value < min) throw new RangeError('"value" argument is out of bounds');
        if (offset + ext > buf.length) throw new RangeError("Index out of range");
      }
      Buffer3.prototype.writeUintLE = Buffer3.prototype.writeUIntLE = function writeUIntLE(value, offset, byteLength2, noAssert) {
        value = +value;
        offset = offset >>> 0;
        byteLength2 = byteLength2 >>> 0;
        if (!noAssert) {
          const maxBytes = Math.pow(2, 8 * byteLength2) - 1;
          checkInt(this, value, offset, byteLength2, maxBytes, 0);
        }
        let mul = 1;
        let i = 0;
        this[offset] = value & 255;
        while (++i < byteLength2 && (mul *= 256)) {
          this[offset + i] = value / mul & 255;
        }
        return offset + byteLength2;
      };
      Buffer3.prototype.writeUintBE = Buffer3.prototype.writeUIntBE = function writeUIntBE(value, offset, byteLength2, noAssert) {
        value = +value;
        offset = offset >>> 0;
        byteLength2 = byteLength2 >>> 0;
        if (!noAssert) {
          const maxBytes = Math.pow(2, 8 * byteLength2) - 1;
          checkInt(this, value, offset, byteLength2, maxBytes, 0);
        }
        let i = byteLength2 - 1;
        let mul = 1;
        this[offset + i] = value & 255;
        while (--i >= 0 && (mul *= 256)) {
          this[offset + i] = value / mul & 255;
        }
        return offset + byteLength2;
      };
      Buffer3.prototype.writeUint8 = Buffer3.prototype.writeUInt8 = function writeUInt8(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 1, 255, 0);
        this[offset] = value & 255;
        return offset + 1;
      };
      Buffer3.prototype.writeUint16LE = Buffer3.prototype.writeUInt16LE = function writeUInt16LE(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 2, 65535, 0);
        this[offset] = value & 255;
        this[offset + 1] = value >>> 8;
        return offset + 2;
      };
      Buffer3.prototype.writeUint16BE = Buffer3.prototype.writeUInt16BE = function writeUInt16BE(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 2, 65535, 0);
        this[offset] = value >>> 8;
        this[offset + 1] = value & 255;
        return offset + 2;
      };
      Buffer3.prototype.writeUint32LE = Buffer3.prototype.writeUInt32LE = function writeUInt32LE(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 4, 4294967295, 0);
        this[offset + 3] = value >>> 24;
        this[offset + 2] = value >>> 16;
        this[offset + 1] = value >>> 8;
        this[offset] = value & 255;
        return offset + 4;
      };
      Buffer3.prototype.writeUint32BE = Buffer3.prototype.writeUInt32BE = function writeUInt32BE(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 4, 4294967295, 0);
        this[offset] = value >>> 24;
        this[offset + 1] = value >>> 16;
        this[offset + 2] = value >>> 8;
        this[offset + 3] = value & 255;
        return offset + 4;
      };
      function wrtBigUInt64LE(buf, value, offset, min, max) {
        checkIntBI(value, min, max, buf, offset, 7);
        let lo = Number(value & BigInt(4294967295));
        buf[offset++] = lo;
        lo = lo >> 8;
        buf[offset++] = lo;
        lo = lo >> 8;
        buf[offset++] = lo;
        lo = lo >> 8;
        buf[offset++] = lo;
        let hi = Number(value >> BigInt(32) & BigInt(4294967295));
        buf[offset++] = hi;
        hi = hi >> 8;
        buf[offset++] = hi;
        hi = hi >> 8;
        buf[offset++] = hi;
        hi = hi >> 8;
        buf[offset++] = hi;
        return offset;
      }
      function wrtBigUInt64BE(buf, value, offset, min, max) {
        checkIntBI(value, min, max, buf, offset, 7);
        let lo = Number(value & BigInt(4294967295));
        buf[offset + 7] = lo;
        lo = lo >> 8;
        buf[offset + 6] = lo;
        lo = lo >> 8;
        buf[offset + 5] = lo;
        lo = lo >> 8;
        buf[offset + 4] = lo;
        let hi = Number(value >> BigInt(32) & BigInt(4294967295));
        buf[offset + 3] = hi;
        hi = hi >> 8;
        buf[offset + 2] = hi;
        hi = hi >> 8;
        buf[offset + 1] = hi;
        hi = hi >> 8;
        buf[offset] = hi;
        return offset + 8;
      }
      Buffer3.prototype.writeBigUInt64LE = defineBigIntMethod(function writeBigUInt64LE(value, offset = 0) {
        return wrtBigUInt64LE(this, value, offset, BigInt(0), BigInt("0xffffffffffffffff"));
      });
      Buffer3.prototype.writeBigUInt64BE = defineBigIntMethod(function writeBigUInt64BE(value, offset = 0) {
        return wrtBigUInt64BE(this, value, offset, BigInt(0), BigInt("0xffffffffffffffff"));
      });
      Buffer3.prototype.writeIntLE = function writeIntLE(value, offset, byteLength2, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) {
          const limit = Math.pow(2, 8 * byteLength2 - 1);
          checkInt(this, value, offset, byteLength2, limit - 1, -limit);
        }
        let i = 0;
        let mul = 1;
        let sub = 0;
        this[offset] = value & 255;
        while (++i < byteLength2 && (mul *= 256)) {
          if (value < 0 && sub === 0 && this[offset + i - 1] !== 0) {
            sub = 1;
          }
          this[offset + i] = (value / mul >> 0) - sub & 255;
        }
        return offset + byteLength2;
      };
      Buffer3.prototype.writeIntBE = function writeIntBE(value, offset, byteLength2, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) {
          const limit = Math.pow(2, 8 * byteLength2 - 1);
          checkInt(this, value, offset, byteLength2, limit - 1, -limit);
        }
        let i = byteLength2 - 1;
        let mul = 1;
        let sub = 0;
        this[offset + i] = value & 255;
        while (--i >= 0 && (mul *= 256)) {
          if (value < 0 && sub === 0 && this[offset + i + 1] !== 0) {
            sub = 1;
          }
          this[offset + i] = (value / mul >> 0) - sub & 255;
        }
        return offset + byteLength2;
      };
      Buffer3.prototype.writeInt8 = function writeInt8(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 1, 127, -128);
        if (value < 0) value = 255 + value + 1;
        this[offset] = value & 255;
        return offset + 1;
      };
      Buffer3.prototype.writeInt16LE = function writeInt16LE(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 2, 32767, -32768);
        this[offset] = value & 255;
        this[offset + 1] = value >>> 8;
        return offset + 2;
      };
      Buffer3.prototype.writeInt16BE = function writeInt16BE(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 2, 32767, -32768);
        this[offset] = value >>> 8;
        this[offset + 1] = value & 255;
        return offset + 2;
      };
      Buffer3.prototype.writeInt32LE = function writeInt32LE(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 4, 2147483647, -2147483648);
        this[offset] = value & 255;
        this[offset + 1] = value >>> 8;
        this[offset + 2] = value >>> 16;
        this[offset + 3] = value >>> 24;
        return offset + 4;
      };
      Buffer3.prototype.writeInt32BE = function writeInt32BE(value, offset, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) checkInt(this, value, offset, 4, 2147483647, -2147483648);
        if (value < 0) value = 4294967295 + value + 1;
        this[offset] = value >>> 24;
        this[offset + 1] = value >>> 16;
        this[offset + 2] = value >>> 8;
        this[offset + 3] = value & 255;
        return offset + 4;
      };
      Buffer3.prototype.writeBigInt64LE = defineBigIntMethod(function writeBigInt64LE(value, offset = 0) {
        return wrtBigUInt64LE(this, value, offset, -BigInt("0x8000000000000000"), BigInt("0x7fffffffffffffff"));
      });
      Buffer3.prototype.writeBigInt64BE = defineBigIntMethod(function writeBigInt64BE(value, offset = 0) {
        return wrtBigUInt64BE(this, value, offset, -BigInt("0x8000000000000000"), BigInt("0x7fffffffffffffff"));
      });
      function checkIEEE754(buf, value, offset, ext, max, min) {
        if (offset + ext > buf.length) throw new RangeError("Index out of range");
        if (offset < 0) throw new RangeError("Index out of range");
      }
      function writeFloat(buf, value, offset, littleEndian, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) {
          checkIEEE754(buf, value, offset, 4, 34028234663852886e22, -34028234663852886e22);
        }
        ieee754.write(buf, value, offset, littleEndian, 23, 4);
        return offset + 4;
      }
      Buffer3.prototype.writeFloatLE = function writeFloatLE(value, offset, noAssert) {
        return writeFloat(this, value, offset, true, noAssert);
      };
      Buffer3.prototype.writeFloatBE = function writeFloatBE(value, offset, noAssert) {
        return writeFloat(this, value, offset, false, noAssert);
      };
      function writeDouble(buf, value, offset, littleEndian, noAssert) {
        value = +value;
        offset = offset >>> 0;
        if (!noAssert) {
          checkIEEE754(buf, value, offset, 8, 17976931348623157e292, -17976931348623157e292);
        }
        ieee754.write(buf, value, offset, littleEndian, 52, 8);
        return offset + 8;
      }
      Buffer3.prototype.writeDoubleLE = function writeDoubleLE(value, offset, noAssert) {
        return writeDouble(this, value, offset, true, noAssert);
      };
      Buffer3.prototype.writeDoubleBE = function writeDoubleBE(value, offset, noAssert) {
        return writeDouble(this, value, offset, false, noAssert);
      };
      Buffer3.prototype.copy = function copy(target, targetStart, start, end) {
        if (!Buffer3.isBuffer(target)) throw new TypeError("argument should be a Buffer");
        if (!start) start = 0;
        if (!end && end !== 0) end = this.length;
        if (targetStart >= target.length) targetStart = target.length;
        if (!targetStart) targetStart = 0;
        if (end > 0 && end < start) end = start;
        if (end === start) return 0;
        if (target.length === 0 || this.length === 0) return 0;
        if (targetStart < 0) {
          throw new RangeError("targetStart out of bounds");
        }
        if (start < 0 || start >= this.length) throw new RangeError("Index out of range");
        if (end < 0) throw new RangeError("sourceEnd out of bounds");
        if (end > this.length) end = this.length;
        if (target.length - targetStart < end - start) {
          end = target.length - targetStart + start;
        }
        const len = end - start;
        if (this === target && typeof Uint8Array.prototype.copyWithin === "function") {
          this.copyWithin(targetStart, start, end);
        } else {
          Uint8Array.prototype.set.call(
            target,
            this.subarray(start, end),
            targetStart
          );
        }
        return len;
      };
      Buffer3.prototype.fill = function fill(val, start, end, encoding) {
        if (typeof val === "string") {
          if (typeof start === "string") {
            encoding = start;
            start = 0;
            end = this.length;
          } else if (typeof end === "string") {
            encoding = end;
            end = this.length;
          }
          if (encoding !== void 0 && typeof encoding !== "string") {
            throw new TypeError("encoding must be a string");
          }
          if (typeof encoding === "string" && !Buffer3.isEncoding(encoding)) {
            throw new TypeError("Unknown encoding: " + encoding);
          }
          if (val.length === 1) {
            const code = val.charCodeAt(0);
            if (encoding === "utf8" && code < 128 || encoding === "latin1") {
              val = code;
            }
          }
        } else if (typeof val === "number") {
          val = val & 255;
        } else if (typeof val === "boolean") {
          val = Number(val);
        }
        if (start < 0 || this.length < start || this.length < end) {
          throw new RangeError("Out of range index");
        }
        if (end <= start) {
          return this;
        }
        start = start >>> 0;
        end = end === void 0 ? this.length : end >>> 0;
        if (!val) val = 0;
        let i;
        if (typeof val === "number") {
          for (i = start; i < end; ++i) {
            this[i] = val;
          }
        } else {
          const bytes = Buffer3.isBuffer(val) ? val : Buffer3.from(val, encoding);
          const len = bytes.length;
          if (len === 0) {
            throw new TypeError('The value "' + val + '" is invalid for argument "value"');
          }
          for (i = 0; i < end - start; ++i) {
            this[i + start] = bytes[i % len];
          }
        }
        return this;
      };
      var errors = {};
      function E(sym, getMessage, Base) {
        errors[sym] = class NodeError extends Base {
          constructor() {
            super();
            Object.defineProperty(this, "message", {
              value: getMessage.apply(this, arguments),
              writable: true,
              configurable: true
            });
            this.name = `${this.name} [${sym}]`;
            this.stack;
            delete this.name;
          }
          get code() {
            return sym;
          }
          set code(value) {
            Object.defineProperty(this, "code", {
              configurable: true,
              enumerable: true,
              value,
              writable: true
            });
          }
          toString() {
            return `${this.name} [${sym}]: ${this.message}`;
          }
        };
      }
      E(
        "ERR_BUFFER_OUT_OF_BOUNDS",
        function(name) {
          if (name) {
            return `${name} is outside of buffer bounds`;
          }
          return "Attempt to access memory outside buffer bounds";
        },
        RangeError
      );
      E(
        "ERR_INVALID_ARG_TYPE",
        function(name, actual) {
          return `The "${name}" argument must be of type number. Received type ${typeof actual}`;
        },
        TypeError
      );
      E(
        "ERR_OUT_OF_RANGE",
        function(str, range, input) {
          let msg = `The value of "${str}" is out of range.`;
          let received = input;
          if (Number.isInteger(input) && Math.abs(input) > 2 ** 32) {
            received = addNumericalSeparator(String(input));
          } else if (typeof input === "bigint") {
            received = String(input);
            if (input > BigInt(2) ** BigInt(32) || input < -(BigInt(2) ** BigInt(32))) {
              received = addNumericalSeparator(received);
            }
            received += "n";
          }
          msg += ` It must be ${range}. Received ${received}`;
          return msg;
        },
        RangeError
      );
      function addNumericalSeparator(val) {
        let res = "";
        let i = val.length;
        const start = val[0] === "-" ? 1 : 0;
        for (; i >= start + 4; i -= 3) {
          res = `_${val.slice(i - 3, i)}${res}`;
        }
        return `${val.slice(0, i)}${res}`;
      }
      function checkBounds(buf, offset, byteLength2) {
        validateNumber(offset, "offset");
        if (buf[offset] === void 0 || buf[offset + byteLength2] === void 0) {
          boundsError(offset, buf.length - (byteLength2 + 1));
        }
      }
      function checkIntBI(value, min, max, buf, offset, byteLength2) {
        if (value > max || value < min) {
          const n = typeof min === "bigint" ? "n" : "";
          let range;
          if (byteLength2 > 3) {
            if (min === 0 || min === BigInt(0)) {
              range = `>= 0${n} and < 2${n} ** ${(byteLength2 + 1) * 8}${n}`;
            } else {
              range = `>= -(2${n} ** ${(byteLength2 + 1) * 8 - 1}${n}) and < 2 ** ${(byteLength2 + 1) * 8 - 1}${n}`;
            }
          } else {
            range = `>= ${min}${n} and <= ${max}${n}`;
          }
          throw new errors.ERR_OUT_OF_RANGE("value", range, value);
        }
        checkBounds(buf, offset, byteLength2);
      }
      function validateNumber(value, name) {
        if (typeof value !== "number") {
          throw new errors.ERR_INVALID_ARG_TYPE(name, "number", value);
        }
      }
      function boundsError(value, length, type) {
        if (Math.floor(value) !== value) {
          validateNumber(value, type);
          throw new errors.ERR_OUT_OF_RANGE(type || "offset", "an integer", value);
        }
        if (length < 0) {
          throw new errors.ERR_BUFFER_OUT_OF_BOUNDS();
        }
        throw new errors.ERR_OUT_OF_RANGE(
          type || "offset",
          `>= ${type ? 1 : 0} and <= ${length}`,
          value
        );
      }
      var INVALID_BASE64_RE = /[^+/0-9A-Za-z-_]/g;
      function base64clean(str) {
        str = str.split("=")[0];
        str = str.trim().replace(INVALID_BASE64_RE, "");
        if (str.length < 2) return "";
        while (str.length % 4 !== 0) {
          str = str + "=";
        }
        return str;
      }
      function utf8ToBytes(string, units) {
        units = units || Infinity;
        let codePoint;
        const length = string.length;
        let leadSurrogate = null;
        const bytes = [];
        for (let i = 0; i < length; ++i) {
          codePoint = string.charCodeAt(i);
          if (codePoint > 55295 && codePoint < 57344) {
            if (!leadSurrogate) {
              if (codePoint > 56319) {
                if ((units -= 3) > -1) bytes.push(239, 191, 189);
                continue;
              } else if (i + 1 === length) {
                if ((units -= 3) > -1) bytes.push(239, 191, 189);
                continue;
              }
              leadSurrogate = codePoint;
              continue;
            }
            if (codePoint < 56320) {
              if ((units -= 3) > -1) bytes.push(239, 191, 189);
              leadSurrogate = codePoint;
              continue;
            }
            codePoint = (leadSurrogate - 55296 << 10 | codePoint - 56320) + 65536;
          } else if (leadSurrogate) {
            if ((units -= 3) > -1) bytes.push(239, 191, 189);
          }
          leadSurrogate = null;
          if (codePoint < 128) {
            if ((units -= 1) < 0) break;
            bytes.push(codePoint);
          } else if (codePoint < 2048) {
            if ((units -= 2) < 0) break;
            bytes.push(
              codePoint >> 6 | 192,
              codePoint & 63 | 128
            );
          } else if (codePoint < 65536) {
            if ((units -= 3) < 0) break;
            bytes.push(
              codePoint >> 12 | 224,
              codePoint >> 6 & 63 | 128,
              codePoint & 63 | 128
            );
          } else if (codePoint < 1114112) {
            if ((units -= 4) < 0) break;
            bytes.push(
              codePoint >> 18 | 240,
              codePoint >> 12 & 63 | 128,
              codePoint >> 6 & 63 | 128,
              codePoint & 63 | 128
            );
          } else {
            throw new Error("Invalid code point");
          }
        }
        return bytes;
      }
      function asciiToBytes(str) {
        const byteArray = [];
        for (let i = 0; i < str.length; ++i) {
          byteArray.push(str.charCodeAt(i) & 255);
        }
        return byteArray;
      }
      function utf16leToBytes(str, units) {
        let c, hi, lo;
        const byteArray = [];
        for (let i = 0; i < str.length; ++i) {
          if ((units -= 2) < 0) break;
          c = str.charCodeAt(i);
          hi = c >> 8;
          lo = c % 256;
          byteArray.push(lo);
          byteArray.push(hi);
        }
        return byteArray;
      }
      function base64ToBytes(str) {
        return base64.toByteArray(base64clean(str));
      }
      function blitBuffer(src, dst, offset, length) {
        let i;
        for (i = 0; i < length; ++i) {
          if (i + offset >= dst.length || i >= src.length) break;
          dst[i + offset] = src[i];
        }
        return i;
      }
      function isInstance(obj, type) {
        return obj instanceof type || obj != null && obj.constructor != null && obj.constructor.name != null && obj.constructor.name === type.name;
      }
      function numberIsNaN(obj) {
        return obj !== obj;
      }
      var hexSliceLookupTable = (function() {
        const alphabet = "0123456789abcdef";
        const table = new Array(256);
        for (let i = 0; i < 16; ++i) {
          const i16 = i * 16;
          for (let j = 0; j < 16; ++j) {
            table[i16 + j] = alphabet[i] + alphabet[j];
          }
        }
        return table;
      })();
      function defineBigIntMethod(fn) {
        return typeof BigInt === "undefined" ? BufferBigIntNotDefined : fn;
      }
      function BufferBigIntNotDefined() {
        throw new Error("BigInt not supported");
      }
    }
  });

  // src/bridge.js
  var KEY = globalThis.__lx_key__;
  function cryptoCall(op, args) {
    if (typeof globalThis.__lx_crypto__ !== "function") throw new Error("crypto bridge missing");
    return globalThis.__lx_crypto__(op, JSON.stringify(args));
  }
  function nativeCall(action, data) {
    if (typeof globalThis.__lx_native_call__ !== "function") throw new Error("native bridge missing");
    globalThis.__lx_native_call__(KEY, action, JSON.stringify(data));
  }

  // shim/quick-base64.js
  var btoa = (s) => {
    const bytes = unescape(encodeURIComponent(String(s)));
    let out = "";
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    for (let i = 0; i < bytes.length; i += 3) {
      const c1 = bytes.charCodeAt(i);
      const c2 = bytes.charCodeAt(i + 1);
      const c3 = bytes.charCodeAt(i + 2);
      out += chars[c1 >> 2];
      out += chars[(c1 & 3) << 4 | (isNaN(c2) ? 0 : c2 >> 4)];
      out += isNaN(c2) ? "=" : chars[(c2 & 15) << 2 | (isNaN(c3) ? 0 : c3 >> 6)];
      out += isNaN(c3) ? "=" : chars[c3 & 63];
    }
    return out;
  };

  // src/utils/nativeModules/crypto.ts
  var strip = (key) => String(key).replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(/\s/g, "");
  var aesEncryptSync = (text, key, iv2, mode) => cryptoCall("aesEncrypt", [text, key, iv2, mode]);
  var aesDecryptSync = (text, key, iv2, mode) => cryptoCall("aesDecrypt", [text, key, iv2, mode]);
  var rsaEncryptSync = (text, key, padding) => cryptoCall("rsaEncrypt", [text, strip(key), padding]);
  var hashSHA1 = async (text) => cryptoCall("sha1", [String(text)]);

  // shim/quick-md5.js
  var stringMd5 = (s) => cryptoCall("md5", [String(s)]);

  // src/utils/common.ts
  var sizeFormate = (size) => {
    if (!size) return "0 B";
    let units = ["B", "KiB", "MiB", "GiB", "TiB"];
    let number = Math.floor(Math.log(size) / Math.log(1024));
    return `${(size / Math.pow(1024, Math.floor(number))).toFixed(2)} ${units[number]}`;
  };
  var toDateObj = (date) => {
    if (!date) return "";
    switch (typeof date) {
      case "string":
        if (!date.includes("T")) date = date.split(".")[0].replace(/-/g, "/");
      // eslint-disable-next-line no-fallthrough
      case "number":
        date = new Date(date);
      // eslint-disable-next-line no-fallthrough
      case "object":
        break;
      default:
        return "";
    }
    return date;
  };
  var numFix = (n) => n < 10 ? `0${n}` : n.toString();
  var dateFormat = (_date, format = "Y-M-D h:m:s") => {
    const date = toDateObj(_date);
    if (!date) return "";
    return format.replace("Y", date.getFullYear().toString()).replace("M", numFix(date.getMonth() + 1)).replace("D", numFix(date.getDate())).replace("h", numFix(date.getHours())).replace("m", numFix(date.getMinutes())).replace("s", numFix(date.getSeconds()));
  };
  var formatPlayTime = (time) => {
    let m = Math.trunc(time / 60);
    let s = Math.trunc(time % 60);
    return m == 0 && s == 0 ? "--/--" : numFix(m) + ":" + numFix(s);
  };

  // src/utils/index.ts
  var import_he = __toESM(require_he());
  var dateFormat2 = (time) => {
    let differ = Math.trunc((Date.now() - time) / 1e3);
    if (differ < 60) {
      return global.i18n.t("date_format_second", { num: differ });
    } else if (differ < 3600) {
      return global.i18n.t("date_format_minute", { num: Math.trunc(differ / 60) });
    } else if (differ < 86400) {
      return global.i18n.t("date_format_hour", { num: Math.trunc(differ / 3600) });
    } else {
      return dateFormat(time);
    }
  };
  var formatPlayCount = (num) => {
    if (num > 1e8) return `${Math.trunc(num / 1e7) / 10}\u4EBF`;
    if (num > 1e4) return `${Math.trunc(num / 1e3) / 10}\u4E07`;
    return String(num);
  };
  var decodeName = (str = "") => {
    if (!str) return "";
    return import_he.default.decode(str);
  };

  // src/utils/musicSdk/utils.js
  var toMD5 = (str) => stringMd5(str);
  var formatSingerName = (singers, nameKey = "name", join = "\u3001") => {
    if (Array.isArray(singers)) {
      const singer = [];
      singers.forEach((item) => {
        let name = item[nameKey];
        if (!name) return;
        singer.push(name);
      });
      return decodeName(singer.join(join));
    }
    return decodeName(String(singers ?? ""));
  };

  // src/utils/musicSdk/wy/utils/crypto.js
  var iv = btoa("0102030405060708");
  var presetKey = btoa("0CoJUm6Qyw8W8jud");
  var linuxapiKey = btoa("rFgB&h#%2?^eDg:Q");
  var publicKey = "-----BEGIN PUBLIC KEY-----\nMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB\n-----END PUBLIC KEY-----";
  var eapiKey = btoa("e82ckenh8dichen8");
  var aesEncrypt = (b64, mode, key, iv2) => {
    return aesEncryptSync(b64, key, iv2, mode);
  };
  var rsaEncrypt = (buffer, key) => {
    buffer = Buffer.concat([Buffer.alloc(128 - buffer.length), buffer]);
    return Buffer.from(rsaEncryptSync(buffer.toString("base64"), key, "RSA/ECB/NoPadding" /* NoPadding */), "base64");
  };
  var weapi = (object) => {
    const text = JSON.stringify(object);
    const secretKey = String(Math.random()).substring(2, 18);
    return {
      params: aesEncrypt(btoa(aesEncrypt(Buffer.from(text).toString("base64"), "AES/CBC/PKCS7Padding" /* CBC_128_PKCS7Padding */, presetKey, iv)), "AES/CBC/PKCS7Padding" /* CBC_128_PKCS7Padding */, btoa(secretKey), iv),
      encSecKey: rsaEncrypt(Buffer.from(secretKey).reverse(), publicKey).toString("hex")
    };
  };
  var linuxapi = (object) => {
    const text = JSON.stringify(object);
    return {
      eparams: Buffer.from(aesEncrypt(Buffer.from(text).toString("base64"), "AES" /* ECB_128_NoPadding */, linuxapiKey, ""), "base64").toString("hex").toUpperCase()
    };
  };
  var eapi = (url, object) => {
    const text = typeof object === "object" ? JSON.stringify(object) : object;
    const message = `nobody${url}use${text}md5forencrypt`;
    const digest = toMD5(message);
    const data = `${url}-36cd479b6b5-${text}-36cd479b6b5-${digest}`;
    return {
      params: Buffer.from(aesEncrypt(Buffer.from(data).toString("base64"), "AES" /* ECB_128_NoPadding */, eapiKey, ""), "base64").toString("hex").toUpperCase()
    };
  };

  // src/utils/request.js
  var seq = 0;
  var pending = /* @__PURE__ */ new Map();
  globalThis.__lx_http_settle__ = (id, payloadJson) => {
    const entry = pending.get(id);
    if (!entry) return;
    pending.delete(id);
    let payload;
    try {
      payload = JSON.parse(payloadJson);
    } catch (e) {
      entry.reject(new Error("bad response payload"));
      return;
    }
    if (payload.error) {
      entry.reject(new Error(payload.error));
      return;
    }
    entry.resolve({
      statusCode: payload.statusCode,
      statusMessage: payload.statusMessage || "",
      headers: payload.headers || {},
      body: payload.body,
      raw: payload.body
    });
  };
  var httpFetch = (url, options = { method: "get" }) => {
    const id = `h${seq++}`;
    const promise = new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject });
      nativeCall("http", { id, url, options });
    });
    return {
      promise,
      cancelHttp() {
        pending.delete(id);
      }
    };
  };

  // src/utils/musicSdk/wy/musicDetail.js
  var musicDetail_default = {
    getSinger(singers) {
      let arr = [];
      singers?.forEach((singer) => {
        arr.push(singer.name);
      });
      return arr.join("\u3001");
    },
    filterList({ songs, privileges }) {
      const list = [];
      songs.forEach((item, index) => {
        const types = [];
        const _types = {};
        let size;
        let privilege = privileges[index];
        if (privilege.id !== item.id) privilege = privileges.find((p) => p.id === item.id);
        if (!privilege) return;
        if (privilege.maxBrLevel == "hires") {
          size = item.hr ? sizeFormate(item.hr.size) : null;
          types.push({ type: "flac24bit", size });
          _types.flac24bit = {
            size
          };
        }
        switch (privilege.maxbr) {
          case 999e3:
            size = item.sq ? sizeFormate(item.sq.size) : null;
            types.push({ type: "flac", size });
            _types.flac = {
              size
            };
          case 32e4:
            size = item.h ? sizeFormate(item.h.size) : null;
            types.push({ type: "320k", size });
            _types["320k"] = {
              size
            };
          case 192e3:
          case 128e3:
            size = item.l ? sizeFormate(item.l.size) : null;
            types.push({ type: "128k", size });
            _types["128k"] = {
              size
            };
        }
        types.reverse();
        if (item.pc) {
          list.push({
            singer: item.pc.ar ?? "",
            name: item.pc.sn ?? "",
            albumName: item.pc.alb ?? "",
            albumId: item.al?.id,
            source: "wy",
            interval: formatPlayTime(item.dt / 1e3),
            songmid: item.id,
            img: item.al?.picUrl ?? "",
            lrc: null,
            otherSource: null,
            types,
            _types,
            typeUrl: {}
          });
        } else {
          list.push({
            singer: this.getSinger(item.ar),
            name: item.name ?? "",
            albumName: item.al?.name,
            albumId: item.al?.id,
            source: "wy",
            interval: formatPlayTime(item.dt / 1e3),
            songmid: item.id,
            img: item.al?.picUrl,
            lrc: null,
            otherSource: null,
            types,
            _types,
            typeUrl: {}
          });
        }
      });
      return list;
    },
    async getList(ids = [], retryNum = 0) {
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      const requestObj = httpFetch("https://music.163.com/weapi/v3/song/detail", {
        method: "post",
        headers: {
          "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
          origin: "https://music.163.com"
        },
        form: weapi({
          c: "[" + ids.map((id) => '{"id":' + id + "}").join(",") + "]",
          ids: "[" + ids.join(",") + "]"
        })
      });
      const { body, statusCode } = await requestObj.promise;
      if (statusCode != 200 || body.code !== 200) throw new Error("\u83B7\u53D6\u6B4C\u66F2\u8BE6\u60C5\u5931\u8D25");
      return { source: "wy", list: this.filterList(body) };
    }
  };

  // src/utils/musicSdk/wy/leaderboard.js
  var topList = [
    { id: "wy__19723756", name: "\u98D9\u5347\u699C", bangid: "19723756" },
    { id: "wy__3779629", name: "\u65B0\u6B4C\u699C", bangid: "3779629" },
    { id: "wy__2884035", name: "\u539F\u521B\u699C", bangid: "2884035" },
    { id: "wy__3778678", name: "\u70ED\u6B4C\u699C", bangid: "3778678" },
    { id: "wy__991319590", name: "\u8BF4\u5531\u699C", bangid: "991319590" },
    { id: "wy__71384707", name: "\u53E4\u5178\u699C", bangid: "71384707" },
    { id: "wy__1978921795", name: "\u7535\u97F3\u699C", bangid: "1978921795" },
    { id: "wy__5453912201", name: "\u9ED1\u80F6VIP\u7231\u542C\u699C", bangid: "5453912201" },
    { id: "wy__71385702", name: "ACG\u699C", bangid: "71385702" },
    { id: "wy__745956260", name: "\u97E9\u8BED\u699C", bangid: "745956260" },
    { id: "wy__10520166", name: "\u56FD\u7535\u699C", bangid: "10520166" },
    { id: "wy__180106", name: "UK\u6392\u884C\u699C\u5468\u699C", bangid: "180106" },
    { id: "wy__60198", name: "\u7F8E\u56FDBillboard\u699C", bangid: "60198" },
    { id: "wy__3812895", name: "Beatport\u5168\u7403\u7535\u5B50\u821E\u66F2\u699C", bangid: "3812895" },
    { id: "wy__21845217", name: "KTV\u551B\u699C", bangid: "21845217" },
    { id: "wy__60131", name: "\u65E5\u672COricon\u699C", bangid: "60131" },
    { id: "wy__2809513713", name: "\u6B27\u7F8E\u70ED\u6B4C\u699C", bangid: "2809513713" },
    { id: "wy__2809577409", name: "\u6B27\u7F8E\u65B0\u6B4C\u699C", bangid: "2809577409" },
    { id: "wy__27135204", name: "\u6CD5\u56FD NRJ Vos Hits \u5468\u699C", bangid: "27135204" },
    { id: "wy__3001835560", name: "ACG\u52A8\u753B\u699C", bangid: "3001835560" },
    { id: "wy__3001795926", name: "ACG\u6E38\u620F\u699C", bangid: "3001795926" },
    { id: "wy__3001890046", name: "ACG VOCALOID\u699C", bangid: "3001890046" },
    { id: "wy__3112516681", name: "\u4E2D\u56FD\u65B0\u4E61\u6751\u97F3\u4E50\u6392\u884C\u699C", bangid: "3112516681" },
    { id: "wy__5059644681", name: "\u65E5\u8BED\u699C", bangid: "5059644681" },
    { id: "wy__5059633707", name: "\u6447\u6EDA\u699C", bangid: "5059633707" },
    { id: "wy__5059642708", name: "\u56FD\u98CE\u699C", bangid: "5059642708" },
    { id: "wy__5338990334", name: "\u6F5C\u529B\u7206\u6B3E\u699C", bangid: "5338990334" },
    { id: "wy__5059661515", name: "\u6C11\u8C23\u699C", bangid: "5059661515" },
    { id: "wy__6688069460", name: "\u542C\u6B4C\u8BC6\u66F2\u699C", bangid: "6688069460" },
    { id: "wy__6723173524", name: "\u7F51\u7EDC\u70ED\u6B4C\u699C", bangid: "6723173524" },
    { id: "wy__6732051320", name: "\u4FC4\u8BED\u699C", bangid: "6732051320" },
    { id: "wy__6732014811", name: "\u8D8A\u5357\u8BED\u699C", bangid: "6732014811" },
    { id: "wy__6886768100", name: "\u4E2D\u6587DJ\u699C", bangid: "6886768100" },
    { id: "wy__6939992364", name: "\u4FC4\u7F57\u65AFtop hit\u6D41\u884C\u97F3\u4E50\u699C", bangid: "6939992364" },
    { id: "wy__7095271308", name: "\u6CF0\u8BED\u699C", bangid: "7095271308" },
    { id: "wy__7356827205", name: "BEAT\u6392\u884C\u699C", bangid: "7356827205" },
    { id: "wy__7325478166", name: "\u7F16\u8F91\u63A8\u8350\u699CVOL.44 \u5929\u624D\u5973\u5B50\u6447\u6EDA\u4E50\u961Fboygenius\u5256\u767D\u5351\u5FAE\u5FC3\u8FF9", bangid: "7325478166" },
    { id: "wy__7603212484", name: "LOOK\u76F4\u64AD\u6B4C\u66F2\u699C", bangid: "7603212484" },
    { id: "wy__7775163417", name: "\u8D4F\u97F3\u699C", bangid: "7775163417" },
    { id: "wy__7785123708", name: "\u9ED1\u80F6VIP\u65B0\u6B4C\u699C", bangid: "7785123708" },
    { id: "wy__7785066739", name: "\u9ED1\u80F6VIP\u70ED\u6B4C\u699C", bangid: "7785066739" },
    { id: "wy__7785091694", name: "\u9ED1\u80F6VIP\u7231\u641C\u699C", bangid: "7785091694" }
  ];
  var leaderboard_default = {
    limit: 1e5,
    list: [
      {
        id: "wybsb",
        name: "\u98D9\u5347\u699C",
        bangid: "19723756"
      },
      {
        id: "wyrgb",
        name: "\u70ED\u6B4C\u699C",
        bangid: "3778678"
      },
      {
        id: "wyxgb",
        name: "\u65B0\u6B4C\u699C",
        bangid: "3779629"
      },
      {
        id: "wyycb",
        name: "\u539F\u521B\u699C",
        bangid: "2884035"
      },
      {
        id: "wygdb",
        name: "\u53E4\u5178\u699C",
        bangid: "71384707"
      },
      {
        id: "wydouyb",
        name: "\u6296\u97F3\u699C",
        bangid: "2250011882"
      },
      {
        id: "wyhyb",
        name: "\u97E9\u8BED\u699C",
        bangid: "745956260"
      },
      {
        id: "wydianyb",
        name: "\u7535\u97F3\u699C",
        bangid: "1978921795"
      },
      {
        id: "wydjb",
        name: "\u7535\u7ADE\u699C",
        bangid: "2006508653"
      },
      {
        id: "wyktvbb",
        name: "KTV\u551B\u699C",
        bangid: "21845217"
      }
    ],
    getUrl(id) {
      return `https://music.163.com/discover/toplist?id=${id}`;
    },
    regExps: {
      list: /<textarea id="song-list-pre-data" style="display:none;">(.+?)<\/textarea>/
    },
    _requestBoardsObj: null,
    getBoardsData() {
      if (this._requestBoardsObj) this._requestBoardsObj.cancelHttp();
      this._requestBoardsObj = httpFetch("https://music.163.com/weapi/toplist", {
        method: "post",
        form: weapi({})
      });
      return this._requestBoardsObj.promise;
    },
    getData(id) {
      const requestBoardsDetailObj = httpFetch("https://music.163.com/weapi/v3/playlist/detail", {
        method: "post",
        form: weapi({
          id,
          n: 1e5,
          p: 1
        })
      });
      return requestBoardsDetailObj.promise;
    },
    filterBoardsData(rawList) {
      let list = [];
      for (const board of rawList) {
        list.push({
          id: "wy__" + board.id,
          name: board.name,
          bangid: String(board.id)
        });
      }
      return list;
    },
    async getBoards(retryNum = 0) {
      this.list = topList;
      return {
        list: topList,
        source: "wy"
      };
    },
    async getList(bangid, page, retryNum = 0) {
      if (++retryNum > 6) return Promise.reject(new Error("try max num"));
      let resp;
      try {
        resp = await this.getData(bangid);
      } catch (err) {
        if (err.message == "try max num") {
          throw err;
        } else {
          return this.getList(bangid, page, retryNum);
        }
      }
      if (resp.statusCode !== 200 || resp.body.code !== 200) return this.getList(bangid, page, retryNum);
      let musicDetail;
      try {
        musicDetail = await musicDetail_default.getList(resp.body.playlist.trackIds.map((trackId) => trackId.id));
      } catch (err) {
        console.log(err);
        if (err.message == "try max num") {
          throw err;
        } else {
          return this.getList(bangid, page, retryNum);
        }
      }
      return {
        total: musicDetail.list.length,
        list: musicDetail.list,
        limit: this.limit,
        page,
        source: "wy"
      };
    },
    getDetailPageUrl(id) {
      if (typeof id == "string") id = id.replace("wy__", "");
      return `https://music.163.com/#/discover/toplist?id=${id}`;
    }
  };

  // src/utils/musicSdk/api-source-info.ts
  var sources = [];
  var api_source_info_default = sources;

  // src/store/setting/state.ts
  var state = {
    setting: {
      "common.apiSource": "builtin",
      "common.sourceNameType": "alias",
      "player.playQuality": "320k",
      "download.savePath": "",
      "download.fileName": "\u6B4C\u540D - \u6B4C\u624B",
      "list.isShowSource": true
    },
    setSetting(patch) {
      Object.assign(state.setting, patch);
    }
  };
  var state_default = state;

  // src/utils/musicSdk/api-source.js
  var apiList = {
    // temp_api_kw,
    // // test_api_bd: require('./bd/api-test'),
    // test_api_kg,
    // test_api_kw,
    // test_api_tx,
    // test_api_wy,
    // test_api_mg,
    // direct_api_kg,
    // direct_api_kw,
    // direct_api_tx,
    // direct_api_wy,
    // direct_api_mg,
    // test_api_tx: require('./tx/api-test'),
    // test_api_wy: require('./wy/api-test'),
    // test_api_xm: require('./xm/api-test'),
  };
  var supportQuality = {};
  for (const api of api_source_info_default) {
    supportQuality[api.id] = api.supportQualitys;
  }
  var getAPI = (source) => apiList[`${state_default.setting["common.apiSource"]}_api_${source}`];
  var apis = (source) => {
    if (/^user_api/.test(state_default.setting["common.apiSource"])) return global.lx.apis[source];
    const api = getAPI(source);
    if (api) return api;
    throw new Error("Api is not found");
  };

  // src/utils/musicSdk/wy/lyric.js
  var eapiRequest = (url, data) => {
    return httpFetch("https://interface3.music.163.com/eapi/song/lyric/v1", {
      method: "post",
      headers: {
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
        origin: "https://music.163.com"
        // cookie: 'os=pc; deviceId=A9C064BB4584D038B1565B58CB05F95290998EE8B025AA2D07AE; osver=Microsoft-Windows-10-Home-China-build-19043-64bit; appver=2.5.2.197409; channel=netease; MUSIC_A=37a11f2eb9de9930cad479b2ad495b0e4c982367fb6f909d9a3f18f876c6b49faddb3081250c4980dd7e19d4bd9bf384e004602712cf2b2b8efaafaab164268a00b47359f85f22705cc95cb6180f3aee40f5be1ebf3148d888aa2d90636647d0c3061cd18d77b7a0; __csrf=05b50d54082694f945d7de75c210ef94; mode=Z7M-KP5(7)GZ; NMTID=00OZLp2VVgq9QdwokUgq3XNfOddQyIAAAF_6i8eJg; ntes_kaola_ad=1',
      },
      form: eapi(url, data)
    });
  };
  var parseTools = {
    rxps: {
      info: /^{"/,
      lineTime: /^\[(\d+),\d+\]/,
      wordTime: /\(\d+,\d+,\d+\)/,
      wordTimeAll: /(\(\d+,\d+,\d+\))/g,
      timeMs2: /\[\d+:\d+\.\d{2}]/,
      timeMs3: /\[\d+:\d+\.\d{3}]/
    },
    msFormat(timeMs, pad3 = true) {
      if (Number.isNaN(timeMs)) return "";
      let ms = (timeMs % 1e3).toString().padStart(pad3 ? 3 : 2, "0");
      if (!pad3 && ms.length > 2) ms = ms.slice(0, 2);
      timeMs /= 1e3;
      let m = parseInt(timeMs / 60).toString().padStart(2, "0");
      timeMs %= 60;
      let s = parseInt(timeMs).toString().padStart(2, "0");
      return `[${m}:${s}.${ms}]`;
    },
    parseLyric(lines) {
      const lxlrcLines = [];
      const lrcLines = [];
      for (let line of lines) {
        line = line.trim();
        let result = this.rxps.lineTime.exec(line);
        if (!result) {
          if (line.startsWith("[offset")) {
            lxlrcLines.push(line);
            lrcLines.push(line);
          }
          continue;
        }
        const startMsTime = parseInt(result[1]);
        const startTimeStr = this.msFormat(startMsTime);
        if (!startTimeStr) continue;
        let words = line.replace(this.rxps.lineTime, "");
        lrcLines.push(`${startTimeStr}${words.replace(this.rxps.wordTimeAll, "")}`);
        let times = words.match(this.rxps.wordTimeAll);
        if (!times) continue;
        times = times.map((time) => {
          const result2 = /\((\d+),(\d+),\d+\)/.exec(time);
          return `<${Math.trunc(Math.max(parseInt(result2[1]) - startMsTime, 0))},${result2[2]}>`;
        });
        const wordArr = words.split(this.rxps.wordTime);
        wordArr.shift();
        const newWords = times.map((time, index) => `${time}${wordArr[index]}`).join("");
        lxlrcLines.push(`${startTimeStr}${newWords}`);
      }
      return {
        lyric: lrcLines.join("\n"),
        lxlyric: lxlrcLines.join("\n")
      };
    },
    parseHeaderInfo(str) {
      str = str.trim();
      str = str.replace(/\r/g, "");
      if (!str) return null;
      const isPad3 = this.rxps.timeMs3.test(str) || !this.rxps.timeMs2.test(str);
      const lines = str.split("\n");
      return lines.map((line) => {
        if (!this.rxps.info.test(line)) return line;
        try {
          const info = JSON.parse(line);
          const timeTag = this.msFormat(info.t, isPad3);
          return timeTag ? `${timeTag}${info.c.map((t) => t.tx).join("")}` : "";
        } catch {
          return "";
        }
      });
    },
    getIntv(interval) {
      if (!interval) return 0;
      if (!interval.includes(".")) interval += ".0";
      let arr = interval.split(/:|\./);
      while (arr.length < 3) arr.unshift("0");
      const [m, s, ms] = arr;
      return parseInt(m) * 36e5 + parseInt(s) * 1e3 + parseInt(ms);
    },
    fixTimeTag(lrc, targetlrc) {
      let lrcLines = lrc.split("\n");
      const targetlrcLines = targetlrc.split("\n");
      const timeRxp = /^\[([\d:.]+)\]/;
      let temp = [];
      let newLrc = [];
      targetlrcLines.forEach((line) => {
        const result = timeRxp.exec(line);
        if (!result) return;
        const words = line.replace(timeRxp, "");
        if (!words.trim()) return;
        const t1 = this.getIntv(result[1]);
        while (lrcLines.length) {
          const lrcLine = lrcLines.shift();
          const lrcLineResult = timeRxp.exec(lrcLine);
          if (!lrcLineResult) continue;
          const t2 = this.getIntv(lrcLineResult[1]);
          if (Math.abs(t1 - t2) < 100) {
            const lrc2 = line.replace(timeRxp, lrcLineResult[0]).trim();
            if (!lrc2) continue;
            newLrc.push(lrc2);
            break;
          }
          temp.push(lrcLine);
        }
        lrcLines = [...temp, ...lrcLines];
        temp = [];
      });
      return newLrc.join("\n");
    },
    parse(ylrc, ytlrc, yrlrc, lrc, tlrc, rlrc) {
      const info = {
        lyric: "",
        tlyric: "",
        rlyric: "",
        lxlyric: ""
      };
      if (ylrc) {
        let lines = this.parseHeaderInfo(ylrc);
        if (lines) {
          const result = this.parseLyric(lines);
          if (ytlrc) {
            const lines2 = this.parseHeaderInfo(ytlrc);
            if (lines2) {
              info.tlyric = this.fixTimeTag(result.lyric, lines2.join("\n"));
            }
          }
          if (yrlrc) {
            const lines2 = this.parseHeaderInfo(yrlrc);
            if (lines2) {
              info.rlyric = this.fixTimeTag(result.lyric, lines2.join("\n"));
            }
          }
          const timeRxp = /^\[[\d:.]+\]/;
          const headers = lines.filter((l) => timeRxp.test(l)).join("\n");
          info.lyric = `${headers}
${result.lyric}`;
          info.lxlyric = result.lxlyric;
          return info;
        }
      }
      if (lrc) {
        const lines = this.parseHeaderInfo(lrc);
        if (lines) info.lyric = lines.join("\n");
      }
      if (tlrc) {
        const lines = this.parseHeaderInfo(tlrc);
        if (lines) info.tlyric = lines.join("\n");
      }
      if (rlrc) {
        const lines = this.parseHeaderInfo(rlrc);
        if (lines) info.rlyric = lines.join("\n");
      }
      return info;
    }
  };
  var fixTimeLabel = (lrc, tlrc, romalrc) => {
    if (lrc) {
      let newLrc = lrc.replace(/\[(\d{2}:\d{2}):(\d{2})]/g, "[$1.$2]");
      let newTlrc = tlrc?.replace(/\[(\d{2}:\d{2}):(\d{2})]/g, "[$1.$2]") ?? tlrc;
      if (newLrc != lrc || newTlrc != tlrc) {
        lrc = newLrc;
        tlrc = newTlrc;
        if (romalrc) romalrc = romalrc.replace(/\[(\d{2}:\d{2}):(\d{2,3})]/g, "[$1.$2]").replace(/\[(\d{2}:\d{2}\.\d{2})0]/g, "[$1]");
      }
    }
    return { lrc, tlrc, romalrc };
  };
  var lyric_default = (songmid) => {
    const requestObj = eapiRequest("/api/song/lyric/v1", {
      id: songmid,
      cp: false,
      tv: 0,
      lv: 0,
      rv: 0,
      kv: 0,
      yv: 0,
      ytv: 0,
      yrv: 0
    });
    requestObj.promise = requestObj.promise.then(({ body }) => {
      if (body.code !== 200 || !body?.lrc?.lyric) return Promise.reject(new Error("Get lyric failed"));
      const fixTimeLabelLrc = fixTimeLabel(body.lrc.lyric, body.tlyric?.lyric, body.romalrc?.lyric);
      const info = parseTools.parse(body.yrc?.lyric, body.ytlrc?.lyric, body.yromalrc?.lyric, fixTimeLabelLrc.lrc, fixTimeLabelLrc.tlrc, fixTimeLabelLrc.romalrc);
      if (!info.lyric) return Promise.reject(new Error("Get lyric failed"));
      return info;
    });
    return requestObj;
  };

  // src/utils/musicSdk/wy/musicInfo.js
  var musicInfo_default = (songmid) => {
    const requestObj = httpFetch("https://music.163.com/weapi/v3/song/detail", {
      method: "post",
      headers: {
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
        Referer: "https://music.163.com/song?id=" + songmid,
        origin: "https://music.163.com"
      },
      form: weapi({
        c: `[{"id":${songmid}}]`,
        ids: `[${songmid}]`
      })
    });
    requestObj.promise = requestObj.promise.then(({ body }) => {
      if (body.code !== 200 || !body.songs.length) return Promise.reject(new Error("\u83B7\u53D6\u6B4C\u66F2\u4FE1\u606F\u5931\u8D25"));
      return body.songs[0];
    });
    return requestObj;
  };

  // src/utils/musicSdk/wy/utils/index.js
  var eapiRequest2 = (url, data) => {
    return httpFetch("http://interface.music.163.com/eapi/batch", {
      method: "post",
      headers: {
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
        origin: "https://music.163.com"
        // cookie: 'os=pc; deviceId=A9C064BB4584D038B1565B58CB05F95290998EE8B025AA2D07AE; osver=Microsoft-Windows-10-Home-China-build-19043-64bit; appver=2.5.2.197409; channel=netease; MUSIC_A=37a11f2eb9de9930cad479b2ad495b0e4c982367fb6f909d9a3f18f876c6b49faddb3081250c4980dd7e19d4bd9bf384e004602712cf2b2b8efaafaab164268a00b47359f85f22705cc95cb6180f3aee40f5be1ebf3148d888aa2d90636647d0c3061cd18d77b7a0; __csrf=05b50d54082694f945d7de75c210ef94; mode=Z7M-KP5(7)GZ; NMTID=00OZLp2VVgq9QdwokUgq3XNfOddQyIAAAF_6i8eJg; ntes_kaola_ad=1',
      },
      form: eapi(url, data)
    });
  };

  // src/utils/musicSdk/wy/musicSearch.js
  var musicSearch_default = {
    limit: 30,
    total: 0,
    page: 0,
    allPage: 1,
    musicSearch(str, page, limit) {
      const searchRequest = eapiRequest2("/api/search/song/list/page", {
        keyword: str,
        needCorrect: "1",
        channel: "typing",
        offset: limit * (page - 1),
        scene: "normal",
        total: page == 1,
        limit
      });
      return searchRequest.promise.then(({ body }) => body);
    },
    getSinger(singers) {
      let arr = [];
      singers.forEach((singer) => {
        arr.push(singer.name);
      });
      return arr.join("\u3001");
    },
    handleResult(rawList) {
      if (!rawList) return [];
      return rawList.map((item) => {
        item = item.baseInfo.simpleSongData;
        const types = [];
        const _types = {};
        let size;
        if (item.privilege.maxBrLevel == "hires") {
          size = item.hr ? sizeFormate(item.hr.size) : null;
          types.push({ type: "flac24bit", size });
          _types.flac24bit = {
            size
          };
        }
        switch (item.privilege.maxbr) {
          case 999e3:
            size = item.sq ? sizeFormate(item.sq.size) : null;
            types.push({ type: "flac", size });
            _types.flac = {
              size
            };
          case 32e4:
            size = item.h ? sizeFormate(item.h.size) : null;
            types.push({ type: "320k", size });
            _types["320k"] = {
              size
            };
          case 192e3:
          case 128e3:
            size = item.l ? sizeFormate(item.l.size) : null;
            types.push({ type: "128k", size });
            _types["128k"] = {
              size
            };
        }
        types.reverse();
        return {
          singer: this.getSinger(item.ar),
          name: item.name,
          albumName: item.al.name,
          albumId: item.al.id,
          source: "wy",
          interval: formatPlayTime(item.dt / 1e3),
          songmid: item.id,
          img: item.al.picUrl,
          lrc: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    search(str, page = 1, limit, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then((result) => {
        if (!result || result.code !== 200) return this.search(str, page, limit, retryNum);
        let list = this.handleResult(result.data.resources || []);
        if (list == null) return this.search(str, page, limit, retryNum);
        this.total = result.data.totalCount || 0;
        this.page = page;
        this.allPage = Math.ceil(this.total / this.limit);
        return {
          list,
          allPage: this.allPage,
          limit: this.limit,
          total: this.total,
          source: "wy"
        };
      });
    }
  };

  // src/utils/musicSdk/wy/songList.js
  var songList_default = {
    _requestObj_tags: null,
    _requestObj_hotTags: null,
    _requestObj_list: null,
    limit_list: 30,
    limit_song: 1e5,
    successCode: 200,
    cookie: "MUSIC_U=",
    sortList: [
      {
        name: "\u6700\u70ED",
        tid: "hot",
        id: "hot"
      }
      // {
      //   name: '最新',
      //   tid: 'new',
      //   id: 'new',
      // },
    ],
    regExps: {
      listDetailLink: /^.+(?:\?|&)id=(\d+)(?:&.*$|#.*$|$)/,
      listDetailLink2: /^.+\/playlist\/(\d+)\/\d+\/.+$/
    },
    async handleParseId(link, retryNum = 0) {
      if (retryNum > 2) throw new Error("link try max num");
      const requestObj_listDetailLink = httpFetch(link);
      const { url, statusCode } = await requestObj_listDetailLink.promise;
      if (statusCode > 400) return this.handleParseId(link, ++retryNum);
      return this.regExps.listDetailLink.test(url) ? url.replace(this.regExps.listDetailLink, "$1") : url.replace(this.regExps.listDetailLink2, "$1");
    },
    async getListId(id) {
      let cookie;
      if (/###/.test(id)) {
        const [url, token] = id.split("###");
        id = url;
        cookie = `MUSIC_U=${token}`;
      }
      if (/[?&:/]/.test(id)) {
        if (this.regExps.listDetailLink.test(id)) {
          id = id.replace(this.regExps.listDetailLink, "$1");
        } else if (this.regExps.listDetailLink2.test(id)) {
          id = id.replace(this.regExps.listDetailLink2, "$1");
        } else {
          id = await this.handleParseId(id);
        }
      }
      return { id, cookie };
    },
    async getListDetail(rawId, page, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      const { id, cookie } = await this.getListId(rawId);
      if (cookie) this.cookie = cookie;
      const requestObj_listDetail = httpFetch("https://music.163.com/api/linux/forward", {
        method: "post",
        headers: {
          "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
          Cookie: this.cookie
        },
        credentials: "omit",
        cache: "default",
        form: linuxapi({
          method: "POST",
          url: "https://music.163.com/api/v3/playlist/detail",
          params: {
            id,
            n: this.limit_song,
            s: 8
          }
        })
      });
      const { statusCode, body } = await requestObj_listDetail.promise;
      if (statusCode !== 200 || body.code !== this.successCode) return this.getListDetail(id, page, ++tryNum);
      let limit = 1e3;
      let rangeStart = (page - 1) * limit;
      let list;
      if (body.playlist.trackIds.length == body.privileges.length) {
        list = this.filterListDetail(body);
      } else {
        try {
          list = (await musicDetail_default.getList(body.playlist.trackIds.slice(rangeStart, limit * page).map((trackId) => trackId.id))).list;
        } catch (err) {
          console.log(err);
          if (err.message == "try max num") {
            throw err;
          } else {
            return this.getListDetail(id, page, ++tryNum);
          }
        }
      }
      return {
        list,
        page,
        limit,
        total: body.playlist.trackIds.length,
        source: "wy",
        info: {
          play_count: formatPlayCount(body.playlist.playCount),
          name: body.playlist.name,
          img: body.playlist.coverImgUrl,
          desc: body.playlist.description,
          author: body.playlist.creator.nickname
        }
      };
    },
    filterListDetail({ playlist: { tracks }, privileges }) {
      const list = [];
      tracks.forEach((item, index) => {
        const types = [];
        const _types = {};
        let size;
        let privilege = privileges[index];
        if (privilege.id !== item.id) privilege = privileges.find((p) => p.id === item.id);
        if (!privilege) return;
        if (privilege.maxBrLevel == "hires") {
          size = item.hr ? sizeFormate(item.hr.size) : null;
          types.push({ type: "flac24bit", size });
          _types.flac24bit = {
            size
          };
        }
        switch (privilege.maxbr) {
          case 999e3:
            size = null;
            types.push({ type: "flac", size });
            _types.flac = {
              size
            };
          case 32e4:
            size = item.h ? sizeFormate(item.h.size) : null;
            types.push({ type: "320k", size });
            _types["320k"] = {
              size
            };
          case 192e3:
          case 128e3:
            size = item.l ? sizeFormate(item.l.size) : null;
            types.push({ type: "128k", size });
            _types["128k"] = {
              size
            };
        }
        types.reverse();
        if (item.pc) {
          list.push({
            singer: item.pc.ar ?? "",
            name: item.pc.sn ?? "",
            albumName: item.pc.alb ?? "",
            albumId: item.al?.id,
            source: "wy",
            interval: formatPlayTime(item.dt / 1e3),
            songmid: item.id,
            img: item.al?.picUrl ?? "",
            lrc: null,
            otherSource: null,
            types,
            _types,
            typeUrl: {}
          });
        } else {
          list.push({
            singer: formatSingerName(item.ar, "name"),
            name: item.name ?? "",
            albumName: item.al?.name,
            albumId: item.al?.id,
            source: "wy",
            interval: formatPlayTime(item.dt / 1e3),
            songmid: item.id,
            img: item.al?.picUrl,
            lrc: null,
            otherSource: null,
            types,
            _types,
            typeUrl: {}
          });
        }
      });
      return list;
    },
    // 获取列表数据
    getList(sortId, tagId, page, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      if (this._requestObj_list) this._requestObj_list.cancelHttp();
      this._requestObj_list = httpFetch("https://music.163.com/weapi/playlist/list", {
        method: "post",
        form: weapi({
          cat: tagId || "\u5168\u90E8",
          // 全部,华语,欧美,日语,韩语,粤语,小语种,流行,摇滚,民谣,电子,舞曲,说唱,轻音乐,爵士,乡村,R&B/Soul,古典,民族,英伦,金属,朋克,蓝调,雷鬼,世界音乐,拉丁,另类/独立,New Age,古风,后摇,Bossa Nova,清晨,夜晚,学习,工作,午休,下午茶,地铁,驾车,运动,旅行,散步,酒吧,怀旧,清新,浪漫,性感,伤感,治愈,放松,孤独,感动,兴奋,快乐,安静,思念,影视原声,ACG,儿童,校园,游戏,70后,80后,90后,网络歌曲,KTV,经典,翻唱,吉他,钢琴,器乐,榜单,00后
          order: sortId,
          // hot,new
          limit: this.limit_list,
          offset: this.limit_list * (page - 1),
          total: true
        })
      });
      return this._requestObj_list.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getList(sortId, tagId, page, ++tryNum);
        return {
          list: this.filterList(body.playlists),
          total: parseInt(body.total),
          page,
          limit: this.limit_list,
          source: "wy"
        };
      });
    },
    filterList(rawData) {
      return rawData.map((item) => ({
        play_count: formatPlayCount(item.playCount),
        id: String(item.id),
        author: item.creator.nickname,
        name: item.name,
        time: item.createTime ? dateFormat(item.createTime, "Y-M-D") : "",
        img: item.coverImgUrl,
        grade: item.grade,
        total: item.trackCount,
        desc: item.description,
        source: "wy"
      }));
    },
    // 获取标签
    getTag(tryNum = 0) {
      if (this._requestObj_tags) this._requestObj_tags.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_tags = httpFetch("https://music.163.com/weapi/playlist/catalogue", {
        method: "post",
        form: weapi({})
      });
      return this._requestObj_tags.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getTag(++tryNum);
        return this.filterTagInfo(body);
      });
    },
    filterTagInfo({ sub, categories }) {
      const subList = {};
      for (const item of sub) {
        if (!subList[item.category]) subList[item.category] = [];
        subList[item.category].push({
          parent_id: categories[item.category],
          parent_name: categories[item.category],
          id: item.name,
          name: item.name,
          source: "wy"
        });
      }
      const list = [];
      for (const key of Object.keys(categories)) {
        list.push({
          name: categories[key],
          list: subList[key],
          source: "wy"
        });
      }
      return list;
    },
    // 获取热门标签
    getHotTag(tryNum = 0) {
      if (this._requestObj_hotTags) this._requestObj_hotTags.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_hotTags = httpFetch("https://music.163.com/weapi/playlist/hottags", {
        method: "post",
        form: weapi({})
      });
      return this._requestObj_hotTags.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getTag(++tryNum);
        return this.filterHotTagInfo(body.tags);
      });
    },
    filterHotTagInfo(rawList) {
      return rawList.map((item) => ({
        id: item.playlistTag.name,
        name: item.playlistTag.name,
        source: "wy"
      }));
    },
    getTags() {
      return Promise.all([this.getTag(), this.getHotTag()]).then(([tags, hotTag]) => ({ tags, hotTag, source: "wy" }));
    },
    async getDetailPageUrl(rawId) {
      const { id } = await this.getListId(rawId);
      return `https://music.163.com/#/playlist?id=${id}`;
    },
    search(text, page, limit = 20) {
      return eapiRequest2("/api/cloudsearch/pc", {
        s: text,
        type: 1e3,
        // 1: 单曲, 10: 专辑, 100: 歌手, 1000: 歌单, 1002: 用户, 1004: MV, 1006: 歌词, 1009: 电台, 1014: 视频
        limit,
        total: page == 1,
        offset: limit * (page - 1)
      }).promise.then(({ body }) => {
        if (body.code != this.successCode) throw new Error("filed");
        return {
          list: this.filterList(body.result.playlists),
          limit,
          total: body.result.playlistCount,
          source: "wy"
        };
      });
    }
  };

  // src/utils/musicSdk/wy/hotSearch.js
  var hotSearch_default = {
    _requestObj: null,
    async getList(retryNum = 0) {
      if (this._requestObj) this._requestObj.cancelHttp();
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      const _requestObj = eapiRequest2("/api/search/chart/detail", {
        id: "HOT_SEARCH_SONG#@#"
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.code !== 200) throw new Error("\u83B7\u53D6\u70ED\u641C\u8BCD\u5931\u8D25");
      return { source: "wy", list: this.filterList(body.data.itemList) };
    },
    filterList(rawList) {
      return rawList.map((item) => item.searchWord);
    }
  };

  // src/utils/musicSdk/wy/comment.js
  var emojis = [
    ["\u5927\u7B11", "\u{1F603}"],
    ["\u53EF\u7231", "\u{1F60A}"],
    ["\u61A8\u7B11", "\u263A\uFE0F"],
    ["\u8272", "\u{1F60D}"],
    ["\u4EB2\u4EB2", "\u{1F619}"],
    ["\u60CA\u6050", "\u{1F631}"],
    ["\u6D41\u6CEA", "\u{1F62D}"],
    ["\u4EB2", "\u{1F61A}"],
    ["\u5446", "\u{1F633}"],
    ["\u54C0\u4F24", "\u{1F614}"],
    ["\u5472\u7259", "\u{1F601}"],
    ["\u5410\u820C", "\u{1F61D}"],
    ["\u6487\u5634", "\u{1F612}"],
    ["\u6012", "\u{1F621}"],
    ["\u5978\u7B11", "\u{1F60F}"],
    ["\u6C57", "\u{1F613}"],
    ["\u75DB\u82E6", "\u{1F616}"],
    ["\u60F6\u6050", "\u{1F630}"],
    ["\u751F\u75C5", "\u{1F628}"],
    ["\u53E3\u7F69", "\u{1F637}"],
    ["\u5927\u54ED", "\u{1F602}"],
    ["\u6655", "\u{1F635}"],
    ["\u53D1\u6012", "\u{1F47F}"],
    ["\u5F00\u5FC3", "\u{1F604}"],
    ["\u9B3C\u8138", "\u{1F61C}"],
    ["\u76B1\u7709", "\u{1F61E}"],
    ["\u6D41\u611F", "\u{1F622}"],
    ["\u7231\u5FC3", "\u2764\uFE0F"],
    ["\u5FC3\u788E", "\u{1F494}"],
    ["\u949F\u60C5", "\u{1F498}"],
    ["\u661F\u661F", "\u2B50\uFE0F"],
    ["\u751F\u6C14", "\u{1F4A2}"],
    ["\u4FBF\u4FBF", "\u{1F4A9}"],
    ["\u5F3A", "\u{1F44D}"],
    ["\u5F31", "\u{1F44E}"],
    ["\u62DC", "\u{1F64F}"],
    ["\u7275\u624B", "\u{1F46B}"],
    ["\u8DF3\u821E", "\u{1F46F}\u200D\u2640\uFE0F"],
    ["\u7981\u6B62", "\u{1F645}\u200D\u2640\uFE0F"],
    ["\u8FD9\u8FB9", "\u{1F481}\u200D\u2640\uFE0F"],
    ["\u7231\u610F", "\u{1F48F}"],
    ["\u793A\u7231", "\u{1F469}\u200D\u2764\uFE0F\u200D\u{1F468}"],
    ["\u5634\u5507", "\u{1F444}"],
    ["\u72D7", "\u{1F436}"],
    ["\u732B", "\u{1F431}"],
    ["\u732A", "\u{1F437}"],
    ["\u5154\u5B50", "\u{1F430}"],
    ["\u5C0F\u9E21", "\u{1F424}"],
    ["\u516C\u9E21", "\u{1F414}"],
    ["\u5E7D\u7075", "\u{1F47B}"],
    ["\u5723\u8BDE", "\u{1F385}"],
    ["\u5916\u661F", "\u{1F47D}"],
    ["\u94BB\u77F3", "\u{1F48E}"],
    ["\u793C\u7269", "\u{1F381}"],
    ["\u7537\u5B69", "\u{1F466}"],
    ["\u5973\u5B69", "\u{1F467}"],
    ["\u86CB\u7CD5", "\u{1F382}"],
    ["18", "\u{1F51E}"],
    ["\u5708", "\u2B55"],
    ["\u53C9", "\u274C"]
  ];
  var applyEmoji = (text) => {
    for (const e of emojis) text = text.replaceAll(`[${e[0]}]`, e[1]);
    return text;
  };
  var cursorTools = {
    cache: {},
    getCursor(id, page, limit) {
      let cacheData = this.cache[id];
      if (!cacheData) cacheData = this.cache[id] = {};
      let orderType;
      let cursor;
      let offset;
      if (page == 1) {
        cacheData.page = 1;
        cursor = cacheData.cursor = cacheData.prevCursor = Date.now();
        orderType = 1;
        offset = 0;
      } else if (cacheData.page) {
        cursor = cacheData.cursor;
        if (page > cacheData.page) {
          orderType = 1;
          offset = (page - cacheData.page - 1) * limit;
        } else if (page < cacheData.page) {
          orderType = 0;
          offset = (cacheData.page - page - 1) * limit;
        } else {
          cursor = cacheData.cursor = cacheData.prevCursor;
          offset = cacheData.offset;
          orderType = cacheData.orderType;
        }
      }
      return {
        orderType,
        cursor,
        offset
      };
    },
    setCursor(id, cursor, orderType, offset, page) {
      let cacheData = this.cache[id];
      if (!cacheData) cacheData = this.cache[id] = {};
      cacheData.prevCursor = cacheData.cursor;
      cacheData.cursor = cursor;
      cacheData.orderType = orderType;
      cacheData.offset = offset;
      cacheData.page = page;
    }
  };
  var comment_default = {
    _requestObj: null,
    _requestObj2: null,
    async getComment({ songmid }, page = 1, limit = 20) {
      if (this._requestObj) this._requestObj.cancelHttp();
      const id = "R_SO_4_" + songmid;
      const cursorInfo = cursorTools.getCursor(songmid, page, limit);
      const _requestObj = httpFetch("https://music.163.com/weapi/comment/resource/comments/get", {
        method: "post",
        headers: {
          "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
          origin: "https://music.163.com",
          Refere: "http://music.163.com/"
        },
        form: weapi({
          cursor: cursorInfo.cursor,
          offset: cursorInfo.offset,
          orderType: cursorInfo.orderType,
          pageNo: page,
          pageSize: limit,
          rid: id,
          threadId: id
        })
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.code !== 200) throw new Error("\u83B7\u53D6\u8BC4\u8BBA\u5931\u8D25");
      cursorTools.setCursor(songmid, body.data.cursor, cursorInfo.orderType, cursorInfo.offset, page);
      return { source: "wy", comments: this.filterComment(body.data.comments), total: body.data.totalCount, page, limit, maxPage: Math.ceil(body.data.totalCount / limit) || 1 };
    },
    async getHotComment({ songmid }, page = 1, limit = 100) {
      if (this._requestObj2) this._requestObj2.cancelHttp();
      const id = "R_SO_4_" + songmid;
      const _requestObj2 = httpFetch(`https://music.163.com/weapi/v1/resource/hotcomments/${id}`, {
        method: "post",
        headers: {
          "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
          origin: "https://music.163.com",
          Refere: "http://music.163.com/"
        },
        form: weapi({
          rid: id,
          limit,
          offset: limit * (page - 1),
          beforeTime: Date.now().toString()
        })
      });
      const { body, statusCode } = await _requestObj2.promise;
      if (statusCode != 200 || body.code !== 200) throw new Error("\u83B7\u53D6\u70ED\u95E8\u8BC4\u8BBA\u5931\u8D25");
      const total = body.total ?? 0;
      return { source: "wy", comments: this.filterComment(body.hotComments), total, page, limit, maxPage: Math.ceil(total / limit) || 1 };
    },
    filterComment(rawList) {
      return rawList.map((item) => {
        let data = {
          id: item.commentId,
          text: item.content ? applyEmoji(item.content) : "",
          time: item.time ? item.time : "",
          timeStr: item.time ? dateFormat2(item.time) : "",
          location: item.ipLocation?.location,
          userName: item.user.nickname,
          avatar: item.user.avatarUrl,
          userId: item.user.userId,
          likedCount: item.likedCount,
          reply: []
        };
        let replyData = item.beReplied && item.beReplied[0];
        return replyData ? {
          id: item.commentId,
          rootId: replyData.beRepliedCommentId,
          text: replyData.content ? applyEmoji(replyData.content) : "",
          time: item.time,
          timeStr: null,
          location: replyData.ipLocation?.location,
          userName: replyData.user.nickname,
          avatar: replyData.user.avatarUrl,
          userId: replyData.user.userId,
          likedCount: null,
          reply: [data]
        } : data;
      });
    }
  };

  // src/utils/musicSdk/wy/index.js
  var wy = {
    // tipSearch,
    leaderboard: leaderboard_default,
    musicSearch: musicSearch_default,
    songList: songList_default,
    hotSearch: hotSearch_default,
    comment: comment_default,
    getMusicUrl(songInfo, type) {
      return apis("wy").getMusicUrl(songInfo, type);
    },
    getLyric(songInfo) {
      return lyric_default(songInfo.songmid);
    },
    getPic(songInfo) {
      const requestObj = musicInfo_default(songInfo.songmid);
      return requestObj.promise.then((info) => info.al.picUrl);
    },
    getMusicDetailPageUrl(songInfo) {
      return `https://music.163.com/#/song?id=${songInfo.songmid}`;
    }
  };
  var wy_default = wy;

  // src/utils/musicSdk/kw/tipSearch.js
  var tipSearch_default = {
    regExps: {
      relWord: /RELWORD=(.+)/
    },
    requestObj: null,
    async tipSearchBySong(str) {
      this.cancelTipSearch();
      this.requestObj = httpFetch(`https://tips.kuwo.cn/t.s?corp=kuwo&newver=3&p2p=1&notrace=0&c=mbox&w=${encodeURIComponent(str)}&encoding=utf8&rformat=json`, {
        Referer: "http://www.kuwo.cn/"
      });
      return this.requestObj.promise.then(({ body, statusCode }) => {
        if (statusCode != 200 || !body.WORDITEMS) return Promise.reject(new Error("\u8BF7\u6C42\u5931\u8D25"));
        return body.WORDITEMS;
      });
    },
    handleResult(rawData) {
      return rawData.map((item) => item.RELWORD);
    },
    cancelTipSearch() {
      if (this.requestObj && this.requestObj.cancelHttp) this.requestObj.cancelHttp();
    },
    async search(str) {
      return this.tipSearchBySong(str).then((result) => this.handleResult(result));
    }
  };

  // src/utils/musicSdk/kw/decodeLyric.js
  var { inflate } = require_pako();
  var handleInflate = (data) => new Promise((resolve, reject) => {
    resolve(Buffer.from(inflate(data)));
  });
  var buf_key = Buffer.from("yeelion");
  var buf_key_len = buf_key.length;
  var decodeLyric = async (rawData, isGetLyricx) => {
    const buf = Buffer.isBuffer(rawData) ? rawData : Buffer.from(rawData);
    if (buf.toString("utf8", 0, 10).toLowerCase() !== "tp=content") return "";
    const lrcData = await handleInflate(buf.subarray(buf.indexOf("\r\n\r\n") + 4));
    if (!isGetLyricx) return lrcData.toString("utf8");
    const buf_str = Buffer.from(lrcData.toString(), "base64");
    const buf_str_len = buf_str.length;
    const output = new Uint8Array(buf_str_len);
    let i = 0;
    while (i < buf_str_len) {
      let j = 0;
      while (j < buf_key_len && i < buf_str_len) {
        output[i] = buf_str[i] ^ buf_key[j];
        i++;
        j++;
      }
    }
    return Buffer.from(output).toString("utf8");
  };
  var decodeLyric_default = async ({ lrcBuffer, isGetLyricx }) => {
    const lrc = await decodeLyric(lrcBuffer, isGetLyricx);
    return lrc;
  };

  // src/utils/musicSdk/kw/util.js
  var objStr2JSON = (str) => {
    return JSON.parse(str.replace(/('(?=(,\s*')))|('(?=:))|((?<=([:,]\s*))')|((?<={)')|('(?=}))/g, '"'));
  };
  var formatSinger = (rawData) => rawData.replace(/&/g, "\u3001");
  var lrcTools = {
    rxps: {
      wordLine: /^(\[\d{1,2}:.*\d{1,4}\])\s*(\S+(?:\s+\S+)*)?\s*/,
      tagLine: /\[(ver|ti|ar|al|offset|by|kuwo):\s*(\S+(?:\s+\S+)*)\s*\]/,
      wordTimeAll: /<(-?\d+),(-?\d+)(?:,-?\d+)?>/g,
      wordTime: /<(-?\d+),(-?\d+)(?:,-?\d+)?>/
    },
    offset: 1,
    offset2: 1,
    isOK: false,
    lines: [],
    tags: [],
    getWordInfo(str, str2, prevWord) {
      const offset = parseInt(str);
      const offset2 = parseInt(str2);
      let startTime = Math.trunc(Math.abs((offset + offset2) / (this.offset * 2)));
      let endTime = Math.trunc(Math.abs((offset - offset2) / (this.offset2 * 2)) + startTime);
      if (prevWord) {
        if (startTime < prevWord.endTime) {
          prevWord.endTime = startTime;
          if (prevWord.startTime > prevWord.endTime) {
            prevWord.startTime = prevWord.endTime;
          }
          prevWord.newTimeStr = `<${prevWord.startTime},${Math.trunc(prevWord.endTime - prevWord.startTime)}>`;
        }
      }
      return {
        startTime,
        endTime,
        timeStr: `<${startTime},${Math.trunc(endTime - startTime)}>`
      };
    },
    parseLine(line) {
      if (line.length < 6) return;
      let result = this.rxps.wordLine.exec(line);
      if (result) {
        const time = result[1];
        let words = result[2];
        if (words == null) {
          words = "";
        }
        const wordTimes = words.match(this.rxps.wordTimeAll);
        if (!wordTimes) return;
        let preTimeInfo;
        for (const timeStr of wordTimes) {
          const result2 = this.rxps.wordTime.exec(timeStr);
          const wordInfo = this.getWordInfo(result2[1], result2[2], preTimeInfo);
          words = words.replace(timeStr, wordInfo.timeStr);
          if (preTimeInfo?.newTimeStr) words = words.replace(preTimeInfo.timeStr, preTimeInfo.newTimeStr);
          preTimeInfo = wordInfo;
        }
        this.lines.push(time + words);
        return;
      }
      result = this.rxps.tagLine.exec(line);
      if (!result) return;
      if (result[1] == "kuwo") {
        let content = result[2];
        if (content != null && content.includes("][")) {
          content = content.substring(0, content.indexOf("]["));
        }
        const valueOf = parseInt(content, 8);
        this.offset = Math.trunc(valueOf / 10);
        this.offset2 = Math.trunc(valueOf % 10);
        if (this.offset == 0 || Number.isNaN(this.offset) || this.offset2 == 0 || Number.isNaN(this.offset2)) {
          this.isOK = false;
        }
      } else {
        this.tags.push(line);
      }
    },
    parse(lrc) {
      const lines = lrc.split(/\r\n|\r|\n/);
      const tools = Object.create(this);
      tools.isOK = true;
      tools.offset = 1;
      tools.offset2 = 1;
      tools.lines = [];
      tools.tags = [];
      for (const line of lines) {
        if (!tools.isOK) throw new Error("failed");
        tools.parseLine(line);
      }
      if (!tools.lines.length) return "";
      let lrcs = tools.lines.join("\n");
      if (tools.tags.length) lrcs = `${tools.tags.join("\n")}
${lrcs}`;
      return lrcs;
    }
  };
  var wbdCrypto = {
    aesMode: "aes-128-ecb",
    // aesKey: Buffer.from([112, 87, 39, 61, 199, 250, 41, 191, 57, 68, 45, 114, 221, 94, 140, 228], 'binary'),
    aesKey: "cFcnPcf6Kb85RC1y3V6M5A==",
    aesIv: "",
    appId: "y67sprxhhpws",
    decodeData(base64Result) {
      const data = decodeURIComponent(base64Result);
      return JSON.parse(aesDecryptSync(data, this.aesKey, this.aesIv, "AES" /* ECB_128_NoPadding */));
    },
    createSign(data, time) {
      const str = `${this.appId}${data}${time}`;
      return toMD5(str).toUpperCase();
    },
    buildParam(jsonData) {
      const data = Buffer.from(JSON.stringify(jsonData)).toString("base64");
      const time = Date.now();
      const encodeData = aesEncryptSync(data, this.aesKey, this.aesIv, "AES" /* ECB_128_NoPadding */);
      const sign = this.createSign(encodeData, time);
      return `data=${encodeURIComponent(encodeData)}&time=${time}&appId=${this.appId}&sign=${sign}`;
    }
  };

  // src/utils/musicSdk/kw/musicSearch.js
  var musicSearch_default2 = {
    regExps: {
      mInfo: /level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)/
    },
    limit: 30,
    total: 0,
    page: 0,
    allPage: 1,
    // cancelFn: null,
    musicSearch(str, page, limit) {
      const musicSearchRequestObj = httpFetch(`http://search.kuwo.cn/r.s?client=kt&all=${encodeURIComponent(str)}&pn=${page - 1}&rn=${limit}&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1`);
      return musicSearchRequestObj.promise;
    },
    // getImg(songId) {
    //   return httpGet(`http://player.kuwo.cn/webmusic/sj/dtflagdate?flag=6&rid=MUSIC_${songId}`)
    // },
    // getLrc(songId) {
    //   return httpGet(`http://mobile.kuwo.cn/mpage/html5/songinfoandlrc?mid=${songId}&flag=0`)
    // },
    handleResult(rawData) {
      const result = [];
      if (!rawData) return result;
      for (let i = 0; i < rawData.length; i++) {
        const info = rawData[i];
        let songId = info.MUSICRID.replace("MUSIC_", "");
        if (!info.N_MINFO) {
          console.log("N_MINFO is undefined");
          return null;
        }
        const types = [];
        const _types = {};
        let infoArr = info.N_MINFO.split(";");
        for (let info2 of infoArr) {
          info2 = info2.match(this.regExps.mInfo);
          if (info2) {
            switch (info2[2]) {
              case "4000":
                types.push({ type: "flac24bit", size: info2[4] });
                _types.flac24bit = {
                  size: info2[4].toLocaleUpperCase()
                };
                break;
              case "2000":
                types.push({ type: "flac", size: info2[4] });
                _types.flac = {
                  size: info2[4].toLocaleUpperCase()
                };
                break;
              case "320":
                types.push({ type: "320k", size: info2[4] });
                _types["320k"] = {
                  size: info2[4].toLocaleUpperCase()
                };
                break;
              case "128":
                types.push({ type: "128k", size: info2[4] });
                _types["128k"] = {
                  size: info2[4].toLocaleUpperCase()
                };
                break;
            }
          }
        }
        types.reverse();
        let interval = parseInt(info.DURATION);
        result.push({
          name: decodeName(info.SONGNAME),
          singer: formatSinger(decodeName(info.ARTIST)),
          source: "kw",
          // img = (info.album.name === '' || info.album.name === '空')
          //   ? `http://player.kuwo.cn/webmusic/sj/dtflagdate?flag=6&rid=MUSIC_160911.jpg`
          //   : `https://y.gtimg.cn/music/photo_new/T002R500x500M000${info.album.mid}.jpg`
          songmid: songId,
          albumId: decodeName(info.ALBUMID || ""),
          interval: Number.isNaN(interval) ? 0 : formatPlayTime(interval),
          albumName: info.ALBUM ? decodeName(info.ALBUM) : "",
          lrc: null,
          img: null,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        });
      }
      return result;
    },
    search(str, page = 1, limit, retryNum = 0) {
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then(({ body: result }) => {
        if (!result || result.TOTAL !== "0" && result.SHOW === "0") return this.search(str, page, limit, ++retryNum);
        let list = this.handleResult(result.abslist);
        if (list == null) return this.search(str, page, limit, ++retryNum);
        this.total = parseInt(result.TOTAL);
        this.page = page;
        this.allPage = Math.ceil(this.total / limit);
        return Promise.resolve({
          list,
          allPage: this.allPage,
          total: this.total,
          limit,
          source: "kw"
        });
      });
    }
  };

  // src/utils/musicSdk/kw/leaderboard.js
  var boardList = [{ id: "kw__93", name: "\u98D9\u5347\u699C", bangid: "93" }, { id: "kw__17", name: "\u65B0\u6B4C\u699C", bangid: "17" }, { id: "kw__16", name: "\u70ED\u6B4C\u699C", bangid: "16" }, { id: "kw__158", name: "\u6296\u97F3\u70ED\u6B4C\u699C", bangid: "158" }, { id: "kw__292", name: "\u94C3\u58F0\u699C", bangid: "292" }, { id: "kw__284", name: "\u70ED\u8BC4\u699C", bangid: "284" }, { id: "kw__290", name: "ACG\u65B0\u6B4C\u699C", bangid: "290" }, { id: "kw__286", name: "\u53F0\u6E7EKKBOX\u699C", bangid: "286" }, { id: "kw__279", name: "\u51AC\u65E5\u6696\u5FC3\u699C", bangid: "279" }, { id: "kw__281", name: "\u5DF4\u58EB\u968F\u8EAB\u542C\u699C", bangid: "281" }, { id: "kw__255", name: "KTV\u70B9\u5531\u699C", bangid: "255" }, { id: "kw__280", name: "\u5BB6\u52A1\u8FDB\u884C\u66F2\u699C", bangid: "280" }, { id: "kw__282", name: "\u71AC\u591C\u4FEE\u4ED9\u699C", bangid: "282" }, { id: "kw__283", name: "\u6795\u8FB9\u8F7B\u97F3\u4E50\u699C", bangid: "283" }, { id: "kw__278", name: "\u53E4\u98CE\u97F3\u4E50\u699C", bangid: "278" }, { id: "kw__264", name: "Vlog\u97F3\u4E50\u699C", bangid: "264" }, { id: "kw__242", name: "\u7535\u97F3\u699C", bangid: "242" }, { id: "kw__187", name: "\u6D41\u884C\u8D8B\u52BF\u699C", bangid: "187" }, { id: "kw__204", name: "\u73B0\u573A\u97F3\u4E50\u699C", bangid: "204" }, { id: "kw__186", name: "ACG\u795E\u66F2\u699C", bangid: "186" }, { id: "kw__185", name: "\u6700\u5F3A\u7FFB\u5531\u699C", bangid: "185" }, { id: "kw__26", name: "\u7ECF\u5178\u6000\u65E7\u699C", bangid: "26" }, { id: "kw__104", name: "\u534E\u8BED\u699C", bangid: "104" }, { id: "kw__182", name: "\u7CA4\u8BED\u699C", bangid: "182" }, { id: "kw__22", name: "\u6B27\u7F8E\u699C", bangid: "22" }, { id: "kw__184", name: "\u97E9\u8BED\u699C", bangid: "184" }, { id: "kw__183", name: "\u65E5\u8BED\u699C", bangid: "183" }, { id: "kw__145", name: "\u4F1A\u5458\u7545\u542C\u699C", bangid: "145" }, { id: "kw__153", name: "\u7F51\u7EA2\u65B0\u6B4C\u699C", bangid: "153" }, { id: "kw__64", name: "\u5F71\u89C6\u91D1\u66F2\u699C", bangid: "64" }, { id: "kw__176", name: "DJ\u55E8\u6B4C\u699C", bangid: "176" }, { id: "kw__106", name: "\u771F\u58F0\u97F3", bangid: "106" }, { id: "kw__12", name: "Billboard\u699C", bangid: "12" }, { id: "kw__49", name: "iTunes\u97F3\u4E50\u699C", bangid: "49" }, { id: "kw__180", name: "beatport\u7535\u97F3\u699C", bangid: "180" }, { id: "kw__13", name: "\u82F1\u56FDUK\u699C", bangid: "13" }, { id: "kw__164", name: "\u767E\u5927DJ\u699C", bangid: "164" }, { id: "kw__246", name: "YouTube\u97F3\u4E50\u6392\u884C\u699C", bangid: "246" }, { id: "kw__265", name: "\u97E9\u56FDGenie\u699C", bangid: "265" }, { id: "kw__14", name: "\u97E9\u56FDM-net\u699C", bangid: "14" }, { id: "kw__8", name: "\u9999\u6E2F\u7535\u53F0\u699C", bangid: "8" }, { id: "kw__15", name: "\u65E5\u672C\u516C\u4FE1\u699C", bangid: "15" }, { id: "kw__151", name: "\u817E\u8BAF\u97F3\u4E50\u4EBA\u539F\u521B\u699C", bangid: "151" }];
  var sortQualityArray = (array) => {
    const qualityMap = {
      flac24bit: 4,
      flac: 3,
      "320k": 2,
      "128k": 1
    };
    const rawQualityArray = [];
    const newQualityArray = [];
    array.forEach((item, index) => {
      const type = qualityMap[item.type];
      if (!type) return;
      rawQualityArray.push({ type, index });
    });
    rawQualityArray.sort((a, b) => a.type - b.type);
    rawQualityArray.forEach((item) => {
      newQualityArray.push(array[item.index]);
    });
    return newQualityArray;
  };
  var leaderboard_default2 = {
    list: [
      {
        id: "kwbiaosb",
        name: "\u98D9\u5347\u699C",
        bangid: 93
      },
      {
        id: "kwregb",
        name: "\u70ED\u6B4C\u699C",
        bangid: 16
      },
      {
        id: "kwhuiyb",
        name: "\u4F1A\u5458\u699C",
        bangid: 145
      },
      {
        id: "kwdouyb",
        name: "\u6296\u97F3\u699C",
        bangid: 158
      },
      {
        id: "kwqsb",
        name: "\u8D8B\u52BF\u699C",
        bangid: 187
      },
      {
        id: "kwhuaijb",
        name: "\u6000\u65E7\u699C",
        bangid: 26
      },
      {
        id: "kwhuayb",
        name: "\u534E\u8BED\u699C",
        bangid: 104
      },
      {
        id: "kwyueyb",
        name: "\u7CA4\u8BED\u699C",
        bangid: 182
      },
      {
        id: "kwoumb",
        name: "\u6B27\u7F8E\u699C",
        bangid: 22
      },
      {
        id: "kwhanyb",
        name: "\u97E9\u8BED\u699C",
        bangid: 184
      },
      {
        id: "kwriyb",
        name: "\u65E5\u8BED\u699C",
        bangid: 183
      }
    ],
    // getUrl: (p, l, id) => `http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn=${p - 1}&rn=${l}&type=bang&data=content&id=${id}&show_copyright_off=0&pcmp4=1&isbang=1`,
    regExps: {
      mInfo: /level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)/
    },
    limit: 100,
    _requestBoardsObj: null,
    getBoardsData() {
      if (this._requestBoardsObj) this._requestBoardsObj.cancelHttp();
      this._requestBoardsObj = httpFetch("http://qukudata.kuwo.cn/q.k?op=query&cont=tree&node=2&pn=0&rn=1000&fmt=json&level=2");
      return this._requestBoardsObj.promise;
    },
    getData(url) {
      const requestDataObj = httpFetch(url);
      return requestDataObj.promise;
    },
    filterData(rawList) {
      return rawList.map((item) => {
        let types = [];
        const _types = {};
        const qualitys = /* @__PURE__ */ new Set();
        item.n_minfo.split(";").forEach((i) => {
          const info = i.match(this.regExps.mInfo);
          if (!info) return;
          const quality = info[2];
          const size = info[4].toLocaleUpperCase();
          if (qualitys.has(quality)) return;
          qualitys.add(quality);
          switch (quality) {
            case "4000":
              types.push({ type: "flac24bit", size });
              _types.flac24bit = { size };
              break;
            case "2000":
              types.push({ type: "flac", size });
              _types.flac = { size };
              break;
            case "320":
              types.push({ type: "320k", size });
              _types["320k"] = { size };
              break;
            case "128":
              types.push({ type: "128k", size });
              _types["128k"] = { size };
              break;
          }
        });
        types = sortQualityArray(types);
        return {
          singer: formatSinger(decodeName(item.artist)),
          name: decodeName(item.name),
          albumName: decodeName(item.album),
          albumId: item.albumId,
          songmid: item.id,
          source: "kw",
          interval: formatPlayTime(parseInt(item.duration)),
          img: item.pic,
          lrc: null,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    filterBoardsData(rawList) {
      let list = [];
      for (const board of rawList) {
        if (board.source != "1") continue;
        list.push({
          id: "kw__" + board.sourceid,
          name: board.name,
          bangid: String(board.sourceid)
        });
      }
      return list;
    },
    async getBoards(retryNum = 0) {
      this.list = boardList;
      return {
        list: boardList,
        source: "kw"
      };
    },
    getList(id, page, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      const requestBody = { uid: "", devId: "", sFrom: "kuwo_sdk", user_type: "AP", carSource: "kwplayercar_ar_6.0.1.0_apk_keluze.apk", id, pn: page - 1, rn: this.limit };
      const requestUrl = `https://wbd.kuwo.cn/api/bd/bang/bang_info?${wbdCrypto.buildParam(requestBody)}`;
      const request = httpFetch(requestUrl, { cache: "default" }).promise;
      return request.then(({ statusCode, body }) => {
        const rawData = wbdCrypto.decodeData(body);
        const data = rawData.data;
        if (statusCode !== 200 || rawData.code != 200 || !data.musiclist) return this.getList(id, page, retryNum);
        const total = parseInt(data.total);
        const list = this.filterData(data.musiclist);
        return {
          total,
          list,
          limit: this.limit,
          page,
          source: "kw"
        };
      });
    }
    // getDetailPageUrl(id) {
    //   return `http://www.kuwo.cn/rankList/${id}`
    // },
  };

  // src/utils/musicSdk/kw/lyric.js
  var timeExp = /^\[([\d:.]*)\]{1}/g;
  var existTimeExp = /\[\d{1,2}:.*\d{1,4}]/;
  var lyricxTag = /^<-?\d+,-?\d+>/;
  var lyric_default2 = {
    /* sortLrcArr(arr) {
        const lrcSet = new Set()
        let lrc = []
        let lrcT = []
        let markIndex = []
        for (const item of arr) {
          if (lrcSet.has(item.time)) {
            if (lrc.length < 2) continue
            const index = lrc.findIndex(l => l.time == item.time)
            markIndex.push(index)
            if (index == lrc.length - 1) {
              lrcT.push({ ...lrc[index], time: item.time })
              lrc.push(item)
            } else {
              lrcT.push({ ...lrc[index], time: lrc[index + 1].time })
              if (item.text) {
                //   const lastIndex = lrc.length - 1
                //   markIndex.push(lastIndex)
                //   lrcT.push({ ...lrc[lastIndex], time: lrc[lastIndex - 1].time })
                lrc.push(item)
              }
            }
          } else {
            lrc.push(item)
            lrcSet.add(item.time)
          }
        }
    
        // console.log(markIndex)
        markIndex = Array.from(new Set(markIndex))
        for (let index = markIndex.length - 1; index >= 0; index--) {
          lrc.splice(markIndex[index], 1)
        }
    
        // if (lrcT.length) {
        //   if (lrc.length * 0.4 < lrcT.length) { // 翻译数量需大于歌词数量的0.4倍，否则认为没有翻译
        //     const tItem = lrc.pop()
        //     tItem.time = lrc[lrc.length - 1].time
        //     lrcT.push(tItem)
        //   } else {
        //     lrc = arr
        //     lrcT = []
        //   }
        // }
    
        console.log(lrc, lrcT)
    
        return {
          lrc,
          lrcT,
        }
      }, */
    sortLrcArr(arr) {
      const lrcSet = /* @__PURE__ */ new Set();
      let lrc = [];
      let lrcT = [];
      let isLyricx = false;
      for (const item of arr) {
        if (lrcSet.has(item.time)) {
          if (lrc.length < 2) continue;
          const tItem = lrc.pop();
          tItem.time = lrc[lrc.length - 1].time;
          lrcT.push(tItem);
          lrc.push(item);
        } else {
          lrc.push(item);
          lrcSet.add(item.time);
        }
        if (!isLyricx && lyricxTag.test(item.text)) isLyricx = true;
      }
      if (!isLyricx && lrcT.length > lrc.length * 0.3 && lrc.length - lrcT.length > 6) {
        throw new Error("failed");
      }
      return {
        lrc,
        lrcT
      };
    },
    transformLrc(tags, lrclist) {
      return `${tags.join("\n")}
${lrclist ? lrclist.map((l) => `[${l.time}]${l.text}
`).join("") : "\u6682\u65E0\u6B4C\u8BCD"}`;
    },
    parseLrc(lrc) {
      const lines = lrc.split(/\r\n|\r|\n/);
      let tags = [];
      let lrcArr = [];
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        let result = timeExp.exec(line);
        if (result) {
          const text = line.replace(timeExp, "").trim();
          let time = RegExp.$1;
          if (/\.\d\d$/.test(time)) time += "0";
          lrcArr.push({
            time,
            text
          });
        } else if (lrcTools.rxps.tagLine.test(line)) {
          tags.push(line);
        }
      }
      const lrcInfo = this.sortLrcArr(lrcArr);
      return {
        lyric: decodeName(this.transformLrc(tags, lrcInfo.lrc)),
        tlyric: lrcInfo.lrcT.length ? decodeName(this.transformLrc(tags, lrcInfo.lrcT)) : ""
      };
    },
    // getLyric2(musicInfo, isGetLyricx = true) {
    //   const requestObj = httpFetch(`http://newlyric.kuwo.cn/newlyric.lrc?${buildParams(musicInfo.songmid, isGetLyricx)}`)
    //   requestObj.promise = requestObj.promise.then(({ statusCode, body, raw }) => {
    //     if (statusCode != 200) return Promise.reject(new Error(JSON.stringify(body)))
    //     return decodeLyric({ lrcBase64: raw.toString('base64'), isGetLyricx }).then(base64Data => {
    //       let lrcInfo
    //       console.log(Buffer.from(base64Data, 'base64').toString())
    //       try {
    //         lrcInfo = this.parseLrc(Buffer.from(base64Data, 'base64').toString())
    //       } catch {
    //         return Promise.reject(new Error('Get lyric failed'))
    //       }
    //       if (lrcInfo.tlyric) lrcInfo.tlyric = lrcInfo.tlyric.replace(lrcTools.rxps.wordTimeAll, '')
    //       lrcInfo.lxlyric = lrcTools.parse(lrcInfo.lyric)
    //       // console.log(lrcInfo.lyric)
    //       // console.log(lrcInfo.tlyric)
    //       // console.log(lrcInfo.lxlyric)
    //       // console.log(JSON.stringify(lrcInfo))
    //     })
    //   })
    //   return requestObj
    // },
    getLyric(musicInfo, isGetLyricx = true) {
      const requestObj = httpFetch(`http://mlyric.kuwo.cn/mobi.s?f=web&type=lyric&lrcx=${isGetLyricx ? 1 : 0}&rid=${musicInfo.songmid}&encode=utf8`, {
        cache: false,
        binary: true
      });
      requestObj.promise = requestObj.promise.then(async ({ statusCode, body }) => {
        if (statusCode != 200) return Promise.reject(new Error(JSON.stringify(body)));
        const lrcText = await decodeLyric_default({ lrcBuffer: body, isGetLyricx });
        let lrcInfo;
        try {
          lrcInfo = this.parseLrc(lrcText);
        } catch (err) {
          return Promise.reject(new Error("Get lyric failed"));
        }
        if (lrcInfo.tlyric) lrcInfo.tlyric = lrcInfo.tlyric.replace(lrcTools.rxps.wordTimeAll, "");
        try {
          lrcInfo.lxlyric = lrcTools.parse(lrcInfo.lyric);
        } catch {
          lrcInfo.lxlyric = "";
        }
        lrcInfo.lyric = lrcInfo.lyric.replace(lrcTools.rxps.wordTimeAll, "");
        if (!existTimeExp.test(lrcInfo.lyric)) return Promise.reject(new Error("Get lyric failed"));
        return lrcInfo;
      });
      return requestObj;
    }
  };

  // src/utils/musicSdk/kw/pic.js
  var pic_default = {
    getPic({ songmid }) {
      const requestObj = httpFetch(`http://artistpicserver.kuwo.cn/pic.web?corp=kuwo&type=rid_pic&pictype=500&size=500&rid=${songmid}`);
      requestObj.promise = requestObj.promise.then(({ body }) => /^http/.test(body) ? body : null);
      return requestObj.promise;
    }
  };

  // src/utils/musicSdk/kw/album.js
  var album_default = {
    limit_list: 36,
    limit_song: 1e3,
    filterListDetail(rawList, albumName, albumId) {
      return rawList.map((item, inedx) => {
        let formats = item.formats.split("|");
        let types = [];
        let _types = {};
        if (formats.includes("MP3128")) {
          types.push({ type: "128k", size: null });
          _types["128k"] = {
            size: null
          };
        }
        if (formats.includes("MP3H")) {
          types.push({ type: "320k", size: null });
          _types["320k"] = {
            size: null
          };
        }
        if (formats.includes("ALFLAC")) {
          types.push({ type: "flac", size: null });
          _types.flac = {
            size: null
          };
        }
        if (formats.includes("HIRFLAC")) {
          types.push({ type: "flac24bit", size: null });
          _types.flac24bit = {
            size: null
          };
        }
        return {
          singer: formatSinger(decodeName(item.artist)),
          name: decodeName(item.name),
          albumName,
          albumId,
          songmid: item.id,
          source: "kw",
          interval: null,
          img: item.pic,
          lrc: null,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    /**
     * 格式化播放数量
     * @param {*} num
     */
    formatPlayCount(num) {
      if (num > 1e8) return parseInt(num / 1e7) / 10 + "\u4EBF";
      if (num > 1e4) return parseInt(num / 1e3) / 10 + "\u4E07";
      return num;
    },
    getAlbumListDetail(id, page, retryNum = 0) {
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      const requestObj_listDetail = httpFetch(`http://search.kuwo.cn/r.s?pn=${page - 1}&rn=${this.limit_song}&stype=albuminfo&albumid=${id}&show_copyright_off=0&encoding=utf&vipver=MUSIC_9.1.0`);
      return requestObj_listDetail.promise.then(({ statusCode, body }) => {
        if (statusCode !== 200) return this.getAlbumListDetail(id, page, ++retryNum);
        body = objStr2JSON(body);
        if (!body.musiclist) return this.getAlbumListDetail(id, page, ++retryNum);
        body.name = decodeName(body.name);
        return {
          list: this.filterListDetail(body.musiclist, body.name, body.albumid),
          page,
          limit: this.limit_song,
          total: parseInt(body.songnum),
          source: "kw",
          info: {
            name: body.name,
            img: body.img || body.hts_img,
            desc: decodeName(body.info),
            author: decodeName(body.artist)
            // play_count: this.formatPlayCount(body.playnum),
          }
        };
      });
    }
    // getAlbumListDetail(id, page, retryNum = 0) {
    //   if (retryNum > 2) return Promise.reject(new Error('try max num'))
    //   return tokenRequest(`http://www.kuwo.cn/api/www/album/albumInfo?albumId=${id}&pn=${page}&rn=${this.limit_song}&httpsStatus=1`).then((resp) => {
    //     return resp.promise.then(({ statusCode, body }) => {
    //       console.log(body)
    //       return Promise.reject(new Error('failed'))
    //       // if (statusCode !== 200) return this.getAlbumListDetail(id, page, ++retryNum)
    //       // const data = body.data
    //       // console.log(data)
    //       // if (!data.musicList) return this.getAlbumListDetail(id, page, ++retryNum)
    //       // return {
    //       //   list: this.filterListDetail(data.musiclist),
    //       //   page,
    //       //   limit: this.limit_song,
    //       //   total: data.total,
    //       //   source: 'kw',
    //       //   info: {
    //       //     name: data.album,
    //       //     img: data.pic,
    //       //     desc: data.albuminfo,
    //       //     author: data.artist,
    //       //     play_count: this.formatPlayCount(data.playCnt),
    //       //   },
    //       // }
    //     })
    //   })
    // },
  };

  // src/utils/musicSdk/kw/songList.js
  var songList_default2 = {
    _requestObj_tags: null,
    _requestObj_hotTags: null,
    _requestObj_list: null,
    limit_list: 36,
    limit_song: 1e3,
    successCode: 200,
    sortList: [
      {
        name: "\u6700\u65B0",
        tid: "new",
        id: "new"
      },
      {
        name: "\u6700\u70ED",
        tid: "hot",
        id: "hot"
      }
    ],
    regExps: {
      mInfo: /level:(\w+),bitrate:(\d+),format:(\w+),size:([\w.]+)/,
      // http://www.kuwo.cn/playlist_detail/2886046289
      // https://m.kuwo.cn/h5app/playlist/2736267853?t=qqfriend
      listDetailLink: /^.+\/playlist(?:_detail)?\/(\d+)(?:\?.*|&.*$|#.*$|$)/
    },
    tagsUrl: "http://wapi.kuwo.cn/api/pc/classify/playlist/getTagList?cmd=rcm_keyword_playlist&user=0&prod=kwplayer_pc_9.0.5.0&vipver=9.0.5.0&source=kwplayer_pc_9.0.5.0&loginUid=0&loginSid=0&appUid=76039576",
    hotTagUrl: "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmTagList?loginUid=0&loginSid=0&appUid=76039576",
    getListUrl({ sortId, id, type, page }) {
      if (!id) return `http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0&appUid=76039576&&pn=${page}&rn=${this.limit_list}&order=${sortId}`;
      switch (type) {
        case "10000":
          return `http://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?loginUid=0&loginSid=0&appUid=76039576&pn=${page}&id=${id}&rn=${this.limit_list}`;
        case "43":
          return `http://mobileinterfaces.kuwo.cn/er.s?type=get_pc_qz_data&f=web&id=${id}&prod=pc`;
      }
    },
    getListDetailUrl(id, page) {
      return `http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${id}&pn=${page - 1}&rn=${this.limit_song}&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1`;
    },
    // http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=2849349915&pn=0&rn=100&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1
    // 获取标签
    getTag(tryNum = 0) {
      if (this._requestObj_tags) this._requestObj_tags.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_tags = httpFetch(this.tagsUrl);
      return this._requestObj_tags.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getTag(++tryNum);
        return this.filterTagInfo(body.data);
      });
    },
    // 获取标签
    getHotTag(tryNum = 0) {
      if (this._requestObj_hotTags) this._requestObj_hotTags.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_hotTags = httpFetch(this.hotTagUrl);
      return this._requestObj_hotTags.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getHotTag(++tryNum);
        return this.filterInfoHotTag(body.data[0].data);
      });
    },
    filterInfoHotTag(rawList) {
      return rawList.map((item) => ({
        id: `${item.id}-${item.digest}`,
        name: item.name,
        source: "kw"
      }));
    },
    filterTagInfo(rawList) {
      return rawList.map((type) => ({
        name: type.name,
        list: type.data.map((item) => ({
          parent_id: type.id,
          parent_name: type.name,
          id: `${item.id}-${item.digest}`,
          name: item.name,
          source: "kw"
        }))
      }));
    },
    // 获取列表数据
    getList(sortId, tagId, page, tryNum = 0) {
      if (this._requestObj_list) this._requestObj_list.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      let id;
      let type;
      if (tagId) {
        let arr = tagId.split("-");
        id = arr[0];
        type = arr[1];
      } else {
        id = null;
      }
      this._requestObj_list = httpFetch(this.getListUrl({ sortId, id, type, page }));
      return this._requestObj_list.promise.then(({ body }) => {
        if (!id || type == "10000") {
          if (body.code !== this.successCode) return this.getList(sortId, tagId, page, ++tryNum);
          return {
            list: this.filterList(body.data.data),
            total: body.data.total,
            page: body.data.pn,
            limit: body.data.rn,
            source: "kw"
          };
        } else if (!body.length) {
          return this.getList(sortId, tagId, page, ++tryNum);
        }
        return {
          list: this.filterList2(body),
          total: 1e3,
          page,
          limit: 1e3,
          source: "kw"
        };
      });
    },
    /**
     * 格式化播放数量
     * @param {*} num
     */
    formatPlayCount(num) {
      if (num > 1e8) return parseInt(num / 1e7) / 10 + "\u4EBF";
      if (num > 1e4) return parseInt(num / 1e3) / 10 + "\u4E07";
      return num;
    },
    filterList(rawData) {
      return rawData.map((item) => ({
        play_count: this.formatPlayCount(item.listencnt),
        id: `digest-${item.digest}__${item.id}`,
        author: item.uname,
        name: item.name,
        // time: item.publish_time,
        total: item.total,
        img: item.img,
        grade: item.favorcnt / 10,
        desc: item.desc,
        source: "kw"
      }));
    },
    filterList2(rawData) {
      const list = [];
      const allowedTypes = ["songlist", "list", "album"];
      rawData.forEach((item) => {
        item.list.forEach((item2) => {
          if (!allowedTypes.includes(item2.type)) return;
          list.push({
            play_count: item2.play_count && this.formatPlayCount(item2.listencnt),
            id: `digest-${item2.digest}__${item2.id}`,
            author: item2.uname,
            name: item2.name,
            total: item2.total,
            // time: item.publish_time,
            img: item2.img,
            grade: item2.favorcnt && item2.favorcnt / 10,
            desc: item2.desc,
            source: "kw"
          });
        });
      });
      return list;
    },
    getListDetailDigest8(id, page, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      const requestObj = httpFetch(this.getListDetailUrl(id, page));
      return requestObj.promise.then(({ body }) => {
        if (body.result !== "ok") return this.getListDetail(id, page, ++tryNum);
        return {
          list: this.filterListDetail(body.musiclist),
          page,
          limit: body.rn,
          total: body.total,
          source: "kw",
          info: {
            name: body.title,
            img: body.pic,
            desc: body.info,
            author: body.uname,
            play_count: this.formatPlayCount(body.playnum)
          }
        };
      });
    },
    getListDetailDigest5Info(id, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      const requestObj = httpFetch(`http://qukudata.kuwo.cn/q.k?op=query&cont=ninfo&node=${id}&pn=0&rn=1&fmt=json&src=mbox&level=2`);
      return requestObj.promise.then(({ statusCode, body }) => {
        if (statusCode != 200 || !body.child) return this.getListDetail(id, ++tryNum);
        return body.child.length ? body.child[0].sourceid : null;
      });
    },
    getListDetailDigest5Music(id, page, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      const requestObj = httpFetch(`http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${id}&pn=${page - 1}}&rn=${this.limit_song}&encode=utf-8&keyset=pl2012&identity=kuwo&pcmp4=1`);
      return requestObj.promise.then(({ body }) => {
        if (body.result !== "ok") return this.getListDetail(id, page, ++tryNum);
        return {
          list: this.filterListDetail(body.musiclist),
          page,
          limit: body.rn,
          total: body.total,
          source: "kw",
          info: {
            name: body.title,
            img: body.pic,
            desc: body.info,
            author: body.uname,
            play_count: this.formatPlayCount(body.playnum)
          }
        };
      });
    },
    async getListDetailDigest5(id, page, retryNum) {
      const detailId = await this.getListDetailDigest5Info(id, retryNum);
      return this.getListDetailDigest5Music(detailId, page, retryNum);
    },
    filterBDListDetail(rawList) {
      return rawList.map((item) => {
        let types = [];
        let _types = {};
        for (let info of item.audios) {
          info.size = info.size?.toLocaleUpperCase();
          switch (info.bitrate) {
            case "4000":
              types.push({ type: "flac24bit", size: info.size });
              _types.flac24bit = {
                size: info.size
              };
              break;
            case "2000":
              types.push({ type: "flac", size: info.size });
              _types.flac = {
                size: info.size
              };
              break;
            case "320":
              types.push({ type: "320k", size: info.size });
              _types["320k"] = {
                size: info.size
              };
              break;
            case "128":
              types.push({ type: "128k", size: info.size });
              _types["128k"] = {
                size: info.size
              };
              break;
          }
        }
        types.reverse();
        return {
          singer: item.artists.map((s) => s.name).join("\u3001"),
          name: item.name,
          albumName: item.album,
          albumId: item.albumId,
          songmid: item.id,
          source: "kw",
          interval: formatPlayTime(item.duration),
          img: item.albumPic,
          releaseDate: item.releaseDate,
          lrc: null,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    getReqId() {
      function t() {
        return (65536 * (1 + Math.random()) | 0).toString(16).substring(1);
      }
      return t() + t() + t() + t() + t() + t() + t() + t();
    },
    async getListDetailMusicListByBDListInfo(id, source) {
      const { body: infoData } = await httpFetch(`https://bd-api.kuwo.cn/api/service/playlist/info/${id}?reqId=${this.getReqId()}&source=${source}`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36",
          plat: "h5"
        }
      }).promise.catch(() => ({ code: 0 }));
      if (infoData.code != 200) return null;
      return {
        name: infoData.data.name,
        img: infoData.data.pic,
        desc: infoData.data.description,
        author: infoData.data.creatorName,
        play_count: infoData.data.playNum
      };
    },
    async getListDetailMusicListByBDUserPub(id) {
      const { body: infoData } = await httpFetch(`https://bd-api.kuwo.cn/api/ucenter/users/pub/${id}?reqId=${this.getReqId()}`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36",
          plat: "h5"
        }
      }).promise.catch(() => ({ code: 0 }));
      if (infoData.code != 200) return null;
      return {
        name: infoData.data.userInfo.nickname + "\u559C\u6B22\u7684\u97F3\u4E50",
        img: infoData.data.userInfo.headImg,
        desc: "",
        author: infoData.data.userInfo.nickname,
        play_count: ""
      };
    },
    async getListDetailMusicListByBDList(id, source, page, tryNum = 0) {
      const { body: listData } = await httpFetch(`https://bd-api.kuwo.cn/api/service/playlist/${id}/musicList?reqId=${this.getReqId()}&source=${source}&pn=${page}&rn=${this.limit_song}`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36",
          plat: "h5"
        }
      }).promise.catch(() => {
        if (tryNum > 2) return Promise.reject(new Error("try max num"));
        return this.getListDetailMusicListByBDList(id, source, page, ++tryNum);
      });
      if (listData.code !== 200) return Promise.reject(new Error("failed"));
      return {
        list: this.filterBDListDetail(listData.data.list),
        page,
        limit: listData.data.pageSize,
        total: listData.data.total,
        source: "kw"
      };
    },
    async getListDetailMusicListByBD(id, page) {
      const uid = /uid=(\d+)/.exec(id)?.[1];
      const listId = /playlistId=(\d+)/.exec(id)?.[1];
      const source = /source=(\d+)/.exec(id)?.[1];
      if (!listId) return Promise.reject(new Error("failed"));
      const task = [this.getListDetailMusicListByBDList(listId, source, page)];
      switch (source) {
        case "4":
          task.push(this.getListDetailMusicListByBDListInfo(listId, source));
          break;
        case "5":
          task.push(this.getListDetailMusicListByBDUserPub(uid ?? listId));
          break;
      }
      const [listData, info] = await Promise.all(task);
      listData.info = info ?? {
        name: "",
        img: "",
        desc: "",
        author: "",
        play_count: ""
      };
      return listData;
    },
    // 获取歌曲列表内的音乐
    getListDetail(id, page, retryNum = 0) {
      if (/\/bodian\//.test(id)) return this.getListDetailMusicListByBD(id, page);
      if (/[?&:/]/.test(id)) id = id.replace(this.regExps.listDetailLink, "$1");
      else if (/^digest-/.test(id)) {
        let [digest, _id] = id.split("__");
        digest = digest.replace("digest-", "");
        id = _id;
        switch (digest) {
          case "8":
            break;
          case "13":
            return album_default.getAlbumListDetail(id, page, retryNum);
          case "5":
          default:
            return this.getListDetailDigest5(id, page, retryNum);
        }
      }
      return this.getListDetailDigest8(id, page, retryNum);
    },
    filterListDetail(rawData) {
      return rawData.map((item) => {
        let infoArr = item.N_MINFO.split(";");
        let types = [];
        let _types = {};
        for (let info of infoArr) {
          info = info.match(this.regExps.mInfo);
          if (info) {
            switch (info[2]) {
              case "4000":
                types.push({ type: "flac24bit", size: info[4] });
                _types.flac24bit = {
                  size: info[4].toLocaleUpperCase()
                };
                break;
              case "2000":
                types.push({ type: "flac", size: info[4] });
                _types.flac = {
                  size: info[4].toLocaleUpperCase()
                };
                break;
              case "320":
                types.push({ type: "320k", size: info[4] });
                _types["320k"] = {
                  size: info[4].toLocaleUpperCase()
                };
                break;
              case "128":
                types.push({ type: "128k", size: info[4] });
                _types["128k"] = {
                  size: info[4].toLocaleUpperCase()
                };
                break;
            }
          }
        }
        types.reverse();
        return {
          singer: formatSinger(decodeName(item.artist)),
          name: decodeName(item.name),
          albumName: decodeName(item.album),
          albumId: item.albumid,
          songmid: item.id,
          source: "kw",
          interval: formatPlayTime(parseInt(item.duration)),
          img: null,
          lrc: null,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    getTags() {
      return Promise.all([this.getTag(), this.getHotTag()]).then(([tags, hotTag]) => ({ tags, hotTag, source: "kw" }));
    },
    getDetailPageUrl(id) {
      if (/[?&:/]/.test(id)) id = id.replace(this.regExps.listDetailLink, "$1");
      else if (/^digest-/.test(id)) {
        let result = id.split("__");
        id = result[1];
      }
      return `http://www.kuwo.cn/playlist_detail/${id}`;
    },
    search(text, page, limit = 20) {
      return httpFetch(`http://search.kuwo.cn/r.s?all=${encodeURIComponent(text)}&pn=${page - 1}&rn=${limit}&rformat=json&encoding=utf8&ver=mbox&vipver=MUSIC_8.7.7.0_BCS37&plat=pc&devid=28156413&ft=playlist&pay=0&needliveshow=0`).promise.then(({ body }) => {
        body = objStr2JSON(body);
        return {
          list: body.abslist.map((item) => {
            return {
              play_count: this.formatPlayCount(item.playcnt),
              id: item.playlistid,
              author: decodeName(item.nickname),
              name: decodeName(item.name),
              total: item.songnum,
              // time: item.publish_time,
              img: item.pic,
              desc: decodeName(item.intro),
              source: "kw"
            };
          }),
          limit,
          total: parseInt(body.TOTAL),
          source: "kw"
        };
      });
    }
  };

  // src/utils/musicSdk/kw/hotSearch.js
  var hotSearch_default2 = {
    _requestObj: null,
    async getList(retryNum = 0) {
      if (this._requestObj) this._requestObj.cancelHttp();
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      const _requestObj = httpFetch("http://hotword.kuwo.cn/hotword.s?prod=kwplayer_ar_9.3.0.1&corp=kuwo&newver=2&vipver=9.3.0.1&source=kwplayer_ar_9.3.0.1_40.apk&p2p=1&notrace=0&uid=0&plat=kwplayer_ar&rformat=json&encoding=utf8&tabid=1", {
        headers: {
          "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 9;)"
        }
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.status !== "ok") throw new Error("\u83B7\u53D6\u70ED\u641C\u8BCD\u5931\u8D25");
      return { source: "kw", list: this.filterList(body.tagvalue) };
    },
    filterList(rawList) {
      return rawList.map((item) => item.key);
    }
  };

  // src/utils/musicSdk/kw/comment.js
  var comment_default2 = {
    _requestObj: null,
    _requestObj2: null,
    async getComment({ songmid }, page = 1, limit = 20) {
      if (this._requestObj) this._requestObj.cancelHttp();
      const _requestObj = httpFetch(`http://ncomment.kuwo.cn/com.s?f=web&type=get_comment&aapiver=1&prod=kwplayer_ar_10.5.2.0&digest=15&sid=${songmid}&start=${limit * (page - 1)}&msgflag=1&count=${limit}&newver=3&uid=0`, {
        headers: {
          "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 9;)"
        }
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.code != "200") throw new Error("\u83B7\u53D6\u8BC4\u8BBA\u5931\u8D25");
      const total = body.comments_counts;
      return {
        source: "kw",
        comments: this.filterComment(body.comments),
        total,
        page,
        limit,
        maxPage: Math.ceil(total / limit) || 1
      };
    },
    async getHotComment({ songmid }, page = 1, limit = 100) {
      if (this._requestObj2) this._requestObj2.cancelHttp();
      const _requestObj2 = httpFetch(`http://ncomment.kuwo.cn/com.s?f=web&type=get_rec_comment&aapiver=1&prod=kwplayer_ar_10.5.2.0&digest=15&sid=${songmid}&start=${limit * (page - 1)}&msgflag=1&count=${limit}&newver=3&uid=0`, {
        headers: {
          "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 9;)"
        }
      });
      const { body, statusCode } = await _requestObj2.promise;
      if (statusCode != 200 || body.code != "200") throw new Error("\u83B7\u53D6\u70ED\u95E8\u8BC4\u8BBA\u5931\u8D25");
      const total = body.hot_comments_counts;
      return {
        source: "kw",
        comments: this.filterComment(body.hot_comments),
        total,
        page,
        limit,
        maxPage: Math.ceil(total / limit) || 1
      };
    },
    filterComment(rawList) {
      if (!rawList) return [];
      return rawList.map((item) => {
        return {
          id: item.id,
          text: item.msg,
          time: item.time,
          timeStr: dateFormat2(Number(item.time) * 1e3),
          userName: item.u_name,
          avatar: item.u_pic,
          userId: item.u_id,
          likedCount: item.like_num,
          images: item.mpic ? [decodeURIComponent(item.mpic)] : [],
          reply: item.child_comments ? item.child_comments.map((i) => {
            return {
              id: i.id,
              text: i.msg,
              time: i.time,
              timeStr: dateFormat2(Number(i.time) * 1e3),
              userName: i.u_name,
              avatar: i.u_pic,
              userId: i.u_id,
              likedCount: i.like_num,
              images: i.mpic ? [i.mpic] : []
            };
          }) : []
        };
      });
    }
  };

  // src/utils/musicSdk/kw/index.js
  var kw = {
    _musicInfoRequestObj: null,
    _musicInfoPromiseCancelFn: null,
    _musicPicRequestObj: null,
    _musicPicPromiseCancelFn: null,
    // context: null,
    // init(context) {
    //   if (this.isInited) return
    //   this.isInited = true
    //   this.context = context
    //   // this.musicSearch.search('我又想你了').then(res => {
    //   //   console.log(res)
    //   // })
    //   // this.getMusicUrl('62355680', '320k').then(url => {
    //   //   console.log(url)
    //   // })
    // },
    tipSearch: tipSearch_default,
    musicSearch: musicSearch_default2,
    leaderboard: leaderboard_default2,
    songList: songList_default2,
    hotSearch: hotSearch_default2,
    comment: comment_default2,
    getLyric(songInfo, isGetLyricx) {
      return lyric_default2.getLyric(songInfo, isGetLyricx);
    },
    handleMusicInfo(songInfo) {
      return this.getMusicInfo(songInfo).then((info) => {
        songInfo.name = info.name;
        songInfo.singer = formatSinger(info.artist);
        songInfo.img = info.pic;
        songInfo.albumName = info.album;
        return songInfo;
      });
    },
    getMusicUrl(songInfo, type) {
      return apis("kw").getMusicUrl(songInfo, type);
    },
    getMusicInfo(songInfo) {
      if (this._musicInfoRequestObj) this._musicInfoRequestObj.cancelHttp();
      this._musicInfoRequestObj = httpFetch(`http://www.kuwo.cn/api/www/music/musicInfo?mid=${songInfo.songmid}`);
      return this._musicInfoRequestObj.promise.then(({ body }) => {
        return body.code === 200 ? body.data : Promise.reject(new Error(body.msg));
      });
    },
    getMusicUrls(musicInfo, cb) {
      let tasks = [];
      let songId = musicInfo.songmid;
      musicInfo.types.forEach((type) => {
        tasks.push(kw.getMusicUrl(songId, type.type).promise);
      });
      Promise.all(tasks).then((urlInfo) => {
        let typeUrl = {};
        urlInfo.forEach((info) => {
          typeUrl[info.type] = info.url;
        });
        cb(typeUrl);
      });
    },
    getPic(songInfo) {
      return pic_default.getPic(songInfo);
    },
    getMusicDetailPageUrl(songInfo) {
      return `http://www.kuwo.cn/play_detail/${songInfo.songmid}`;
    }
    // init() {
    //   return getToken()
    // },
  };
  var kw_default = kw;

  // src/utils/musicSdk/tx/leaderboard.js
  var boardList2 = [{ id: "tx__4", name: "\u6D41\u884C\u6307\u6570\u699C", bangid: "4" }, { id: "tx__26", name: "\u70ED\u6B4C\u699C", bangid: "26" }, { id: "tx__27", name: "\u65B0\u6B4C\u699C", bangid: "27" }, { id: "tx__62", name: "\u98D9\u5347\u699C", bangid: "62" }, { id: "tx__58", name: "\u8BF4\u5531\u699C", bangid: "58" }, { id: "tx__57", name: "\u559C\u529B\u7535\u97F3\u699C", bangid: "57" }, { id: "tx__28", name: "\u7F51\u7EDC\u6B4C\u66F2\u699C", bangid: "28" }, { id: "tx__5", name: "\u5185\u5730\u699C", bangid: "5" }, { id: "tx__3", name: "\u6B27\u7F8E\u699C", bangid: "3" }, { id: "tx__59", name: "\u9999\u6E2F\u5730\u533A\u699C", bangid: "59" }, { id: "tx__16", name: "\u97E9\u56FD\u699C", bangid: "16" }, { id: "tx__60", name: "\u6296\u5FEB\u699C", bangid: "60" }, { id: "tx__29", name: "\u5F71\u89C6\u91D1\u66F2\u699C", bangid: "29" }, { id: "tx__17", name: "\u65E5\u672C\u699C", bangid: "17" }, { id: "tx__52", name: "\u817E\u8BAF\u97F3\u4E50\u4EBA\u539F\u521B\u699C", bangid: "52" }, { id: "tx__36", name: "K\u6B4C\u91D1\u66F2\u699C", bangid: "36" }, { id: "tx__61", name: "\u53F0\u6E7E\u5730\u533A\u699C", bangid: "61" }, { id: "tx__63", name: "DJ\u821E\u66F2\u699C", bangid: "63" }, { id: "tx__64", name: "\u7EFC\u827A\u65B0\u6B4C\u699C", bangid: "64" }, { id: "tx__65", name: "\u56FD\u98CE\u70ED\u6B4C\u699C", bangid: "65" }, { id: "tx__67", name: "\u542C\u6B4C\u8BC6\u66F2\u699C", bangid: "67" }, { id: "tx__72", name: "\u52A8\u6F2B\u97F3\u4E50\u699C", bangid: "72" }, { id: "tx__73", name: "\u6E38\u620F\u97F3\u4E50\u699C", bangid: "73" }, { id: "tx__75", name: "\u6709\u58F0\u699C", bangid: "75" }, { id: "tx__131", name: "\u6821\u56ED\u97F3\u4E50\u4EBA\u6392\u884C\u699C", bangid: "131" }];
  var leaderboard_default3 = {
    limit: 300,
    list: [
      {
        id: "txlxzsb",
        name: "\u6D41\u884C\u699C",
        bangid: 4
      },
      {
        id: "txrgb",
        name: "\u70ED\u6B4C\u699C",
        bangid: 26
      },
      {
        id: "txwlhgb",
        name: "\u7F51\u7EDC\u699C",
        bangid: 28
      },
      {
        id: "txdyb",
        name: "\u6296\u97F3\u699C",
        bangid: 60
      },
      {
        id: "txndb",
        name: "\u5185\u5730\u699C",
        bangid: 5
      },
      {
        id: "txxgb",
        name: "\u9999\u6E2F\u699C",
        bangid: 59
      },
      {
        id: "txtwb",
        name: "\u53F0\u6E7E\u699C",
        bangid: 61
      },
      {
        id: "txoumb",
        name: "\u6B27\u7F8E\u699C",
        bangid: 3
      },
      {
        id: "txhgb",
        name: "\u97E9\u56FD\u699C",
        bangid: 16
      },
      {
        id: "txrbb",
        name: "\u65E5\u672C\u699C",
        bangid: 17
      },
      {
        id: "txtybb",
        name: "YouTube\u699C",
        bangid: 128
      }
    ],
    listDetailRequest(id, period, limit) {
      return httpFetch("https://u.y.qq.com/cgi-bin/musicu.fcg", {
        method: "post",
        headers: {
          "User-Agent": "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)"
        },
        body: {
          toplist: {
            module: "musicToplist.ToplistInfoServer",
            method: "GetDetail",
            param: {
              topid: id,
              num: limit,
              period
            }
          },
          comm: {
            uin: 0,
            format: "json",
            ct: 20,
            cv: 1859
          }
        }
      }).promise;
    },
    regExps: {
      periodList: /<i class="play_cover__btn c_tx_link js_icon_play" data-listkey=".+?" data-listname=".+?" data-tid=".+?" data-date=".+?" .+?<\/i>/g,
      period: /data-listname="(.+?)" data-tid=".*?\/(.+?)" data-date="(.+?)" .+?<\/i>/
    },
    periods: {},
    periodUrl: "https://c.y.qq.com/node/pc/wk_v15/top.html",
    _requestBoardsObj: null,
    getBoardsData() {
      if (this._requestBoardsObj) this._requestBoardsObj.cancelHttp();
      this._requestBoardsObj = httpFetch("https://c.y.qq.com/v8/fcg-bin/fcg_myqq_toplist.fcg?g_tk=1928093487&inCharset=utf-8&outCharset=utf-8&notice=0&format=json&uin=0&needNewCode=1&platform=h5");
      return this._requestBoardsObj.promise;
    },
    getData(url) {
      const requestDataObj = httpFetch(url);
      return requestDataObj.promise;
    },
    filterData(rawList) {
      return rawList.map((item) => {
        let types = [];
        let _types = {};
        if (item.file.size_128mp3 !== 0) {
          let size = sizeFormate(item.file.size_128mp3);
          types.push({ type: "128k", size });
          _types["128k"] = {
            size
          };
        }
        if (item.file.size_320mp3 !== 0) {
          let size = sizeFormate(item.file.size_320mp3);
          types.push({ type: "320k", size });
          _types["320k"] = {
            size
          };
        }
        if (item.file.size_flac !== 0) {
          let size = sizeFormate(item.file.size_flac);
          types.push({ type: "flac", size });
          _types.flac = {
            size
          };
        }
        if (item.file.size_hires !== 0) {
          let size = sizeFormate(item.file.size_hires);
          types.push({ type: "flac24bit", size });
          _types.flac24bit = {
            size
          };
        }
        return {
          singer: formatSingerName(item.singer, "name"),
          name: item.title,
          albumName: item.album.name,
          albumId: item.album.mid,
          source: "tx",
          interval: formatPlayTime(item.interval),
          songId: item.id,
          albumMid: item.album.mid,
          strMediaMid: item.file.media_mid,
          songmid: item.mid,
          img: item.album.name === "" || item.album.name === "\u7A7A" ? item.singer?.length ? `https://y.gtimg.cn/music/photo_new/T001R500x500M000${item.singer[0].mid}.jpg` : "" : `https://y.gtimg.cn/music/photo_new/T002R500x500M000${item.album.mid}.jpg`,
          lrc: null,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    getPeriods(bangid) {
      return this.getData(this.periodUrl).then(({ body: html }) => {
        let result = html.match(this.regExps.periodList);
        if (!result) return Promise.reject(new Error("get data failed"));
        result.forEach((item) => {
          let result2 = item.match(this.regExps.period);
          if (!result2) return;
          this.periods[result2[2]] = {
            name: result2[1],
            bangid: result2[2],
            period: result2[3]
          };
        });
        const info = this.periods[bangid];
        return info && info.period;
      });
    },
    filterBoardsData(rawList) {
      let list = [];
      for (const board of rawList) {
        if (board.id == 201) continue;
        if (board.topTitle.startsWith("\u5DC5\u5CF0\u699C\xB7")) {
          board.topTitle = board.topTitle.substring(4, board.topTitle.length);
        }
        if (!board.topTitle.endsWith("\u699C")) board.topTitle += "\u699C";
        list.push({
          id: "tx__" + board.id,
          name: board.topTitle,
          bangid: String(board.id)
        });
      }
      return list;
    },
    async getBoards(retryNum = 0) {
      this.list = boardList2;
      return {
        list: boardList2,
        source: "tx"
      };
    },
    getList(bangid, page, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      bangid = parseInt(bangid);
      let info = this.periods[bangid];
      let p = info ? Promise.resolve(info.period) : this.getPeriods(bangid);
      return p.then((period) => {
        return this.listDetailRequest(bangid, period, this.limit).then((resp) => {
          if (resp.body.code !== 0) return this.getList(bangid, page, retryNum);
          return {
            total: resp.body.toplist.data.songInfoList.length,
            list: this.filterData(resp.body.toplist.data.songInfoList),
            limit: this.limit,
            page: 1,
            source: "tx"
          };
        });
      });
    },
    getDetailPageUrl(id) {
      if (typeof id == "string") id = id.replace("tx__", "");
      return `https://y.qq.com/n/ryqq/toplist/${id}`;
    }
  };

  // src/utils/musicSdk/tx/musicInfo.js
  var getSinger = (singers) => {
    let arr = [];
    singers.forEach((singer) => {
      arr.push(singer.name);
    });
    return arr.join("\u3001");
  };
  var musicInfo_default2 = (songmid) => {
    const requestObj = httpFetch("https://u.y.qq.com/cgi-bin/musicu.fcg", {
      method: "post",
      headers: {
        "User-Agent": "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)"
      },
      body: {
        comm: {
          ct: "19",
          cv: "1859",
          uin: "0"
        },
        req: {
          module: "music.pf_song_detail_svr",
          method: "get_song_detail_yqq",
          param: {
            song_type: 0,
            song_mid: songmid
          }
        }
      }
    });
    return requestObj.promise.then(({ body }) => {
      if (body.code != 0 || body.req.code != 0) return Promise.reject(new Error("\u83B7\u53D6\u6B4C\u66F2\u4FE1\u606F\u5931\u8D25"));
      const item = body.req.data.track_info;
      if (!item.file?.media_mid) return null;
      let types = [];
      let _types = {};
      const file = item.file;
      if (file.size_128mp3 != 0) {
        let size = sizeFormate(file.size_128mp3);
        types.push({ type: "128k", size });
        _types["128k"] = {
          size
        };
      }
      if (file.size_320mp3 !== 0) {
        let size = sizeFormate(file.size_320mp3);
        types.push({ type: "320k", size });
        _types["320k"] = {
          size
        };
      }
      if (file.size_flac !== 0) {
        let size = sizeFormate(file.size_flac);
        types.push({ type: "flac", size });
        _types.flac = {
          size
        };
      }
      if (file.size_hires !== 0) {
        let size = sizeFormate(file.size_hires);
        types.push({ type: "flac24bit", size });
        _types.flac24bit = {
          size
        };
      }
      let albumId = "";
      let albumName = "";
      if (item.album) {
        albumName = item.album.name;
        albumId = item.album.mid;
      }
      return {
        singer: getSinger(item.singer),
        name: item.title,
        albumName,
        albumId,
        source: "tx",
        interval: formatPlayTime(item.interval),
        songId: item.id,
        albumMid: item.album?.mid ?? "",
        strMediaMid: item.file.media_mid,
        songmid: item.mid,
        img: albumId === "" || albumId === "\u7A7A" ? item.singer?.length ? `https://y.gtimg.cn/music/photo_new/T001R500x500M000${item.singer[0].mid}.jpg` : "" : `https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumId}.jpg`,
        types,
        _types,
        typeUrl: {}
      };
    });
  };

  // src/utils/musicSdk/tx/qrcDecode.js
  var import_pako = __toESM(require_pako());
  var DES_ENCRYPT = 1;
  var DES_DECRYPT = 0;
  var sbox = [
    [
      14,
      4,
      13,
      1,
      2,
      15,
      11,
      8,
      3,
      10,
      6,
      12,
      5,
      9,
      0,
      7,
      0,
      15,
      7,
      4,
      14,
      2,
      13,
      1,
      10,
      6,
      12,
      11,
      9,
      5,
      3,
      8,
      4,
      1,
      14,
      8,
      13,
      6,
      2,
      11,
      15,
      12,
      9,
      7,
      3,
      10,
      5,
      0,
      15,
      12,
      8,
      2,
      4,
      9,
      1,
      7,
      5,
      11,
      3,
      14,
      10,
      0,
      6,
      13
    ],
    [
      15,
      1,
      8,
      14,
      6,
      11,
      3,
      4,
      9,
      7,
      2,
      13,
      12,
      0,
      5,
      10,
      3,
      13,
      4,
      7,
      15,
      2,
      8,
      15,
      12,
      0,
      1,
      10,
      6,
      9,
      11,
      5,
      0,
      14,
      7,
      11,
      10,
      4,
      13,
      1,
      5,
      8,
      12,
      6,
      9,
      3,
      2,
      15,
      13,
      8,
      10,
      1,
      3,
      15,
      4,
      2,
      11,
      6,
      7,
      12,
      0,
      5,
      14,
      9
    ],
    [
      10,
      0,
      9,
      14,
      6,
      3,
      15,
      5,
      1,
      13,
      12,
      7,
      11,
      4,
      2,
      8,
      13,
      7,
      0,
      9,
      3,
      4,
      6,
      10,
      2,
      8,
      5,
      14,
      12,
      11,
      15,
      1,
      13,
      6,
      4,
      9,
      8,
      15,
      3,
      0,
      11,
      1,
      2,
      12,
      5,
      10,
      14,
      7,
      1,
      10,
      13,
      0,
      6,
      9,
      8,
      7,
      4,
      15,
      14,
      3,
      11,
      5,
      2,
      12
    ],
    [
      7,
      13,
      14,
      3,
      0,
      6,
      9,
      10,
      1,
      2,
      8,
      5,
      11,
      12,
      4,
      15,
      13,
      8,
      11,
      5,
      6,
      15,
      0,
      3,
      4,
      7,
      2,
      12,
      1,
      10,
      14,
      9,
      10,
      6,
      9,
      0,
      12,
      11,
      7,
      13,
      15,
      1,
      3,
      14,
      5,
      2,
      8,
      4,
      3,
      15,
      0,
      6,
      10,
      10,
      13,
      8,
      9,
      4,
      5,
      11,
      12,
      7,
      2,
      14
    ],
    [
      2,
      12,
      4,
      1,
      7,
      10,
      11,
      6,
      8,
      5,
      3,
      15,
      13,
      0,
      14,
      9,
      14,
      11,
      2,
      12,
      4,
      7,
      13,
      1,
      5,
      0,
      15,
      10,
      3,
      9,
      8,
      6,
      4,
      2,
      1,
      11,
      10,
      13,
      7,
      8,
      15,
      9,
      12,
      5,
      6,
      3,
      0,
      14,
      11,
      8,
      12,
      7,
      1,
      14,
      2,
      13,
      6,
      15,
      0,
      9,
      10,
      4,
      5,
      3
    ],
    [
      12,
      1,
      10,
      15,
      9,
      2,
      6,
      8,
      0,
      13,
      3,
      4,
      14,
      7,
      5,
      11,
      10,
      15,
      4,
      2,
      7,
      12,
      9,
      5,
      6,
      1,
      13,
      14,
      0,
      11,
      3,
      8,
      9,
      14,
      15,
      5,
      2,
      8,
      12,
      3,
      7,
      0,
      4,
      10,
      1,
      13,
      11,
      6,
      4,
      3,
      2,
      12,
      9,
      5,
      15,
      10,
      11,
      14,
      1,
      7,
      6,
      0,
      8,
      13
    ],
    [
      4,
      11,
      2,
      14,
      15,
      0,
      8,
      13,
      3,
      12,
      9,
      7,
      5,
      10,
      6,
      1,
      13,
      0,
      11,
      7,
      4,
      9,
      1,
      10,
      14,
      3,
      5,
      12,
      2,
      15,
      8,
      6,
      1,
      4,
      11,
      13,
      12,
      3,
      7,
      14,
      10,
      15,
      6,
      8,
      0,
      5,
      9,
      2,
      6,
      11,
      13,
      8,
      1,
      4,
      10,
      7,
      9,
      5,
      0,
      15,
      14,
      2,
      3,
      12
    ],
    [
      13,
      2,
      8,
      4,
      6,
      15,
      11,
      1,
      10,
      9,
      3,
      14,
      5,
      0,
      12,
      7,
      1,
      15,
      13,
      8,
      10,
      3,
      7,
      4,
      12,
      5,
      6,
      11,
      0,
      14,
      9,
      2,
      7,
      11,
      4,
      1,
      9,
      12,
      14,
      2,
      0,
      6,
      10,
      13,
      15,
      3,
      5,
      8,
      2,
      1,
      14,
      7,
      4,
      10,
      8,
      13,
      15,
      12,
      9,
      0,
      3,
      5,
      6,
      11
    ]
  ];
  var key_rnd_shift = [1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1];
  var key_perm_c = [
    56,
    48,
    40,
    32,
    24,
    16,
    8,
    0,
    57,
    49,
    41,
    33,
    25,
    17,
    9,
    1,
    58,
    50,
    42,
    34,
    26,
    18,
    10,
    2,
    59,
    51,
    43,
    35
  ];
  var key_perm_d = [
    62,
    54,
    46,
    38,
    30,
    22,
    14,
    6,
    61,
    53,
    45,
    37,
    29,
    21,
    13,
    5,
    60,
    52,
    44,
    36,
    28,
    20,
    12,
    4,
    27,
    19,
    11,
    3
  ];
  var key_compression = [
    13,
    16,
    10,
    23,
    0,
    4,
    2,
    27,
    14,
    5,
    20,
    9,
    22,
    18,
    11,
    3,
    25,
    7,
    15,
    6,
    26,
    19,
    12,
    1,
    40,
    51,
    30,
    36,
    46,
    54,
    29,
    39,
    50,
    44,
    32,
    47,
    43,
    48,
    38,
    55,
    33,
    52,
    45,
    41,
    49,
    35,
    28,
    31
  ];
  var bitnum = (a, b, c) => (a[(b / 32 | 0) * 4 + 3 - (b % 32 / 8 | 0)] >>> 7 - b % 8 & 1) << c >>> 0;
  var bitnum_intr = (a, b, c) => (a >>> 31 - b & 1) << c >>> 0;
  var bitnum_intl = (a, b, c) => (a << b >>> 0 & 2147483648) >>> 0 >>> c >>> 0;
  var sbox_bit = (a) => a & 32 | (a & 31) >>> 1 | (a & 1) << 4;
  var initial_permutation = (input) => {
    const s0 = (bitnum(input, 57, 31) | bitnum(input, 49, 30) | bitnum(input, 41, 29) | bitnum(input, 33, 28) | bitnum(input, 25, 27) | bitnum(input, 17, 26) | bitnum(input, 9, 25) | bitnum(input, 1, 24) | bitnum(input, 59, 23) | bitnum(input, 51, 22) | bitnum(input, 43, 21) | bitnum(input, 35, 20) | bitnum(input, 27, 19) | bitnum(input, 19, 18) | bitnum(input, 11, 17) | bitnum(input, 3, 16) | bitnum(input, 61, 15) | bitnum(input, 53, 14) | bitnum(input, 45, 13) | bitnum(input, 37, 12) | bitnum(input, 29, 11) | bitnum(input, 21, 10) | bitnum(input, 13, 9) | bitnum(input, 5, 8) | bitnum(input, 63, 7) | bitnum(input, 55, 6) | bitnum(input, 47, 5) | bitnum(input, 39, 4) | bitnum(input, 31, 3) | bitnum(input, 23, 2) | bitnum(input, 15, 1) | bitnum(input, 7, 0)) >>> 0;
    const s1 = (bitnum(input, 56, 31) | bitnum(input, 48, 30) | bitnum(input, 40, 29) | bitnum(input, 32, 28) | bitnum(input, 24, 27) | bitnum(input, 16, 26) | bitnum(input, 8, 25) | bitnum(input, 0, 24) | bitnum(input, 58, 23) | bitnum(input, 50, 22) | bitnum(input, 42, 21) | bitnum(input, 34, 20) | bitnum(input, 26, 19) | bitnum(input, 18, 18) | bitnum(input, 10, 17) | bitnum(input, 2, 16) | bitnum(input, 60, 15) | bitnum(input, 52, 14) | bitnum(input, 44, 13) | bitnum(input, 36, 12) | bitnum(input, 28, 11) | bitnum(input, 20, 10) | bitnum(input, 12, 9) | bitnum(input, 4, 8) | bitnum(input, 62, 7) | bitnum(input, 54, 6) | bitnum(input, 46, 5) | bitnum(input, 38, 4) | bitnum(input, 30, 3) | bitnum(input, 22, 2) | bitnum(input, 14, 1) | bitnum(input, 6, 0)) >>> 0;
    return [s0, s1];
  };
  var inverse_permutation = (s0, s1, out) => {
    out[3] = (bitnum_intr(s1, 7, 7) | bitnum_intr(s0, 7, 6) | bitnum_intr(s1, 15, 5) | bitnum_intr(s0, 15, 4) | bitnum_intr(s1, 23, 3) | bitnum_intr(s0, 23, 2) | bitnum_intr(s1, 31, 1) | bitnum_intr(s0, 31, 0)) & 255;
    out[2] = (bitnum_intr(s1, 6, 7) | bitnum_intr(s0, 6, 6) | bitnum_intr(s1, 14, 5) | bitnum_intr(s0, 14, 4) | bitnum_intr(s1, 22, 3) | bitnum_intr(s0, 22, 2) | bitnum_intr(s1, 30, 1) | bitnum_intr(s0, 30, 0)) & 255;
    out[1] = (bitnum_intr(s1, 5, 7) | bitnum_intr(s0, 5, 6) | bitnum_intr(s1, 13, 5) | bitnum_intr(s0, 13, 4) | bitnum_intr(s1, 21, 3) | bitnum_intr(s0, 21, 2) | bitnum_intr(s1, 29, 1) | bitnum_intr(s0, 29, 0)) & 255;
    out[0] = (bitnum_intr(s1, 4, 7) | bitnum_intr(s0, 4, 6) | bitnum_intr(s1, 12, 5) | bitnum_intr(s0, 12, 4) | bitnum_intr(s1, 20, 3) | bitnum_intr(s0, 20, 2) | bitnum_intr(s1, 28, 1) | bitnum_intr(s0, 28, 0)) & 255;
    out[7] = (bitnum_intr(s1, 3, 7) | bitnum_intr(s0, 3, 6) | bitnum_intr(s1, 11, 5) | bitnum_intr(s0, 11, 4) | bitnum_intr(s1, 19, 3) | bitnum_intr(s0, 19, 2) | bitnum_intr(s1, 27, 1) | bitnum_intr(s0, 27, 0)) & 255;
    out[6] = (bitnum_intr(s1, 2, 7) | bitnum_intr(s0, 2, 6) | bitnum_intr(s1, 10, 5) | bitnum_intr(s0, 10, 4) | bitnum_intr(s1, 18, 3) | bitnum_intr(s0, 18, 2) | bitnum_intr(s1, 26, 1) | bitnum_intr(s0, 26, 0)) & 255;
    out[5] = (bitnum_intr(s1, 1, 7) | bitnum_intr(s0, 1, 6) | bitnum_intr(s1, 9, 5) | bitnum_intr(s0, 9, 4) | bitnum_intr(s1, 17, 3) | bitnum_intr(s0, 17, 2) | bitnum_intr(s1, 25, 1) | bitnum_intr(s0, 25, 0)) & 255;
    out[4] = (bitnum_intr(s1, 0, 7) | bitnum_intr(s0, 0, 6) | bitnum_intr(s1, 8, 5) | bitnum_intr(s0, 8, 4) | bitnum_intr(s1, 16, 3) | bitnum_intr(s0, 16, 2) | bitnum_intr(s1, 24, 1) | bitnum_intr(s0, 24, 0)) & 255;
  };
  var des_f = (state2, key) => {
    const t1 = (bitnum_intl(state2, 31, 0) | (state2 & 4026531840) >>> 0 >>> 1 | bitnum_intl(state2, 4, 5) | bitnum_intl(state2, 3, 6) | (state2 & 251658240) >>> 0 >>> 3 | bitnum_intl(state2, 8, 11) | bitnum_intl(state2, 7, 12) | (state2 & 15728640) >>> 0 >>> 5 | bitnum_intl(state2, 12, 17) | bitnum_intl(state2, 11, 18) | (state2 & 983040) >>> 0 >>> 7 | bitnum_intl(state2, 16, 23)) >>> 0;
    const t2 = (bitnum_intl(state2, 15, 0) | (state2 & 61440) << 15 >>> 0 | bitnum_intl(state2, 20, 5) | bitnum_intl(state2, 19, 6) | (state2 & 3840) << 13 >>> 0 | bitnum_intl(state2, 24, 11) | bitnum_intl(state2, 23, 12) | (state2 & 240) << 11 >>> 0 | bitnum_intl(state2, 28, 17) | bitnum_intl(state2, 27, 18) | (state2 & 15) << 9 >>> 0 | bitnum_intl(state2, 0, 23)) >>> 0;
    const lrgstate = [
      t1 >>> 24 & 255,
      t1 >>> 16 & 255,
      t1 >>> 8 & 255,
      t2 >>> 24 & 255,
      t2 >>> 16 & 255,
      t2 >>> 8 & 255
    ];
    for (let i = 0; i < 6; i++) lrgstate[i] ^= key[i];
    let s = (sbox[0][sbox_bit(lrgstate[0] >>> 2)] << 28 | sbox[1][sbox_bit((lrgstate[0] & 3) << 4 | lrgstate[1] >>> 4)] << 24 | sbox[2][sbox_bit((lrgstate[1] & 15) << 2 | lrgstate[2] >>> 6)] << 20 | sbox[3][sbox_bit(lrgstate[2] & 63)] << 16 | sbox[4][sbox_bit(lrgstate[3] >>> 2)] << 12 | sbox[5][sbox_bit((lrgstate[3] & 3) << 4 | lrgstate[4] >>> 4)] << 8 | sbox[6][sbox_bit((lrgstate[4] & 15) << 2 | lrgstate[5] >>> 6)] << 4 | sbox[7][sbox_bit(lrgstate[5] & 63)]) >>> 0;
    return (bitnum_intl(s, 15, 0) | bitnum_intl(s, 6, 1) | bitnum_intl(s, 19, 2) | bitnum_intl(s, 20, 3) | bitnum_intl(s, 28, 4) | bitnum_intl(s, 11, 5) | bitnum_intl(s, 27, 6) | bitnum_intl(s, 16, 7) | bitnum_intl(s, 0, 8) | bitnum_intl(s, 14, 9) | bitnum_intl(s, 22, 10) | bitnum_intl(s, 25, 11) | bitnum_intl(s, 4, 12) | bitnum_intl(s, 17, 13) | bitnum_intl(s, 30, 14) | bitnum_intl(s, 9, 15) | bitnum_intl(s, 1, 16) | bitnum_intl(s, 7, 17) | bitnum_intl(s, 23, 18) | bitnum_intl(s, 13, 19) | bitnum_intl(s, 31, 20) | bitnum_intl(s, 26, 21) | bitnum_intl(s, 2, 22) | bitnum_intl(s, 8, 23) | bitnum_intl(s, 18, 24) | bitnum_intl(s, 12, 25) | bitnum_intl(s, 29, 26) | bitnum_intl(s, 5, 27) | bitnum_intl(s, 21, 28) | bitnum_intl(s, 10, 29) | bitnum_intl(s, 3, 30) | bitnum_intl(s, 24, 31)) >>> 0;
  };
  var des_crypt = (input, schedule, output) => {
    let [s0, s1] = initial_permutation(input);
    for (let i = 0; i < 15; i++) {
      const prev = s1;
      s1 = (des_f(s1, schedule[i]) ^ s0) >>> 0;
      s0 = prev;
    }
    s0 = (des_f(s1, schedule[15]) ^ s0) >>> 0;
    inverse_permutation(s0, s1, output);
  };
  var key_schedule = (key, mode) => {
    const schedule = Array.from({ length: 16 }, () => new Uint8Array(6));
    let c = 0;
    let d = 0;
    for (let i = 0; i < 28; i++) {
      c = (c | bitnum(key, key_perm_c[i], 31 - i)) >>> 0;
      d = (d | bitnum(key, key_perm_d[i], 31 - i)) >>> 0;
    }
    for (let i = 0; i < 16; i++) {
      c = ((c << key_rnd_shift[i] >>> 0 | c >>> 28 - key_rnd_shift[i]) & 4294967280) >>> 0;
      d = ((d << key_rnd_shift[i] >>> 0 | d >>> 28 - key_rnd_shift[i]) & 4294967280) >>> 0;
      const togen = mode === DES_DECRYPT ? 15 - i : i;
      for (let j = 0; j < 24; j++) {
        schedule[togen][j / 8 | 0] |= bitnum_intr(c, key_compression[j], 7 - j % 8);
      }
      for (let j = 24; j < 48; j++) {
        schedule[togen][j / 8 | 0] |= bitnum_intr(d, key_compression[j] - 27, 7 - j % 8);
      }
    }
    return schedule;
  };
  var tripledes_key_setup = (key, mode) => {
    if (mode === DES_ENCRYPT) {
      return [
        key_schedule(key.subarray(0, 8), DES_ENCRYPT),
        key_schedule(key.subarray(8, 16), DES_DECRYPT),
        key_schedule(key.subarray(16, 24), DES_ENCRYPT)
      ];
    }
    return [
      key_schedule(key.subarray(16, 24), DES_DECRYPT),
      key_schedule(key.subarray(8, 16), DES_ENCRYPT),
      key_schedule(key.subarray(0, 8), DES_DECRYPT)
    ];
  };
  var tripledes_crypt = (input, schedule, output) => {
    const buf = new Uint8Array(8);
    des_crypt(input, schedule[0], buf);
    des_crypt(buf, schedule[1], output);
    des_crypt(output, schedule[2], buf);
    output.set(buf);
  };
  var QRC_KEY = Buffer.from([
    33,
    64,
    35,
    41,
    40,
    42,
    36,
    37,
    49,
    50,
    51,
    90,
    88,
    67,
    33,
    64,
    33,
    64,
    35,
    41,
    40,
    78,
    72,
    76
  ]);
  var handleInflate2 = (data) => new Promise((resolve, reject) => {
    resolve((0, import_pako.inflate)(data, { finishFlush: import_pako.constants.Z_SYNC_FLUSH }));
  });
  var decodeQrc = async (hexData) => {
    if (!hexData || hexData.length % 2 !== 0) return "";
    const encrypted = Buffer.from(hexData, "hex");
    if (encrypted.length === 0) return "";
    const schedule = tripledes_key_setup(QRC_KEY, DES_DECRYPT);
    const block = new Uint8Array(8);
    for (let i = 0; i + 8 <= encrypted.length; i += 8) {
      tripledes_crypt(encrypted.subarray(i, i + 8), schedule, block);
      encrypted.set(block, i);
    }
    try {
      const result = await handleInflate2(encrypted);
      return Buffer.from(result).toString("utf-8");
    } catch {
      return "";
    }
  };

  // src/utils/musicSdk/tx/lyric.js
  var songIdMap = /* @__PURE__ */ new Map();
  var promises = /* @__PURE__ */ new Map();
  var decodeLyric2 = async (lrc, tlrc, rlrc) => ({
    lyric: await decodeQrc(lrc),
    tlyric: await decodeQrc(tlrc),
    rlyric: await decodeQrc(rlrc)
  });
  var parseTools2 = {
    rxps: {
      info: /^{"/,
      lineTime: /^\[(\d+),\d+\]/,
      lineTime2: /^\[([\d:.]+)\]/,
      wordTime: /\(\d+,\d+\)/,
      wordTimeAll: /(\(\d+,\d+\))/g,
      timeLabelFixRxp: /(?:\.0+|0+)$/
    },
    msFormat(timeMs) {
      if (Number.isNaN(timeMs)) return "";
      let ms = timeMs % 1e3;
      timeMs /= 1e3;
      let m = parseInt(timeMs / 60).toString().padStart(2, "0");
      timeMs %= 60;
      let s = parseInt(timeMs).toString().padStart(2, "0");
      return `[${m}:${s}.${String(ms).padStart(3, "0")}]`;
    },
    parseLyric(lrc) {
      lrc = lrc.trim();
      lrc = lrc.replace(/\r/g, "");
      if (!lrc) return { lyric: "", lxlyric: "" };
      const lines = lrc.split("\n");
      const lxlrcLines = [];
      const lrcLines = [];
      for (let line of lines) {
        line = line.trim();
        let result = this.rxps.lineTime.exec(line);
        if (!result) {
          if (line.startsWith("[offset")) {
            lxlrcLines.push(line);
            lrcLines.push(line);
          }
          if (this.rxps.lineTime2.test(line)) {
            lrcLines.push(line);
          }
          continue;
        }
        const startMsTime = parseInt(result[1]);
        const startTimeStr = this.msFormat(startMsTime);
        if (!startTimeStr) continue;
        let words = line.replace(this.rxps.lineTime, "");
        lrcLines.push(`${startTimeStr}${words.replace(this.rxps.wordTimeAll, "")}`);
        let times = words.match(this.rxps.wordTimeAll);
        if (!times) continue;
        times = times.map((time) => {
          const result2 = /\((\d+),(\d+)\)/.exec(time);
          return `<${Math.max(parseInt(result2[1]) - startMsTime, 0)},${result2[2]}>`;
        });
        const wordArr = words.split(this.rxps.wordTime);
        const newWords = times.map((time, index) => `${time}${wordArr[index]}`).join("");
        lxlrcLines.push(`${startTimeStr}${newWords}`);
      }
      return {
        lyric: lrcLines.join("\n"),
        lxlyric: lxlrcLines.join("\n")
      };
    },
    parseRlyric(lrc) {
      lrc = lrc.trim();
      lrc = lrc.replace(/\r/g, "");
      if (!lrc) return { lyric: "", lxlyric: "" };
      const lines = lrc.split("\n");
      const lrcLines = [];
      for (let line of lines) {
        line = line.trim();
        let result = this.rxps.lineTime.exec(line);
        if (!result) continue;
        const startMsTime = parseInt(result[1]);
        const startTimeStr = this.msFormat(startMsTime);
        if (!startTimeStr) continue;
        let words = line.replace(this.rxps.lineTime, "");
        lrcLines.push(`${startTimeStr}${words.replace(this.rxps.wordTimeAll, "")}`);
      }
      return lrcLines.join("\n");
    },
    removeTag(str) {
      return str.replace(/^[\S\s]*?LyricContent="/, "").replace(/"\/>[\S\s]*?$/, "");
    },
    getIntv(interval) {
      if (!interval) return 0;
      if (!interval.includes(".")) interval += ".0";
      let arr = interval.split(/:|\./);
      while (arr.length < 3) arr.unshift("0");
      const [m, s, ms] = arr;
      return parseInt(m) * 36e5 + parseInt(s) * 1e3 + parseInt(ms);
    },
    fixRlrcTimeTag(rlrc, lrc) {
      const rlrcLines = rlrc.split("\n");
      let lrcLines = lrc.split("\n");
      let newLrc = [];
      rlrcLines.forEach((line) => {
        const result = this.rxps.lineTime2.exec(line);
        if (!result) return;
        const words = line.replace(this.rxps.lineTime2, "");
        if (!words.trim()) return;
        const t1 = this.getIntv(result[1]);
        while (lrcLines.length) {
          const lrcLine = lrcLines.shift();
          const lrcLineResult = this.rxps.lineTime2.exec(lrcLine);
          if (!lrcLineResult) continue;
          const t2 = this.getIntv(lrcLineResult[1]);
          if (Math.abs(t1 - t2) < 100) {
            newLrc.push(line.replace(this.rxps.lineTime2, lrcLineResult[0]));
            break;
          }
        }
      });
      return newLrc.join("\n");
    },
    fixTlrcTimeTag(tlrc, lrc) {
      const tlrcLines = tlrc.split("\n");
      let lrcLines = lrc.split("\n");
      let newLrc = [];
      tlrcLines.forEach((line) => {
        const result = this.rxps.lineTime2.exec(line);
        if (!result) return;
        const words = line.replace(this.rxps.lineTime2, "");
        if (!words.trim()) return;
        let time = result[1];
        if (time.includes(".")) {
          time += "".padStart(3 - time.split(".")[1].length, "0");
        }
        const t1 = this.getIntv(time);
        while (lrcLines.length) {
          const lrcLine = lrcLines.shift();
          const lrcLineResult = this.rxps.lineTime2.exec(lrcLine);
          if (!lrcLineResult) continue;
          const t2 = this.getIntv(lrcLineResult[1]);
          if (Math.abs(t1 - t2) < 100) {
            newLrc.push(line.replace(this.rxps.lineTime2, lrcLineResult[0]));
            break;
          }
        }
      });
      return newLrc.join("\n");
    },
    parse(lrc, tlrc, rlrc) {
      const info = {
        lyric: "",
        tlyric: "",
        rlyric: "",
        lxlyric: ""
      };
      if (lrc) {
        let { lyric, lxlyric } = this.parseLyric(this.removeTag(lrc));
        info.lyric = lyric;
        info.lxlyric = lxlyric;
      }
      if (rlrc) info.rlyric = this.fixRlrcTimeTag(this.parseRlyric(this.removeTag(rlrc)), info.lyric);
      if (tlrc) info.tlyric = this.fixTlrcTimeTag(tlrc, info.lyric);
      return info;
    }
  };
  var lyric_default3 = {
    successCode: 0,
    async getSongId({ songId, songmid }) {
      if (songId) return songId;
      if (songIdMap.has(songmid)) return songIdMap.get(songmid);
      if (promises.has(songmid)) return (await promises.get(songmid)).songId;
      const promise = musicInfo_default2(songmid);
      promises.set(promise);
      const info = await promise;
      songIdMap.set(songmid, info.songId);
      promises.delete(songmid);
      return info.songId;
    },
    async parseLyric(lrc, tlrc, rlrc) {
      const { lyric, tlyric, rlyric } = await decodeLyric2(lrc, tlrc, rlrc);
      return parseTools2.parse(lyric, tlyric, rlyric);
    },
    getLyric(mInfo, retryNum = 0) {
      if (retryNum > 3) return Promise.reject(new Error("Get lyric failed"));
      return {
        cancelHttp() {
        },
        promise: this.getSongId(mInfo).then((songId) => {
          const requestObj = httpFetch("https://u.y.qq.com/cgi-bin/musicu.fcg", {
            method: "post",
            headers: {
              referer: "https://y.qq.com",
              "user-agent": "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36"
            },
            body: {
              comm: {
                ct: "19",
                cv: "1859",
                uin: "0"
              },
              req: {
                method: "GetPlayLyricInfo",
                module: "music.musichallSong.PlayLyricInfo",
                param: {
                  format: "json",
                  crypt: 1,
                  ct: 19,
                  cv: 1873,
                  interval: 0,
                  lrc_t: 0,
                  qrc: 1,
                  qrc_t: 0,
                  roma: 1,
                  roma_t: 0,
                  songID: songId,
                  trans: 1,
                  trans_t: 0,
                  type: -1
                }
              }
            }
          });
          return requestObj.promise.then(({ body }) => {
            if (body.code != this.successCode || body.req.code != this.successCode) return this.getLyric(songId, ++retryNum);
            const data = body.req.data;
            return this.parseLyric(data.lyric, data.trans, data.roma);
          });
        })
      };
    }
  };

  // src/utils/musicSdk/tx/songList.js
  var songList_default3 = {
    _requestObj_tags: null,
    _requestObj_hotTags: null,
    _requestObj_list: null,
    limit_list: 36,
    limit_song: 1e5,
    successCode: 0,
    sortList: [
      {
        name: "\u63A8\u8350",
        tid: "recommend",
        id: "-1"
      },
      {
        name: "\u6700\u70ED",
        tid: "hot",
        id: "3"
      },
      {
        name: "\u6700\u65B0",
        tid: "new",
        id: "2"
      }
    ],
    regExps: {
      hotTagHtml: /class="c_bg_link js_tag_item" data-id="\w+">.+?<\/a>/g,
      hotTag: /data-id="(\w+)">(.+?)<\/a>/,
      // https://y.qq.com/n/yqq/playlist/7217720898.html
      // https://i.y.qq.com/n2/m/share/details/taoge.html?platform=11&appshare=android_qq&appversion=9050006&id=7217720898&ADTAG=qfshare
      listDetailLink: /\/playlist\/(\d+)/,
      listDetailLink2: /id=(\d+)/
    },
    tagsUrl: "https://u.y.qq.com/cgi-bin/musicu.fcg?loginUin=0&hostUin=0&format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=wk_v15.json&needNewCode=0&data=%7B%22tags%22%3A%7B%22method%22%3A%22get_all_categories%22%2C%22param%22%3A%7B%22qq%22%3A%22%22%7D%2C%22module%22%3A%22playlist.PlaylistAllCategoriesServer%22%7D%7D",
    hotTagUrl: "https://c.y.qq.com/node/pc/wk_v15/category_playlist.html",
    getListUrl(id, page) {
      id = parseInt(id);
      return `https://u.y.qq.com/cgi-bin/musicu.fcg?loginUin=0&hostUin=0&format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=wk_v15.json&needNewCode=0&data=${encodeURIComponent(JSON.stringify({
        comm: { cv: 1602, ct: 20 },
        playlist: {
          method: "get_category_content",
          param: {
            titleid: id,
            caller: "0",
            category_id: id,
            size: this.limit_list,
            page: page - 1,
            use_page: 1
          },
          module: "playlist.PlayListCategoryServer"
        }
      }))}`;
    },
    getListDetailUrl(id) {
      return `https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=${id}&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0`;
    },
    // http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=2849349915&pn=0&rn=100&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1
    // 获取标签
    getTag(tryNum = 0) {
      if (this._requestObj_tags) this._requestObj_tags.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_tags = httpFetch(this.tagsUrl);
      return this._requestObj_tags.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getTag(++tryNum);
        return this.filterTagInfo(body.tags.data.v_group);
      });
    },
    // 获取标签
    getHotTag(tryNum = 0) {
      if (this._requestObj_hotTags) this._requestObj_hotTags.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_hotTags = httpFetch(this.hotTagUrl);
      return this._requestObj_hotTags.promise.then(({ statusCode, body }) => {
        if (statusCode !== 200) return this.getHotTag(++tryNum);
        return this.filterInfoHotTag(body);
      });
    },
    filterInfoHotTag(html) {
      let hotTag = html.match(this.regExps.hotTagHtml);
      const hotTags = [];
      if (!hotTag) return hotTags;
      hotTag.forEach((tagHtml) => {
        let result = tagHtml.match(this.regExps.hotTag);
        if (!result) return;
        hotTags.push({
          id: parseInt(result[1]),
          name: result[2],
          source: "tx"
        });
      });
      return hotTags;
    },
    filterTagInfo(rawList) {
      return rawList.map((type) => ({
        name: type.group_name,
        list: type.v_item.map((item) => ({
          parent_id: type.group_id,
          parent_name: type.group_name,
          id: item.id,
          name: item.name,
          source: "tx"
        }))
      }));
    },
    async getRecommendList(page) {
      const { body } = await httpFetch(
        `https://u.y.qq.com/cgi-bin/musicu.fcg?loginUin=0&hostUin=0&format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=wk_v15.json&needNewCode=0&data=${encodeURIComponent(
          JSON.stringify({
            comm: { cv: 1602, ct: 20 },
            playlist: {
              module: "music.playlist.PlaylistSquare",
              method: "GetRecommendWhole",
              param: {
                IsReqFeed: true,
                FeedReq: {
                  From: (page - 1) * this.limit_list,
                  Size: this.limit_list
                }
              }
            }
          })
        )}`
      ).promise;
      if (body.code !== this.successCode) throw new Error("tx getRecommendList failed");
      return {
        list: body.playlist.data.FeedRsp.List?.map(({ Playlist }) => ({
          play_count: formatPlayCount(Playlist.basic.play_cnt),
          id: String(Playlist.basic.tid),
          author: decodeName(Playlist.basic.creator.nick),
          name: decodeName(Playlist.basic.title),
          time: Playlist.basic.modify_time ? dateFormat(Playlist.basic.modify_time * 1e3, "Y-M-D") : "",
          img: Playlist.basic.cover.medium_url || Playlist.basic.cover.default_url,
          total: Playlist.basic.song_cnt,
          desc: decodeName(Playlist.basic.desc).replace(/<br>/g, "\n"),
          source: "tx"
        })) || [],
        total: body.playlist.data.FeedRsp.FromLimit,
        limit: this.limit_list,
        page,
        source: "tx"
      };
    },
    // 获取列表数据
    getList(sortId, tagId, page, tryNum = 0) {
      if (this._requestObj_list) this._requestObj_list.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      if (!tagId) {
        if (!tagId && sortId == -1) return this.getRecommendList(page);
        tagId = sortId;
      }
      this._requestObj_list = httpFetch(
        this.getListUrl(tagId, page)
      );
      return this._requestObj_list.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getList(sortId, tagId, page, ++tryNum);
        return this.filterList(body.playlist.data, page);
      });
    },
    // filterList(data, page) {
    //   return {
    //     list: data.v_playlist.map(item => ({
    //       play_count: formatPlayCount(item.access_num),
    //       id: String(item.tid),
    //       author: item.creator_info.nick,
    //       name: item.title,
    //       time: item.modify_time ? dateFormat(item.modify_time * 1000, 'Y-M-D') : '',
    //       img: item.cover_url_medium,
    //       // grade: item.favorcnt / 10,
    //       total: item.song_ids?.length,
    //       desc: decodeName(item.desc).replace(/<br>/g, '\n'),
    //       source: 'tx',
    //     })),
    //     total: data.total,
    //     page,
    //     limit: this.limit_list,
    //     source: 'tx',
    //   }
    // },
    filterList({ content }, page) {
      return {
        list: content.v_item.map(({ basic }) => ({
          play_count: formatPlayCount(basic.play_cnt),
          id: String(basic.tid),
          author: basic.creator.nick,
          name: basic.title,
          // time: basic.publish_time,
          img: basic.cover.medium_url || basic.cover.default_url,
          // grade: basic.favorcnt / 10,
          desc: decodeName(basic.desc).replace(/<br>/g, "\n"),
          source: "tx"
        })),
        total: content.total_cnt,
        page,
        limit: this.limit_list,
        source: "tx"
      };
    },
    async handleParseId(link, retryNum = 0) {
      if (retryNum > 2) return Promise.reject(new Error("link try max num"));
      const requestObj_listDetailLink = httpFetch(link);
      const { url, statusCode } = await requestObj_listDetailLink.promise;
      if (statusCode > 400) return this.handleParseId(link, ++retryNum);
      return url;
    },
    async getListId(id) {
      if (/[?&:/]/.test(id)) {
        if (!this.regExps.listDetailLink.test(id)) {
          id = await this.handleParseId(id);
        }
        let result = this.regExps.listDetailLink.exec(id);
        if (!result) {
          result = this.regExps.listDetailLink2.exec(id);
          if (!result) throw new Error("failed");
        }
        id = result[1];
      }
      return id;
    },
    // 获取歌曲列表内的音乐
    async getListDetail2(id, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      const requestObj_listDetail = httpFetch("https://u.y.qq.com/cgi-bin/musicu.fcg", {
        method: "post",
        headers: {
          Origin: "https://y.qq.com",
          Referer: `https://y.qq.com/n/yqq/playsquare/${id}.html`
        },
        body: {
          comm: {
            cv: 4747474,
            ct: 24,
            format: "json",
            inCharset: "utf-8",
            outCharset: "utf-8",
            platform: "yqq.json",
            needNewCode: 1,
            uin: 0
          },
          req_1: {
            module: "music.srfDissInfo.aiDissInfo",
            method: "uniform_get_Dissinfo",
            param: {
              disstid: parseInt(id),
              userinfo: 1,
              tag: 1,
              orderlist: 1,
              song_begin: 0,
              song_num: this.limit_song,
              onlysonglist: 0,
              enc_host_uin: ""
            }
          }
        }
      });
      const { body } = await requestObj_listDetail.promise;
      if (body.code !== this.successCode) return this.getListDetail2(id, ++tryNum);
      if (body.req_1.code !== this.successCode) throw new Error("failed");
      const result = body.req_1.data;
      const dirinfo = result.dirinfo;
      return {
        list: this.filterListDetail(result.songlist),
        page: 1,
        limit: this.limit_song,
        total: result.total_song_num,
        source: "tx",
        info: {
          name: dirinfo.title,
          img: dirinfo.picurl,
          desc: decodeName(dirinfo.desc ?? "").replace(/<br>/g, "\n"),
          author: dirinfo.host_nick,
          play_count: formatPlayCount(dirinfo.listennum)
        }
      };
    },
    // 获取歌曲列表内的音乐
    async getListDetail(id, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      id = await this.getListId(id);
      const requestObj_listDetail = httpFetch(this.getListDetailUrl(id), {
        headers: {
          Origin: "https://y.qq.com",
          Referer: `https://y.qq.com/n/yqq/playsquare/${id}.html`
        }
      });
      const { body } = await requestObj_listDetail.promise;
      if (body.code !== this.successCode) return this.getListDetail(id, ++tryNum);
      if (body.subcode !== this.successCode || !body.cdlist) return this.getListDetail2(id);
      const cdlist = body.cdlist[0];
      return {
        list: this.filterListDetail(cdlist.songlist),
        page: 1,
        limit: cdlist.songlist.length + 1,
        total: cdlist.songlist.length,
        source: "tx",
        info: {
          name: cdlist.dissname,
          img: cdlist.logo,
          desc: decodeName(cdlist.desc).replace(/<br>/g, "\n"),
          author: cdlist.nickname,
          play_count: formatPlayCount(cdlist.visitnum)
        }
      };
    },
    filterListDetail(rawList) {
      return rawList.map((item) => {
        let types = [];
        let _types = {};
        if (item.file.size_128mp3 !== 0) {
          let size = sizeFormate(item.file.size_128mp3);
          types.push({ type: "128k", size });
          _types["128k"] = {
            size
          };
        }
        if (item.file.size_320mp3 !== 0) {
          let size = sizeFormate(item.file.size_320mp3);
          types.push({ type: "320k", size });
          _types["320k"] = {
            size
          };
        }
        if (item.file.size_flac !== 0) {
          let size = sizeFormate(item.file.size_flac);
          types.push({ type: "flac", size });
          _types.flac = {
            size
          };
        }
        if (item.file.size_hires !== 0) {
          let size = sizeFormate(item.file.size_hires);
          types.push({ type: "flac24bit", size });
          _types.flac24bit = {
            size
          };
        }
        return {
          singer: formatSingerName(item.singer, "name"),
          name: item.title,
          albumName: item.album.name,
          albumId: item.album.mid,
          source: "tx",
          interval: formatPlayTime(item.interval),
          songId: item.id,
          albumMid: item.album.mid,
          strMediaMid: item.file.media_mid,
          songmid: item.mid,
          img: item.album.name === "" || item.album.name === "\u7A7A" ? item.singer?.length ? `https://y.gtimg.cn/music/photo_new/T001R500x500M000${item.singer[0].mid}.jpg` : "" : `https://y.gtimg.cn/music/photo_new/T002R500x500M000${item.album.mid}.jpg`,
          lrc: null,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    getTags() {
      return Promise.all([this.getTag(), this.getHotTag()]).then(([tags, hotTag]) => ({ tags, hotTag, source: "tx" }));
    },
    async getDetailPageUrl(id) {
      id = await this.getListId(id);
      return `https://y.qq.com/n/ryqq/playlist/${id}`;
    },
    search(text, page, limit = 20, retryNum = 0) {
      if (retryNum > 5) throw new Error("max retry");
      return httpFetch(`http://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist?page_no=${page - 1}&num_per_page=${limit}&format=json&query=${encodeURIComponent(text)}&remoteplace=txt.yqq.playlist&inCharset=utf8&outCharset=utf-8`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)",
          Referer: "http://y.qq.com/portal/search.html"
        }
      }).promise.then(({ body }) => {
        if (body.code != 0) return this.search(text, page, limit, ++retryNum);
        return {
          list: body.data.list.map((item) => {
            return {
              play_count: formatPlayCount(item.listennum),
              id: String(item.dissid),
              author: decodeName(item.creator.name),
              name: decodeName(item.dissname),
              time: dateFormat(item.createtime, "Y-M-D"),
              img: item.imgurl,
              // grade: item.favorcnt / 10,
              total: item.song_count,
              desc: decodeName(decodeName(item.introduction)).replace(/<br>/g, "\n"),
              source: "tx"
            };
          }),
          limit,
          total: body.data.sum,
          source: "tx"
        };
      });
    }
  };

  // src/utils/musicSdk/tx/utils/crypto.js
  var PART_1_INDEXES = [23, 14, 6, 36, 16, 40, 7, 19];
  var PART_2_INDEXES = [16, 1, 32, 12, 19, 27, 8, 5];
  var SCRAMBLE_VALUES = [89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179];
  function pickHashByIdx(hash, indexes) {
    return indexes.map((idx) => hash[idx]).join("");
  }
  function base64Encode(data) {
    return Buffer.from(data).toString("base64").replace(/[\\/+=]/g, "");
  }
  async function zzcSign(text) {
    const hash = await hashSHA1(text);
    const part1 = pickHashByIdx(hash, PART_1_INDEXES);
    const part2 = pickHashByIdx(hash, PART_2_INDEXES);
    const part3 = SCRAMBLE_VALUES.map((value, i) => value ^ parseInt(hash.slice(i * 2, i * 2 + 2), 16));
    const b64Part = base64Encode(part3).replace(/[\\/+=]/g, "");
    return `zzc${part1}${b64Part}${part2}`.toLowerCase();
  }

  // src/utils/musicSdk/tx/utils/index.js
  var signRequest = async (data) => {
    const sign = await zzcSign(JSON.stringify(data));
    return httpFetch(`https://u.y.qq.com/cgi-bin/musics.fcg?sign=${sign}`, {
      method: "post",
      headers: {
        "User-Agent": "QQMusic 14090508(android 12)"
      },
      body: data
    }).promise;
  };

  // src/utils/musicSdk/tx/musicSearch.js
  var musicSearch_default3 = {
    limit: 50,
    total: 0,
    page: 0,
    allPage: 1,
    successCode: 0,
    musicSearch(str, page, limit, retryNum = 0) {
      if (retryNum > 5) return Promise.reject(new Error("\u641C\u7D22\u5931\u8D25"));
      const searchRequest = signRequest({
        comm: {
          _channelid: "0",
          _os_version: "6.2.9200-2",
          ct: "19",
          cv: "2151",
          guid: "1F70E520B2EAA7D25E11760783C53CA9",
          patch: "118",
          psrf_access_token_expiresAt: 0,
          psrf_qqaccess_token: "",
          psrf_qqopenid: "",
          psrf_qqunionid: "",
          tmeAppID: "qqmusic",
          tmeLoginType: 0,
          uin: "0",
          wid: "7223299733393904640"
        },
        "music.search.SearchCgiService": {
          module: "music.search.SearchCgiService",
          method: "DoSearchForQQMusicDesktop",
          param: {
            grp: 1,
            num_per_page: limit,
            page_num: page,
            query: str,
            remoteplace: "txt.newclient.top",
            search_type: 0,
            searchid: this.getSearchId()
          }
        }
      });
      return searchRequest.then(({ body }) => {
        const req = body?.["music.search.SearchCgiService"] ?? body?.req;
        if (!req || body.code != this.successCode || req.code != this.successCode) {
          return this.musicSearch(str, page, limit, ++retryNum);
        }
        return req.data;
      });
    },
    /**
     * PC 客户端版 searchid：32 位大写十六进制 GUID + 5 位补零随机数 = 37 字符。
     * 对应 QQ 音乐 PC 端 searchid 形状（服务端只需要唯一的会话 ID，形状一致即可）。
     */
    getSearchId() {
      let guid = "";
      for (let i = 0; i < 32; i++) guid += Math.floor(Math.random() * 16).toString(16);
      return guid.toUpperCase() + String(Math.floor(Math.random() * 1e5)).padStart(5, "0");
    },
    handleResult(rawList) {
      if (!rawList || !Array.isArray(rawList)) return [];
      const list = [];
      rawList.forEach((item) => {
        if (!item.file?.media_mid) return;
        let types = [];
        let _types = {};
        const file = item.file;
        if (file.size_128mp3 != 0) {
          let size = sizeFormate(file.size_128mp3);
          types.push({ type: "128k", size });
          _types["128k"] = {
            size
          };
        }
        if (file.size_320mp3 !== 0) {
          let size = sizeFormate(file.size_320mp3);
          types.push({ type: "320k", size });
          _types["320k"] = {
            size
          };
        }
        if (file.size_flac !== 0) {
          let size = sizeFormate(file.size_flac);
          types.push({ type: "flac", size });
          _types.flac = {
            size
          };
        }
        if (file.size_hires !== 0) {
          let size = sizeFormate(file.size_hires);
          types.push({ type: "flac24bit", size });
          _types.flac24bit = {
            size
          };
        }
        let albumId = "";
        let albumName = "";
        if (item.album) {
          albumName = item.album.name;
          albumId = item.album.mid;
        }
        list.push({
          singer: formatSingerName(item.singer, "name"),
          // name: item.name + (item.title_extra ?? ''),
          name: item.title,
          albumName,
          albumId,
          source: "tx",
          interval: item.interval ? formatPlayTime(item.interval) : null,
          songId: item.id,
          albumMid: item.album?.mid ?? "",
          strMediaMid: item.file.media_mid,
          songmid: item.mid,
          img: albumId === "" || albumId === "\u7A7A" ? item.singer?.length ? `https://y.gtimg.cn/music/photo_new/T001R500x500M000${item.singer[0].mid}.jpg` : "" : `https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumId}.jpg`,
          types,
          _types,
          typeUrl: {}
        });
      });
      return list;
    },
    search(str, page = 1, limit) {
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then(({ body, meta }) => {
        let list = this.handleResult(body.song.list);
        this.total = meta.sum;
        this.page = page;
        this.allPage = Math.ceil(this.total / limit);
        return Promise.resolve({
          list,
          allPage: this.allPage,
          limit,
          total: this.total,
          source: "tx"
        });
      });
    }
  };

  // src/utils/musicSdk/tx/hotSearch.js
  var hotSearch_default3 = {
    _requestObj: null,
    async getList(retryNum = 0) {
      if (this._requestObj) this._requestObj.cancelHttp();
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      const _requestObj = httpFetch("https://u.y.qq.com/cgi-bin/musicu.fcg", {
        method: "post",
        body: {
          comm: {
            ct: "19",
            cv: "1803",
            guid: "0",
            patch: "118",
            psrf_access_token_expiresAt: 0,
            psrf_qqaccess_token: "",
            psrf_qqopenid: "",
            psrf_qqunionid: "",
            tmeAppID: "qqmusic",
            tmeLoginType: 0,
            uin: "0",
            wid: "0"
          },
          hotkey: {
            method: "GetHotkeyForQQMusicPC",
            module: "tencent_musicsoso_hotkey.HotkeyService",
            param: {
              search_id: "",
              uin: 0
            }
          }
        },
        headers: {
          Referer: "https://y.qq.com/portal/player.html"
        }
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.code !== 0) throw new Error("\u83B7\u53D6\u70ED\u641C\u8BCD\u5931\u8D25");
      return { source: "tx", list: this.filterList(body.hotkey.data.vec_hotkey) };
    },
    filterList(rawList) {
      return rawList.map((item) => item.query);
    }
  };

  // src/utils/musicSdk/tx/comment.js
  var emojis2 = {
    e400846: "\u{1F618}",
    e400874: "\u{1F634}",
    e400825: "\u{1F603}",
    e400847: "\u{1F619}",
    e400835: "\u{1F60D}",
    e400873: "\u{1F633}",
    e400836: "\u{1F60E}",
    e400867: "\u{1F62D}",
    e400832: "\u{1F60A}",
    e400837: "\u{1F60F}",
    e400875: "\u{1F62B}",
    e400831: "\u{1F609}",
    e400855: "\u{1F621}",
    e400823: "\u{1F604}",
    e400862: "\u{1F628}",
    e400844: "\u{1F616}",
    e400841: "\u{1F613}",
    e400830: "\u{1F608}",
    e400828: "\u{1F606}",
    e400833: "\u{1F60B}",
    e400822: "\u{1F600}",
    e400843: "\u{1F615}",
    e400829: "\u{1F607}",
    e400824: "\u{1F602}",
    e400834: "\u{1F60C}",
    e400877: "\u{1F637}",
    e400132: "\u{1F349}",
    e400181: "\u{1F37A}",
    e401067: "\u2615\uFE0F",
    e400186: "\u{1F967}",
    e400343: "\u{1F437}",
    e400116: "\u{1F339}",
    e400126: "\u{1F343}",
    e400613: "\u{1F48B}",
    e401236: "\u2764\uFE0F",
    e400622: "\u{1F494}",
    e400637: "\u{1F4A3}",
    e400643: "\u{1F4A9}",
    e400773: "\u{1F52A}",
    e400102: "\u{1F31B}",
    e401328: "\u{1F31E}",
    e400420: "\u{1F44F}",
    e400914: "\u{1F64C}",
    e400408: "\u{1F44D}",
    e400414: "\u{1F44E}",
    e401121: "\u270B",
    e400396: "\u{1F44B}",
    e400384: "\u{1F449}",
    e401115: "\u270A",
    e400402: "\u{1F44C}",
    e400905: "\u{1F648}",
    e400906: "\u{1F649}",
    e400907: "\u{1F64A}",
    e400562: "\u{1F47B}",
    e400932: "\u{1F64F}",
    e400644: "\u{1F4AA}",
    e400611: "\u{1F489}",
    e400185: "\u{1F381}",
    e400655: "\u{1F4B0}",
    e400325: "\u{1F425}",
    e400612: "\u{1F48A}",
    e400198: "\u{1F389}",
    e401685: "\u26A1\uFE0F",
    e400631: "\u{1F49D}",
    e400768: "\u{1F525}",
    e400432: "\u{1F451}"
  };
  var songIdMap2 = /* @__PURE__ */ new Map();
  var promises2 = /* @__PURE__ */ new Map();
  var comment_default3 = {
    _requestObj: null,
    _requestObj2: null,
    async getSongId({ songId, songmid }) {
      if (songId) return songId;
      if (songIdMap2.has(songmid)) return songIdMap2.get(songmid);
      if (promises2.has(songmid)) return (await promises2.get(songmid)).songId;
      const promise = musicInfo_default2(songmid);
      promises2.set(promise);
      const info = await promise;
      songIdMap2.set(songmid, info.songId);
      promises2.delete(songmid);
      return info.songId;
    },
    async getComment(mInfo, page = 1, limit = 20) {
      if (this._requestObj) this._requestObj.cancelHttp();
      const songId = await this.getSongId(mInfo);
      const _requestObj = httpFetch("http://c.y.qq.com/base/fcgi-bin/fcg_global_comment_h5.fcg", {
        method: "POST",
        headers: {
          "User-Agent": "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)"
        },
        form: {
          uin: "0",
          format: "json",
          cid: "205360772",
          reqtype: "2",
          biztype: "1",
          topid: songId,
          cmd: "8",
          needmusiccrit: "1",
          pagenum: page - 1,
          pagesize: limit
        }
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.code !== 0) throw new Error("\u83B7\u53D6\u8BC4\u8BBA\u5931\u8D25");
      const comment = body.comment;
      return {
        source: "tx",
        comments: this.filterNewComment(comment.commentlist),
        total: comment.commenttotal,
        page,
        limit,
        maxPage: Math.ceil(comment.commenttotal / limit) || 1
      };
    },
    async getHotComment(mInfo, page = 1, limit = 20) {
      if (this._requestObj2) this._requestObj2.cancelHttp();
      const songId = await this.getSongId(mInfo);
      const _requestObj2 = httpFetch("https://u.y.qq.com/cgi-bin/musicu.fcg", {
        method: "POST",
        body: {
          comm: {
            cv: 4747474,
            ct: 24,
            format: "json",
            inCharset: "utf-8",
            outCharset: "utf-8",
            notice: 0,
            platform: "yqq.json",
            needNewCode: 1,
            uin: 0
          },
          req: {
            module: "music.globalComment.CommentRead",
            method: "GetHotCommentList",
            param: {
              BizType: 1,
              BizId: String(songId),
              LastCommentSeqNo: "",
              PageSize: limit,
              PageNum: page - 1,
              HotType: 1,
              WithAirborne: 0,
              PicEnable: 1
            }
          }
        },
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36 Edg/113.0.0.0",
          referer: "https://y.qq.com/",
          origin: "https://y.qq.com"
        }
      });
      const { body, statusCode } = await _requestObj2.promise;
      if (statusCode != 200 || body.code !== 0 || body.req.code !== 0) throw new Error("\u83B7\u53D6\u70ED\u95E8\u8BC4\u8BBA\u5931\u8D25");
      const comment = body.req.data.CommentList;
      return {
        source: "tx",
        comments: this.filterHotComment(comment.Comments),
        total: comment.Total,
        page,
        limit,
        maxPage: Math.ceil(comment.Total / limit) || 1
      };
    },
    filterNewComment(rawList) {
      return rawList.map((item) => {
        let time = this.formatTime(item.time);
        let timeStr = time ? dateFormat2(time) : null;
        if (item.middlecommentcontent) {
          let firstItem = item.middlecommentcontent[0];
          firstItem.avatarurl = item.avatarurl;
          firstItem.praisenum = item.praisenum;
          item.avatarurl = null;
          item.praisenum = null;
          item.middlecommentcontent.reverse();
        }
        return {
          id: `${item.rootcommentid}_${item.commentid}`,
          rootId: item.rootcommentid,
          text: item.rootcommentcontent ? this.replaceEmoji(item.rootcommentcontent).replace(/\\n/g, "\n") : "",
          time: item.rootcommentid == item.commentid ? time : null,
          timeStr: item.rootcommentid == item.commentid ? timeStr : null,
          userName: item.rootcommentnick ? item.rootcommentnick.substring(1) : "",
          avatar: item.avatarurl,
          userId: item.encrypt_rootcommentuin,
          likedCount: item.praisenum,
          reply: item.middlecommentcontent ? item.middlecommentcontent.map((c) => {
            return {
              id: `sub_${item.rootcommentid}_${c.subcommentid}`,
              text: this.replaceEmoji(c.subcommentcontent).replace(/\\n/g, "\n"),
              time: c.subcommentid == item.commentid ? time : null,
              timeStr: c.subcommentid == item.commentid ? timeStr : null,
              userName: c.replynick.substring(1),
              avatar: c.avatarurl,
              userId: c.encrypt_replyuin,
              likedCount: c.praisenum
            };
          }) : []
        };
      });
    },
    filterHotComment(rawList) {
      return rawList.map((item) => {
        return {
          id: `${item.SeqNo}_${item.CmId}`,
          rootId: item.SeqNo,
          text: item.Content ? this.replaceEmoji(item.Content).replace(/\\n/g, "\n") : "",
          time: item.PubTime ? this.formatTime(item.PubTime) : null,
          timeStr: item.PubTime ? dateFormat2(this.formatTime(item.PubTime)) : null,
          userName: item.Nick ?? "",
          images: item.Pic ? [item.Pic] : [],
          avatar: item.Avatar,
          location: item.Location ? item.Location : "",
          userId: item.EncryptUin,
          likedCount: item.PraiseNum,
          reply: item.SubComments ? item.SubComments.map((c) => {
            return {
              id: `sub_${c.SeqNo}_${c.CmId}`,
              text: this.replaceEmoji(c.Content).replace(/\\n/g, "\n"),
              time: c.PubTime ? this.formatTime(c.PubTime) : null,
              timeStr: c.PubTime ? dateFormat2(this.formatTime(c.PubTime)) : null,
              userName: c.Nick ?? "",
              avatar: c.Avatar,
              images: c.Pic ? [c.Pic] : [],
              userId: c.EncryptUin,
              likedCount: c.PraiseNum
            };
          }) : []
        };
      });
    },
    replaceEmoji(msg) {
      let rxp = /^\[em\](e\d+)\[\/em\]$/;
      let result = msg.match(/\[em\]e\d+\[\/em\]/g);
      if (!result) return msg;
      result = Array.from(new Set(result));
      for (let item of result) {
        let code = item.replace(rxp, "$1");
        msg = msg.replace(new RegExp(item.replace("[em]", "\\[em\\]").replace("[/em]", "\\[\\/em\\]"), "g"), emojis2[code] || "");
      }
      return msg;
    },
    formatTime(time) {
      return String(time).length < 10 ? null : parseInt(time + "000");
    }
  };

  // src/utils/musicSdk/tx/index.js
  var tx = {
    // tipSearch,
    leaderboard: leaderboard_default3,
    songList: songList_default3,
    musicSearch: musicSearch_default3,
    hotSearch: hotSearch_default3,
    comment: comment_default3,
    getMusicUrl(songInfo, type) {
      return apis("tx").getMusicUrl(songInfo, type);
    },
    getLyric(songInfo) {
      return lyric_default3.getLyric(songInfo);
    },
    async getPic(songInfo) {
      return `https://y.gtimg.cn/music/photo_new/T002R500x500M000${songInfo.albumId}.jpg`;
    },
    getMusicDetailPageUrl(songInfo) {
      return `https://y.qq.com/n/yqq/song/${songInfo.songmid}.html`;
    }
  };
  var tx_default = tx;

  // src/utils/musicSdk/mg/utils/index.js
  var createHttpFetch = async (url, options, retryNum = 0) => {
    if (retryNum > 2) throw new Error("try max num");
    let result;
    try {
      result = await httpFetch(url, options).promise;
    } catch (err) {
      console.log(err);
      return createHttpFetch(url, options, ++retryNum);
    }
    if (result.statusCode !== 200 || (result.body.code !== void 0 ? result.body.code : result.body.returnCode !== void 0 ? result.body.returnCode : result.body.code) !== "000000") return createHttpFetch(url, options, ++retryNum);
    if (result.body.data) return result.body.data;
    return result.body;
  };

  // src/utils/musicSdk/mg/musicInfo.js
  var createGetMusicInfosTask = (ids) => {
    let list = ids;
    let tasks = [];
    while (list.length) {
      tasks.push(list.slice(0, 100));
      if (list.length < 100) break;
      list = list.slice(100);
    }
    let url = "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?resourceType=2";
    return Promise.all(tasks.map((task) => createHttpFetch(url, {
      method: "POST",
      form: {
        resourceId: task.join("|")
      }
    }).then((data) => data.resource)));
  };
  var filterMusicInfoList = (rawList) => {
    let ids = /* @__PURE__ */ new Set();
    const list = [];
    rawList.forEach((item) => {
      if (!item.songId || ids.has(item.songId)) return;
      ids.add(item.songId);
      const types = [];
      const _types = {};
      item.newRateFormats?.forEach((type) => {
        let size;
        switch (type.formatType) {
          case "PQ":
            size = sizeFormate(type.size ?? type.androidSize);
            types.push({ type: "128k", size });
            _types["128k"] = {
              size
            };
            break;
          case "HQ":
            size = sizeFormate(type.size ?? type.androidSize);
            types.push({ type: "320k", size });
            _types["320k"] = {
              size
            };
            break;
          case "SQ":
            size = sizeFormate(type.size ?? type.androidSize);
            types.push({ type: "flac", size });
            _types.flac = {
              size
            };
            break;
          case "ZQ":
            size = sizeFormate(type.size ?? type.androidSize);
            types.push({ type: "flac24bit", size });
            _types.flac24bit = {
              size
            };
            break;
        }
      });
      const intervalTest = /(\d\d:\d\d)$/.test(item.length);
      list.push({
        singer: formatSingerName(item.artists, "name"),
        name: item.songName,
        albumName: item.album,
        albumId: item.albumId,
        songmid: item.songId,
        copyrightId: item.copyrightId,
        source: "mg",
        interval: intervalTest ? RegExp.$1 : null,
        img: item.albumImgs?.length ? item.albumImgs[0].img : null,
        lrc: null,
        lrcUrl: item.lrcUrl,
        mrcUrl: item.mrcUrl,
        trcUrl: item.trcUrl,
        otherSource: null,
        types,
        _types,
        typeUrl: {}
      });
    });
    return list;
  };
  var filterMusicInfoListV5 = (rawList) => {
    let ids = /* @__PURE__ */ new Set();
    const list = [];
    rawList.forEach((item) => {
      if (!item.songId || ids.has(item.songId)) return;
      ids.add(item.songId);
      const types = [];
      const _types = {};
      item.audioFormats?.forEach((type) => {
        let size;
        switch (type.formatType) {
          case "PQ":
            size = sizeFormate(type.size ?? type.androidSize);
            types.push({ type: "128k", size });
            _types["128k"] = {
              size
            };
            break;
          case "HQ":
            size = sizeFormate(type.size ?? type.androidSize);
            types.push({ type: "320k", size });
            _types["320k"] = {
              size
            };
            break;
          case "SQ":
            size = sizeFormate(type.size ?? type.androidSize);
            types.push({ type: "flac", size });
            _types.flac = {
              size
            };
            break;
          case "ZQ":
            size = sizeFormate(type.size ?? type.androidSize);
            types.push({ type: "flac24bit", size });
            _types.flac24bit = {
              size
            };
            break;
        }
      });
      list.push({
        singer: formatSingerName(item.singerList, "name"),
        name: item.songName,
        albumName: item.album,
        albumId: item.albumId,
        songmid: item.songId,
        copyrightId: item.copyrightId,
        source: "mg",
        interval: formatPlayTime(item.duration),
        img: item.img3 || item.img2 || item.img1 || null,
        lrc: null,
        lrcUrl: item.lrcUrl,
        mrcUrl: item.mrcUrl,
        trcUrl: item.trcUrl,
        otherSource: null,
        types,
        _types,
        typeUrl: {}
      });
    });
    return list;
  };
  var getMusicInfo = async (copyrightId) => {
    return getMusicInfos([copyrightId]).then((data) => data[0]);
  };
  var getMusicInfos = async (copyrightIds) => {
    return filterMusicInfoList(await Promise.all(createGetMusicInfosTask(copyrightIds)).then((data) => data.flat()));
  };

  // src/utils/musicSdk/mg/leaderboard.js
  var boardList3 = [
    {
      id: "mg__27553319",
      name: "\u65B0\u6B4C\u699C",
      bangid: "27553319",
      source: "mg"
    },
    {
      id: "mg__27186466",
      name: "\u70ED\u6B4C\u699C",
      bangid: "27186466",
      source: "mg"
    },
    {
      id: "mg__27553408",
      name: "\u539F\u521B\u699C",
      bangid: "27553408",
      source: "mg"
    },
    {
      id: "mg__75959118",
      name: "\u97F3\u4E50\u98CE\u5411\u699C",
      bangid: "75959118",
      source: "mg"
    },
    {
      id: "mg__76557036",
      name: "\u5F69\u94C3\u5206\u8D1D\u699C",
      bangid: "76557036",
      source: "mg"
    },
    {
      id: "mg__76557745",
      name: "\u4F1A\u5458\u81FB\u7231\u699C",
      bangid: "76557745",
      source: "mg"
    },
    {
      id: "mg__23189800",
      name: "\u6E2F\u53F0\u699C",
      bangid: "23189800",
      source: "mg"
    },
    {
      id: "mg__23189399",
      name: "\u5185\u5730\u699C",
      bangid: "23189399",
      source: "mg"
    },
    {
      id: "mg__19190036",
      name: "\u6B27\u7F8E\u699C",
      bangid: "19190036",
      source: "mg"
    },
    {
      id: "mg__83176390",
      name: "\u56FD\u98CE\u91D1\u66F2\u699C",
      bangid: "83176390",
      source: "mg"
    }
  ];
  var leaderboard_default4 = {
    limit: 200,
    list: [
      {
        id: "mgyyb",
        name: "\u97F3\u4E50\u699C",
        bangid: "27553319"
      },
      {
        id: "mgysb",
        name: "\u5F71\u89C6\u699C",
        bangid: "23603721"
      },
      {
        id: "mghybnd",
        name: "\u534E\u8BED\u5185\u5730\u699C",
        bangid: "23603926"
      },
      {
        id: "mghyjqbgt",
        name: "\u534E\u8BED\u6E2F\u53F0\u699C",
        bangid: "23603954"
      },
      {
        id: "mgomb",
        name: "\u6B27\u7F8E\u699C",
        bangid: "23603974"
      },
      {
        id: "mgrhb",
        name: "\u65E5\u97E9\u699C",
        bangid: "23603982"
      },
      {
        id: "mgwlb",
        name: "\u7F51\u7EDC\u699C",
        bangid: "23604058"
      },
      {
        id: "mgclb",
        name: "\u5F69\u94C3\u699C",
        bangid: "23604023"
      },
      {
        id: "mgktvb",
        name: "KTV\u699C",
        bangid: "23604040"
      },
      {
        id: "mgrcb",
        name: "\u539F\u521B\u699C",
        bangid: "23604032"
      }
    ],
    getUrl(id, page) {
      return `https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/querycontentbyId.do?columnId=${id}&needAll=0`;
    },
    successCode: "000000",
    requestBoardsObj: null,
    getBoardsData() {
      if (this.requestBoardsObj) this._requestBoardsObj.cancelHttp();
      this.requestBoardsObj = httpFetch("https://app.c.nf.migu.cn/pc/bmw/rank/rank-index/v1.0", {
        // this.requestBoardsObj = httpFetch('https://app.c.nf.migu.cn/MIGUM3.0/v1.0/template/rank-list/release', {
        // this.requestBoardsObj = httpFetch('https://app.c.nf.migu.cn/MIGUM2.0/v2.0/content/indexrank.do?templateVersion=8', {
        headers: {
          Referer: "https://app.c.nf.migu.cn/",
          "User-Agent": "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 6 Build/LYZ28E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.115 Mobile Safari/537.36",
          channel: "0146921"
        }
      });
      return this.requestBoardsObj.promise;
    },
    getData(url) {
      const requestObj = httpFetch(url);
      return requestObj.promise;
    },
    // filterBoardsData(listData, list = [], ids = new Set()) {
    //   for (const item of listData) {
    //     if (item.rankId && !ids.has(item.rankId)) {
    //       ids.add(item.rankId)
    //       list.push({
    //         id: 'mg__' + item.rankId,
    //         name: item.rankName,
    //         bangid: String(item.rankId),
    //         source: 'mg',
    //       })
    //     } else if (item.contents) this.filterBoardsData(item.contents, list, ids)
    //   }
    //   return list
    // },
    // filterBoardsData(rawList) {
    //   // console.log(rawList)
    //   let list = []
    //   for (const board of rawList) {
    //     if (board.template != 'group1') continue
    //     for (const item of board.itemList) {
    //       if ((item.template != 'row1' && item.template != 'grid1' && !item.actionUrl) || !item.actionUrl.includes('rank-info')) continue
    //       let data = item.displayLogId.param
    //       list.push({
    //         id: 'mg__' + data.rankId,
    //         name: data.rankName,
    //         bangid: String(data.rankId),
    //       })
    //     }
    //   }
    //   return list
    // },
    async getBoards(retryNum = 0) {
      this.list = boardList3;
      return {
        list: boardList3,
        source: "mg"
      };
    },
    getList(bangid, page, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      return this.getData(this.getUrl(bangid, page)).then(({ statusCode, body }) => {
        if (statusCode !== 200 || body.code !== this.successCode) return this.getList(bangid, page, retryNum);
        const list = filterMusicInfoList(body.columnInfo.contents.map((m) => m.objectInfo));
        return {
          total: list.length,
          list,
          limit: this.limit,
          page,
          source: "mg"
        };
      });
    },
    getDetailPageUrl(id) {
      if (typeof id == "string") id = id.replace("mg__", "");
      for (const item of boardList3) {
        if (item.bangid == id) {
          return `https://music.migu.cn/v3/music/top/${item.webId}`;
        }
      }
      return null;
    }
  };

  // src/utils/musicSdk/mg/musicSearch.js
  var createSignature = (time, str) => {
    const deviceId = "963B7AA0D21511ED807EE5846EC87D20";
    const signatureMd5 = "6cdc72a439cef99a3418d2a78aa28c73";
    const sign = toMD5(`${str}${signatureMd5}yyapp2d16148780a1dcc7408e06336b98cfd50${deviceId}${time}`);
    return { sign, deviceId };
  };
  var musicSearch_default4 = {
    limit: 20,
    total: 0,
    page: 0,
    allPage: 1,
    // 旧版API
    // musicSearch(str, page, limit) {
    //   const searchRequest = httpFetch(`http://pd.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?ua=Android_migu&version=5.0.1&text=${encodeURIComponent(str)}&pageNo=${page}&pageSize=${limit}&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A0%2C%22mvSong%22%3A0%2C%22songlist%22%3A0%2C%22bestShow%22%3A1%7D`, {
    // searchRequest = httpFetch(`http://pd.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?ua=Android_migu&version=5.0.1&text=${encodeURIComponent(str)}&pageNo=${page}&pageSize=${limit}&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A0%2C%22mvSong%22%3A0%2C%22songlist%22%3A0%2C%22bestShow%22%3A1%7D`, {
    // searchRequest = httpFetch(`http://jadeite.migu.cn:7090/music_search/v2/search/searchAll?sid=4f87090d01c84984a11976b828e2b02c18946be88a6b4c47bcdc92fbd40762db&isCorrect=1&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize=${limit}&text=${encodeURIComponent(str)}&pageNo=${page}&sort=0`, {
    // searchRequest = httpFetch(`https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do?isCopyright=1&isCorrect=1&pageNo=${page}&pageSize=${limit}&searchSwitch={%22song%22:1,%22album%22:0,%22singer%22:0,%22tagSong%22:0,%22mvSong%22:0,%22songlist%22:0,%22bestShow%22:0}&sort=0&text=${encodeURIComponent(str)}`)
    //   // searchRequest = httpFetch(`http://jadeite.migu.cn:7090/music_search/v2/search/searchAll?sid=4f87090d01c84984a11976b828e2b02c18946be88a6b4c47bcdc92fbd40762db&isCorrect=1&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize=${limit}&text=${encodeURIComponent(str)}&pageNo=${page}&sort=0`, {
    //     headers: {
    //       // sign: 'c3b7ae985e2206e97f1b2de8f88691e2',
    //       // timestamp: 1578225871982,
    //       // appId: 'yyapp2',
    //       // mode: 'android',
    //       // ua: 'Android_migu',
    //       // version: '6.9.4',
    //       osVersion: 'android 7.0',
    //       'User-Agent': 'okhttp/3.9.1',
    //     },
    //   })
    //   // searchRequest = httpFetch(`https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do?isCopyright=1&isCorrect=1&pageNo=${page}&pageSize=${limit}&searchSwitch={%22song%22:1,%22album%22:0,%22singer%22:0,%22tagSong%22:0,%22mvSong%22:0,%22songlist%22:0,%22bestShow%22:0}&sort=0&text=${encodeURIComponent(str)}`)
    //   return searchRequest.promise.then(({ body }) => body)
    // },
    // handleResult(rawData) {
    //   // console.log(rawData)
    //   let ids = new Set()
    //   const list = []
    //   rawData.forEach(item => {
    //     if (ids.has(item.id)) return
    //     ids.add(item.id)
    //     const types = []
    //     const _types = {}
    //     item.newRateFormats && item.newRateFormats.forEach(type => {
    //       let size
    //       switch (type.formatType) {
    //         case 'PQ':
    //           size = sizeFormate(type.size ?? type.androidSize)
    //           types.push({ type: '128k', size })
    //           _types['128k'] = {
    //             size,
    //           }
    //           break
    //         case 'HQ':
    //           size = sizeFormate(type.size ?? type.androidSize)
    //           types.push({ type: '320k', size })
    //           _types['320k'] = {
    //             size,
    //           }
    //           break
    //         case 'SQ':
    //           size = sizeFormate(type.size ?? type.androidSize)
    //           types.push({ type: 'flac', size })
    //           _types.flac = {
    //             size,
    //           }
    //           break
    //         case 'ZQ':
    //           size = sizeFormate(type.size ?? type.androidSize)
    //           types.push({ type: 'flac24bit', size })
    //           _types.flac24bit = {
    //             size,
    //           }
    //           break
    //       }
    //     })
    //     const albumNInfo = item.albums && item.albums.length
    //       ? {
    //           id: item.albums[0].id,
    //           name: item.albums[0].name,
    //         }
    //       : {}
    //     list.push({
    //       singer: this.getSinger(item.singers),
    //       name: item.name,
    //       albumName: albumNInfo.name,
    //       albumId: albumNInfo.id,
    //       songmid: item.songId,
    //       copyrightId: item.copyrightId,
    //       source: 'mg',
    //       interval: null,
    //       img: item.imgItems && item.imgItems.length ? item.imgItems[0].img : null,
    //       lrc: null,
    //       lrcUrl: item.lyricUrl,
    //       mrcUrl: item.mrcurl,
    //       trcUrl: item.trcUrl,
    //       otherSource: null,
    //       types,
    //       _types,
    //       typeUrl: {},
    //     })
    //   })
    //   return list
    // },
    musicSearch(str, page, limit) {
      const time = Date.now().toString();
      const signData = createSignature(time, str);
      const searchRequest = httpFetch(`https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=0&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D&pageSize=${limit}&text=${encodeURIComponent(str)}&pageNo=${page}&sort=0&sid=USS`, {
        headers: {
          uiVersion: "A_music_3.6.1",
          deviceId: signData.deviceId,
          timestamp: time,
          sign: signData.sign,
          channel: "0146921",
          "User-Agent": "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"
        }
      });
      return searchRequest.promise.then(({ body }) => body);
    },
    filterData(rawData) {
      const list = [];
      const ids = /* @__PURE__ */ new Set();
      rawData.forEach((item) => {
        item.forEach((data) => {
          if (!data.songId || !data.copyrightId || ids.has(data.copyrightId)) return;
          ids.add(data.copyrightId);
          const types = [];
          const _types = {};
          data.audioFormats && data.audioFormats.forEach((type) => {
            let size;
            switch (type.formatType) {
              case "PQ":
                size = sizeFormate(type.asize ?? type.isize);
                types.push({ type: "128k", size });
                _types["128k"] = {
                  size
                };
                break;
              case "HQ":
                size = sizeFormate(type.asize ?? type.isize);
                types.push({ type: "320k", size });
                _types["320k"] = {
                  size
                };
                break;
              case "SQ":
                size = sizeFormate(type.asize ?? type.isize);
                types.push({ type: "flac", size });
                _types.flac = {
                  size
                };
                break;
              case "ZQ24":
                size = sizeFormate(type.asize ?? type.isize);
                types.push({ type: "flac24bit", size });
                _types.flac24bit = {
                  size
                };
                break;
            }
          });
          let img = data.img3 || data.img2 || data.img1 || null;
          if (img && !/https?:/.test(data.img3)) img = "http://d.musicapp.migu.cn" + img;
          list.push({
            singer: formatSingerName(data.singerList),
            name: data.name,
            albumName: data.album,
            albumId: data.albumId,
            songmid: data.songId,
            copyrightId: data.copyrightId,
            source: "mg",
            interval: formatPlayTime(data.duration),
            img,
            lrc: null,
            lrcUrl: data.lrcUrl,
            mrcUrl: data.mrcurl,
            trcUrl: data.trcUrl,
            types,
            _types,
            typeUrl: {}
          });
        });
      });
      return list;
    },
    search(str, page = 1, limit, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then((result) => {
        if (!result || result.code !== "000000") return Promise.reject(new Error(result ? result.info : "\u641C\u7D22\u5931\u8D25"));
        const songResultData = result.songResultData || { resultList: [], totalCount: 0 };
        let list = this.filterData(songResultData.resultList);
        if (list == null) return this.search(str, page, limit, retryNum);
        this.total = parseInt(songResultData.totalCount);
        this.page = page;
        this.allPage = Math.ceil(this.total / limit);
        return {
          list,
          allPage: this.allPage,
          limit,
          total: this.total,
          source: "mg"
        };
      });
    }
  };

  // src/utils/musicSdk/mg/songList.js
  var songList_default4 = {
    _requestObj_tags: null,
    _requestObj_list: null,
    limit_list: 30,
    limit_song: 30,
    successCode: "000000",
    cachedDetailInfo: {},
    cachedUrl: {},
    sortList: [
      {
        name: "\u63A8\u8350",
        id: "15127315",
        tid: "recommend"
        // id: '1',
      }
      // {
      //   name: '最新',
      //   id: '15127272',
      //   tid: 'new',
      //   // id: '2',
      // },
    ],
    regExps: {
      list: /<li><div class="thumb">.+?<\/li>/g,
      listInfo: /.+data-original="(.+?)".*data-id="(\d+)".*<div class="song-list-name"><a\s.*?>(.+?)<\/a>.+<i class="iconfont cf-bofangliang"><\/i>(.+?)<\/div>/,
      // https://music.migu.cn/v3/music/playlist/161044573?page=1
      listDetailLink: /^.+\/playlist\/(\d+)(?:\?.*|&.*$|#.*$|$)/
    },
    tagsUrl: "https://app.c.nf.migu.cn/pc/v1.0/template/musiclistplaza-taglist/release",
    // tagsUrl: 'https://app.c.nf.migu.cn/MIGUM3.0/v1.0/template/musiclistplaza-taglist/release',
    // tagsUrl: 'https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/indexTagPage.do?needAll=0',
    getSongListUrl(sortId, tagId, page) {
      if (!tagId) {
        return `https://app.c.nf.migu.cn/pc/bmw/page-data/playlist-square-recommend/v1.0?templateVersion=2&pageNo=${page}`;
      }
      return `https://app.c.nf.migu.cn/pc/v1.0/template/musiclistplaza-listbytag/release?pageNumber=${page}&templateVersion=2&tagId=${tagId}`;
    },
    getSongListDetailUrl(id, page) {
      return `https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0?pageNo=${page}&pageSize=${this.limit_song}&playlistId=${id}`;
    },
    defaultHeaders: {
      "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1",
      Referer: "https://m.music.migu.cn/"
      // language: 'Chinese',
      // ua: 'Android_migu',
      // mode: 'android',
      // version: '6.8.5',
    },
    getListDetailList(id, page, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      const requestObj_listDetail = httpFetch(this.getSongListDetailUrl(id, page), { headers: this.defaultHeaders });
      return requestObj_listDetail.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getListDetailList(id, page, ++tryNum);
        return {
          list: filterMusicInfoListV5(body.data.songList),
          page,
          limit: this.limit_song,
          total: body.data.totalCount,
          source: "mg"
        };
      });
    },
    getListDetailInfo(id, tryNum = 0) {
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      if (this.cachedDetailInfo[id]) return Promise.resolve(this.cachedDetailInfo[id]);
      const requestObj_listDetailInfo = httpFetch(`https://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/v2.0?playlistId=${id}`, {
        headers: this.defaultHeaders
      });
      return requestObj_listDetailInfo.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getListDetail(id, ++tryNum);
        const cachedDetailInfo = this.cachedDetailInfo[id] = {
          name: body.data.title,
          img: body.data.imgItem.img,
          desc: body.data.summary,
          author: body.data.ownerName,
          play_count: formatPlayCount(body.data.opNumItem.playNum)
        };
        return cachedDetailInfo;
      });
    },
    async getDetailUrl(link, page, retryNum = 0) {
      if (retryNum > 3) return Promise.reject(new Error("link try max num"));
      const requestObj_listDetailLink = httpFetch(link, {
        headers: {
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1",
          Referer: link
        }
      });
      const { url: location, statusCode } = await requestObj_listDetailLink.promise;
      if (statusCode > 400) return this.getDetailUrl(link, page, ++retryNum);
      if (location.split("?")[0] != link.split("?")[0]) {
        this.cachedUrl[link] = location;
        return this.getListDetail(location, page);
      }
      return Promise.reject(new Error("link get failed"));
    },
    getListDetail(id, page, retryNum = 0) {
      if (/\/playlist[/?]/.test(id)) {
        id = /(?:playlistId|id)=(\d+)/.exec(id)?.[1];
        if (!id) throw new Error("list detail id parse failed");
      } else if (this.regExps.listDetailLink.test(id)) {
        id = id.replace(this.regExps.listDetailLink, "$1");
      } else if (/[?&:/]/.test(id)) {
        const url = this.cachedUrl[id];
        return url ? this.getListDetail(url, page) : this.getDetailUrl(id, page);
      }
      return Promise.all([
        this.getListDetailList(id, page, retryNum),
        this.getListDetailInfo(id, retryNum)
      ]).then(([listData, info]) => {
        listData.info = info;
        return listData;
      });
    },
    // 获取列表数据
    getList(sortId, tagId, page, tryNum = 0) {
      if (this._requestObj_list) this._requestObj_list.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_list = httpFetch(this.getSongListUrl(sortId, tagId, page), {
        headers: this.defaultHeaders
        // headers: {
        //   sign: 'c3b7ae985e2206e97f1b2de8f88691e2',
        //   timestamp: 1578225871982,
        //   appId: 'yyapp2',
        //   mode: 'android',
        //   ua: 'Android_migu',
        //   version: '6.9.4',
        //   osVersion: 'android 7.0',
        //   'User-Agent': 'okhttp/3.9.1',
        // },
      });
      return this._requestObj_list.promise.then(({ body }) => {
        if (body.code !== "000000") return this.getList(sortId, tagId, page, ++tryNum);
        const list = body.data.contents ? this.filterList2(body.data.contents) : this.filterList(body.data.contentItemList[1].itemList);
        return {
          list,
          total: 99999,
          page,
          limit: this.limit_list,
          source: "mg"
        };
      });
    },
    filterList2(listData, list = [], ids = /* @__PURE__ */ new Set()) {
      for (const item of listData) {
        if (item.contents) this.filterList2(item.contents, list, ids);
        else if (item.resType == "2021" && !ids.has(item.resId)) {
          ids.add(item.resId);
          list.push({
            id: String(item.resId),
            author: "",
            name: item.txt,
            // time: dateFormat(item.createTime, 'Y-M-D'),
            img: item.img,
            // grade: item.grade,
            // total: item.contentCount,
            desc: item.txt2,
            source: "mg"
          });
        }
      }
      return list;
    },
    filterList(rawData) {
      return rawData.map((item) => ({
        play_count: item.barList[0]?.title,
        id: String(item.logEvent.contentId),
        author: "",
        name: item.title,
        // time: dateFormat(item.createTime, 'Y-M-D'),
        img: item.imageUrl,
        // grade: item.grade,
        // total: item.contentCount,
        desc: "",
        source: "mg"
      }));
    },
    // 获取标签
    getTag(tryNum = 0) {
      if (this._requestObj_tags) this._requestObj_tags.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_tags = httpFetch(this.tagsUrl, { headers: this.defaultHeaders });
      return this._requestObj_tags.promise.then(({ body }) => {
        if (body.code !== this.successCode) return this.getTag(++tryNum);
        return this.filterTagInfo(body.data);
      });
    },
    filterTagInfo(rawList) {
      return {
        hotTag: rawList[0].content.map(({ texts: [name, id] }) => ({
          id,
          name,
          source: "mg"
        })),
        tags: rawList.slice(1).map(({ header, content }) => ({
          name: header.title,
          list: content.map(({ texts: [name, id] }) => ({
            // parent_id: objectInfo.columnId,
            // parent_name: objectInfo.columnTitle,
            id,
            name,
            source: "mg"
          }))
        })),
        source: "mg"
      };
    },
    getTags() {
      return this.getTag();
    },
    getDetailPageUrl(id) {
      if (/playlist\/index\.html\?/.test(id)) {
        id = id.replace(/.*(?:\?|&)id=(\d+)(?:&.*|$)/, "$1");
      } else if (this.regExps.listDetailLink.test(id)) {
        id = id.replace(this.regExps.listDetailLink, "$1");
      }
      return `https://music.migu.cn/v3/music/playlist/${id}`;
    },
    filterSongListResult(raw) {
      const list = [];
      raw.forEach((item) => {
        if (!item.id) return;
        const playCount = parseInt(item.playNum);
        list.push({
          play_count: isNaN(playCount) ? 0 : formatPlayCount(playCount),
          id: item.id,
          author: item.userName,
          name: item.name,
          img: item.musicListPicUrl,
          total: item.musicNum,
          source: "mg"
        });
      });
      return list;
    },
    search(text, page, limit = 20) {
      const timeStr = Date.now().toString();
      const signResult = createSignature(timeStr, text);
      return createHttpFetch(`https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=1&isCopyright=1&searchSwitch=%7B%22song%22%3A0%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A0%2C%22mvSong%22%3A0%2C%22bestShow%22%3A0%2C%22songlist%22%3A1%2C%22lyricSong%22%3A0%7D&pageSize=${limit}&text=${encodeURIComponent(text)}&pageNo=${page}&sort=0&sid=USS`, {
        headers: {
          uiVersion: "A_music_3.6.1",
          deviceId: signResult.deviceId,
          timestamp: timeStr,
          sign: signResult.sign,
          channel: "0146921",
          "User-Agent": "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"
        }
      }).then((body) => {
        if (!body.songListResultData) throw new Error("get song list faild.");
        const list = this.filterSongListResult(body.songListResultData.result);
        return {
          list,
          limit,
          total: parseInt(body.songListResultData.totalCount),
          source: "mg"
        };
      });
    }
  };

  // src/utils/musicSdk/mg/pic.js
  var pic_default2 = {
    async getPic(songInfo) {
      const info = await getMusicInfo(songInfo.songmid);
      return info.img;
    }
  };

  // src/utils/musicSdk/mg/utils/mrc.js
  var DELTA = 2654435769n;
  var MIN_LENGTH = 32;
  var keyArr = [
    27303562373562475n,
    18014862372307051n,
    22799692160172081n,
    34058940340699235n,
    30962724186095721n,
    27303523720101991n,
    27303523720101998n,
    31244139033526382n,
    28992395054481524n
  ];
  var teaDecrypt = (data, key) => {
    const length = data.length;
    const lengthBitint = BigInt(length);
    if (length >= 1) {
      let j2 = data[0];
      let j3 = toLong((6n + 52n / lengthBitint) * DELTA);
      while (true) {
        let j4 = j3;
        if (j4 == 0n) break;
        let j5 = toLong(3n & toLong(j4 >> 2n));
        let j6 = lengthBitint;
        while (true) {
          j6--;
          if (j6 > 0n) {
            let j7 = data[j6 - 1n];
            let i = j6;
            j2 = toLong(data[i] - (toLong(toLong(j2 ^ j4) + toLong(j7 ^ key[toLong(toLong(3n & j6) ^ j5)])) ^ toLong(toLong(toLong(j7 >> 5n) ^ toLong(j2 << 2n)) + toLong(toLong(j2 >> 3n) ^ toLong(j7 << 4n)))));
            data[i] = j2;
          } else break;
        }
        let j8 = data[lengthBitint - 1n];
        j2 = toLong(data[0n] - toLong(toLong(toLong(key[toLong(toLong(j6 & 3n) ^ j5)] ^ j8) + toLong(j2 ^ j4)) ^ toLong(toLong(toLong(j8 >> 5n) ^ toLong(j2 << 2n)) + toLong(toLong(j2 >> 3n) ^ toLong(j8 << 4n)))));
        data[0] = j2;
        j3 = toLong(j4 - DELTA);
      }
    }
    return data;
  };
  var longArrToString = (data) => {
    const arrayList = [];
    for (const j of data) arrayList.push(longToBytes(j).toString("utf16le"));
    return arrayList.join("");
  };
  var longToBytes = (l) => {
    const result = Buffer.alloc(8);
    for (let i = 0; i < 8; i++) {
      result[i] = parseInt(l & 0xFFn);
      l >>= 8n;
    }
    return result;
  };
  var toBigintArray = (data) => {
    const length = Math.floor(data.length / 16);
    const jArr = Array(length);
    for (let i = 0; i < length; i++) {
      jArr[i] = toLong(data.substring(i * 16, i * 16 + 16));
    }
    return jArr;
  };
  var MAX = 9223372036854775807n;
  var MIN = -9223372036854775808n;
  var toLong = (str) => {
    const num = typeof str == "string" ? BigInt("0x" + str) : str;
    if (num > MAX) return toLong(num - (1n << 64n));
    else if (num < MIN) return toLong(num + (1n << 64n));
    return num;
  };
  var decrypt = (data) => {
    return data == null || data.length < MIN_LENGTH ? data : longArrToString(teaDecrypt(toBigintArray(data), keyArr));
  };

  // src/utils/musicSdk/mg/lyric.js
  var mrcTools = {
    rxps: {
      lineTime: /^\s*\[(\d+),\d+\]/,
      wordTime: /\(\d+,\d+\)/,
      wordTimeAll: /(\(\d+,\d+\))/g
    },
    parseLyric(str) {
      str = str.replace(/\r/g, "");
      const lines = str.split("\n");
      const lxlrcLines = [];
      const lrcLines = [];
      for (const line of lines) {
        if (line.length < 6) continue;
        let result = this.rxps.lineTime.exec(line);
        if (!result) continue;
        const startTime = parseInt(result[1]);
        let time = startTime;
        let ms = (time % 1e3).toString().padStart(3, "0");
        time /= 1e3;
        let m = parseInt(time / 60).toString().padStart(2, "0");
        time %= 60;
        let s = parseInt(time).toString().padStart(2, "0");
        time = `${m}:${s}.${ms}`;
        let words = line.replace(this.rxps.lineTime, "");
        lrcLines.push(`[${time}]${words.replace(this.rxps.wordTimeAll, "")}`);
        let times = words.match(this.rxps.wordTimeAll);
        if (!times) continue;
        times = times.map((time2) => {
          const result2 = /\((\d+),(\d+)\)/.exec(time2);
          return `<${Math.trunc(parseInt(result2[1]) - startTime)},${result2[2]}>`;
        });
        const wordArr = words.split(this.rxps.wordTime);
        const newWords = times.map((time2, index) => `${time2}${wordArr[index]}`).join("");
        lxlrcLines.push(`[${time}]${newWords}`);
      }
      return {
        lyric: lrcLines.join("\n"),
        lxlyric: lxlrcLines.join("\n")
      };
    },
    getText(url, tryNum = 0) {
      const requestObj = httpFetch(url, {
        headers: {
          Referer: "https://app.c.nf.migu.cn/",
          "User-Agent": "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 6 Build/LYZ28E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.115 Mobile Safari/537.36",
          channel: "0146921"
        }
      });
      return requestObj.promise.then(({ statusCode, body }) => {
        if (statusCode == 200) return body;
        if (tryNum > 5 || statusCode == 404) return Promise.reject(new Error("\u6B4C\u8BCD\u83B7\u53D6\u5931\u8D25"));
        return this.getText(url, ++tryNum);
      });
    },
    getMrc(url) {
      return this.getText(url).then((text) => {
        return this.parseLyric(decrypt(text));
      });
    },
    getLrc(url) {
      return this.getText(url).then((text) => ({ lxlyric: "", lyric: text }));
    },
    getTrc(url) {
      if (!url) return Promise.resolve("");
      return this.getText(url);
    },
    async getMusicInfo(songInfo) {
      return songInfo.mrcUrl == null ? getMusicInfo(songInfo.songmid) : songInfo;
    },
    getLyric(songInfo) {
      return {
        promise: this.getMusicInfo(songInfo).then((info) => {
          let p;
          if (info.mrcUrl) p = this.getMrc(info.mrcUrl);
          else if (info.lrcUrl) p = this.getLrc(info.lrcUrl);
          if (p == null) return Promise.reject(new Error("\u83B7\u53D6\u6B4C\u8BCD\u5931\u8D25"));
          return Promise.all([p, this.getTrc(info.trcUrl)]).then(([lrcInfo, tlyric]) => {
            lrcInfo.tlyric = tlyric;
            return lrcInfo;
          });
        }),
        cancelHttp() {
        }
      };
    }
  };
  var lyric_default4 = {
    getLyric(songInfo) {
      let requestObj = mrcTools.getLyric(songInfo);
      return requestObj;
    }
  };

  // src/utils/musicSdk/mg/hotSearch.js
  var hotSearch_default4 = {
    _requestObj: null,
    async getList(retryNum = 0) {
      if (this._requestObj) this._requestObj.cancelHttp();
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      const _requestObj = httpFetch("http://jadeite.migu.cn:7090/music_search/v3/search/hotword");
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.code !== "000000") throw new Error("\u83B7\u53D6\u70ED\u641C\u8BCD\u5931\u8D25");
      return { source: "mg", list: this.filterList(body.data.hotwords[0].hotwordList) };
    },
    filterList(rawList) {
      return rawList.filter((item) => item.resourceType == "song").map((item) => item.word);
    }
  };

  // src/utils/musicSdk/mg/songId.js
  var getSongId = async (mInfo) => {
    if (mInfo.songmid != mInfo.copyrightId) return mInfo.songmid;
    const musicInfo = await getMusicInfo(mInfo.songmid);
    return musicInfo.songmid;
  };
  var songId_default = getSongId;

  // src/utils/musicSdk/mg/comment.js
  var comment_default4 = {
    _requestObj: null,
    _requestObj2: null,
    _requestObj3: null,
    lastCommentIds: /* @__PURE__ */ new Map(),
    async getComment(musicInfo, page = 1, limit = 20) {
      if (this._requestObj) this._requestObj.cancelHttp();
      if (!musicInfo.songId) {
        let id = await songId_default(musicInfo);
        if (!id) throw new Error("\u83B7\u53D6\u8BC4\u8BBA\u5931\u8D25");
        musicInfo.songId = id;
      }
      if (page === 1) this.lastCommentIds.clear();
      const lastCommentId = this.lastCommentIds.get(String(page)) || "";
      if (!lastCommentId && page > 1) throw new Error("\u83B7\u53D6\u8BC4\u8BBA\u5931\u8D25");
      const _requestObj = httpFetch(`https://app.c.nf.migu.cn/MIGUM3.0/user/comment/stack/v1.0?pageSize=${limit}&queryType=1&resourceId=${musicInfo.songId}&resourceType=2&commentId=${lastCommentId}`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"
          // Referer: 'https://music.migu.cn',
        }
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.code !== "000000") throw new Error("\u83B7\u53D6\u8BC4\u8BBA\u5931\u8D25");
      const total = parseInt(body.data.commentNums);
      const list = this.filterComment(body.data.comments);
      this.lastCommentIds.set(String(page + 1), list.length ? list[list.length - 1].id : "");
      return { source: "mg", comments: list, total, page, limit, maxPage: Math.ceil(total / limit) || 1 };
    },
    async getHotComment(musicInfo, page = 1, limit = 20) {
      if (this._requestObj2) this._requestObj2.cancelHttp();
      if (!musicInfo.songId) {
        let id = await songId_default(musicInfo);
        if (!id) throw new Error("\u83B7\u53D6\u8BC4\u8BBA\u5931\u8D25");
        musicInfo.songId = id;
      }
      const _requestObj2 = httpFetch(`https://app.c.nf.migu.cn/MIGUM3.0/user/comment/stack/v1.0?pageSize=${limit}&queryType=2&resourceId=${musicInfo.songId}&resourceType=2&hotCommentStart=${(page - 1) * limit}`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"
          // Referer: 'https://music.migu.cn',
        }
      });
      const { body, statusCode } = await _requestObj2.promise;
      if (statusCode != 200 || body.code !== "000000") throw new Error("\u83B7\u53D6\u70ED\u95E8\u8BC4\u8BBA\u5931\u8D25");
      const total = parseInt(body.data.cfgHotCount);
      return { source: "mg", comments: this.filterComment(body.data.hotComments), total, page, limit, maxPage: Math.ceil(total / limit) || 1 };
    },
    async getReplyComment(musicInfo, replyId, page = 1, limit = 10) {
      if (this._requestObj2) this._requestObj2.cancelHttp();
      const _requestObj2 = httpFetch(`https://app.c.nf.migu.cn/MIGUM3.0/user/comment/stack/${replyId}/v1.0?pageSize=${limit}&queryType=2&resourceId=${musicInfo.songId}&resourceType=2&start=${(page - 1) * limit}`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"
        }
      });
      const { body, statusCode } = await _requestObj2.promise;
      if (statusCode != 200 || body.code !== "000000") throw new Error("\u83B7\u53D6\u56DE\u590D\u8BC4\u8BBA\u5931\u8D25");
      const total = parseInt(body.data.replyTotalCount);
      return { source: "mg", comments: this.filterComment(body.data.mainCommentItem.replyComments), total, page, limit, maxPage: Math.ceil(total / limit) || 1 };
    },
    filterComment(rawList) {
      return rawList.map((item) => ({
        id: item.commentId,
        text: item.commentInfo,
        time: item.commentTime,
        timeStr: dateFormat2(new Date(item.commentTime).getTime()),
        userName: item.user.nickName,
        avatar: item.user.middleIcon || item.user.bigIcon || item.user.smallIcon,
        userId: item.user.userId,
        likedCount: item.opNumItem.thumbNum,
        replyNum: item.replyTotalCount,
        reply: item.replyComments.map((c) => ({
          id: c.replyId,
          text: c.replyInfo,
          time: c.replyTime,
          timeStr: dateFormat2(new Date(c.replyTime).getTime()),
          userName: c.user.nickName,
          avatar: c.user.middleIcon || c.user.bigIcon || c.user.smallIcon,
          userId: c.user.userId,
          likedCount: null,
          replyNum: null
        }))
      }));
    }
  };

  // src/utils/musicSdk/mg/index.js
  var mg = {
    // tipSearch,
    songList: songList_default4,
    musicSearch: musicSearch_default4,
    leaderboard: leaderboard_default4,
    hotSearch: hotSearch_default4,
    comment: comment_default4,
    getMusicUrl(songInfo, type) {
      return apis("mg").getMusicUrl(songInfo, type);
    },
    getLyric(songInfo) {
      return lyric_default4.getLyric(songInfo);
    },
    getPic(songInfo) {
      return pic_default2.getPic(songInfo);
    },
    getMusicDetailPageUrl(songInfo) {
      return `http://music.migu.cn/v3/music/song/${songInfo.copyrightId}`;
    }
  };
  var mg_default = mg;

  // src/utils/musicSdk/kg/leaderboard.js
  var boardList4 = [{ id: "kg__8888", name: "TOP500", bangid: "8888" }, { id: "kg__6666", name: "\u98D9\u5347\u699C", bangid: "6666" }, { id: "kg__59703", name: "\u8702\u9E1F\u6D41\u884C\u97F3\u4E50\u699C", bangid: "59703" }, { id: "kg__52144", name: "\u6296\u97F3\u70ED\u6B4C\u699C", bangid: "52144" }, { id: "kg__52767", name: "\u5FEB\u624B\u70ED\u6B4C\u699C", bangid: "52767" }, { id: "kg__24971", name: "DJ\u70ED\u6B4C\u699C", bangid: "24971" }, { id: "kg__23784", name: "\u7F51\u7EDC\u7EA2\u6B4C\u699C", bangid: "23784" }, { id: "kg__44412", name: "\u8BF4\u5531\u5148\u950B\u699C", bangid: "44412" }, { id: "kg__31308", name: "\u5185\u5730\u699C", bangid: "31308" }, { id: "kg__33160", name: "\u7535\u97F3\u699C", bangid: "33160" }, { id: "kg__31313", name: "\u9999\u6E2F\u5730\u533A\u699C", bangid: "31313" }, { id: "kg__51341", name: "\u6C11\u8C23\u699C", bangid: "51341" }, { id: "kg__54848", name: "\u53F0\u6E7E\u5730\u533A\u699C", bangid: "54848" }, { id: "kg__31310", name: "\u6B27\u7F8E\u699C", bangid: "31310" }, { id: "kg__33162", name: "ACG\u65B0\u6B4C\u699C", bangid: "33162" }, { id: "kg__31311", name: "\u97E9\u56FD\u699C", bangid: "31311" }, { id: "kg__31312", name: "\u65E5\u672C\u699C", bangid: "31312" }, { id: "kg__49225", name: "80\u540E\u70ED\u6B4C\u699C", bangid: "49225" }, { id: "kg__49223", name: "90\u540E\u70ED\u6B4C\u699C", bangid: "49223" }, { id: "kg__49224", name: "00\u540E\u70ED\u6B4C\u699C", bangid: "49224" }, { id: "kg__33165", name: "\u7CA4\u8BED\u91D1\u66F2\u699C", bangid: "33165" }, { id: "kg__33166", name: "\u6B27\u7F8E\u91D1\u66F2\u699C", bangid: "33166" }, { id: "kg__33163", name: "\u5F71\u89C6\u91D1\u66F2\u699C", bangid: "33163" }, { id: "kg__51340", name: "\u4F24\u611F\u699C", bangid: "51340" }, { id: "kg__35811", name: "\u4F1A\u5458\u4E13\u4EAB\u699C", bangid: "35811" }, { id: "kg__37361", name: "\u96F7\u8FBE\u699C", bangid: "37361" }, { id: "kg__21101", name: "\u5206\u4EAB\u699C", bangid: "21101" }, { id: "kg__46910", name: "\u7EFC\u827A\u65B0\u6B4C\u699C", bangid: "46910" }, { id: "kg__30972", name: "\u9177\u72D7\u97F3\u4E50\u4EBA\u539F\u521B\u699C", bangid: "30972" }, { id: "kg__60170", name: "\u95FD\u5357\u8BED\u699C", bangid: "60170" }, { id: "kg__65234", name: "\u513F\u6B4C\u699C", bangid: "65234" }, { id: "kg__4681", name: "\u7F8E\u56FDBillBoard\u699C", bangid: "4681" }, { id: "kg__25028", name: "Beatport\u7535\u5B50\u821E\u66F2\u699C", bangid: "25028" }, { id: "kg__4680", name: "\u82F1\u56FD\u5355\u66F2\u699C", bangid: "4680" }, { id: "kg__38623", name: "\u97E9\u56FDMelon\u97F3\u4E50\u699C", bangid: "38623" }, { id: "kg__42807", name: "joox\u672C\u5730\u70ED\u6B4C\u699C", bangid: "42807" }, { id: "kg__36107", name: "\u5C0F\u8BED\u79CD\u70ED\u6B4C\u699C", bangid: "36107" }, { id: "kg__4673", name: "\u65E5\u672C\u516C\u4FE1\u699C", bangid: "4673" }, { id: "kg__46868", name: "\u65E5\u672CSPACE SHOWER\u699C", bangid: "46868" }, { id: "kg__42808", name: "KKBOX\u98CE\u4E91\u699C", bangid: "42808" }, { id: "kg__60171", name: "\u8D8A\u5357\u8BED\u699C", bangid: "60171" }, { id: "kg__60172", name: "\u6CF0\u8BED\u699C", bangid: "60172" }, { id: "kg__59895", name: "R&B\u699C", bangid: "59895" }, { id: "kg__59896", name: "\u6447\u6EDA\u699C", bangid: "59896" }, { id: "kg__59897", name: "\u7235\u58EB\u699C", bangid: "59897" }, { id: "kg__59898", name: "\u4E61\u6751\u97F3\u4E50\u699C", bangid: "59898" }, { id: "kg__59900", name: "\u7EAF\u97F3\u4E50\u699C", bangid: "59900" }, { id: "kg__59899", name: "\u53E4\u5178\u699C", bangid: "59899" }, { id: "kg__22603", name: "5sing\u97F3\u4E50\u699C", bangid: "22603" }, { id: "kg__21335", name: "\u7E41\u661F\u97F3\u4E50\u699C", bangid: "21335" }, { id: "kg__33161", name: "\u53E4\u98CE\u65B0\u6B4C\u699C", bangid: "33161" }];
  var leaderboard_default5 = {
    listDetailLimit: 100,
    list: [
      {
        id: "kgtop500",
        name: "TOP500",
        bangid: "8888"
      },
      {
        id: "kgwlhgb",
        name: "\u7F51\u7EDC\u699C",
        bangid: "23784"
      },
      {
        id: "kgbsb",
        name: "\u98D9\u5347\u699C",
        bangid: "6666"
      },
      {
        id: "kgfxb",
        name: "\u5206\u4EAB\u699C",
        bangid: "21101"
      },
      {
        id: "kgcyyb",
        name: "\u7EAF\u97F3\u4E50\u699C",
        bangid: "33164"
      },
      {
        id: "kggfjqb",
        name: "\u53E4\u98CE\u699C",
        bangid: "33161"
      },
      {
        id: "kgyyjqb",
        name: "\u7CA4\u8BED\u699C",
        bangid: "33165"
      },
      {
        id: "kgomjqb",
        name: "\u6B27\u7F8E\u699C",
        bangid: "33166"
      },
      {
        id: "kgdyrgb",
        name: "\u7535\u97F3\u699C",
        bangid: "33160"
      },
      {
        id: "kgjdrgb",
        name: "DJ\u70ED\u6B4C\u699C",
        bangid: "24971"
      },
      {
        id: "kghyxgb",
        name: "\u534E\u8BED\u65B0\u6B4C\u699C",
        bangid: "31308"
      }
    ],
    getUrl(p, id, limit) {
      return `http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=1&plat=0&pagesize=${limit}&area_code=1&page=${p}&rankid=${id}&with_res_tag=0&show_portrait_mv=1`;
    },
    regExps: {
      total: /total: '(\d+)',/,
      page: /page: '(\d+)',/,
      limit: /pagesize: '(\d+)',/,
      listData: /global\.features = (\[.+\]);/
    },
    _requestBoardsObj: null,
    getBoardsData() {
      if (this._requestBoardsObj) this._requestBoardsObj.cancelHttp();
      this._requestBoardsObj = httpFetch("http://mobilecdnbj.kugou.com/api/v5/rank/list?version=9108&plat=0&showtype=2&parentid=0&apiver=6&area_code=1&withsong=1");
      return this._requestBoardsObj.promise;
    },
    getData(url) {
      const requestDataObj = httpFetch(url);
      return requestDataObj.promise;
    },
    getSinger(singers) {
      let arr = [];
      singers.forEach((singer) => {
        arr.push(singer.author_name);
      });
      return arr.join("\u3001");
    },
    filterData(rawList) {
      return rawList.map((item) => {
        const types = [];
        const _types = {};
        if (item.filesize !== 0) {
          let size = sizeFormate(item.filesize);
          types.push({ type: "128k", size, hash: item.hash });
          _types["128k"] = {
            size,
            hash: item.hash
          };
        }
        if (item["320filesize"] !== 0) {
          let size = sizeFormate(item["320filesize"]);
          types.push({ type: "320k", size, hash: item["320hash"] });
          _types["320k"] = {
            size,
            hash: item["320hash"]
          };
        }
        if (item.sqfilesize !== 0) {
          let size = sizeFormate(item.sqfilesize);
          types.push({ type: "flac", size, hash: item.sqhash });
          _types.flac = {
            size,
            hash: item.sqhash
          };
        }
        if (item.filesize_high !== 0) {
          let size = sizeFormate(item.filesize_high);
          types.push({ type: "flac24bit", size, hash: item.hash_high });
          _types.flac24bit = {
            size,
            hash: item.hash_high
          };
        }
        return {
          singer: formatSingerName(item.authors, "author_name"),
          name: decodeName(item.songname),
          albumName: decodeName(item.remark),
          albumId: item.album_id,
          songmid: item.audio_id,
          source: "kg",
          interval: formatPlayTime(item.duration),
          img: null,
          lrc: null,
          hash: item.hash,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    filterBoardsData(rawList) {
      let list = [];
      for (const board of rawList) {
        if (board.isvol != 1) continue;
        list.push({
          id: "kg__" + board.rankid,
          name: board.rankname,
          bangid: String(board.rankid)
        });
      }
      return list;
    },
    async getBoards(retryNum = 0) {
      this.list = boardList4;
      return {
        list: boardList4,
        source: "kg"
      };
    },
    async getList(bangid, page, retryNum = 0) {
      if (++retryNum > 3) throw new Error("try max num");
      const { body } = await this.getData(this.getUrl(page, bangid, this.listDetailLimit));
      if (body.errcode != 0) return this.getList(bangid, page, retryNum);
      let total = body.data.total;
      let limit = 100;
      let listData = this.filterData(body.data.info);
      return {
        total,
        list: listData,
        limit,
        page,
        source: "kg"
      };
    },
    getDetailPageUrl(id) {
      if (typeof id == "string") id = id.replace("kg__", "");
      return `https://www.kugou.com/yy/rank/home/1-${id}.html`;
    }
  };

  // src/utils/musicSdk/kg/songList.js
  var import_infSign = __toESM(require_infSign_min());

  // src/utils/musicSdk/kg/util.js
  var import_pako2 = __toESM(require_pako());
  var import_buffer = __toESM(require_buffer());
  var enc_key = import_buffer.Buffer.from([64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105], "binary");
  var decodeLyric3 = (str) => new Promise((resolve, reject) => {
    if (!str.length) return;
    const buf_str = import_buffer.Buffer.from(str, "base64").slice(4);
    for (let i = 0, len = buf_str.length; i < len; i++) {
      buf_str[i] = buf_str[i] ^ enc_key[i % 16];
    }
    const result = import_pako2.default.inflate(buf_str, { to: "string" });
    resolve(result);
  });
  var signatureParams = (params, platform = "android", body = "") => {
    let keyparam = "OIlwieks28dk2k092lksi2UIkp";
    if (platform === "web") keyparam = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    let param_list = params.split("&");
    param_list.sort();
    let sign_params = `${keyparam}${param_list.join("")}${body}${keyparam}`;
    return toMD5(sign_params);
  };

  // src/utils/musicSdk/kg/songList.js
  var handleSignature = (id, page, limit) => new Promise((resolve, reject) => {
    (0, import_infSign.default)({ appid: 1058, type: 0, module: "playlist", page, pagesize: limit, specialid: id }, null, {
      useH5: true,
      isCDN: true,
      callback(i) {
        resolve(i.signature);
      }
    });
  });
  var songList_default5 = {
    _requestObj_tags: null,
    _requestObj_listInfo: null,
    _requestObj_list: null,
    _requestObj_listRecommend: null,
    listDetailLimit: 1e4,
    currentTagInfo: {
      id: void 0,
      info: void 0
    },
    sortList: [
      {
        name: "\u63A8\u8350",
        tid: "recommend",
        id: "5"
      },
      {
        name: "\u6700\u70ED",
        tid: "hot",
        id: "6"
      },
      {
        name: "\u6700\u65B0",
        tid: "new",
        id: "7"
      },
      {
        name: "\u70ED\u85CF",
        tid: "hot_collect",
        id: "3"
      },
      {
        name: "\u98D9\u5347",
        tid: "rise",
        id: "8"
      }
    ],
    cache: /* @__PURE__ */ new Map(),
    regExps: {
      listData: /global\.data = (\[.+\]);/,
      listInfo: /global = {[\s\S]+?name: "(.+)"[\s\S]+?pic: "(.+)"[\s\S]+?};/,
      // https://www.kugou.com/yy/special/single/1067062.html
      listDetailLink: /^.+\/(\d+)\.html(?:\?.*|&.*$|#.*$|$)/
    },
    // async getGlobalSpecialId(specialId) {
    //   return httpFetch(`http://mobilecdnbj.kugou.com/api/v5/special/info?specialid=${specialId}`, {
    //     headers: {
    //       'User-Agent': 'Mozilla/5.0 (Linux; Android 10; HLK-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.5112.102 Mobile Safari/537.36 EdgA/104.0.1293.70',
    //     },
    //   }).promise.then(({ body }) => {
    //     // console.log(body)
    //     if (!body.data.global_specialid) Promise.reject(new Error('Failed to get global collection id.'))
    //     return body.data.global_specialid
    //   })
    // },
    // async getListInfoBySpecialId(special_id, retry = 0) {
    //   if (++retry > 2) throw new Error('failed')
    //   return httpFetch(`https://m.kugou.com/plist/list/${special_id}/?json=true`, {
    //     headers: {
    //       'User-Agent': 'Mozilla/5.0 (Linux; Android 10; HLK-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.5112.102 Mobile Safari/537.36 EdgA/104.0.1293.70',
    //     },
    //     follow_max: 2,
    //   }).promise.then(({ body }) => {
    //     // console.log(body)
    //     if (!body.info.list) return this.getListInfoBySpecialId(special_id, retry)
    //     let listinfo = body.info.list
    //     return {
    //       listInfo: {
    //         name: listinfo.specialname,
    //         image: listinfo.imgurl.replace('{size}', '150'),
    //         intro: listinfo.intro,
    //         author: listinfo.nickname,
    //         playcount: listinfo.playcount,
    //         total: listinfo.songcount,
    //       },
    //       globalSpecialId: listinfo.global_specialid,
    //     }
    //   })
    // },
    async getSongListDetailByGlobalSpecialId(id, page, limit = 100, retry = 0) {
      if (++retry > 2) throw new Error("failed");
      const params = `specialid=0&need_sort=1&module=CloudMusic&clientver=11409&pagesize=${limit}&global_collection_id=${id}&userid=0&page=${page}&type=1&area_code=1&appid=1005`;
      return httpFetch(`http://pubsongscdn.tx.kugou.com/v2/get_other_list_file?${params}&signature=${signatureParams(params)}`, {
        cache: "default"
      }).promise.then(({ body }) => {
        if (body.data?.info == null) return this.getSongListDetailByGlobalSpecialId(id, page, limit, retry);
        return body.data.info;
      });
    },
    parseHtmlDesc(html) {
      const prefix = '<div class="pc_specail_text pc_singer_tab_content" id="specailIntroduceWrap">';
      let index = html.indexOf(prefix);
      if (index < 0) return null;
      const afterStr = html.substring(index + prefix.length);
      index = afterStr.indexOf("</div>");
      if (index < 0) return null;
      return decodeName(afterStr.substring(0, index));
    },
    async getListDetailBySpecialId(id, page, tryNum = 0) {
      if (tryNum > 2) throw new Error("try max num");
      const { body } = await httpFetch(this.getSongListDetailUrl(id)).promise;
      let listData = body.match(this.regExps.listData);
      let listInfo = body.match(this.regExps.listInfo);
      if (!listData) return this.getListDetailBySpecialId(id, page, ++tryNum);
      let list = await this.getMusicInfos(JSON.parse(listData[1]));
      let name;
      let pic;
      if (listInfo) {
        name = listInfo[1];
        pic = listInfo[2];
      }
      let desc = this.parseHtmlDesc(body);
      return {
        list,
        page: 1,
        limit: 1e4,
        total: list.length,
        source: "kg",
        info: {
          name,
          img: pic,
          desc
          // author: body.result.info.userinfo.username,
          // play_count: formatPlayCount(body.result.listen_num),
        }
      };
    },
    getInfoUrl(tagId) {
      return tagId ? `http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_smarty=1&cdn=cdn&t=5&c=${tagId}` : "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_smarty=1&";
    },
    getSongListUrl(sortId, tagId, page) {
      if (tagId == null) tagId = "";
      return `http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_ajax=1&cdn=cdn&t=${sortId}&c=${tagId}&p=${page}`;
    },
    getSongListDetailUrl(id) {
      return `http://www2.kugou.kugou.com/yueku/v9/special/single/${id}-5-9999.html`;
    },
    filterInfoHotTag(rawData) {
      const result = [];
      if (rawData.status !== 1) return result;
      for (const key of Object.keys(rawData.data)) {
        let tag = rawData.data[key];
        result.push({
          id: tag.special_id,
          name: tag.special_name,
          source: "kg"
        });
      }
      return result;
    },
    filterTagInfo(rawData) {
      const result = [];
      for (const name of Object.keys(rawData)) {
        result.push({
          name,
          list: rawData[name].data.map((tag) => ({
            parent_id: tag.parent_id,
            parent_name: tag.pname,
            id: tag.id,
            name: tag.name,
            source: "kg"
          }))
        });
      }
      return result;
    },
    getSongList(sortId, tagId, page, tryNum = 0) {
      if (this._requestObj_list) this._requestObj_list.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_list = httpFetch(
        this.getSongListUrl(sortId, tagId, page)
      );
      return this._requestObj_list.promise.then(({ body }) => {
        if (!body || body.status !== 1) return this.getSongList(sortId, tagId, page, ++tryNum);
        return this.filterList(body.special_db);
      });
    },
    getSongListRecommend(tryNum = 0) {
      if (this._requestObj_listRecommend) this._requestObj_listRecommend.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_listRecommend = httpFetch(
        "http://everydayrec.service.kugou.com/guess_special_recommend",
        {
          method: "post",
          headers: {
            "User-Agent": "KuGou2012-8275-web_browser_event_handler"
          },
          body: {
            appid: 1001,
            clienttime: 1566798337219,
            clientver: 8275,
            key: "f1f93580115bb106680d2375f8032d96",
            mid: "21511157a05844bd085308bc76ef3343",
            platform: "pc",
            userid: "262643156",
            return_min: 6,
            return_max: 15
          }
        }
      );
      return this._requestObj_listRecommend.promise.then(({ body }) => {
        if (body.status !== 1) return this.getSongListRecommend(++tryNum);
        return this.filterList(body.data.special_list);
      });
    },
    filterList(rawData) {
      return rawData.map((item) => ({
        play_count: item.total_play_count || formatPlayCount(item.play_count),
        id: "id_" + item.specialid,
        author: item.nickname,
        name: item.specialname,
        time: dateFormat(item.publish_time || item.publishtime, "Y-M-D"),
        img: item.img || item.imgurl,
        total: item.songcount,
        grade: item.grade,
        desc: item.intro,
        source: "kg"
      }));
    },
    async createHttp(url, options, retryNum = 0) {
      if (retryNum > 2) throw new Error("try max num");
      let result;
      options.cache = "default";
      try {
        result = await httpFetch(url, options).promise;
      } catch (err) {
        console.log(err);
        return this.createHttp(url, options, ++retryNum);
      }
      if (result.statusCode !== 200 || (result.body.error_code !== void 0 ? result.body.error_code : result.body.errcode !== void 0 ? result.body.errcode : result.body.err_code) !== 0) return this.createHttp(url, options, ++retryNum);
      if (result.body.data) return result.body.data;
      if (Array.isArray(result.body.info)) return result.body;
      return result.body.info;
    },
    createTask(hashs) {
      let data = {
        area_code: "1",
        show_privilege: 1,
        show_album_info: "1",
        is_publish: "",
        appid: 1005,
        clientver: 11451,
        mid: "1",
        dfid: "-",
        clienttime: Date.now(),
        key: "OIlwieks28dk2k092lksi2UIkp",
        fields: "album_info,author_name,audio_info,ori_audio_name,base,songname"
      };
      let list = hashs;
      let tasks = [];
      while (list.length) {
        tasks.push(Object.assign({ data: list.slice(0, 100) }, data));
        if (list.length < 100) break;
        list = list.slice(100);
      }
      let url = "http://gateway.kugou.com/v2/album_audio/audio";
      return tasks.map((task) => this.createHttp(url, {
        method: "POST",
        body: task,
        headers: {
          "KG-THash": "13a3164",
          "KG-RC": "1",
          "KG-Fake": "0",
          "KG-RF": "00869891",
          "User-Agent": "Android712-AndroidPhone-11451-376-0-FeeCacheUpdate-wifi",
          "x-router": "kmr.service.kugou.com"
        }
      }).then((data2) => data2.map((s) => s[0])));
    },
    async getMusicInfos(list) {
      return this.filterData2(
        await Promise.all(
          this.createTask(
            this.deDuplication(list).map((item) => ({ hash: item.hash }))
          )
        ).then(([...datas]) => datas.flat())
      );
    },
    async getUserListDetailByCode(id) {
      const songInfo = await this.createHttp("http://t.kugou.com/command/", {
        method: "POST",
        headers: {
          "KG-RC": 1,
          "KG-THash": "network_super_call.cpp:3676261689:379",
          "User-Agent": ""
        },
        body: { appid: 1001, clientver: 9020, mid: "21511157a05844bd085308bc76ef3343", clienttime: 640612895, key: "36164c4015e704673c588ee202b9ecb8", data: id }
      });
      let songList;
      let info = songInfo.info;
      switch (info.type) {
        case 2:
          if (!info.global_collection_id) return this.getListDetailBySpecialId(info.id);
          break;
        default:
          break;
      }
      if (info.global_collection_id) return this.getUserListDetail2(info.global_collection_id);
      if (info.userid != null) {
        songList = await this.createHttp("http://www2.kugou.kugou.com/apps/kucodeAndShare/app/", {
          method: "POST",
          headers: {
            "KG-RC": 1,
            "KG-THash": "network_super_call.cpp:3676261689:379",
            "User-Agent": ""
          },
          body: { appid: 1001, clientver: 9020, mid: "21511157a05844bd085308bc76ef3343", clienttime: 640612895, key: "36164c4015e704673c588ee202b9ecb8", data: { id: info.id, type: 3, userid: info.userid, collect_type: 0, page: 1, pagesize: info.count } }
        });
      }
      let list = await this.getMusicInfos(songList || songInfo.list);
      return {
        list,
        page: 1,
        limit: info.count,
        total: list.length,
        source: "kg",
        info: {
          name: info.name,
          img: info.img_size && info.img_size.replace("{size}", 240) || info.img,
          // desc: body.result.info.list_desc,
          author: info.username
          // play_count: formatPlayCount(info.count),
        }
      };
    },
    async getUserListDetail3(chain, page) {
      const songInfo = await this.createHttp(`http://m.kugou.com/schain/transfer?pagesize=${this.listDetailLimit}&chain=${chain}&su=1&page=${page}&n=0.7928855356604456`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1"
        }
      });
      if (!songInfo.list) {
        if (songInfo.global_collection_id) {
          return this.getListDetailByGcidChain(songInfo.global_collection_id, chain).catch(() => this.getUserListDetail2(songInfo.global_collection_id));
        } else return this.getUserListDetail4(songInfo, chain, page).catch(() => this.getUserListDetail5(chain));
      }
      let list = await this.getMusicInfos(songInfo.list);
      return {
        list,
        page: 1,
        limit: this.listDetailLimit,
        total: list.length,
        source: "kg",
        info: {
          name: songInfo.info.name,
          img: songInfo.info.img,
          // desc: body.result.info.list_desc,
          author: songInfo.info.username
          // play_count: formatPlayCount(info.count),
        }
      };
    },
    deDuplication(datas) {
      let ids = /* @__PURE__ */ new Set();
      return datas.filter(({ hash }) => {
        if (ids.has(hash)) return false;
        ids.add(hash);
        return true;
      });
    },
    async decodeGcid(gcid) {
      const params = "dfid=-&appid=1005&mid=0&clientver=20109&clienttime=640612895&uuid=-";
      const body = {
        ret_info: 1,
        data: [
          {
            id: gcid,
            id_type: 2
          }
        ]
      };
      const result = await this.createHttp(`https://t.kugou.com/v1/songlist/batch_decode?${params}&signature=${signatureParams(params, "android", JSON.stringify(body))}`, {
        method: "POST",
        headers: {
          "User-Agent": "Mozilla/5.0 (Linux; Android 10; HUAWEI HMA-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36",
          Referer: "https://m.kugou.com/"
        },
        body
      });
      return result.list[0].global_collection_id;
    },
    async getUserListDetailByLink({ info }, link) {
      let listInfo = info["0"];
      let total = listInfo.count;
      let tasks = [];
      let page = 0;
      while (total) {
        const limit = total > 90 ? 90 : total;
        total -= limit;
        page += 1;
        tasks.push(this.createHttp(link.replace(/pagesize=\d+/, "pagesize=" + limit).replace(/page=\d+/, "page=" + page), {
          headers: {
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1",
            Referer: link
          }
        }).then((data) => data.list.info));
      }
      let result = await Promise.all(tasks).then(([...datas]) => datas.flat());
      result = await this.getMusicInfos(result);
      return {
        list: result,
        page,
        limit: this.listDetailLimit,
        total: result.length,
        source: "kg",
        info: {
          name: listInfo.name,
          img: listInfo.pic && listInfo.pic.replace("{size}", 240),
          // desc: body.result.info.list_desc,
          author: listInfo.list_create_username
          // play_count: formatPlayCount(listInfo.count),
        }
      };
    },
    createGetListDetail2Task(id, total) {
      let tasks = [];
      let page = 0;
      const limit = total > 300 ? 300 : total;
      while (total > 0) {
        total -= limit;
        page += 1;
        tasks.push(this.getSongListDetailByGlobalSpecialId(id, page, limit));
      }
      return Promise.all(tasks).then(([...datas]) => datas.flat());
    },
    async getListDetailByGcidChain(id, chain) {
      const params = "srcappid=2919&clientver=20000&clienttime=1789566751657&mid=1789566751657&uuid=1789566751657&dfid=-&listid=15&type=0&pagesize=50&global_collection_id=" + id + "&page=1&share_type=collect&appid=1058&chain=" + chain;
      const data = await this.createHttp(`https://m3ws.kugou.com/v2/zlist/list?${params}&signature=${signatureParams(params, "web")}`, {
        headers: {
          mid: "1586163263991",
          Referer: "https://m3ws.kugou.com/share/index.php",
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Mobile/15A372 Safari/604.1",
          dfid: "-",
          clienttime: "1586163263991"
        }
      });
      const info = data.info[0];
      if (!info) throw new Error("get list error");
      let tasks = [Promise.resolve(data.list.info)];
      let page = 1;
      let totalPage = Math.ceil(info.count / 50);
      while (page < totalPage) {
        const limit = 50;
        page += 1;
        const params2 = "srcappid=2919&clientver=20000&clienttime=1789566751657&mid=1789566751657&uuid=1789566751657&dfid=-&listid=15&type=0&pagesize=" + limit + "&global_collection_id=" + id + "&page=" + page + "&share_type=collect&appid=1058&chain=" + chain;
        tasks.push(this.createHttp(`https://m3ws.kugou.com/v2/zlist/list?${params2}&signature=${signatureParams(params2, "web")}`, {
          headers: {
            mid: "1586163263991",
            Referer: "https://m3ws.kugou.com/share/index.php",
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Mobile/15A372 Safari/604.1",
            dfid: "-",
            clienttime: "1586163263991"
          }
        }).then((data2) => data2.list.info));
      }
      const hashs = await Promise.all(tasks).then(([...datas]) => datas.flat());
      let list = await this.getMusicInfos(hashs);
      return {
        list,
        page: 1,
        limit: this.listDetailLimit,
        total: info.count,
        source: "kg",
        info: {
          name: info.name,
          img: info.pic && info.pic.replace("{size}", 240),
          desc: info.intro,
          author: info.list_create_username
          // play_count: formatPlayCount(info.playcount),
        }
      };
    },
    async getUserListDetail2(global_collection_id) {
      let id = global_collection_id;
      if (id.length > 1e3) throw new Error("get list error");
      const params = "appid=1058&specialid=0&global_specialid=" + id + "&format=jsonp&srcappid=2919&clientver=20000&clienttime=1586163242519&mid=1586163242519&uuid=1586163242519&dfid=-";
      let info = await this.createHttp(`https://mobiles.kugou.com/api/v5/special/info_v2?${params}&signature=${signatureParams(params, "web")}`, {
        headers: {
          mid: "1586163242519",
          Referer: "https://m3ws.kugou.com/share/index.php",
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Mobile/15A372 Safari/604.1",
          dfid: "-",
          clienttime: "1586163242519"
        }
      });
      const songInfo = await this.createGetListDetail2Task(id, info.songcount);
      let list = await this.getMusicInfos(songInfo);
      return {
        list,
        page: 1,
        limit: this.listDetailLimit,
        total: list.length,
        source: "kg",
        info: {
          name: info.specialname,
          img: info.imgurl && info.imgurl.replace("{size}", 240),
          desc: info.intro,
          author: info.nickname,
          play_count: formatPlayCount(info.playcount)
        }
      };
    },
    async getListInfoByChain(chain) {
      if (this.cache.has(chain)) return this.cache.get(chain);
      const { body } = await httpFetch(`https://m.kugou.com/share/?chain=${chain}&id=${chain}`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"
        }
      }).promise;
      let result = body.match(/var\sphpParam\s=\s({.+?});/);
      if (result) result = JSON.parse(result[1]);
      this.cache.set(chain, result);
      return result;
    },
    async getUserListDetailByPcChain(chain) {
      let key = `${chain}_pc_list`;
      if (this.cache.has(key)) return this.cache.get(key);
      const { body } = await httpFetch(`http://www.kugou.com/share/${chain}.html`, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36"
        }
      }).promise;
      let result = body.match(/var\sdataFromSmarty\s=\s(\[.+?\])/);
      if (result) result = JSON.parse(result[1]);
      this.cache.set(chain, result);
      result = await this.getMusicInfos(result);
      return result;
    },
    async getUserListDetail4(songInfo, chain, page) {
      const limit = 100;
      const [listInfo, list] = await Promise.all([
        this.getListInfoByChain(chain),
        this.getUserListDetailById(songInfo.id, page, limit)
      ]);
      return {
        list: list || [],
        page,
        limit,
        total: list.length ?? 0,
        source: "kg",
        info: {
          name: listInfo.specialname,
          img: listInfo.imgurl && listInfo.imgurl.replace("{size}", 240),
          // desc: body.result.info.list_desc,
          author: listInfo.nickname
          // play_count: formatPlayCount(info.count),
        }
      };
    },
    async getUserListDetail5(chain) {
      const [listInfo, list] = await Promise.all([
        this.getListInfoByChain(chain),
        this.getUserListDetailByPcChain(chain)
      ]);
      return {
        list: list || [],
        page: 1,
        limit: this.listDetailLimit,
        total: list.length ?? 0,
        source: "kg",
        info: {
          name: listInfo.specialname,
          img: listInfo.imgurl && listInfo.imgurl.replace("{size}", 240),
          // desc: body.result.info.list_desc,
          author: listInfo.nickname
          // play_count: formatPlayCount(info.count),
        }
      };
    },
    async getUserListDetailById(id, page, limit) {
      const signature = await handleSignature(id, page, limit);
      let info = await this.createHttp(`https://pubsongscdn.kugou.com/v2/get_other_list_file?srcappid=2919&clientver=20000&appid=1058&type=0&module=playlist&page=${page}&pagesize=${limit}&specialid=${id}&signature=${signature}`, {
        headers: {
          Referer: "https://m3ws.kugou.com/share/index.php",
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Mobile/15A372 Safari/604.1",
          dfid: "-"
        }
      });
      let result = await this.getMusicInfos(info.info);
      return result;
    },
    async getUserListDetail(link, page, retryNum = 0) {
      if (retryNum > 3) return Promise.reject(new Error("link try max num"));
      if (link.includes("#")) link = link.replace(/#.*$/, "");
      if (link.includes("global_collection_id") && link.includes("chain")) {
        return this.getListDetailByGcidChain(link.replace(/^.*?global_collection_id=(\w+)(?:&.*$|#.*$|$)/, "$1"), link.replace(/^.*?chain=(\w+)(?:&.*$|#.*$|$)/, "$1"));
      }
      if (link.includes("chain=")) return this.getUserListDetail3(link.replace(/^.*?chain=(\w+)(?:&.*$|#.*$|$)/, "$1"), page);
      if (link.includes("gcid_")) {
        let gcid = link.match(/gcid_\w+/)?.[0];
        if (gcid) {
          const global_collection_id = await this.decodeGcid(gcid);
          if (global_collection_id) return this.getUserListDetail2(global_collection_id);
        }
      }
      if (link.includes(".html")) {
        if (link.includes("zlist.html")) {
          link = link.replace(/^(.*)zlist\.html/, "https://m3ws.kugou.com/zlist/list");
          if (link.includes("pagesize")) {
            link = link.replace("pagesize=30", "pagesize=" + this.listDetailLimit).replace("page=1", "page=" + page);
          } else {
            link += `&pagesize=${this.listDetailLimit}&page=${page}`;
          }
        } else if (!link.includes("song.html")) return this.getUserListDetail3(link.replace(/.+\/(\w+).html(?:\?.*|&.*$|#.*$|$)/, "$1"), page);
      }
      if (link.includes("global_collection_id")) return this.getUserListDetail2(link.replace(/^.*?global_collection_id=(\w+)(?:&.*$|#.*$|$)/, "$1"));
      const requestObj_listDetailLink = httpFetch(link, {
        headers: {
          "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1",
          Referer: link
        }
      });
      const { url: location, statusCode, body } = await requestObj_listDetailLink.promise;
      if (statusCode > 400) return this.getUserListDetail(link, page, ++retryNum);
      if (location.split("?")[0] != link.split("?")[0]) {
        if (location.includes("global_collection_id") && location.includes("chain")) {
          return this.getListDetailByGcidChain(location.replace(/^.*?global_collection_id=(\w+)(?:&.*$|#.*$|$)/, "$1"), location.replace(/^.*?chain=(\w+)(?:&.*$|#.*$|$)/, "$1"));
        }
        if (location.includes("chain=")) return this.getUserListDetail3(location.replace(/^.*?chain=(\w+)(?:&.*$|#.*$|$)/, "$1"), page);
        if (location.includes("gcid_")) {
          let gcid = location.match(/gcid_\w+/)?.[0];
          if (gcid) {
            const global_collection_id = await this.decodeGcid(gcid);
            if (global_collection_id) return this.getUserListDetail2(global_collection_id);
          }
        }
        if (location.includes(".html")) {
          if (location.includes("zlist.html")) {
            let link2 = location.replace(/^(.*)zlist\.html/, "https://m3ws.kugou.com/zlist/list");
            if (link2.includes("pagesize")) {
              link2 = link2.replace("pagesize=30", "pagesize=" + this.listDetailLimit).replace("page=1", "page=" + page);
            } else {
              link2 += `&pagesize=${this.listDetailLimit}&page=${page}`;
            }
            return this.getUserListDetail(link2, page, ++retryNum);
          } else return this.getUserListDetail3(location.replace(/.+\/(\w+).html(?:\?.*|&.*$|#.*$|$)/, "$1"), page);
        }
        if (location.includes("global_collection_id")) return this.getUserListDetail2(location.replace(/^.*?global_collection_id=(\w+)(?:&.*$|#.*$|$)/, "$1"));
      }
      if (typeof body == "string") return this.getUserListDetail2(body.replace(/^[\s\S]+?"global_collection_id":"(\w+)"[\s\S]+?$/, "$1"));
      if (body.errcode !== 0) return this.getUserListDetail(link, page, ++retryNum);
      return this.getUserListDetailByLink(body, link);
    },
    async getListDetail(id, page) {
      id = id.toString();
      if (id.includes("special/single/")) {
        id = id.replace(this.regExps.listDetailLink, "$1");
      } else if (/https?:/.test(id)) {
        return this.getUserListDetail(id.replace(/^.*?http/, "http"), page);
      } else if (/^\d+$/.test(id)) {
        return this.getUserListDetailByCode(id);
      } else if (id.startsWith("id_")) {
        id = id.replace("id_", "");
      }
      return this.getListDetailBySpecialId(id, page);
    },
    filterData(rawList) {
      return rawList.map((item) => {
        const types = [];
        const _types = {};
        if (item.filesize !== 0) {
          let size = sizeFormate(item.filesize);
          types.push({ type: "128k", size, hash: item.hash });
          _types["128k"] = {
            size,
            hash: item.hash
          };
        }
        if (item.filesize_320 !== 0) {
          let size = sizeFormate(item.filesize_320);
          types.push({ type: "320k", size, hash: item.hash_320 });
          _types["320k"] = {
            size,
            hash: item.hash_320
          };
        }
        if (item.filesize_ape !== 0) {
          let size = sizeFormate(item.filesize_ape);
          types.push({ type: "ape", size, hash: item.hash_ape });
          _types.ape = {
            size,
            hash: item.hash_ape
          };
        }
        if (item.filesize_flac !== 0) {
          let size = sizeFormate(item.filesize_flac);
          types.push({ type: "flac", size, hash: item.hash_flac });
          _types.flac = {
            size,
            hash: item.hash_flac
          };
        }
        return {
          singer: decodeName(item.singername),
          name: decodeName(item.songname),
          albumName: decodeName(item.album_name),
          albumId: item.album_id,
          songmid: item.audio_id,
          source: "kg",
          interval: formatPlayTime(item.duration / 1e3),
          img: null,
          lrc: null,
          hash: item.hash,
          types,
          _types,
          typeUrl: {}
        };
      });
    },
    // getSinger(singers) {
    //   let arr = []
    //   singers?.forEach(singer => {
    //     arr.push(singer.name)
    //   })
    //   return arr.join('、')
    // },
    // v9 API
    // filterDatav9(rawList) {
    //   console.log(rawList)
    //   return rawList.map(item => {
    //     const types = []
    //     const _types = {}
    //     item.relate_goods.forEach(qualityObj => {
    //       if (qualityObj.level === 2) {
    //         let size = sizeFormate(qualityObj.size)
    //         types.push({ type: '128k', size, hash: qualityObj.hash })
    //         _types['128k'] = {
    //           size,
    //           hash: qualityObj.hash,
    //         }
    //       } else if (qualityObj.level === 4) {
    //         let size = sizeFormate(qualityObj.size)
    //         types.push({ type: '320k', size, hash: qualityObj.hash })
    //         _types['320k'] = {
    //           size,
    //           hash: qualityObj.hash,
    //         }
    //       } else if (qualityObj.level === 5) {
    //         let size = sizeFormate(qualityObj.size)
    //         types.push({ type: 'flac', size, hash: qualityObj.hash })
    //         _types.flac = {
    //           size,
    //           hash: qualityObj.hash,
    //         }
    //       } else if (qualityObj.level === 6) {
    //         let size = sizeFormate(qualityObj.size)
    //         types.push({ type: 'flac24bit', size, hash: qualityObj.hash })
    //         _types.flac24bit = {
    //           size,
    //           hash: qualityObj.hash,
    //         }
    //       }
    //     })
    //     const nameInfo = item.name.split(' - ')
    //     return {
    //       singer: this.getSinger(item.singerinfo),
    //       name: decodeName((nameInfo[1] ?? nameInfo[0]).trim()),
    //       albumName: decodeName(item.albuminfo.name),
    //       albumId: item.albuminfo.id,
    //       songmid: item.audio_id,
    //       source: 'kg',
    //       interval: formatPlayTime(item.timelen / 1000),
    //       img: null,
    //       lrc: null,
    //       hash: item.hash,
    //       types,
    //       _types,
    //       typeUrl: {},
    //     }
    //   })
    // },
    // hash list filter
    filterData2(rawList) {
      let ids = /* @__PURE__ */ new Set();
      let list = [];
      rawList.forEach((item) => {
        if (!item) return;
        if (ids.has(item.audio_info.audio_id)) return;
        ids.add(item.audio_info.audio_id);
        const types = [];
        const _types = {};
        if (item.audio_info.filesize !== "0") {
          let size = sizeFormate(parseInt(item.audio_info.filesize));
          types.push({ type: "128k", size, hash: item.audio_info.hash });
          _types["128k"] = {
            size,
            hash: item.audio_info.hash
          };
        }
        if (item.audio_info.filesize_320 !== "0") {
          let size = sizeFormate(parseInt(item.audio_info.filesize_320));
          types.push({ type: "320k", size, hash: item.audio_info.hash_320 });
          _types["320k"] = {
            size,
            hash: item.audio_info.hash_320
          };
        }
        if (item.audio_info.filesize_flac !== "0") {
          let size = sizeFormate(parseInt(item.audio_info.filesize_flac));
          types.push({ type: "flac", size, hash: item.audio_info.hash_flac });
          _types.flac = {
            size,
            hash: item.audio_info.hash_flac
          };
        }
        if (item.audio_info.filesize_high !== "0") {
          let size = sizeFormate(parseInt(item.audio_info.filesize_high));
          types.push({ type: "flac24bit", size, hash: item.audio_info.hash_high });
          _types.flac24bit = {
            size,
            hash: item.audio_info.hash_high
          };
        }
        list.push({
          singer: decodeName(item.author_name),
          name: decodeName(item.songname),
          albumName: decodeName(item.album_info.album_name),
          albumId: item.album_info.album_id,
          songmid: item.audio_info.audio_id,
          source: "kg",
          interval: formatPlayTime(parseInt(item.audio_info.timelength) / 1e3),
          img: null,
          lrc: null,
          hash: item.audio_info.hash,
          otherSource: null,
          types,
          _types,
          typeUrl: {}
        });
      });
      return list;
    },
    // 获取列表信息
    getListInfo(tagId, tryNum = 0) {
      if (this._requestObj_listInfo) this._requestObj_listInfo.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_listInfo = httpFetch(this.getInfoUrl(tagId));
      return this._requestObj_listInfo.promise.then(({ body }) => {
        if (body.status !== 1) return this.getListInfo(tagId, ++tryNum);
        return {
          limit: body.data.params.pagesize,
          page: body.data.params.p,
          total: body.data.params.total,
          source: "kg"
        };
      });
    },
    // 获取列表数据
    getList(sortId, tagId, page) {
      let tasks = [this.getSongList(sortId, tagId, page)];
      tasks.push(
        this.currentTagInfo.id === tagId ? Promise.resolve(this.currentTagInfo.info) : this.getListInfo(tagId).then((info) => {
          this.currentTagInfo.id = tagId;
          this.currentTagInfo.info = Object.assign({}, info);
          return info;
        })
      );
      if (!tagId && page === 1 && sortId === this.sortList[0].id) tasks.push(this.getSongListRecommend());
      return Promise.all(tasks).then(([list, info, recommendList]) => {
        if (recommendList) list.unshift(...recommendList);
        return {
          list,
          ...info
        };
      });
    },
    // 获取标签
    getTags(tryNum = 0) {
      if (this._requestObj_tags) this._requestObj_tags.cancelHttp();
      if (tryNum > 2) return Promise.reject(new Error("try max num"));
      this._requestObj_tags = httpFetch(this.getInfoUrl());
      return this._requestObj_tags.promise.then(({ body }) => {
        if (body.status !== 1) return this.getTags(++tryNum);
        return {
          hotTag: this.filterInfoHotTag(body.data.hotTag),
          tags: this.filterTagInfo(body.data.tagids),
          source: "kg"
        };
      });
    },
    getDetailPageUrl(id) {
      if (typeof id == "string") {
        if (/^https?:\/\//.test(id)) return id;
        id = id.replace("id_", "");
      }
      return `https://www.kugou.com/yy/special/single/${id}.html`;
    },
    search(text, page, limit = 20) {
      return httpFetch(`http://msearchretry.kugou.com/api/v3/search/special?keyword=${encodeURIComponent(text)}&page=${page}&pagesize=${limit}&showtype=10&filter=0&version=7910&sver=2`).promise.then(({ body }) => {
        if (body.errcode != 0) throw new Error("filed");
        return {
          list: body.data.info.map((item) => {
            return {
              play_count: formatPlayCount(item.playcount),
              id: "id_" + item.specialid,
              author: item.nickname,
              name: item.specialname,
              time: dateFormat(item.publishtime, "Y-M-D"),
              img: item.imgurl,
              grade: item.grade,
              desc: item.intro,
              total: item.songcount,
              source: "kg"
            };
          }),
          limit,
          total: body.data.total,
          source: "kg"
        };
      });
    }
  };

  // src/utils/musicSdk/kg/musicSearch.js
  var musicSearch_default5 = {
    limit: 30,
    total: 0,
    page: 0,
    allPage: 1,
    musicSearch(str, page, limit) {
      const searchRequest = httpFetch(`https://songsearch.kugou.com/song_search_v2?keyword=${encodeURIComponent(str)}&page=${page}&pagesize=${limit}&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1`);
      return searchRequest.promise.then(({ body }) => body);
    },
    filterData(rawData) {
      const types = [];
      const _types = {};
      if (rawData.FileSize !== 0) {
        let size = sizeFormate(rawData.FileSize);
        types.push({ type: "128k", size, hash: rawData.FileHash });
        _types["128k"] = {
          size,
          hash: rawData.FileHash
        };
      }
      if (rawData.HQFileSize !== 0) {
        let size = sizeFormate(rawData.HQFileSize);
        types.push({ type: "320k", size, hash: rawData.HQFileHash });
        _types["320k"] = {
          size,
          hash: rawData.HQFileHash
        };
      }
      if (rawData.SQFileSize !== 0) {
        let size = sizeFormate(rawData.SQFileSize);
        types.push({ type: "flac", size, hash: rawData.SQFileHash });
        _types.flac = {
          size,
          hash: rawData.SQFileHash
        };
      }
      if (rawData.ResFileSize !== 0) {
        let size = sizeFormate(rawData.ResFileSize);
        types.push({ type: "flac24bit", size, hash: rawData.ResFileHash });
        _types.flac24bit = {
          size,
          hash: rawData.ResFileHash
        };
      }
      return {
        singer: decodeName(formatSingerName(rawData.Singers, "name")),
        name: decodeName(`${rawData.OriSongName}${rawData.Suffix ? ` ${rawData.Suffix}` : ""}`),
        albumName: decodeName(rawData.AlbumName),
        albumId: rawData.AlbumID,
        songmid: rawData.Audioid,
        source: "kg",
        interval: formatPlayTime(rawData.Duration),
        _interval: rawData.Duration,
        img: null,
        lrc: null,
        otherSource: null,
        hash: rawData.FileHash,
        types,
        _types,
        typeUrl: {}
      };
    },
    handleResult(rawData) {
      let ids = /* @__PURE__ */ new Set();
      const list = [];
      rawData.forEach((item) => {
        const key = item.Audioid + item.FileHash;
        if (ids.has(key)) return;
        ids.add(key);
        list.push(this.filterData(item));
        for (const childItem of item.Grp) {
          const key2 = item.Audioid + item.FileHash;
          if (ids.has(key2)) continue;
          ids.add(key2);
          list.push(this.filterData(childItem));
        }
      });
      return list;
    },
    search(str, page = 1, limit, retryNum = 0) {
      if (++retryNum > 3) return Promise.reject(new Error("try max num"));
      if (limit == null) limit = this.limit;
      return this.musicSearch(str, page, limit).then((result) => {
        if (!result || result.error_code !== 0) return this.search(str, page, limit, retryNum);
        let list = this.handleResult(result.data.lists);
        if (list == null) return this.search(str, page, limit, retryNum);
        this.total = result.data.total;
        this.page = page;
        this.allPage = Math.ceil(this.total / limit);
        return Promise.resolve({
          list,
          allPage: this.allPage,
          limit,
          total: this.total,
          source: "kg"
        });
      });
    }
  };

  // src/utils/musicSdk/kg/pic.js
  var pic_default3 = {
    getPic(songInfo) {
      const requestObj = httpFetch(
        "http://media.store.kugou.com/v1/get_res_privilege",
        {
          method: "POST",
          headers: {
            "KG-RC": 1,
            "KG-THash": "expand_search_manager.cpp:852736169:451",
            "User-Agent": "KuGou2012-9020-ExpandSearchManager"
          },
          body: {
            appid: 1001,
            area_code: "1",
            behavior: "play",
            clientver: "9020",
            need_hash_offset: 1,
            relate: 1,
            resource: [
              {
                album_audio_id: songInfo.songmid.length == 32 ? songInfo.audioId.split("_")[0] : songInfo.songmid,
                album_id: songInfo.albumId,
                hash: songInfo.hash,
                id: 0,
                name: `${songInfo.singer} - ${songInfo.name}.mp3`,
                type: "audio"
              }
            ],
            token: "",
            userid: 2626431536,
            vip: 1
          }
        }
      );
      return requestObj.promise.then(({ body }) => {
        if (body.error_code !== 0) return Promise.reject(new Error("\u56FE\u7247\u83B7\u53D6\u5931\u8D25"));
        let info = body.data[0].info;
        const img = info.imgsize ? info.image.replace("{size}", info.imgsize[0]) : info.image;
        if (!img) return Promise.reject(new Error("Pic get failed"));
        return img;
      });
    }
  };

  // src/utils/musicSdk/kg/lyric.js
  var headExp = /^.*\[id:\$\w+\]\n/;
  var parseLyric = (str) => {
    str = str.replace(/\r/g, "");
    if (headExp.test(str)) str = str.replace(headExp, "");
    let trans = str.match(/\[language:([\w=\\/+]+)\]/);
    let lyric;
    let rlyric;
    let tlyric;
    if (trans) {
      str = str.replace(/\[language:[\w=\\/+]+\]\n/, "");
      let json = JSON.parse(Buffer.from(trans[1], "base64").toString());
      for (const item of json.content) {
        switch (item.type) {
          case 0:
            rlyric = item.lyricContent;
            break;
          case 1:
            tlyric = item.lyricContent;
            break;
        }
      }
    }
    let i = 0;
    let lxlyric = str.replace(/\[((\d+),\d+)\].*/g, (str2) => {
      let result = str2.match(/\[((\d+),\d+)\].*/);
      let time = parseInt(result[2]);
      let ms = (time % 1e3).toString().padStart(3, "0");
      time /= 1e3;
      let m = parseInt(time / 60).toString().padStart(2, "0");
      time %= 60;
      let s = parseInt(time).toString().padStart(2, "0");
      time = `${m}:${s}.${ms}`;
      if (rlyric) rlyric[i] = `[${time}]${rlyric[i]?.join("") ?? ""}`;
      if (tlyric) tlyric[i] = `[${time}]${tlyric[i]?.join("") ?? ""}`;
      i++;
      return str2.replace(result[1], time);
    });
    rlyric = rlyric ? rlyric.join("\n") : "";
    tlyric = tlyric ? tlyric.join("\n") : "";
    lxlyric = lxlyric.replace(/<(\d+,\d+),\d+>/g, "<$1>");
    lxlyric = decodeName(lxlyric);
    lyric = lxlyric.replace(/<\d+,\d+>/g, "");
    rlyric = decodeName(rlyric);
    tlyric = decodeName(tlyric);
    return {
      lyric,
      tlyric,
      rlyric,
      lxlyric
    };
  };
  var lyric_default5 = {
    getIntv(interval) {
      if (!interval) return 0;
      let intvArr = interval.split(":");
      let intv = 0;
      let unit = 1;
      while (intvArr.length) {
        intv += intvArr.pop() * unit;
        unit *= 60;
      }
      return parseInt(intv);
    },
    // getLyric(songInfo, tryNum = 0) {
    //   let requestObj = httpFetch(`http://m.kugou.com/app/i/krc.php?cmd=100&keyword=${encodeURIComponent(songInfo.name)}&hash=${songInfo.hash}&timelength=${songInfo._interval || this.getIntv(songInfo.interval)}&d=0.38664927426725626`, {
    //     headers: {
    //       'KG-RC': 1,
    //       'KG-THash': 'expand_search_manager.cpp:852736169:451',
    //       'User-Agent': 'KuGou2012-9020-ExpandSearchManager',
    //     },
    //   })
    //   requestObj.promise = requestObj.promise.then(({ body, statusCode }) => {
    //     if (statusCode !== 200) {
    //       if (tryNum > 5) return Promise.reject(new Error('歌词获取失败'))
    //       let tryRequestObj = this.getLyric(songInfo, ++tryNum)
    //       requestObj.cancelHttp = tryRequestObj.cancelHttp.bind(tryRequestObj)
    //       return tryRequestObj.promise
    //     }
    //     return {
    //       lyric: body,
    //       tlyric: '',
    //     }
    //   })
    //   return requestObj
    // },
    searchLyric(name, hash, time, tryNum = 0) {
      let requestObj = httpFetch(`http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${encodeURIComponent(name)}&hash=${hash}&timelength=${time}&lrctxt=1`, {
        headers: {
          "KG-RC": 1,
          "KG-THash": "expand_search_manager.cpp:852736169:451",
          "User-Agent": "KuGou2012-9020-ExpandSearchManager"
        }
      });
      requestObj.promise = requestObj.promise.then(({ body, statusCode }) => {
        if (statusCode !== 200) {
          if (tryNum > 5) return Promise.reject(new Error("\u6B4C\u8BCD\u83B7\u53D6\u5931\u8D25"));
          let tryRequestObj = this.searchLyric(name, hash, time, ++tryNum);
          requestObj.cancelHttp = tryRequestObj.cancelHttp.bind(tryRequestObj);
          return tryRequestObj.promise;
        }
        if (body.candidates.length) {
          let info = body.candidates[0];
          return { id: info.id, accessKey: info.accesskey, fmt: info.krctype == 1 && info.contenttype != 1 ? "krc" : "lrc" };
        }
        return null;
      });
      return requestObj;
    },
    getLyricDownload(id, accessKey, fmt, tryNum = 0) {
      let requestObj = httpFetch(`http://lyrics.kugou.com/download?ver=1&client=pc&id=${id}&accesskey=${accessKey}&fmt=${fmt}&charset=utf8`, {
        headers: {
          "KG-RC": 1,
          "KG-THash": "expand_search_manager.cpp:852736169:451",
          "User-Agent": "KuGou2012-9020-ExpandSearchManager"
        }
      });
      requestObj.promise = requestObj.promise.then(({ body, statusCode }) => {
        if (statusCode !== 200) {
          if (tryNum > 5) return Promise.reject(new Error("\u6B4C\u8BCD\u83B7\u53D6\u5931\u8D25"));
          let tryRequestObj = this.getLyric(id, accessKey, fmt, ++tryNum);
          requestObj.cancelHttp = tryRequestObj.cancelHttp.bind(tryRequestObj);
          return tryRequestObj.promise;
        }
        switch (body.fmt) {
          case "krc":
            return decodeLyric3(body.content).then((result) => parseLyric(result));
          case "lrc":
            return {
              lyric: Buffer.from(body.content, "base64").toString("utf-8"),
              tlyric: "",
              rlyric: "",
              lxlyric: ""
            };
          default:
            return Promise.reject(new Error(`\u672A\u77E5\u6B4C\u8BCD\u683C\u5F0F: ${body.fmt}`));
        }
      });
      return requestObj;
    },
    getLyric(songInfo, tryNum = 0) {
      let requestObj = this.searchLyric(songInfo.name, songInfo.hash, songInfo._interval || this.getIntv(songInfo.interval));
      requestObj.promise = requestObj.promise.then((result) => {
        if (!result) return Promise.reject(new Error("Get lyric failed"));
        let requestObj2 = this.getLyricDownload(result.id, result.accessKey, result.fmt);
        requestObj.cancelHttp = requestObj2.cancelHttp.bind(requestObj2);
        return requestObj2.promise;
      });
      return requestObj;
    }
  };

  // src/utils/musicSdk/kg/hotSearch.js
  var hotSearch_default5 = {
    _requestObj: null,
    async getList(retryNum = 0) {
      if (this._requestObj) this._requestObj.cancelHttp();
      if (retryNum > 2) return Promise.reject(new Error("try max num"));
      const _requestObj = httpFetch("http://gateway.kugou.com/api/v3/search/hot_tab?signature=ee44edb9d7155821412d220bcaf509dd&appid=1005&clientver=10026&plat=0", {
        method: "get",
        cache: null,
        headers: {
          dfid: "1ssiv93oVqMp27cirf2CvoF1",
          mid: "156798703528610303473757548878786007104",
          clienttime: 1584257267,
          "x-router": "msearch.kugou.com",
          "user-agent": "Android9-AndroidPhone-10020-130-0-searchrecommendprotocol-wifi",
          "kg-rc": 1
        }
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.errcode !== 0) throw new Error("\u83B7\u53D6\u70ED\u641C\u8BCD\u5931\u8D25");
      return { source: "kg", list: this.filterList(body.data.list) };
    },
    filterList(rawList) {
      const list = [];
      rawList.forEach((item) => {
        item.keywords.map((k) => list.push(decodeName(k.keyword)));
      });
      return list;
    }
  };

  // src/utils/musicSdk/kg/comment.js
  var comment_default5 = {
    _requestObj: null,
    _requestObj2: null,
    async getComment({ hash }, page = 1, limit = 20) {
      if (this._requestObj) this._requestObj.cancelHttp();
      let timestamp = Date.now();
      const params = `dfid=0&mid=16249512204336365674023395779019&clienttime=${timestamp}&uuid=0&extdata=${hash}&appid=1005&code=fc4be23b4e972707f36b8a828a93ba8a&schash=${hash}&clientver=11409&p=${page}&clienttoken=&pagesize=${limit}&ver=10&kugouid=0`;
      const _requestObj = httpFetch(`http://m.comment.service.kugou.com/r/v1/rank/newest?${params}&signature=${signatureParams(params)}`, {
        cache: "default",
        headers: {
          "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36 Edg/107.0.1418.24"
        }
      });
      const { body, statusCode } = await _requestObj.promise;
      if (statusCode != 200 || body.err_code !== 0) throw new Error("\u83B7\u53D6\u8BC4\u8BBA\u5931\u8D25");
      const total = body.count ?? 0;
      return { source: "kg", comments: this.filterComment(body.list || []), total, page, limit, maxPage: Math.ceil(total / limit) || 1 };
    },
    async getHotComment({ hash }, page = 1, limit = 20) {
      if (this._requestObj2) this._requestObj2.cancelHttp();
      let timestamp = Date.now();
      const params = `dfid=0&mid=16249512204336365674023395779019&clienttime=${timestamp}&uuid=0&extdata=${hash}&appid=1005&code=fc4be23b4e972707f36b8a828a93ba8a&schash=${hash}&clientver=11409&p=${page}&clienttoken=&pagesize=${limit}&ver=10&kugouid=0`;
      const _requestObj2 = httpFetch(`http://m.comment.service.kugou.com/r/v1/rank/topliked?${params}&signature=${signatureParams(params)}`, {
        cache: "default",
        headers: {
          "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36 Edg/107.0.1418.24"
        }
      });
      const { body, statusCode } = await _requestObj2.promise;
      if (statusCode != 200 || body.err_code !== 0) throw new Error("\u83B7\u53D6\u70ED\u95E8\u8BC4\u8BBA\u5931\u8D25");
      const total = body.count ?? 0;
      return { source: "kg", comments: this.filterComment(body.list || []), total, page, limit, maxPage: Math.ceil(total / limit) || 1 };
    },
    async getReplyComment({ songmid, audioId }, replyId, page = 1, limit = 100) {
      if (this._requestObj2) this._requestObj2.cancelHttp();
      songmid = songmid.length == 32 ? audioId.split("_")[0] : songmid;
      const _requestObj2 = httpFetch(`http://comment.service.kugou.com/index.php?r=commentsv2/getReplyWithLike&code=fc4be23b4e972707f36b8a828a93ba8a&p=${page}&pagesize=${limit}&ver=1.01&clientver=8373&kugouid=687373022&need_show_image=1&appid=1001&childrenid=${songmid}&tid=${replyId}`, {
        headers: {
          "User-Agent": "Android712-AndroidPhone-8983-18-0-COMMENT-wifi"
        }
      });
      const { body, statusCode } = await _requestObj2.promise;
      if (statusCode != 200 || body.err_code !== 0) throw new Error("\u83B7\u53D6\u56DE\u590D\u8BC4\u8BBA\u5931\u8D25");
      return { source: "kg", comments: this.filterComment(body.list || []) };
    },
    replaceAt(raw, atList) {
      atList.forEach((atobj) => {
        raw = raw.replaceAll(`[at=${atobj.id}]`, `@${atobj.name} `);
      });
      return raw;
    },
    filterComment(rawList) {
      return rawList.map((item) => {
        let data = {
          id: item.id,
          text: decodeName((item.atlist ? this.replaceAt(item.content, item.atlist) : item.content) || ""),
          images: item.images ? item.images.map((i) => i.url) : [],
          location: item.location,
          time: item.addtime,
          timeStr: dateFormat2(new Date(item.addtime).getTime()),
          userName: item.user_name,
          avatar: item.user_pic,
          userId: item.user_id,
          likedCount: item.like.likenum,
          replyNum: item.reply_num,
          reply: []
        };
        return item.pcontent ? {
          id: item.id,
          text: decodeName(item.pcontent),
          time: null,
          userName: item.puser,
          avatar: null,
          userId: item.puser_id,
          likedCount: null,
          replyNum: null,
          reply: [data]
        } : data;
      });
    }
  };

  // src/utils/musicSdk/kg/index.js
  var kg = {
    // tipSearch,
    leaderboard: leaderboard_default5,
    songList: songList_default5,
    musicSearch: musicSearch_default5,
    hotSearch: hotSearch_default5,
    comment: comment_default5,
    getMusicUrl(songInfo, type) {
      return apis("kg").getMusicUrl(songInfo, type);
    },
    getLyric(songInfo) {
      return lyric_default5.getLyric(songInfo);
    },
    // getLyric(songInfo) {
    //   return apis('kg').getLyric(songInfo)
    // },
    getPic(songInfo) {
      return pic_default3.getPic(songInfo);
    },
    getMusicDetailPageUrl(songInfo) {
      return `https://www.kugou.com/song/#hash=${songInfo.hash}&album_id=${songInfo.albumId}`;
    }
    // getPic(songInfo) {
    //   return apis('kg').getPic(songInfo)
    // },
  };
  var kg_default = kg;

  // entry.js
  var MODULES = { wy: wy_default, kw: kw_default, tx: tx_default, mg: mg_default, kg: kg_default };
  var SOURCES = ["wy", "kw", "tx", "mg", "kg"];
  function mod(source) {
    const m = MODULES[source];
    if (!m) throw new Error(`\u4E0D\u652F\u6301\u7684\u5E73\u53F0: ${source}`);
    return m;
  }
  var ACTIONS = {
    /** 按歌名搜。payload: {source, name, page, limit} */
    async search({ source, name, page = 1, limit = 30 }) {
      const r = await mod(source).musicSearch.search(name, page, limit);
      return { list: r.list || [], total: r.total || 0, allPage: r.allPage || 1 };
    },
    /** 热搜词。payload: {source} */
    async hotSearch({ source }) {
      const r = await mod(source).hotSearch.getList();
      return { list: r.list || r || [] };
    },
    /** 歌单搜索。payload: {source, name, page, limit} */
    async songListSearch({ source, name, page = 1, limit = 30 }) {
      const r = await mod(source).songList.search(name, page, limit);
      return { list: r.list || [], total: r.total || 0 };
    },
    /** 歌单详情。payload: {source, id, page, limit} */
    async songListDetail({ source, id, page = 1, limit = 1e3 }) {
      const r = await mod(source).songList.getListDetail(id, page, limit);
      return { list: r.list || [], total: r.total || 0 };
    },
    /** 歌词。payload: {source, musicInfo} */
    async lyric({ source, musicInfo }) {
      const r = await mod(source).getLyric(musicInfo);
      return r || {};
    },
    /** 封面地址。payload: {source, musicInfo} */
    async pic({ source, musicInfo }) {
      const url = await mod(source).getPic(musicInfo);
      return { url: url || "" };
    },
    /** 排行榜列表。payload: {source} */
    async leaderboard({ source }) {
      const r = await mod(source).leaderboard.getBoards();
      return { boards: r.boards || [] };
    },
    /** 平台清单 */
    async sources() {
      return { sources: SOURCES };
    }
  };
  globalThis.lxSdkCall = (reqId, action, payloadJson) => {
    let payload = {};
    try {
      payload = payloadJson ? JSON.parse(payloadJson) : {};
    } catch (e) {
      nativeCall("sdkResponse", { reqId, ok: false, error: "payload \u4E0D\u662F\u5408\u6CD5 JSON" });
      return;
    }
    const fn = ACTIONS[action];
    if (!fn) {
      nativeCall("sdkResponse", { reqId, ok: false, error: `\u4E0D\u652F\u6301\u7684 action: ${action}` });
      return;
    }
    Promise.resolve().then(() => fn(payload)).then((data) => nativeCall("sdkResponse", { reqId, ok: true, data })).catch((e) => nativeCall("sdkResponse", { reqId, ok: false, error: String(e && e.message || e) }));
  };
  globalThis.__lx_sdk_ready__ = true;
})();
/*! Bundled license information:

he/he.js:
  (*! https://mths.be/he v1.2.0 by @mathias | MIT license *)

ieee754/index.js:
  (*! ieee754. BSD-3-Clause License. Feross Aboukhadijeh <https://feross.org/opensource> *)

buffer/index.js:
  (*!
   * The buffer module from node.js, for the browser.
   *
   * @author   Feross Aboukhadijeh <https://feross.org>
   * @license  MIT
   *)
*/
