 # 守护线程

## 心智模型

设计一个守护线程的关键在于两个方面：

1. 通信机制。
2. 对于出现错误时的处理，这里又分为 Err 处理和 Panic 处理。

我们假设有一个 APP 实体，其中跑了一个守护线程，那么其处理流程应该是如下的几步：

```rust
impl App {
    /// App 初始化函数，除了 init 外不可调用
    fn new(handle: BorrowedUserContainerHandle) -> Arc<Self> {
        log::debug!("App: init an app");
        // Step1. 创建 "任务发端" 和 "任务收端", 这是一个抽象的模型, 绝大部份情况下我们使用 MPSC 信道
        let (task_worker_sender, task_worker_receiver) = create_task_worker_channel();

        let app = Arc::new(Self {
            handle: handle.to_owned(),
            // Step2. "任务发端", 让 App 持有
            task_worker_sender,
            config: Config::new(),
            cancel_tokens: LockMap::default(),
        });

        // Step3. 构建执行任务的 Worker, 让其持有 "任务收端", 这一步的关键在于, 我们需要一个抽象的 Worker
        let task_worker = ResourceTaskWorker::new(app.clone(), task_worker_receiver);
        let cancel_token = handle.cancellation_token().clone();
      	
      	// Step4. 最后将 worker 放入守护线程中去跑, 为了支持异步, 使用的异步函数
        let app_task_worker = demean_worker::running(task_worker, async move {
            log::debug!("App: init an app, demean worker canceled!");
            cancel_token.wait().await;
        });
        handle.spawn(app_task_worker);
      	
        //......
        app
    }
}
```

在上面的代码中，我们启动了一个负责给守护线程发送任务的 App，其持有 channel 的发端；我们还给 demean 线程注册了一个执行任务的 Worker 抽象，它持有任务的收端，也是 demean 线程中负责执行任务的实体。

## 错误处理——unwind safety

在我们开始之前，需要先简单介绍一下 unwind safety 的概念。在 Rust 中，如果一个类型被标记了`UnwindSafe`则我们说他是 unwind safety 的：

```ru
impl UnwindSafe for LoopServer {}
```

提出 unwind safety 是为了应对一类典型 bug：如果一个函数自身 panic 了或者其内部调用的函数 panic 了，此时会发生：

1. panic 的线程中的数据结构会处于一个不可用的状态；
2. 这个损坏的数据结构（或者说变量）会被其他线程观察到。

这两种情况极其恶劣，会导致各种 UB 问题。

Rust 尽可能的避免了这两种情况的发生，因为当 Rust 中出现 panic 的时候，此时至少意味着 panic 被捕获了，继而可以推出此时的 Rust 程序中，至少有着一个新的线程用于 catch panic（此时，Rust 的线程安全特性及 catch panic 的线程会促使错误数据难以被正常线程观察到），或者是此时存在一个`catch_unwind`方法。在目前的 Rust 实现中，尤其是还考虑到 Rust 并不允许没有初始化的数据存在，以上两个问题不会演变为恶劣的 UB 问题。但是损坏的数据始终会造成逻辑错误，而这是编译器力所不及的，因此 Rust 抽象了 unwind safety trait，让用户自己来保障出现 panic 时的数据恢复流程正确无误。

上面提到了`catch_unwind`方法，他可以安全的处理 panic 问题，但其原理很简单————如果出现了 panic，进行 catch 保存状态，并使得当前的环境（闭包） 可以被重新使用，也即通过`catch_unwind`我们可以拿到 crash 瞬间的环境状态并进行恢复。



总结，Rust 使得当一段代码（执行流）意外中断时(panic/.await之后不执行/?提早返回), 调用方可以及时观察到, 并且可以通过某种方式恢复。 例如:

1. Mutex<T>对于panic是abort safe的, 因为如果持有锁的线程panic了, 其他持有锁的线程可以观察到锁陷入到了poison的状态
2. &mut T一般不是abort safe的, foo(&mut T)如果panic被捕获了, T的所有者其实无法感知到T是否处于一个正确的状态。

## UnwindSafe 的 Worker 抽象设计

我们设计一个 UnwindSafe 的 Worker 主要就是为了当`catch_unwind`捕获到 panic 的时候能够立刻停止运行。 此时有可能会产生脏数据（并继续运行），所以其抽象中需要确保实现方保证 unwind safe：

