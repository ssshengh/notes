# View 三过程

View的三大流程依次为**measure(测量)—>layout(布局)—>draw(绘制)**。

> 总的过程：
>
> 结合activity的启动流程，activity对象被创建，然后经过create、start、resume阶段后，在resume中调用`wm.addView(decor, l)`。该方法中创建了`ViewRootImpl`对象，并调用ViewRootImpl的`setVIew(Decorview)`方法，之后调用`requestLayout`、`scheduleTraversals()`。
>
> `scheduleTraversals()`中首先发送了同步屏障消息，然后发送异步消息来出发三个流程，msg的`runnable`为`TraversalRunnable`，回调里调用了`doTraversal`，之后调用了`performTraversals`，然后又调用了`measureHierarchy`，最后调到`performMeasure`，`performLayout`，`performDraw`开始了三个流程测量、布局、绘制。

# WindowManager

## Window/WindowManager 创建与使用

