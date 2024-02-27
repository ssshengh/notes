# 安卓 Flow

# Koltin Flow是什么 

Kotlin Flow 是 Kotlin 协程中的一种状态流，用于处理异步事件和数据的流式处理。它是一种可观察的序列，可以通过订阅来接收和处理事件或数据的变化。 Flow 提供了一种简单而直观的方式来处理异步操作和数据，并且可以组合多个 Flow 来构建更复杂的流式处理管道。Flow 可以用于处理各种类型的数据，例如网络请求、数据库查询、传感器数据等。 Flow 的核心概念包括发射（emitting）和收集（collecting）事件或数据。发射者（emitter）负责产生事件或数据，并将其发送到 Flow 中。收集者（collector）则订阅 Flow，并根据需要处理事件或数据。 Flow 还提供了许多操作符，例如 map、filter、reduce 等，用于对 Flow 进行转换和操作。这些操作符可以组合在一起，形成复杂的流式处理管道，从而实现对异步事件和数据的高效处理。 总的来说，Kotlin Flow 是一种强大的工具，可用于处理各种异步操作和数据，并提供了一种简洁而直观的方式来构建复杂的流式处理管道。(来自AI)

# Flow和Channel

**冷流**  

> 只有消费者订阅的时候，生产者才开始生产即发射数据，每对生产者和消费者的消息都是独立的，完整发送的。

 **热流**  

> 不管有没有接收方，发送方都会工作，多个消费者订阅同一个生产者的时候，多个消费者不会消费同一个消息。

## Channel

Channel 就是传输数据的管道，典型的生产者消费者模型，它可以定义**管道的容量**、**超过容量后的策略**来实现不同状态下的数据流的发送和接受。

### Channel的构造方法：

```Kotlin
public fun <E> Channel(
    capacity: Int = RENDEZVOUS,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    onUndeliveredElement: ((E) -> Unit)? = null
): Channel<E> {}
```

**参数介绍：**

#### **capacity：管道容量**

| **RENDEZVOUS (默认值)** | **容量为 0**                                                 |
| ----------------------- | ------------------------------------------------------------ |
| **UNLIMITED**           | **无限容量**                                                 |
| **CONFLATED**           | **容量为 1，新的数据会替代旧的数据**                         |
| **BUFFERED**            | **默认情况下是 64，具体容量由** **VM** **参数决定 "kotlinx.coroutines.channels.defaultBuffer"** |

#### onBufferOverflow：背压策略

| **SUSPEND**     | **当管道的容量满了以后, 将发送方的执行流程挂起，等管道中有了空闲位置以后再恢复** |
| --------------- | ------------------------------------------------------------ |
| **DROP_OLDEST** | **丢弃最旧的那条数据，然后发送新的数据**                     |
| **DROP_LATEST** | **丢弃最新的那条数据**                                       |

#### onUndeliveredElement:  发送出去的 Channel 数据无法被接收方处理的时候，就可以通过 这个回调，来进行监听

![img](./assets/(null))

示例：

```Kotlin
runBlocking {
    val channel = Channel<Int>(
    capacity = 3, 
    onBufferOverflow = BufferOverflow.DROP_OLDEST) 
    {
        println("onUndeliveredElement = $it")
    }

    launch {
        (1..10).forEach {
            channel.send(it)
        }

        channel.close() 
    }

    launch {
        for (i in channel) {
            delay(500)
            println("custom1 Receive: $i")
            // channel.cancel()
        }
    }
    
    launch {
        for (i in channel) {
            delay(500)
            println("custom2 Receive: $i")
            // channel.cancel()
        }
    }

}

/** 输出结果
custom1 Receive: 8
custom1 Receive: 9  // 如果cancel 这个元素会回调onUndeliveredElement
custom1 Receive: 10
**/
```

**Channel 是热流，即不管有没有接受方，发送方都会工作.**

## Flow

Flow的数据模型除了 **发送方（上游**）和 **接收方（下游）**，数据中间是可以经过多次的中转处理。

```Kotlin
// 代码段1

fun main() = runBlocking {
    flow {                  // 上游，发源地
        emit(1)             // 挂起函数
        emit(2)
        emit(3)
        emit(4)
        emit(5)
    }.filter { it > 2 }     // 中转站1
        .map { it * 2 }     // 中转站2
        .take(2)            // 中转站3
        .collect{           // 下游
            println(it)
        }
}

/*
输出结果：                       
6
8
*/
```

这种链式调用代码显得非常的清晰易懂。

### Flow操作符

#### 创建FLow

| **Flow创建方式** | **试用场景**       | **用法**                |
| ---------------- | ------------------ | ----------------------- |
| `flow{}`         | 未知数据集         | flow { emit(getUser())} |
| `flowOf()`       | 已知数据集         | flowOf(1,2,3)           |
| `.asFlow()`      | 已有各种集合和序列 | list.asFlow()           |

#### 监听生命周期

