# SeqLock

目前 SeqLock 的实现有着两个版本，一个是较为复杂的库`SeqLock`的实现，另一个是`Crossbeam`中用于内部工具的实现。

# SeqLock 中的实现

在该库中，SeqLock 比较偏向于经典的实现，也即改良版的读写锁，重点优化读。接下来来详细看。

## 初始化

有着两个结构体，采用了 Mutex 典型的 Guard 结构，利用生命周期来监测写锁：

```rust
/// A sequential lock
pub struct SeqLock<T> {
    seq: AtomicUsize,
    data: UnsafeCell<T>,
    mutex: Mutex<()>,
}

unsafe impl<T: Send> Send for SeqLock<T> {}
unsafe impl<T: Send> Sync for SeqLock<T> {}

/// RAII structure used to release the exclusive write access of a `SeqLock`
/// when dropped.
pub struct SeqLockGuard<'a, T> {
    _guard: MutexGuard<'a, ()>,
    seqlock: &'a SeqLock<T>,
    seq: usize,
}
```

初始化的时候很简单：

```rust
    /// Creates a new SeqLock with the given initial value.
    #[inline]
    pub const fn new(val: T) -> SeqLock<T> {
        SeqLock {
            seq: AtomicUsize::new(0),
            data: UnsafeCell::new(val),
            mutex: Mutex::new(()),
        }
    }
```

关键就是 seq 为 0.

## 优化读

优化读是`SeqLock`的核心优化点：

```rust
		/// Reads the value protected by the `SeqLock`.
    ///
    /// This operation is extremely fast since it only reads the `SeqLock`,
    /// which allows multiple readers to read the value without interfering with
    /// each other.
    ///
    /// If a writer is currently modifying the contained value then the calling
    /// thread will block until the writer thread releases the lock.
    ///
    /// Attempting to read from a `SeqLock` while already holding a write lock
    /// in the current thread will result in a deadlock.
    #[inline]
    pub fn read(&self) -> T {
        loop {
            // Load the first sequence number. The acquire ordering ensures that
            // this is done before reading the data.
            let seq1 = self.seq.load(Ordering::Acquire);

            // If the sequence number is odd then it means a writer is currently
            // modifying the value.
            if seq1 & 1 != 0 {
                // Yield to give the writer a chance to finish. Writing is
                // expected to be relatively rare anyways so this isn't too
                // performance critical.
                thread::yield_now();
                continue;
            }

            // We need to use a volatile read here because the data may be
            // concurrently modified by a writer. We also use MaybeUninit in
            // case we read the data in the middle of a modification.
            let result = unsafe { ptr::read_volatile(self.data.get() as *mut MaybeUninit<T>) };

            // Make sure the seq2 read occurs after reading the data. What we
            // ideally want is a load(Release), but the Release ordering is not
            // available on loads.
            fence(Ordering::Acquire);

            // If the sequence number is the same then the data wasn't modified
            // while we were reading it, and can be returned.
            let seq2 = self.seq.load(Ordering::Relaxed);
            if seq1 == seq2 {
                return unsafe { result.assume_init() };
            }
        }
    }
```

在读取过程中分为几个过程：

1. 读取 seq
2. 比对，如果为偶数则说明有线程在修改数据，yield 线程。
3. 读取数据
4. 再次读取 seq
5. 比对两个 seq，如果一致才返回。

可以看出，关键是通过 seq 这个值来保证了读写安全。内存顺序上很简单，第一个 Acquire Load 确保了后续所有操作都必须在第一次读取 seq 之后进行，Acquire fence 使得之前所有的操作都必须在读取 seq2 之前完成，因此上面提到的五个过程在多线程环境中的任意单个线程是保证了顺序的。

## 写

### 包裹写数据的开始与结束

```rust
    #[inline]
    fn begin_write(&self) -> usize {
        // Increment the sequence number. At this point, the number will be odd,
        // which will force readers to spin until we finish writing.
        let seq = self.seq.load(Ordering::Relaxed).wrapping_add(1);
        self.seq.store(seq, Ordering::Relaxed);

        // Make sure any writes to the data happen after incrementing the
        // sequence number. What we ideally want is a store(Acquire), but the
        // Acquire ordering is not available on stores.
        fence(Ordering::Release);

        seq
    }

impl<T> SeqLock<T> {
    #[inline]
    fn end_write(&self, seq: usize) {
        // Increment the sequence number again, which will make it even and
        // allow readers to access the data. The release ordering ensures that
        // all writes to the data are done before writing the sequence number.
        self.seq.store(seq.wrapping_add(1), Ordering::Release);
    }
}
```

开始写与结束写，其中任何类型的数据都可以结束写，而只有 Copy 的数据可以开始写。内存顺序上比较有意思，整个写的过程为了被确保发生在开始之后，结束之前，分别使用了 Release fence 来阻止其前的任意操作往后的 store 乱序，以及 Release 写来保证了之前的操作相对于 store 的顺序。

