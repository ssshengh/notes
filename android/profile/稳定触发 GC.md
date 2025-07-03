安卓触发 GC 时，`System.gc()`不一定能触发，可以通过`Runtime.getRuntime().gc();`触发，成功之后会触发 nativeGc。

