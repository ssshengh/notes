## 协程名词

**Coroutine Context**：来自于 coroutine 官方文档的解释是，该上下文是一个各种元素的集合。而其中主要元素是 Job。

> 可以参考：https://kt.academy/article/cc-coroutine-context?source=post_page-----15e0d180fc1f--------------------------------

**Job**：Job 是一个可取消的任务，该任务有一个明确的完成时间，也即生命周期的终点。每个协程都会创建自己的 Job（该 job 是唯一一种不从父协程继承协程上下文的 Job）。

> 可以参考：https://kt.academy/article/cc-job?source=post_page-----15e0d180fc1f--------------------------------

**Dispatcher**：负责决定协程应该在哪个线程（或线程池）上运行（启动和恢复）。

> 可以参考：https://kt.academy/article/cc-dispatchers?source=post_page-----15e0d180fc1f--------------------------------

**Coroutine scope**：Scope 定义了协程的生命周期和上下文。它负责管理协程的生命周期，包括它们的取消和错误处理。

**Coroutine builder**：是基于 CoroutineScope 的扩展函数，用于启动异步协程（`launch`、`async`）。

## Coroutine 使用的主要规则

* 需要一个 CoroutineScope 来启动协程（`launch`、`async`）。`viewModelScope`是 Android 中最常用的 CoroutineScope，但我们也可以构建自己的 Scope。
* 子协程（从一个协程启动的另一个协程）将从他们的父母协程继承他们的协程上下文（除了 Job）。
* 父协程的 Job 用作新协程 Job 的父级。
* 如果父协程被挂起，直到它的所有子协程（挂起点执行完毕）都完成才会继续。
* 当父协程被 cancel 时，它的所有子协程也会被取消。
* 当子协程由于**未捕获的异常**而中断时，它将 cancel 其所有父协程（除非您使用SupervisorJobs，见下文）。
* 永远不要使用 GlobalScope，它会导致内存泄漏，即使启动协程的 Activity 或 Fragment 被销毁后后，它也会保持活动状态。
* 不应该将协程 scope 作为参数传递，而是使用 coroutineScope 函数（参见下面的示例）。

## Coroutine Scope 函数

coroutineScope：一个挂起函数，用于启动 scope 并返回作为参数的函数的返回值

> `public suspend fun <R> coroutineScope(block: suspend kotlinx.coroutines.CoroutineScope.() -> R): R`

SuperorScope：类似于coroutineScope，但它会 overrides context 的 Job，因此当子协程抛出异常时不会取消该 scope。

> `public suspend fun <R> supervisorScope(block: suspend kotlinx.coroutines.CoroutineScope.() -> R): R`

withContext：类似于 coroutineScope，但允许在 scope 内部进行一些更改（通常用于设置Dispatcher）。

withTimeout：类似于 coroutineScope，但设置了函数体内执行的时间限制。如果超时将被取消并抛出TimeoutCancellationException。

withTimeoutOrNull：与with Timeout相同，但会返回null而不是在超时时抛出异常。

## Dispatchers

调度协程是有代价的。例如当我们调用 withContext 时，我们会暂停外部的父协程，因此，withContext 内部的函数必须在队列中等待下一次调度（下面讲述如何避免不必要的重新调度）。

### `Dispatchers.Default`

* 如果未设置 Dispatchers，默认使用。
* 旨在运行CPU密集型操作。
* 线程池的大小等于机器上的内核数。
* 我们可以使用`Dispatcher.Default.limitededParallelism（3）`限制其中的协程可以使用的线程数。

### `Dispatchers.Main`

* 对于 Android 来说运行在UI线程上。
* 小心阻塞这个线程。
* 单元测试中不存在该调度器。

### `Dispatchers.IO`

* 旨在运行阻塞操作（I/O操作、读/写文件、共享首选项等……）。
* 线程池的大小为64（如果大于64，则为内核数）。
* 与`Dispatcher.Default`共享相同的线程池，但它们的限制选项相互独立。例如，Default 可以设置使用 3 个线程，而 IO 设置使用 5 个。
* 主要用于跑阻塞函数。
* 启动该调度器下协程的方法：`withContext（Dispatchers.IO) { // 一些阻塞函数 }`。
* 同样的，我们可以限制操作可以使用的线程数：`Dispatcher.Default.limitededParallelism（3）`。
* `Dispatchers.IO`的`limitededParallelism`有特殊的实现：它创建了一个具有独立线程池的新调度程序（限制可以高于64）。

### `Dispatchers.Unconfined`

* 在启动协程的同一线程上运行，它**不会更改任何线程**。
* 对单元测试很有用。
* 就性能而言，它是最优的（因为不存在线程切换）。
* 但是在生产环境代码中使用很危险（我们可能会不小心在主线程上运行阻塞调用）。

### `Performance observations`

