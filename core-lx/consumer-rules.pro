# QuickJS 通过 JNI 回调 Java 方法，名字不能被混淆
-keep class com.whl.quickjs.** { *; }
-keepclassmembers class io.github.laozhudu.linkplay.lx.** { @com.whl.quickjs.wrapper.JSCallFunction *; }