在 seq 的变化上，开始与结束都会将其 +1.

## 数据写过程

```rust
		#[inline]
    fn lock_guard<'a>(&'a self, guard: MutexGuard<'a, ()>) -> SeqLockGuard<'a, T> {
        let seq = self.begin_write();
        SeqLockGuard {
            _guard: guard,
            seqlock: self,
            seq: seq,
        }
    }

    /// Locks this `SeqLock` with exclusive write access, blocking the current
    /// thread until it can be acquired.
    ///
    /// This function does not block while waiting for concurrent readers.
    /// Instead, readers will detect the concurrent write and retry the read.
    ///
    /// Returns an RAII guard which will drop the write access of this `SeqLock`
    /// when dropped.
    #[inline]
    pub fn lock_write(&self) -> SeqLockGuard<'_, T> {
        self.lock_guard(self.mutex.lock())
    }

```

可以看到，开始写被放在了 lock 里面，此时还给到了一个 Mutex 来进行额外的保护。

在完成写之后释放锁采用了一样的生命周期监控策略：

```rust
impl<T> Drop for SeqLockGuard<'_, T> {
    #[inline]
    fn drop(&mut self) {
        self.seqlock.end_write(self.seq);
    }
}
```

结束写就在里面。

因此这就是为啥只要 seq 为偶数一定是在写中，而奇数在写前或者写后。

# Crossbeam 中的实现

在 Crossbeam 中实现要简单很多，就只有一个字段：

```rust
/// A simple stamped lock.
pub(crate) struct SeqLock {
    /// The current state of the lock.
    ///
    /// All bits except the least significant one hold the current stamp. When locked, the state
    /// equals 1 and doesn't contain a valid stamp.
    state: AtomicUsize,
}

    pub(crate) const fn new() -> Self {
        Self {
            state: AtomicUsize::new(0),
        }
    }
```

他的优化读的设计主要是服务 Crossbeam 的：

```rust
    /// If not locked, returns the current stamp.
    ///
    /// This method should be called before optimistic reads.
    #[inline]
    pub(crate) fn optimistic_read(&self) -> Option<usize> {
        let state = self.state.load(Ordering::Acquire);
        if state == 1 {
            None
        } else {
            Some(state)
        }
    }

    /// Returns `true` if the current stamp is equal to `stamp`.
    ///
    /// This method should be called after optimistic reads to check whether they are valid. The
    /// argument `stamp` should correspond to the one returned by method `optimistic_read`.
    #[inline]
    pub(crate) fn validate_read(&self, stamp: usize) -> bool {
        atomic::fence(Ordering::Acquire);
        self.state.load(Ordering::Relaxed) == stamp
    }
```

如果 state 为 1 的时候说明有线程正在写，那么就读失败，库`SeqLock`中的 loop 被 Crossbeam 设计在了外部。而如果优化读成功了，说明此时没有线程在写，再进行 validate 读，确保读过程中数据没有被写修改，这个对应库`SeqLock`中读 loop 中的第二次读 seq。

而写就简单很多了：

```rust
    /// Grabs the lock for writing.
    #[inline]
    pub(crate) fn write(&'static self) -> SeqLockWriteGuard {
        let backoff = Backoff::new();
        loop {
            let previous = self.state.swap(1, Ordering::Acquire);

            if previous != 1 {
                atomic::fence(Ordering::Release);

                return SeqLockWriteGuard {
                    lock: self,
                    state: previous,
                };
            }

            backoff.snooze();
        }
    }
```

可以看到，写时 state 一定是个 1，如果为 1 就说明其他线程在写。fence 也比较有意思，只有在真正进行写的时候才确保之前对 state 的写入一定发生在写数据之前。

写数据的过程使用了和上面一样的思路，通过 Guard 来利用生命周期保证：

```rust
/// An RAII guard that releases the lock and increments the stamp when dropped.
pub(crate) struct SeqLockWriteGuard {
    /// The parent lock.
    lock: &'static SeqLock,

    /// The stamp before locking.
    state: usize,
}

impl SeqLockWriteGuard {
    /// Releases the lock without incrementing the stamp.
    #[inline]
    pub(crate) fn abort(self) {
        self.lock.state.store(self.state, Ordering::Release);

        // We specifically don't want to call drop(), since that's
        // what increments the stamp.
        mem::forget(self);
    }
}

impl Drop for SeqLockWriteGuard {
    #[inline]
    fn drop(&mut self) {
        // Release the lock and increment the stamp.
        self.lock
            .state
            .store(self.state.wrapping_add(2), Ordering::Release);
    }
}
```

他的一个特点是可以取消写，本质上就是阻止了进行 drop，而在 drop 时会 +2 导致 state 不为 1，因此不标记为写。