```rust
/// `working`出错的两种情况
#[derive(Debug)]
pub enum Failure<E> {
    Mistake(E),
    Panic(Box<dyn Any + Send>),
}

/// 一个 UnwindSafe 的 Worker 设计
#[async_trait]
pub trait Worker: UnwindSafe {
  	/// Task 可以有多个
    const TASK_NAME: &'static str;

    /// 在`working`正常结束时返回的类型
    type Done;
  	
    /// 在`working`出错时返回的类型(包含了 Err 和 Panic)
    type Mistake;


    /// 该函数需要实现方保证**unwind safe**, 比如业务需要有清理预期外脏数据的能力
  	/// 如果执行成功，返回 Done，否则返回 Mistake 并处理脏数据
    async fn working(&mut self) -> Result<Self::Done, Self::Mistake>;

    // 一些lifecycle函数

    /// 在`working`发生panic或错误后触发,
    /// 如果想直接退出的话就返回`ControlFlow::Break(...)`  -> 这个是 Rust 提供的标记控制流状态的方法
    fn reset(&mut self, cause: Failure<Self::Mistake>) -> ControlFlow<Self::Done>;

    /// 执行完或被中断时执行
    /// 其中中断有多种可能
    /// 1. `stop`信号到(见 running 运行时函数)
    /// 2. `running`这个任务不执行了
    ///    a. runtime 退出
    ///    b. 没执行完就退出了(例如: `timeout(running()).await`, 但不建议这么做)
    ///    c. 被别的地方panic影响到了(tokio不会)
    ///    ...
    ///
    /// 保证能执行
    fn done(self, res: Option<Self::Done>);
}
```

接口设计十分简单，在 Worker working job 的基础上仅仅添加了 job 返回态的标记以及重置方法。

## running 运行时实现

running 方法是一个死循环，也即 demean 线程执行任务的监听 loop，我们先看下实现方法再做解释：

```Rust
pub async fn running<Worker, GetOff>(worker: Worker, stop: Stop)
where
    Worker: Worker,
    Stop: Future,		// 是一个用于退出的 future
{
    // scopeguard 库确保了`done`能始终被执行
    let (worker, res) = &mut *scopeguard::guard((worker, None), |(worker, res)| {
        worker.done(res);
    });

    let job = async {
        loop {
          	// catch_unwind 保护的 worker 执行, 其本身返回一个 OK 意味着没有 panic, 而 Err 意味着 panic 了
            let failure = match AssertUnwindSafe(worker.working()).catch_unwind().await {
                Ok(Ok(done)) => return done,
                Ok(Err(miss)) => Failure::Mistake(miss),
                Err(panic_err) => Failure::Panic(panic_err),
            };
          	// panic 时拿到现场之后的数据恢复
            if let ControlFlow::Break(done) = worker.reset(failure) {
                return done;
            }
        }
    };
		
  	// tokio 的 select 宏
    select! {
        done = job => *res = Some(done),	// 当 job 在不断执行时，如果 woker 返回了 done 则退出
        _ = stop => {
            // 当 stop Future 被执行并唤醒时，不用做什么，自然就退出了
        },
    };
}
```

首先是在上面的处理中很优雅的考虑了两层 panic：

1. `running`函数本身的 panic，此时`scopeguard`库确保了能够在`running`函数结束时一定执行其 guard 的内容。
2. Worker 执行时的 panic，`catch_unwind`保证了出现问题时能够被正确的拿到脏数据并进行恢复。

关于`scopeguard`库我们可以看一下官方文档：

```rust
/// If the scope guard closure needs to access an outer value that is also mutated outside of the scope guard, then you may want to use the scope guard with a value. 
/// **The guard works like a smart pointer**, so the inner value can be accessed by reference or by mutable reference.

/// In this example, the scope guard owns a file and ensures pending writes are synced at scope exit.
extern crate scopeguard;

use std::fs::*;
use std::io::{self, Write};

fn try_main() -> io::Result<()> {
    let f = File::create("newfile.txt")?;
    let mut file = scopeguard::guard(f, |f| {
        // ensure we flush file at return or panic
        let _ = f.sync_all();
    });
    // Access the file through the scope guard itself
    file.write_all(b"test me\n").map(|_| ())
}

fn main() {
    try_main().unwrap();
}
```

可以看到`scopeguard::guard`保障了 IO 写入 panic 时的正确 flush。



其次是其中 job 的处理方式：

```Rust
let job = async {
    loop {
        // catch_unwind 保护的 worker 执行, 其本身返回一个 OK 意味着没有 panic, 而 Err 意味着 panic 了
        let failure = match AssertUnwindSafe(worker.working()).catch_unwind().await {
            Ok(Ok(done)) => return done,
            Ok(Err(miss)) => Failure::Mistake(miss),
            Err(panic_err) => Failure::Panic(panic_err),
        };
        // panic 时拿到现场之后的数据恢复
        if let ControlFlow::Break(done) = worker.reset(failure) {
            return done;
        }
    }
};
```

本质上是一个状态机`catch_unwind`通过返回一个 Result 指示了当前的情况，而`working`也返回了一个 Result 近一步区分了错误情况和正确情况，而 job 本身是一个异步函数，作为一个 task 可以被 tokio 充分的调用并节约资源，此时我们无需手动 yeild 线程。



最后是使用`tokio::select!`来完成优雅的执行和退出：

```rust
// tokio 的 select 宏
select! {
    done = job => *res = Some(done),	// 当 job 在不断执行时，如果 woker 返回了 done 则退出
    _ = stop => {
        // 当 stop Future 被执行并唤醒时，不用做什么，自然就退出了
    },
};
```

select 宏确保其中**每一行**所代表的 Future 被并发执行，并且在**第一个** Future 返回结果并匹配到判断语句时返回结果。因此上面的代码实际上是并发执行了两个 future：

1. 实际处理业务的 job。
2. 用于监听结束事件的 stop future。