* 当挂起时，我们正在使用的线程数并不影响性能，因为并无任务执行。
* 当阻塞时，我们使用的线程越多，协程的完成速度就越快。
* 在进行 CPU 密集型工作时，`Dispatchers.Default` 是最佳选择。
* 在进行 IO 密集型工作时，更多的线程可能会带来更好的性能（但这并不重要）。

## 并行调用函数

当想同时执行两个异步操作，并在返回结果之前**同时**等待两者的结果时：

### 当能够访问到 scope 时（以 ViewModel 为例）

假设有一个 API，我们可以在其中获取歌曲的配置和歌曲本身。一般来说，我们想在显示页面给用户之前先下载所有数据。我们如下并行触发请求（或者异步接受推送）。然后在配置和歌曲都 ready 之后，我们利用此份数据驱动 UI 展示：3：

```kotlin
suspend fun getConfigFromAPI(): UserConfig {
  // do API call here or any suspend functions
}

suspend fun getSongsFromAPI(): List<Song> {
  // do API call here or any suspend functions
}

fun getConfigAndSongs() {
  // scope can be any scope you'd want a typical case would be viewModelScope
  scope.launch {
    val userConfig = async { getConfigFromAPI() }
    val songs = async { getSongsFromAPI()}
    return Pair(userConfig.await(), songs.await())
  }
}
```

下面还有个例子，我们需要从 API 中获取十份歌曲相关的数据，并在他们都 OK 之后再返回完整的数据驱动 UI 显示：

```kotlin
suspend fun getSongsFromAPI(page: Int): List<Song> {
  // do API call
}
const val totalNumberOfPages = 10

fun getAllSongs() {
  // scope can be any scope you'd want a typical case would be viewModelScope
  scope.launch {
    val allNews = (0 until totalNumberOfPages)
                  .map { page -> async { getSongsFromAPI(page) } }
                  .awaitAll()
  }
}
```

上面的例子中，我们解决此类多 Job 一起完成的方案是使用`async/wait`。

相比于 launch 调用协程时立即开始运行，async 是返回一个类型为Deferred<T>（在我们的例子中是Deferred<List<Song>>）的对象。该 Deferred 通过挂起函数 await 通知开始执行。而当 await 准备好才返回值。

### 当不能够访问到 scope 时（以 repo 为例）

假设我们想从 repo 中定义一个将并行启动2个（或更多）调用的协程。现在的问题在于我们需要一个 scope 来使用 coroutine。在一般的设计模式中，如果我们不在 viewModel 或者 UI 层时，是没有 scope 的（请记住，将范围作为参数传递不是一个好的解决方案）。可以从我们上一节的示例中将其修改：

```kotlin
suspend fun getConfigAndSongs(): Pair<UserConfig, List<Song> = coroutineScope {
   val userConfig = async { getConfigFromAPI() }
   val songs = async { getSongsFromAPI()}
   Pair(userConfig.await(), songs.await())
}
```

此时 VM 访问 repo 时，就可以通过 VM 的 scope 来调用对应的异步函数了。

> PS: 我们在设计中其实也不用这么死板，在 repo 中可以单独给一个基于 IO 线程的 scope，甚至有时候处理的数据复杂还需要个 CPU 线程的 cope。

## Cleaning when a Coroutine is cancelled

如果一个协程被 cancel，那么它将在实际 cancel 之前切换为`cancelling`状态。因此在协程被 cancel 时，coroutine 给了我们一些时间在必要时进行一些清理（例如，清理本地数据库，因为操作没有成功执行，或者执行API调用让服务器知道操作没有成功）。

我们可以使用`finally`块来执行操作:

```kotlin
viewModelScope.launch {
  try {
    // call some suspend function here
  } finally {
    // execute clean up operation here
  }
}
```

在 finally 的清理期间**不再允许暂停操作**。如果还需要挂起的话，将需要执行：

```kotlin
viewModelScope.launch {
  try {
    // call some suspend function here
  } finally {
    withContext(NonCancellable) {
      // execute clean up suspend function here
    }
  }
}
```

注意：cancel 将在第一个挂起点就执行。但这也意味着，如果在函数中没有挂起点，调度器是没办法执行 cancel 的。这也是协作式调度的基础。

### Cleaning a Coroutine when it completes

与取消协程时的清理类似，您可能希望在协程达到结束状态（`completes`或`canceled`）时执行一些回收操作：

```kotlin
suspend fun myFunction() = coroutineScope {
  val job = launch { /* suspend call here */ }
  job.invokeOnCompletion { exception: Throwable -> 
    // do something here
  }
}
```

## 当协程的一个子任务失败时，如何才能不取消整个协程树

我们可以使用`SupervisorJobs`，它将忽略其子级中的所有异常。有三种主要的实现方法：

1. 创建一个自己的 scope

```kotlin
val scope = CoroutineScope(SupervisorJob())
// if one throw an error the other coroutine will not be cancelled
scope.launch { myFirstCoroutine() }
scope.launch { mySecondCoroutine() }
```

