# Crossbeam::Deque

# Buffer 与 Inner

Buffer 的定义为：

```rust
/// A buffer that holds tasks in a worker queue.
///
/// This is just a pointer to the buffer and its length - dropping an instance of this struct will
/// *not* deallocate the buffer.
struct Buffer<T> {
    /// Pointer to the allocated memory. 这其实是一个指针数组。
    ptr: *mut T,

    /// Capacity of the buffer. Always a power of two.
    cap: usize,
}
```

其中很有意思的是，对 Buffer 的读写不是内存安全的，但是他是`Send`的：

```rust
unsafe impl<T> Send for Buffer<T> {}

impl<T> Buffer<T> {
    /// Allocates a new buffer with the specified capacity.
    fn alloc(cap: usize) -> Buffer<T> {
        debug_assert_eq!(cap, cap.next_power_of_two());

        let mut v = ManuallyDrop::new(Vec::with_capacity(cap));
        let ptr = v.as_mut_ptr();

        Buffer { ptr, cap }
    }

    /// Deallocates the buffer.
    unsafe fn dealloc(self) {
        drop(Vec::from_raw_parts(self.ptr, 0, self.cap));
    }

    /// Returns a pointer to the task at the specified `index`.
    unsafe fn at(&self, index: isize) -> *mut T {
        // `self.cap` is always a power of two.
        // We do all the loads at `MaybeUninit` because we might realize, after loading, that we
        // don't actually have the right to access this memory.
        self.ptr.offset(index & (self.cap - 1) as isize)
    }

    /// Writes `task` into the specified `index`.
    ///
    /// This method might be concurrently called with another `read` at the same index, which is
    /// technically speaking a data race and therefore UB. We should use an atomic store here, but
    /// that would be more expensive and difficult to implement generically for all types `T`.
    /// Hence, as a hack, we use a volatile write instead.
    unsafe fn write(&self, index: isize, task: MaybeUninit<T>) {
        ptr::write_volatile(self.at(index).cast::<MaybeUninit<T>>(), task)
    }

    /// Reads a task from the specified `index`.
    ///
    /// This method might be concurrently called with another `write` at the same index, which is
    /// technically speaking a data race and therefore UB. We should use an atomic load here, but
    /// that would be more expensive and difficult to implement generically for all types `T`.
    /// Hence, as a hack, we use a volatile load instead.
    unsafe fn read(&self, index: isize) -> MaybeUninit<T> {
        ptr::read_volatile(self.at(index).cast::<MaybeUninit<T>>())
    }
}
```

对于`Send`是很明显的，显然是一个读安全的数据结构。这里的重点是使用了 volatile 而不是原子读写，在注释中我们可以得到最明显的答案是：极致的优化，优化指令数量。但是就不需要考虑安全性了吗？要回答这个问题需要看上层结构`Inner`:

```rust
/// Internal queue data shared between the worker and stealers.
struct Inner<T> {
    /// The front index.
    front: AtomicIsize,

    /// The back index.
    back: AtomicIsize,

    /// The underlying buffer.
    buffer: CachePadded<Atomic<Buffer<T>>>,
}

impl<T> Drop for Inner<T> {
    fn drop(&mut self) {
        // Load the back index, front index, and buffer.
        let b = *self.back.get_mut();
        let f = *self.front.get_mut();

        unsafe {
            let buffer = self.buffer.load(Ordering::Relaxed, epoch::unprotected());

            // Go through the buffer from front to back and drop all tasks in the queue.
            let mut i = f;
            while i != b {
                buffer.deref().at(i).drop_in_place();
                i = i.wrapping_add(1);
            }

            // Free the memory allocated by the buffer.
            buffer.into_owned().into_box().dealloc();
        }
    }
}
```

可以看到，在这里 Buffer 被上了 Atomic 保证了读写原子性，这个很聪明：

1. Buffer 负责存储数据，而 Inner 负责管理 Buffer 和读写指针。这是一种责任分离的设计，使得 Buffer 可以更关注如何存储数据，而 Inner 则更关注如何高效地在多线程环境下协调读写操作。
2. Buffer 中的数据可能会被同时读写（在不同的 index），但是每一个特定 index 上的数据只会被一个线程读或写。这样，便可以通过 volatile 读写在 Buffer 中避免高昂的原子操作代价。
3. Inner 中的 front 和 back 指针则可能会被多个线程同时读写，因此必需要通过原子操作来保证其访问和修改的原子性。为了避免数据竞态，我们需要确保 front 和 back 指针的更新是原子的，也就是说，任何时候我们都可以看到它们的最新值。
4. Buffer 作为一个整体，可能需要在 Inner 中进行替换（例如在 resize 时）。这是需要原子操作的另一个地方，以保证内存安全和避免数据竞态。因此 Atomic<Buffer<T>> 是必要的。

这种设计充分地利用了 volatile 读写和原子操作各自的优点，并降低了性能的损失。