此时我们也能看到正常退出时处于两种情况:

1. stop future 触发，且返回了一个非 Pending。
2. working 返回 Ok，也即 Worker 抽象中的 Done 事件到来。

也即，实际上在 Woker 的抽象中，Ok 这个返回值仅只有结束 demean 线程会触发，他的主要作用就是执行任务，然后出现问题时抛出 Err。



最后，一般我们只需要阻塞的把 running 丢到一个线程中处理即可，可以直接spawn出去：

```rust
handle.spawn(running(your_worker, cancel))
```

而对于线程资源敏感的工作, 可以开一个单独的线程, 并开一个单独的 tokio 的 runtime 来跑：
```
thread::spawn(move || {
    let rt = Runtime::new().unwrap();
    rt.block_on(running(your_worker, cancel));
});
```

## 例子

```rust
#[cfg(test)]
mod tests {
    use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};

    use crate::{running, worker::Failure, Worker};
    use async_trait::async_trait;
    use rand::prelude::*;
    use std::{ops::ControlFlow, panic::UnwindSafe};
    use tokio::sync::oneshot;

    #[derive(Eq, PartialEq, Debug)]
    enum State {
        Ready,
        Return,
        Cancel,
        Err,
        Panic,
    }

    enum Action {
        Return,
        Err,
        Panic,
    }

    struct LoopClient {
        recv_state: UnboundedReceiver<State>,
        send_action: UnboundedSender<Action>,
    }

    struct LoopServer {
        send_state: UnboundedSender<State>,
        recv_action: UnboundedReceiver<Action>,
    }

    fn lp() -> (LoopClient, LoopServer) {
        let (state_tx, state_rx) = unbounded_channel();
        let (action_tx, action_rx) = unbounded_channel();

        (
            LoopClient {
                recv_state: state_rx,
                send_action: action_tx,
            },
            LoopServer {
                send_state: state_tx,
                recv_action: action_rx,
            },
        )
    }

    impl UnwindSafe for LoopServer {}

    #[async_trait]
    impl Worker for LoopServer {
        const NAME: &'static str = "test_loop";
        type Done = ();
        type Mistake = ();

        async fn working(&mut self) -> Result<Self::Done, Self::Mistake> {
            let _ = self.send_state.send(State::Ready);
            while let Some(action) = self.recv_action.recv().await {
                match action {
                    Action::Return => {
                        break;
                    }
                    Action::Err => return Err(()),
                    Action::Panic => {
                        panic!("receive panic")
                    }
                }
            }

            Ok(())
        }

        fn reset(&mut self, cause: Failure<Self::Mistake>) -> ControlFlow<Self::Done> {
            match cause {
                Failure::Mistake(_) => {
                    let _ = self.send_state.send(State::Err);
                }
                Failure::Panic(_) => {
                    let _ = self.send_state.send(State::Panic);
                }
            }
            ControlFlow::Continue(())
        }

        fn done(self, res: Option<Self::Done>) {
            if res.is_some() {
                let _ = self.send_state.send(State::Return);
            } else {
                let _ = self.send_state.send(State::Cancel);
            }
        }
    }

    async fn err_work(cli: &mut LoopClient) {
        let _ = cli.send_action.send(Action::Err);
        assert_eq!(cli.recv_state.recv().await, Some(State::Err));
        assert_eq!(cli.recv_state.recv().await, Some(State::Ready));
    }

    async fn panic_work(cli: &mut LoopClient) {
        let _ = cli.send_action.send(Action::Panic);
        assert_eq!(cli.recv_state.recv().await, Some(State::Panic));
        assert_eq!(cli.recv_state.recv().await, Some(State::Ready));
    }

    async fn cancel_work(cli: &mut LoopClient, shutdown: oneshot::Sender<()>) {
        let _ = shutdown.send(());
        assert_eq!(cli.recv_state.recv().await, Some(State::Cancel));
    }

    async fn return_work(cli: &mut LoopClient) {
        let _ = cli.send_action.send(Action::Return);
        assert_eq!(cli.recv_state.recv().await, Some(State::Return));
    }

    #[tokio::test]
    async fn it_works_step_by_step() {
        let mut rng = thread_rng();

        let (mut cli, srv) = lp();
        let (tx, rx) = oneshot::channel();

        tokio::spawn(running(srv, rx));

        assert_eq!(cli.recv_state.recv().await, Some(State::Ready));
        for _ in 0..100 {
            if rng.gen_bool(0.5) {
                err_work(&mut cli).await;
            } else {
                panic_work(&mut cli).await;
            }
        }
        cancel_work(&mut cli, tx).await;
    }

    #[tokio::test]
    async fn it_works_return() {
        let mut rng = thread_rng();

        let (mut cli, srv) = lp();
        tokio::spawn(running(srv, tokio::signal::ctrl_c()));

        assert_eq!(cli.recv_state.recv().await, Some(State::Ready));
        for _ in 0..100 {
            if rng.gen_bool(0.5) {
                err_work(&mut cli).await;
            } else {
                panic_work(&mut cli).await;
            }
        }
        return_work(&mut cli).await;
    }
}
```