| **生命周期监听方法** | **调用时机**                                                 |
| -------------------- | ------------------------------------------------------------ |
| `onStart`            | 在Flow启动的时候回调                                         |
| `onCompletion`       | 在Flow数据流执行完毕的时候回调Flow正常执行完毕Flow执行出现异常Flow被取消 |

```Kotlin
runBlocking {
    launch {
        flow {
            emit(1)
            emit(2)
            emit(3)
            emit(4)
            emit(5)
        }.onCompletion { println("onCompletion first: $it") }
            .filter {  
                println("filter: $it") 
                it > 2}
            .onStart { println("onStart") }
            .collect {
                println("collect: $it")
                if (it == 4) {
                    cancel()            // 1
                    println("cancel")
                }
            }
    }

    delay(100L)

    flowOf(6, 7, 8)
        .onCompletion { println("onCompletion second: $it") }
        .collect {
            println("collect: $it")
            // 仅用于测试
            throw IllegalStateException() // 2
        }
}

/*
输出结果：                       
onStart
filter: 1
filter: 2
filter: 3
collect: 3
filter: 4
collect: 4
cancel
onCompletion first: kotlinx.coroutines.JobCancellationException: StandaloneCoroutine was cancelled; job="coroutine#2":StandaloneCoroutine{Cancelling}@5b7a8434
collect: 6
onCompletion second: java.lang.IllegalStateException
*/
```

#### 异常处理 catch 操作符

catch 的作用域，**仅限于 catch 的上游**。换句话说，发生在 catch 上游的异常，才会被捕获，发生在 catch 下游的异常，则不会被捕获。

```Kotlin
runBlocking {
            flow<Int> {
                emit(1)
                emit(2)
                emit(3)
                emit(4)
            }.map{
                if(it > 2){
                    throw NullPointerException("不应该大于2")
                }else{
                    it
                }
            }.catch { e ->
                println(e)
            }.collect {
                println(it)
//                throw IllegalStateException()
            }
        }

/*
输出结果：
1
2
java.lang.NullPointerException: 不应该大于2
*/
```

#### 线程切换

- **withContext**

协程的切换方式，但是在`flow {...}`中无法随意切换调度器，这是因为 `emit` 函数不是线程安全的，`flow {...}` 构建器中的代码必须遵循上下文保存属性，并且不允许从其他上下文中发射（emit）。

**在Flow中不推荐用withContent**

```Kotlin
flow {
    withContext(Dispatchers.IO){  //error
        emit(2)
    }
    emit(1)
}.collect {
    println(it)
}
```

- **flowOn**

flowOn 操作符也是和它的位置强相关的。它的作用域跟前面的 catch 类似：flowOn 仅限于它的上游

```Kotlin
fun main() = runBlocking {
   flow {
        emit("Context")
        println(" emit on ${Thread.currentThread().name}")
    }
    .flowOn(Dispatchers.IO)
    .map {
        println(" map on ${Thread.currentThread().name}")
        it + " Preservation"
     }
     .flowOn(Dispatchers.Default)
     .collect { value ->
        println(" collect on ${Thread.currentThread().name}")
        println(value)
      }
}
/*
输出结果：
 emit on DefaultDispatcher-worker-2
 map on DefaultDispatcher-worker-1
 collect on main
 Context Preservation
*/
```

- **launchIn**

改变除了 flowOn 以外所有代码的 Context,

```Kotlin
val scope = CoroutineScope(mySingleDispatcher)
flow.flowOn(Dispatchers.IO)
    .filter {
        println("Filter: $it")
        it > 2
    }
    .onEach {
        println("onEach $it")
    }
    .launchIn(scope)

/*
输出结果：
onEach{}将运行在MySingleThread
filter{}运行在MySingleThread
flow{}运行在DefaultDispatcher
*/
```

LaunchIn的源码

```Kotlin
public fun <T> Flow<T>.launchIn(scope: CoroutineScope): Job = scope.launch {
    collect() // tail-call
}
```

**launchIn 从严格意义来讲，应该算是一个下游的终止操作符，因为它本质上是调用了 collect()**。

#### 背压操作符  `.buffer(capity, onBufferOver)`