2. 通过拓展函数创建 scope

```kotlin
suspend fun myFunction() = supervisorScope {
  // if one throw an error the other coroutine will not be cancelled
  launch { myFirstCoroutine() }
  launch { mySecondCoroutine() }
}
```

3. 捕获异常

```kotlin
suspend fun myFunction() {
  try {
    coroutineScope {
      launch { myFirstCoroutine() }
    }
  } catch (e: Exception) {
    // handle error here
  }
  try {
    coroutineScope {
      launch { mySecondCoroutine() }
    }
  } catch (e: Exception) {
    // handle error here
  }
}

```

在该 scope 中，`CancellationException`不会传播到其父级，因此只有当前协程将被 cancel。当然，我们也可以扩展`CancellationException`以创建自己的类型的异常，而该异常同样不会传播到父级。

### 定义异常情况下的默认行为

使用 `CoroutineExceptionHandler`

> 例如，当服务器以 401 响应时，可用于自动注销用户。

```kotlin
val handler = CoroutineExceptionHandler { context, exception ->
  // define default behaviour like showing a dialog or error message
}
// 创建 scope 时使用
val scope = CoroutineScope(SupervisorJob() + handler)
scope. launch { /* suspend call here */ }
scope. launch { /* suspend call here */ }
```

### 运行非必要操作

如果想运行一个**不应该影响其他函数**的挂起函数（例如，如果它抛出错误，因此只有这个函数不会取消父协程，但其他函数会）。

案例示例：打埋点以分析操作的调用数据

```kotlin
val nonEssentialOperationScope = CoroutineScope(SupervisorJob())

suspend fun getConfigAndSongs(): Pair<UserConfig, List<Song> = coroutineScope {
   val userConfig = async { getConfigFromAPI() }
   val songs = async { getSongsFromAPI()}
   // 在这里起一个打埋点的协程，他的 cancel 不会取消父协程，因为他是一个 SupervisorJob 并且不会传播 Exception
   nonEssentialOperationScope.launch { /* non essential op here */ }
   Pair(userConfig.await(), songs.await())
}
```

理想情况下，您应该在类中注入 nonEssentialOperationScope（更容易测试）。

## 在单个线程上运行操作以避免同步问题

在异步场景中，往往会在线程池中运行不同的任务，此时可能会引入一些多线程的同步问题，我们保持协程所运行的线程数量为 1 是最简单的方案：

```kotlin
suspend fun myFunction() = withContext(Dispatchers.Default.limitedParallelism(1)) {
  // suspend call here
}
// Can also use Dispatchers.IO
```

### 避免多线程同步问题的其他方法

可以使用`AtomicReference` (from Java)

```kotlin
private val myList = AtomicReference(listOf(/* add objects here */))

suspend fun fetchNewElement() {
  val myNewElement = // fetch new element here
  myList.getAndSet { it + myNewElement }
}
```

可以使用 Mutex：

```kotlin
val mutex = Mutex()
private var myList = listOf(/* add objects here */)

suspend fun fetchNewElement() {
  mutex.withLock {
    val myNewElement = // fetch new element here
    myList = myList += myNewElement
  }
}
```

还可以使用 channel 的无锁方法：

```kotlin
class CoroutinesSafe(private val coroutineScope: CoroutineScope) {
    companion object {
        private const val TAG = "CoroutinesSafe"
    }

    private lateinit var concurrentEvent: SendChannel<ConcurrentEvent>

    private fun initConcurrentEventIfNeed(coroutineScope: CoroutineScope) {
        if (!::concurrentEvent.isInitialized) {
            Logger.i(TAG, "initConcurrentEventIfNeed")
            concurrentEvent = coroutineScope.actor {
                for (event in channel) {
                    event.block.invoke()
                }
            }
        }
    }

    fun runConcurrentSafe(block: suspend () -> Unit) {
        coroutineScope.launch {
            initConcurrentEventIfNeed(this)
            concurrentEvent.send(ConcurrentEvent(block))
        }
    }

    suspend fun <T> makeConcurrentSafe(t: T): T {
        return suspendCancellableCoroutine {
            coroutineScope.launch {
                initConcurrentEventIfNeed(this)
                val concurrentJob: (suspend () -> Unit) = {
                    it.resume(t)
                }
                concurrentEvent.send(ConcurrentEvent(concurrentJob))
            }
        }
    }

    fun close() {
        if (this::concurrentEvent.isInitialized) {
            concurrentEvent.close()
        }
    }
}
```

## 避免将协程重新分派给同一个调度程序

如果我们已经在主调度程序上，请避免切换调度导致的不必要成本：

```kotlin
// this will only dispatch if it is needed
suspend fun myFunction() = withContext(Dispatcher.Main.immediate) {
  // suspend call here
}
```

目前只有`Dispatcher.Main`支持`immediate`调度(立即执行)