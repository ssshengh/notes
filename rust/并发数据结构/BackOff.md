# BackOff

`Backoff`这个结构体主要被用在并发编程中，它提供了“自旋”和“休眠”两种形式的等待。每当线程尝试执行某项操作但失败了（通常是因为其它线程正在执行相关的操作），就会使用Backoff策略。Backoff策略顾名思义，就是让线程“后退”一点，给其它线程一些空间让它们完成各自的操作。

该策略的关键在于它能动态调整等待时间，这也是为什么在`Backoff`结构体中要有一个`step`字段的原因。`step`字段记录了Backoff已经等待了多少步，而等待的时间则与这个步骤数呈指数关系。比如刚开始后退时，可能只等待1个CPU周期，但当后退多次后，可能等待的时间就会加倍，最终甚至会放弃运行权，让操作系统调度其它线程运行。

`Backoff`提供了`spin`和`snooze`两种方法来等待。`spin`方式是忙等，也就是在等待期间CPU会一直处于运行状态。而`snooze`方式则应用了系统调用`yield_now`，在等待期间放弃CPU时间片，让操作系统调度其它线程运行。`spin`方式适用于等待时间预期较短的场景，而`snooze`方式适用于等待时间预期较长的场景。

> Spin和Snooze都是两种对互斥处理（或者平常所说的加锁）的方式。但他们的运行方式和呈现出的性质是不同的。
>
> Spinlock（自旋锁）是一种通过忙等待的方式来实现的锁。当需要获取锁而锁已被其他进程或线程获取时，**请求获取锁的线程**不会被阻塞挂起，而是会继续在用户态忙等待，反复检查锁是否已经释放。因此，自旋锁是一种无阻塞的锁，特别是在锁被持有的时间非常短的情况下，自旋锁的效率可能非常高。相反，snooze（或称睡眠）型锁，当**线程试图获取一个已经被占用的锁**的时候，系统会把这个线程挂起，即阻塞该线程，直到锁被释放。这个过程通常涉及到系统的调度操作，即把该线程移出CPU的运行队列，让出CPU给其他线程，因此通常需要更多的处理器时间。因此，snooze（或睡眠）型锁是阻塞的。

简单来说，自旋锁是通过不停地进行查询和尝试来获取锁，而睡眠型锁则是通过阻塞线程并等待通知来获取锁。两者在不同的应用场景下各有优劣。

总的来说，`Backoff`策略能有效地降低并发环境下的资源竞争，提高系统的整体性能。

# 结构体定义及关键方法

## 定义

结构体定义很简单，主要就是包装了一个衡量等待了多久的`step`，等待的时间的上限步数分别为6和10:

```rust
const SPIN_LIMIT: u32 = 6;
const YIELD_LIMIT: u32 = 10;

pub struct Backoff {
    step: Cell<u32>,
}

impl BackOff {
    #[inline]
    pub fn new() -> Self {
        Backoff { step: Cell::new(0) }
    }
  	
  	#[inline]
    pub fn reset(&self) {
        self.step.set(0);
    }
}
```

需要注意的是，`BackOff`不是线程安全的，`Cell`仅仅提供内部可变性，即可以更改`let bo = BackOff::new()`定义时的`bo.step` field。

## 关键方法

`spin`方法主要是忙等的策略，适用于等待时间预期较短的场景。可以在下面的方法中看见，每一次 spin 的时候都会是`2^step`次，上限是`2^6`:

```rust
/// Backs off in a lock-free loop.
/// This method should be used when we need to retry an operation because another thread made progress.
/// The processor may yield using the YIELD or PAUSE instruction.  
#[inline]
pub fn spin(&self) {
    for _ in 0..1 << self.step.get().min(SPIN_LIMIT) {
        // TODO(taiki-e): once we bump the minimum required Rust version to 1.49+,
        // use [`core::hint::spin_loop`] instead.
        #[allow(deprecated)]
        atomic::spin_loop_hint();
    }

    if self.step.get() <= SPIN_LIMIT {
        self.step.set(self.step.get() + 1);
    }
}
```

`snooze`来等待最主要的区别就是会让渡时间片，适用于等待时间预期较长的场景。在下面的方法中可以看见`snooze`是一个渐进的过程，最开始也是忙等的，但是次数不再是幂次。由于 yield 不是等待一个具体的时间，而是直接让渡时间片，因此没有 loop 操作：

```rust
/// Backs off in a blocking loop.
/// This method should be used when we need to wait for another thread to make progress.
/// The processor may yield using the YIELD or PAUSE instruction and the current thread may yield by giving up a timeslice to the OS scheduler.  
#[inline]
pub fn snooze(&self) {
    if self.step.get() <= SPIN_LIMIT {
        for _ in 0..1 << self.step.get() {
            // TODO(taiki-e): once we bump the minimum required Rust version to 1.49+,
            // use [`core::hint::spin_loop`] instead.
            #[allow(deprecated)]
            atomic::spin_loop_hint();
        }
    } else {
        #[cfg(not(feature = "std"))]
        for _ in 0..1 << self.step.get() {
            // TODO(taiki-e): once we bump the minimum required Rust version to 1.49+,
            // use [`core::hint::spin_loop`] instead.
            #[allow(deprecated)]
            atomic::spin_loop_hint();
        }

        #[cfg(feature = "std")]
        ::std::thread::yield_now();
    }

    if self.step.get() <= YIELD_LIMIT {
        self.step.set(self.step.get() + 1);
    }
}
```

如果等待时间很长很长了，那么`BackOff`提供了一个新的方法来进行标明：

```rust
#[inline]
pub fn is_completed(&self) -> bool {
    self.step.get() > YIELD_LIMIT
}
```



# 使用例子

1. 等待时间超时后 park 线程的例子

```rust
use crossbeam_utils::Backoff;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering::SeqCst;
use std::thread;
use std::time::Duration;

fn blocking_wait(ready: &AtomicBool) {
    let backoff = Backoff::new();
    while !ready.load(SeqCst) {
        if backoff.is_completed() {
            thread::park();
        } else {
            backoff.snooze();
        }
    }
}

let ready = Arc::new(AtomicBool::new(false));
let ready2 = ready.clone();
let waiter = thread::current();

thread::spawn(move || {
    thread::sleep(Duration::from_millis(100));
    ready2.store(true, SeqCst);
    waiter.unpark();
});

assert_eq!(ready.load(SeqCst), false);
blocking_wait(&ready);
assert_eq!(ready.load(SeqCst), true);
```

2. 短暂的在 loop 里等待值：

```rust
/// Backing off in a lock-free loop:
use crossbeam_utils::Backoff;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering::SeqCst;

fn fetch_mul(a: &AtomicUsize, b: usize) -> usize {
    let backoff = Backoff::new();
    loop {
        let val = a.load(SeqCst);
        if a.compare_exchange(val, val.wrapping_mul(b), SeqCst, SeqCst).is_ok() {
            return val;
        }
        backoff.spin();
    }
}
```

3. 等待一个原子变量：

```rust
/// Waiting for an AtomicBool to become true:

use crossbeam_utils::Backoff;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering::SeqCst;

fn spin_wait(ready: &AtomicBool) {
    let backoff = Backoff::new();
    while !ready.load(SeqCst) {
        backoff.snooze();
    }
}
```

