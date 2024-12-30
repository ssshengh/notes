## 基础使用

[`RecyclerView`](https://developer.android.com/develop/ui/views/layout/recyclerview?hl=zh-cn) 是一个 View 组件，可轻松高效地显示大量数据。`RecyclerView` 不会为数据集中的每个项创建视图，而是会保持一个较小的视图池并在滚动浏览这些项时重复利用视图，从而提高应用性能。

> 在 Compose 中，可以使用[延迟列表](https://developer.android.com/develop/ui/compose/lists?hl=zh-cn#lazy)(LazyList)来完成相同的操作。

### RecyclerView 基本使用

使用 RecyclerView 时，仅仅需要几个简单的步骤，首先是将其放入 XML 中：

```xml

<androidx.recyclerview.widget.RecyclerView-->
  android:id="@+id/recycler_view"
  android:layout_width="match_parent"
	android:layout_height="match_parent />
```

然后新建一个对应的字段以及