> Capity 同 [Channel的captiy定义](https://bytedance.feishu.cn/wiki/GkE3wYKB1i6970kfJoFcB0oOnlc?create_from=create_doc_to_wiki&larkTabName=space#part-FZ6SdsTJooNXOgxkWFIc0nCGnne) 
>
> onBufferOver 同 [Channel的BufferOver定义](https://bytedance.feishu.cn/wiki/GkE3wYKB1i6970kfJoFcB0oOnlc?create_from=create_doc_to_wiki&larkTabName=space#part-F3NjdzeizoRE0nxZ2m9cGDCnnbC)

#### 终止操作符

**由于 Flow 的消费端一定需要运行在****协程****当中，因此末端操作符都是挂起函数**。

末端操作符，大体分为三种类：

1. 集合类型转换操作，包括 `toList`、`toSet` 等。
2. 聚合操作，包括将 Flow 规约到单值的 `reduce`、`fold` 等操作，以及获得单个元素的操作包括 `single`、`singleOrNull`、`first` 等。
3. 无操作 `collect() 和 launchIn()`等。

**Flow是冷流 一个** **`Flow`** **创建出来之后，不消费则不生产，多次消费则多次生产，生产和消费总是相对应的**

#### **总结：**

![img](./assets/(null)-20240227114928631.(null))

## StateFlow和SharedFlow

- **ShareFlow**

```Kotlin
public fun <T> MutableSharedFlow(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
): MutableSharedFlow<T>
```

参数定义：

| **参数**                | **说明**                                          | **默认值**                             |
| ----------------------- | ------------------------------------------------- | -------------------------------------- |
| **replay**              | 当新的订阅者`Collect`时，发送几个已经发送过的数据 | **0：** 默认新订阅者不会获取以前的数据 |
| **extraBufferCapacity** | 出去replay外，ShareFlow还缓存多少个数据           | **0：**默认不缓存                      |
| **onBufferOverflow**    | 缓冲区满了之后`Flow`如何处理                      | **SUSPEND：** 默认挂起                 |

普通`flow`可使用`shareIn`扩展方法，转化成`SharedFlow`。

```Kotlin
//ViewModel
val sharedFlow=MutableSharedFlow<String>()

viewModelScope.launch{
      sharedFlow.emit("Hello")
      sharedFlow.emit("SharedFlow")
}

//Activity
lifecycleScope.launch{
    viewMode.sharedFlow.collect { 
       print(it)
    }
}
```

- **StateFlow**

```Kotlin
public fun <T> MutableStateFlow(value: T): MutableStateFlow<T> = StateFlowImpl(value ?: NULL)
```

**`StateFlow`本质上是一个`replay`为1，并且没有缓冲区的`SharedFlow`**,因此第一次订阅时会先获得默认值

```Kotlin
    //ViewModel
    private val _uiState = MutableStateFlow(LatestNewsUiState.Success(emptyList()))
    // The UI collects from this StateFlow to get its state updates
    val uiState: StateFlow<LatestNewsUiState> = _uiState
    
    viewModelScope.launch {
            newsRepository.favoriteLatestNews
                .collect { favoriteNews ->
                    _uiState.value = LatestNewsUiState.Success(favoriteNews)
                }
        }
   
   //Activity
   lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger the flow and start listening for values.
                // Note that this happens when lifecycle is STARTED and stops
                // collecting when the lifecycle is STOPPED
                latestNewsViewModel.uiState.collect { uiState ->
                    // New value received
                    when (uiState) {
                        is LatestNewsUiState.Success -> showFavoriteNews(uiState.news)
                        is LatestNewsUiState.Error -> showError(uiState.exception)
                    }
                }
            }
```

# 能解决什么痛点

## LiveData的问题

1. `LiveData`不支持背压，也就是在一段时间内**发送**数据的速度 > **接受**数据的速度，LiveData 无法正确的处理这些请求，有概率丢失中间数据。

```Kotlin
 protected void postValue(T value) {
    boolean postTask;
    synchronized (mDataLock) {
        postTask = mPendingData == NOT_SET;
        // 1.每次会先将新数据暂存在mPendingData变量中
        mPendingData = value;
    }
    // PS: 如果在有一个数据正在更新中，则会直接返回，而不会再抛出新的Runnable去更新数据
    if (!postTask) {
        return;
    }
    // 2.这里会向主线程抛出一个Runnable进行数据更新
    ArchTaskExecutor.getInstance().postToMainThread(mPostValueRunnable);
}
```

1. LiveData更新数据时，即使新数据与旧数据相同，也会进行赋值，可能会导致频繁触发回调
2. LiveData的value为可空类型，每次使用时都需要进行判空操作
3. LiveData的粘性事件问题

## 用Flow替代

**数据丢失问题**： 

 Flow支持背压在更新数据时，使用emit()进行发射，将数据推送到Buffer中，然后依次取出进行更新，不存在丢失中间数据的问题。

**相同数据问题**：

`StateFlow`仅在值已更新，并且值发生了变化时才会返回，即如果更新后的值没有变化，也不会回调`Collect`方法。

**数据为空问题**：

`StateFlow` 始终是有值的，构造的时候需要传入默认值

**粘性事件问题**：

`SharedFlow`默认是没有粘性事件的, 但是造方法为我们提供了reply参数，reply的默认值是0，即没有粘性事件，设置reply的值，可以回放任意多条粘性事件，更灵活

| **问题** | **当前解决方案**                   | **Flow方案** |
| -------- | ---------------------------------- | ------------ |
| 数据丢失 | LiveDataPostUtil                   | `StateFlow`  |
| 相同数据 | DeduplicatedNonNullMutableLiveData | `StateFlow`  |
| 数据为空 | *NonNullLiveData*                  | `StateFlow`  |
| 粘性事件 | SingleEventLiveData                | `SharedFlow` |

除了解决LiveData的问题，使用flowOn操作符切换协程上下文、使用buffer、conflate操作符处理背压、使用debounce操作符实现防抖、使用combine操作符实现flow的组合等等，能够在一些复杂场景用很简洁清晰的表达。