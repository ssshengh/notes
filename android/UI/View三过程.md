# View 三过程

View的三大流程依次为**measure(测量)—>layout(布局)—>draw(绘制)**。

> 总的过程：
>
> 结合activity的启动流程，activity对象被创建，然后经过create、start、resume阶段后，在resume中调用`wm.addView(decor, l)`。该方法中创建了`ViewRootImpl`对象，并调用ViewRootImpl的`setVIew(Decorview)`方法，之后调用`requestLayout`、`scheduleTraversals()`。
>
> `scheduleTraversals()`中首先发送了同步屏障消息，然后发送异步消息来出发三个流程，msg的`runnable`为`TraversalRunnable`，回调里调用了`doTraversal`，之后调用了`performTraversals`，然后又调用了`measureHierarchy`，最后调到`performMeasure`，`performLayout`，`performDraw`开始了三个流程测量、布局、绘制。

# WindowManager

## Window/WindowManager 创建与使用

> 参考及摘抄[文章](https://juejin.cn/post/7015978746104512548)

```kotlin
private fun showView() {
    //获取WindowManager实例，这里的App是继承自Application
    val wm = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    //设置LayoutParams属性
    val layoutParams = WindowManager.LayoutParams()
    layoutParams.height = 400
    layoutParams.width = 400
    layoutParams.format = PixelFormat.RGBA_8888

    //窗口标记属性
    layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

    //Window类型
    layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

    //构造TextView
    windowTv = TextView(this);
    windowTv?.background = ColorDrawable(Color.WHITE);
    windowTv?.text = "hello windowManager";

    //将textView添加到WindowManager
    wm.addView(windowTv, layoutParams);
    windowTvVisibility = true
}
```

上面代码分为三部分：

1. 获取WindowManager对象
2. 设置LayoutParams属性
3. 将View添加到Window里

### 获取 WindowManager 对象

application 是当前 app 的实例，本身是一个 context。而 WindowManager 是一个接口：

```kotlin
public interface WindowManager extends ViewManager
```

继承了ViewManager，ViewManager也是个接口:

```kotlin
/** Interface to let you add and remove child views to an Activity. To get an instance
  * of this class, call {@link android.content.Context#getSystemService(java.lang.String) Context.getSystemService()}.
  */
public interface ViewManager
{
    /**
     * Assign the passed LayoutParams to the passed View and add the view to the window.
     * <p>Throws {@link android.view.WindowManager.BadTokenException} for certain programming
     * errors, such as adding a second view to a window without removing the first view.
     * <p>Throws {@link android.view.WindowManager.InvalidDisplayException} if the window is on a
     * secondary {@link Display} and the specified display can't be found
     * (see {@link android.app.Presentation}).
     * @param view The view to be added to this window.
     * @param params The LayoutParams to assign to view.
     */
  	// 添加View， view 表示内容本身，params表示对此view位置、大小等属性的限制
    public void addView(View view, ViewGroup.LayoutParams params);
  	// 更新 View
    public void updateViewLayout(View view, ViewGroup.LayoutParams params);
  	// 移除 View
    public void removeView(View view);
}
```

WindowManager是个接口，那么必然有实现它的类，在getSystemService(Context.WINDOW_SERVICE)里有所体现：

```kotlin
ContextImpl.java
    @Override
    public Object getSystemService(String name) {
        return SystemServiceRegistry.getSystemService(this, name);
    }

SystemServiceRegistry.java
    public static Object getSystemService(ContextImpl ctx, String name) {
        ServiceFetcher<?> fetcher = SYSTEM_SERVICE_FETCHERS.get(name);
        return fetcher != null ? fetcher.getService(ctx) : null;
    }

    registerService(Context.WINDOW_SERVICE, WindowManager .class,
                new CachedServiceFetcher<WindowManager>() {
        @Override
        public WindowManager createService (ContextImpl ctx){
            return new WindowManagerImpl(ctx);
        }
    });

```

可以看到 windowManager 是由 WindowManagerImpl 实现的：

```kotlin
public final class WindowManagerImpl implements WindowManager {
    //WindowManagerImpl 代理类 WindowManagerGlobal单例
    private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();
    //getSystemService传进来的Context
    private final Context mContext;
    //记录构造WindowManager父Window
    private final Window mParentWindow;
    //关联Activity时会赋值
    private IBinder mDefaultToken;

    public WindowManagerImpl(Context context) {
        this(context, null);
    }

    private WindowManagerImpl(Context context, Window parentWindow) {
        mContext = context;
        mParentWindow = parentWindow;
    }

    public WindowManagerImpl createLocalWindowManager(Window parentWindow) {
        return new WindowManagerImpl(mContext, parentWindow);
    }
    //省略
}
```

而实际上的 add、update、remove 如下，是 WindowManagerGlobal 实现的：

![image-20230818111506836](/Users/bytedance/Library/Application Support/typora-user-images/image-20230818111506836.png)

WindowManagerGlobal记录着该App内所有展示的Window一些相关信息

### 设置LayoutParams属性

WindowManager.LayoutParams 继承自ViewGroup.LayoutParams，来看看一些我们关注的属性：

> width : 指定Window的宽度
> height : 指定Window的高度
> x : Window在屏幕X轴的偏移（偏移的起点是gravity设置的位置）
> y : Window在屏幕Y轴的偏移（偏移的起点是gravity设置的位置）
> flags ：控制Window一些行为，比如能否让下层的Window获得点击事件，Window能否超出屏幕展示等
> type ：Window类型，分为三种：
> 	FIRST_APPLICATION_WINDOW ~ LAST_APPLICATION_WINDOW（1~99）应用窗口
> 	FIRST_SUB_WINDOW ~ LAST_SUB_WINDOW （1000 ~ 1999）子窗口
> 	FIRST_SYSTEM_WINDOW ~ LAST_SYSTEM_WINDOW （2000 ~ 2999）系统窗口
> 数值越大，层级越高，也就是层级越高的就能显示在层级低的上边。
> gravity : Window的位置，取值自Gravity
> windowAnimations : Window动画

### 将View添加到Window里

添加 View 的源码在：

```kotlin
WindowManagerGlobal.java
    public void addView(View view, ViewGroup.LayoutParams params,
                        Display display, Window parentWindow) {
        
        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;
        if (parentWindow != null) {
            //调整LayoutParams
            parentWindow.adjustLayoutParamsForSubWindow(wparams);
        } else {
            //省略
        }

        ViewRootImpl root;
        View panelParentView = null;
        synchronized (mLock) {
            //构造ViewRootImpl, 注意, window 的 root 是 ViewRootImpl
            root = new ViewRootImpl(view.getContext(), display);
            view.setLayoutParams(wparams);
            //用数组记录
            //mViews 存放添加到Window的view
            //mRoots 存放ViewRootImpl
            //mParams 存放Window参数
            mViews.add(view);
            mRoots.add(root);
            mParams.add(wparams);
            try {
                //调用ViewRootImpl setView
                root.setView(view, wparams, panelParentView);
            } catch (RuntimeException e) {
            }
        }
        //省略
    }
```

可以看到`WindowManagerGlobal`创建了一个`ViewRootImpl`，而其中有着多个`ViewRootImpl`组成的数组，而特定的`ViewRootImpl`才是最终执行添加 View 的:

```kotlin
ViewRootImpl.java
    public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
        synchronized (this) {
            if (mView == null) {
                mView = view;
                //省略
                int res;
                //提交View展示请求(测量、布局、绘制），只是提交到队列里
                //当屏幕刷新信号到来之时从队列取出执行
                requestLayout();
                try {
                    //添加到窗口
                    //进程间通信，告诉WindowManagerService为我们开辟一个Window空间
                    res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                            getHostVisibility(), mDisplay.getDisplayId(), mTmpFrame,
                            mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                            mAttachInfo.mOutsets, mAttachInfo.mDisplayCutout, mInputChannel,
                            mTempInsets);
                } catch (RemoteException e) {
                } finally {
                }
                if (res < WindowManagerGlobal.ADD_OKAY) {
                    //窗口添加失败抛出各种异常
                }
                //Window根View的mParent是ViewRootImpl 而其他View的mParent是其父控件
                //这参数是向上遍历View Tree的关键
                view.assignParent(this);
                //输入事件相关 touch、key事件接收
                CharSequence counterSuffix = attrs.getTitle();
                mSyntheticInputStage = new SyntheticInputStage();
                InputStage viewPostImeStage = new ViewPostImeInputStage(mSyntheticInputStage);
                InputStage nativePostImeStage = new NativePostImeInputStage(viewPostImeStage,
                        "aq:native-post-ime:" + counterSuffix);
                InputStage earlyPostImeStage = new EarlyPostImeInputStage(nativePostImeStage);
                InputStage imeStage = new ImeInputStage(earlyPostImeStage,
                        "aq:ime:" + counterSuffix);
                InputStage viewPreImeStage = new ViewPreImeInputStage(imeStage);
                InputStage nativePreImeStage = new NativePreImeInputStage(viewPreImeStage,
                        "aq:native-pre-ime:" + counterSuffix);
            }
        }
    }

```

使用Binder方式，ViewRootImpl与WindowManagerService建立Session进行通信，我们可以通过 mWindowSession.addToDisplay 简单来看看后续调用：

```kotlin
Session.java
    public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
            int viewVisibility, int displayId, Rect outFrame, Rect outContentInsets,
            Rect outStableInsets, Rect outOutsets,
            DisplayCutout.ParcelableWrapper outDisplayCutout, InputChannel outInputChannel,
            InsetsState outInsetsState) {
        return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId, outFrame,
                outContentInsets, outStableInsets, outOutsets, outDisplayCutout, outInputChannel,
                outInsetsState);
    }


WindowManagerService.java
    public int addWindow(Session session, IWindow client, int seq,
            LayoutParams attrs, int viewVisibility, int displayId, Rect outFrame,
            Rect outContentInsets, Rect outStableInsets, Rect outOutsets,
            DisplayCutout.ParcelableWrapper outDisplayCutout, InputChannel outInputChannel,
            InsetsState outInsetsState) {
            //省略
}
```

### WindowManager.LayoutParams flag 属性

Activity 本质上也是通过 WindowManager 来添加的，此时我们在其上加了一个新的 Window，就引入了一个问题：key/touch 事件如何进行分发的？

![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/8e714f2c09754c35aaf0d6d65619ec1a~tplv-k3u1fbpfcp-zoom-in-crop-mark:1512:0:0:0.awebp)

如上图所示，Window2 是在Window1之上，层级比Window1高，决定Window2 key/touch事件是否分发给Window1取决于WindowManager.LayoutParams flag 参数，flag默认为0。结合上图来看看一些常用的值及其作用，Window2 可以使用如下参数进行调整：

```
    public static final int FLAG_NOT_TOUCHABLE      = 0x00000010;
    public static final int FLAG_NOT_FOCUSABLE      = 0x00000008;
    public static final int FLAG_NOT_TOUCH_MODAL    = 0x00000020;
    public static final int FLAG_WATCH_OUTSIDE_TOUCH = 0x00040000;
    public static final int FLAG_ALT_FOCUSABLE_IM = 0x00020000;
```

flag 默认为0，不对flag设置时，Window2默认接受所有的touch/key 事件，即使点击区域不在Window2的范围内。

1. FLAG_NOT_TOUCHABLE

>  表示Window 不接收所有的touch事件。此时无论点击Window2 区域还是Window2之外的区域，touch事件都分发给了下一层Window1。而key事件则不受影响。

2. FLAG_NOT_FOCUSABLE

> 表示Window不接收输入焦点，不和键盘交互。比如当Window里使用editText时，是无法弹出键盘的。另外一个作用就是：当点击Window2之外的区域时，touch事件分发给了Window1，而点击Window2区域是分发给了其自身，key事件也不会分发给Window2，而是给了Window1（该作用相当于设置了FLAG_NOT_TOUCH_MODAL）。

3. FLAG_NOT_TOUCH_MODAL

> 表示当点击Window2之外的区域时，touch事件分发给了Window1，而key事件不受影响。当然此时Window2是能获取焦点的，能和键盘交互。

4. FLAG_WATCH_OUTSIDE_TOUCH

> 该值配合FLAG_NOT_TOUCH_MODAL才会生效。意思就是当设置了FLAG_NOT_TOUCH_MODAL时，点击Window2外部区域其收不到touch事件，但是这个时候Window2想要收到外部点击的事件，同时又不影响事件分发给Window1，此时FLAG_WATCH_OUTSIDE_TOUCH标记就发挥其作用了。此Window2接收到ACTION_OUTSIDE类型的事件，而touch事件(down/move/up)则分发给了Window1。key事件不受影响。

5. FLAG_ALT_FOCUSABLE_IM

> 与键盘相关。当FLAG_NOT_FOCUSABLE没有设置且FLAG_ALT_FOCUSABLE_IM设置时，表示无需与键盘交互。当FLAG_NOT_FOCUSABLE/FLAG_ALT_FOCUSABLE_IM同时设置时，表示需要与输入法交互。FLAG_ALT_FOCUSABLE_IM单独设置时不影响touch/key 事件。



### View 如何与 Window 关联起来的

通过前面的分析，并没有发现View和Window的直接关联，那么View的内容怎么显示在Window上的呢？

#### surface 与 canvas

平时我们都是重写View onDraw(Canvas canvas)，通过Canvas绘制我们想要的效果，来看看Canvas是怎么来的。**对于软件绘制**：

```kotlin
ViewRootImpl.java
  public final Surface mSurface = new Surface();
  final Canvas canvas = mSurface.lockCanvas(dirty);
```

可以看到Canvas是从Surface获取的，那自然想到Surface和Window是否有关系呢？

```kotlin
ViewRootImpl.java
    private int relayoutWindow(WindowManager.LayoutParams params, int viewVisibility,
                               boolean insetsPending) throws RemoteException {
        //省略
        //传入SurfaceControl，在WindowManagerService里处理
        int relayoutResult = mWindowSession.relayout(mWindow, mSeq, params,
                (int) (mView.getMeasuredWidth() * appScale + 0.5f),
                (int) (mView.getMeasuredHeight() * appScale + 0.5f), viewVisibility,
                insetsPending ? WindowManagerGlobal.RELAYOUT_INSETS_PENDING : 0, frameNumber,
                mTmpFrame, mPendingOverscanInsets, mPendingContentInsets, mPendingVisibleInsets,
                mPendingStableInsets, mPendingOutsets, mPendingBackDropFrame, mPendingDisplayCutout,
                                             // 在这
                mPendingMergedConfiguration, mSurfaceControl, mTempInsets);
        if (mSurfaceControl.isValid()) {
            //返回App层的Surface
            mSurface.copyFrom(mSurfaceControl);
        } else {
            destroySurface();
        }
        //省略
        return relayoutResult;
    }
```

在View开启ViewTree三大流程时，performTraversals->relayoutWindow，将Window与SurfaceControl关联，进而关联Surface。这样，Window->Surface->Canvas就关联起来了，通过Canvas将View绘制到Surface上，最终显示出来。

**而对于硬件加速来说**，每个View都有RenderNode,绘制该View的Canvas通过beginRecording获取，Canvas绘制的操作封装在DisplayList:

```kotlin
RenderNode.java
    public @NonNull RecordingCanvas beginRecording(int width, int height) {
        if (mCurrentRecordingCanvas != null) {
            throw new IllegalStateException(
                    "Recording currently in progress - missing #endRecording() call?");
        }
        mCurrentRecordingCanvas = RecordingCanvas.obtain(this, width, height);
        return mCurrentRecordingCanvas;
    }

```

然后在在ViewRootImpl->performTraversals中：

```kotlin
hwInitialized = mAttachInfo.mThreadedRenderer.initialize(
                    mSurface);
```

建立ThreadedRenderer和Surface关联，而ThreadedRenderer里持有：

```kotlin
protected RenderNode mRootNode;
```

该mRootNode是整个ViewTree的根node。这样Surface和Canvas建立了关联。
用图表示View、Window、Surface关系：

![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/9a85630540ec4587865febabced5f947~tplv-k3u1fbpfcp-zoom-in-crop-mark:1512:0:0:0.awebp)

Window内容是通过Surface展示，而SurfaceFlinger将多个Surface合成显示在屏幕上。

# View 的measure、layout、draw三大流程

从上面可以知道，window 在添加 View 之后创建了 ViewRootImpl 对象，然后 ViewRootImpl 在`setView(Decorview)`过程中调用了`requestLayout()`注册了回调，当屏幕刷新信号到来之时执行`performTraversals()`开启三大流程。

对于一个简单的布局：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
   android:id="@+id/layout"
   android:layout_width="match_parent"
   android:layout_height="match_parent">

   <FrameLayout
       android:id="@+id/layout1"
       android:layout_width="match_parent"
       android:layout_height="300dp">

       <TextView
           android:id="@+id/text"
           android:layout_width="wrap_content"
           android:layout_height="wrap_content"
           android:text="Hello World" />
   </FrameLayout>

   <Button
       android:id="@+id/button"
       android:layout_width="wrap_content"
       android:layout_height="wrap_content"
       android:text="我是按钮" />

</FrameLayout>
```

## measure

其 measure 过程大致为：

`ViewRootImpl#performMeasure -> DecorView#measure（实际是View#measure）->View#onMeasure（实际是DecorView#onMeasure）-> FrameLayout#onMeasure-> 子View#measure。`

因此可以这么来表示：

```kotlin
R.id.layout 的measure被执行
R.id.layout 的onMeasure被执行
    R.id.layout1 的measure被执行
    R.id.layout1 的onMeasure被执行
            R.id.text 的measure被执行
            R.id.text 的onMeasure被执行
            R.id.text 的setMeasuredDimension被执行
		// 父布局的尺寸最后在这里设置，作为 measure 的最后一步
    R.id.layout1 的setMeasuredDimension被执行
    R.id.button 的measure被执行
    R.id.button 的onMeasure被执行
    R.id.button 的setMeasuredDimension被执行
// 父布局的尺寸最后在这里设置，作为 measure 的最后一步
R.id.layout 的setMeasuredDimension被执行
```

需要注意：因为父View需要子View测量后的尺寸来设置自己的尺寸，因此父view的setMeasuredDimension总是比子View的晚。

安卓对子 View 的计算抽象为了一个对象`MeasureSpec`，该对象由模式和尺寸组成，通过使用二进制，将mode和size打包成一个int值。一个int型有32位，其中31和32两位表示mode，前面30位表示size。 MeasureSpec类用一个变量携带两个数据（size，mode）来减少对象内存分配，并提供了打包和解包的方法，

onMeasure调用child.measure一般如下：
`onMeasure -> measureChildWithMargins -> getChildMeasureSpec -> child.measure`

关键看getChildMeasureSpec方法，里面的switch-case 和 if-else 可以汇总成下面的表:

| 横：父View测量模式 纵：子View的LayoutParms | EXACTLY（精确）        | AT_MOST（至多） | UNSPECIFIED (未指明) |
| ------------------------------------------ | ---------------------- | --------------- | -------------------- |
| 具体数值                                   | EXACTLY+childDimension | EXACTLY+size    | EXACTLY+size         |
| match_parent                               | EXACTLY+size           | AT_MOST+size    | UNSPECIFIED+0        |
| wrap_content                               | AT_MOST+size           | AT_MOST+size    | UNSPECIFIED+0        |

> *size为父View的测量尺寸-子View的内外边距，与0取最大值*

子View得到宽高的MeasureSpec后，会根据自己的特性进行计算，默认的计算方式如下：

```kotlin
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
            getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
}
    
    
public static int getDefaultSize(int size, int measureSpec) {
    int result = size;
    int specMode = MeasureSpec.getMode(measureSpec);
    int specSize = MeasureSpec.getSize(measureSpec);

    switch (specMode) {
    case MeasureSpec.UNSPECIFIED:
        result = size;
        break;
    case MeasureSpec.AT_MOST:
    case MeasureSpec.EXACTLY:
        result = specSize;
        break;
    }
    return result;
}
```

getSuggestedMinimumWidth根据最小宽和背景计算，哪个大用哪个，因此当测量模式是UNSPECIFIED，就是根据这个建议的尺寸，另外两个模式时，则使用测量尺寸（见上方表格）

## layout

ViewRootImpl#performLayout执行流程：

`DecorView#layout（实际是ViewGroup#layout）->View#layout->DecorView#onLayout->FrameLayout#onLayout`

对于上面的布局，其 layout 过程如下：

```
R.id.layout 的layout被执行
R.id.layout 的setFrame被执行
R.id.layout 的sizeChange被执行
R.id.layout 的onLayout被执行
    R.id.layout1 的layout被执行
    R.id.layout1 的setFrame被执行
    R.id.layout1 的sizeChange被执行
    R.id.layout1 的onLayout被执行
            R.id.text 的layout被执行
            R.id.text 的setFrame被执行
            R.id.text 的sizeChange被执行
            R.id.text 的onLayout被执行
            R.id.text 的onLayoutChange()被执行
    R.id.layout1 的onLayoutChange()被执行
    R.id.button 的layout被执行
    R.id.button 的setFrame被执行
    R.id.button 的sizeChange被执行
    R.id.button 的onLayout被执行
    R.id.button 的onLayoutChange()被执行
R.id.layout 的onLayoutChange()被执行
```

layout->setFrame（保存l、t、r、b）->sizeChange（宽高变化）->onLayout->onLayoutChange()

**触发 onMeasure 后会标记PFLAG_LAYOUT_REQUIRED，因此必会触发onLayout。**

## draw

ViewRootImpl#performDraw执行流程：

`DecorView#draw（实际是View#draw）->View#drawBackground画背景->View#onDraw->View#dispatchDraw（实际上子View#draw）->View#onDrawForeground画前景`

performDraw到view的draw还需要判断PFLAG_INVALIDATE。基于上面的布局，draw中重要的方法调用如下：

```
R.id.layout 的draw被执行
R.id.layout 的drawBackground被执行
R.id.layout 的onDraw被执行
R.id.layout 的dispatchDraw被执行
   R.id.layout1 的draw被执行
   R.id.layout1 的drawBackground被执行
   R.id.layout1 的onDraw被执行
   R.id.layout1 的dispatchDraw被执行
           R.id.text 的draw被执行
           R.id.text 的drawBackground被执行
           R.id.text 的onDraw被执行
           R.id.text 的dispatchDraw被执行
           R.id.text 的onDrawForeground被执行
   R.id.layout1 的onDrawForeground被执行
   R.id.button 的draw被执行
   R.id.button 的drawBackground被执行
   R.id.button 的onDraw被执行
   R.id.button 的dispatchDraw被执行
   R.id.button 的onDrawForeground被执行
R.id.layout 的onDrawForeground被执行
```

## 三过程之前的 requestLayout 及最后的 invalidate

### requestLayout

调用某个View的requestLayout，会不断调用parent的requestLayout，最终调到ViewRootImp的requestLayout。

```java
View#requestLayout
    public void requestLayout() {
        ……
        //添加了PFLAG_FORCE_LAYOUT的标记
        mPrivateFlags |= PFLAG_FORCE_LAYOUT;
        //添加了PFLAG_INVALIDATED的标记
        mPrivateFlags |= PFLAG_INVALIDATED;

        if (mParent != null && !mParent.isLayoutRequested()) {
            mParent.requestLayout();
        }
        ……
    }

ViewRootImp#requestLayout
    public void requestLayout() {
        if (!mHandlingLayoutInLayoutRequest) {
            checkThread();
          	// 在这里设置了 mLayoutRequested
            mLayoutRequested = true;
            scheduleTraversals();
        }
    }
```

在View的requestLayout方法中，会给View设置PFLAG_FORCE_LAYOUT，这个flag会让View的measure方法中的forceLayout为true，从而触发onMeasure测量。而测量后会设置上PFLAG_LAYOUT_REQUIRED，这个flag会让View触发onLayout：

```java
public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
    ……
    // 在这里判断的
    final boolean forceLayout = (mPrivateFlags & PFLAG_FORCE_LAYOUT) == PFLAG_FORCE_LAYOUT;
    ……
    if (forceLayout || needsLayout) {
        // first clears the measured dimension flag
        ……
            onMeasure(widthMeasureSpec, heightMeasureSpec);
        ……
        mPrivateFlags |= PFLAG_LAYOUT_REQUIRED;
    }
    ……
}


public void layout(int l, int t, int r, int b) {
    ……
		// 在这里判断
    if (changed || (mPrivateFlags & PFLAG_LAYOUT_REQUIRED) == PFLAG_LAYOUT_REQUIRED) {
        onLayout(changed, l, t, r, b);
        ……
    }
……
}
```

在ViewRootImp的requestLayout方法中，设置mLayoutRequested为true，在performTraversals时候会用mLayoutRequested来判断是否调用measureHierarchy，该方法会触发performMeasure、performLayout、performDraw。

performMeasure、performLayout会执行onMeasure和onLayout。而在performDraw内部draw的过程中发现mDirty为空，所以所有View的draw不会被调用。

```java
//ViewRootImp#draw
private boolean draw(boolean fullRedrawNeeded) {
    ……
    //requestLayout时，dirty为empty，无法触发view的draw方法
    if (!dirty.isEmpty() || mIsAnimating || accessibilityFocusDirty) {
        ……
    }
    ……
}
```

此外，在layout方法调用中，会调用setFrame，该方法有个逻辑会触发invalidate，**invalidate会触发onDraw**，因此可以说requestLayout只有当某个view的l,t,r,b发生改变时，才会触发invalidate，从而触发onDraw。

requestLayout是不断往上调用的，因此子view没有机会标记PFLAG_FORCE_LAYOUT，所以不会用forceLayout为true来判断是否要测量，而是走needsLayout的判断条件。

```java
final boolean specChanged = widthMeasureSpec != mOldWidthMeasureSpec || heightMeasureSpec != mOldHeightMeasureSpec;
final boolean isSpecExactly = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;
final boolean matchesSpecSize = getMeasuredWidth() == MeasureSpec.getSize(widthMeasureSpec) && getMeasuredHeight() == MeasureSpec.getSize(heightMeasureSpec);
final boolean needsLayout = specChanged && (sAlwaysRemeasureExactly || !isSpecExactly || !matchesSpecSize);
```

根据上面的代码：

1. specChanged 表示宽或高的测量规格发生变化，可以理解测量规格发生变化是needsLayout的前提。
2. 后面还跟着三个条件，满足其一即可：
   - 版本小于等于6.0
   - 该view的宽或高的测量规格不是精确的
   - 该view的宽或高的测量尺寸发生了变化

### invalidate

invalidate相对于requestLayout会比较复杂，调用某个View的invalidate()，内部调用invalidateInternal来修改标记，然后调用 parent的invalidateChild(this, damage);

```kotlin
void invalidateInternal(int l, int t, int r, int b, boolean invalidateCache,
            boolean fullInvalidate) {
    ……
        //添加 PFLAG_DIRTY
        mPrivateFlags |= PFLAG_DIRTY;
    
        if (invalidateCache) {
            //添加 PFLAG_INVALIDATED，只有该View加了这个标记，parent都没有加
            mPrivateFlags |= PFLAG_INVALIDATED;
            //移除 PFLAG_DRAWING_CACHE_VALID
            mPrivateFlags &= ~PFLAG_DRAWING_CACHE_VALID;
        } 
    ……
            p.invalidateChild(this, damage);
    ……
}
```

invalidateChild内部有个do while循环，不停调parent的invalidateChildInParent，一直到调用ViewRootImpl的invalidateChildInParent。

```java
public final void invalidateChild(View child, final Rect dirty) {
    ……
        if (child.mLayerType != LAYER_TYPE_NONE) {
            //添加 PFLAG_INVALIDATED
            mPrivateFlags |= PFLAG_INVALIDATED;
            //移除 PFLAG_DRAWING_CACHE_VALID
            mPrivateFlags &= ~PFLAG_DRAWING_CACHE_VALID;
        }
        ……
        do {
            ……
            if (view != null) {
                if ((view.mPrivateFlags & PFLAG_DIRTY_MASK) != PFLAG_DIRTY) {
                    //mPrivateFlags 移除PFLAG_DIRTY_MASK，添加PFLAG_DIRTY
                    view.mPrivateFlags = (view.mPrivateFlags & ~PFLAG_DIRTY_MASK) | PFLAG_DIRTY;
                }
            }
                
            parent = parent.invalidateChildInParent(location, dirty);
            ……
        } while (parent != null);
    }
}

ViewGroup#invalidateChildInParent
    public ViewParent invalidateChildInParent(final int[] location, final Rect dirty) {
        // mPrivateFlags 包含 PFLAG_DRAWN 或者 PFLAG_DRAWING_CACHE_VALID的标记
        if ((mPrivateFlags & (PFLAG_DRAWN | PFLAG_DRAWING_CACHE_VALID)) != 0) {
            //省去计算dirty，硬件渲染时不需要用到dirty
            ……
            //移除 PFLAG_DRAWING_CACHE_VALID
            mPrivateFlags &= ~PFLAG_DRAWING_CACHE_VALID;
            ////未设置LayerType，因此未添加 PFLAG_INVALIDATED
            if (mLayerType != LAYER_TYPE_NONE) {
                mPrivateFlags |= PFLAG_INVALIDATED;
            }
            return mParent;
        }
        return null;
    }
```

ViewGroup的invalidateChildInParent方法主要是计算了dirty，移除了PFLAG_DRAWING_CACHE_VALID，注意是没有添加了PFLAG_INVALIDATED。

ViewRootImpl的invalidateChildInParent内部调用了invalidateRectOnScreen，之后调用scheduleTraversals，进而 performDraw->draw，mDirty非空就会调mAttachInfo.mThreadedRenderer.draw(mView, mAttachInfo, this);最终调到mThreadedRenderer的updateViewTreeDisplayList

```kotlin
private void updateViewTreeDisplayList(View view) {
    //添加了 View.PFLAG_DRAWN;
    view.mPrivateFlags |= View.PFLAG_DRAWN;
    //判断是否有 PFLAG_INVALIDATED， 有的话为true
    view.mRecreateDisplayList = (view.mPrivateFlags & View.PFLAG_INVALIDATED)== View.PFLAG_INVALIDATED;
    //移除PFLAG_INVALIDATED
    view.mPrivateFlags &= ~View.PFLAG_INVALIDATED;
    //调了view的updateDisplayListIfDirty
    view.updateDisplayListIfDirty();
    view.mRecreateDisplayList = false;
}
```

View中的updateDisplayListIfDirty();

```java
public RenderNode updateDisplayListIfDirty() {
    ……

    if ((mPrivateFlags & PFLAG_DRAWING_CACHE_VALID) == 0
            || !renderNode.hasDisplayList()
            || (mRecreateDisplayList)) {
        // 只有添加了PFLAG_INVALIDATED标记的mRecreateDisplayList为true
        if (renderNode.hasDisplayList()
                && !mRecreateDisplayList) {
            mPrivateFlags |= PFLAG_DRAWN | PFLAG_DRAWING_CACHE_VALID;
            mPrivateFlags &= ~PFLAG_DIRTY_MASK;
            dispatchGetDisplayList();

            return renderNode; // no work needed
        }
        //mRecreateDisplayList为true才有机会走到下面的draw
        ……
                    draw(canvas);
        ……
    return renderNode;
}
```

ViewGroup中的dispatchGetDisplayList,主要就是for循环调用recreateChildDisplayList，recreateChildDisplayList调用child.updateDisplayListIfDirty(); 如此不断往child走，直到遇到有PFLAG_INVALIDATED标记的view（即被调invalidate的那个view），才开始调用draw。

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/606818ec76c14934bdd78e624f91330e~tplv-k3u1fbpfcp-zoom-in-crop-mark:1512:0:0:0.image)

## 其他问题

#### 调用两次requestLayout，会导致流程执行两遍吗？

requestLayout会调到scheduleTraversals，scheduleTraversals使用了标记字段（mTraversalScheduled）控制， 如果上一个scheduleTraversals发出的消息被执行，mTraversalScheduled会置为false，那么第二次requestLayout会生效。如果上一个scheduleTraversals发出的消息没有被执行，那么第二次requestLayout不会生效；生效与否通过标记位（mTraversalScheduled）控制。

#### 可以在非主线程更新UI吗？

在Activity启动流程的resume阶段及之前，通过异步线程进行某些UI操作来调用requestLayout时，因为parent那时还没分分配，所以不会调到ViewRootImpl，也就不会调到requestLayout里的checkThread()，所以不会抛出异常。checkThread里判断了当前现场和创建ViewRootImpl的线程是否是同一个线程。

#### getMeasureWidth()和getWidth()的区别？

- getMeasureWidth()是在measure后有值，getWidth()是在layout后有值；
- View#getWidth()通过mRight - mLeft计算而来，View#layout方法为public，可以通过自定义参数修改mRight、mLeft的值，导致getMeasureWidth()和getWidth()获得的值不一致。

#### onDraw在什么时候情况下不会被调用？如何让它调用？

在draw方法中，判断是透明，就不再走onDraw方法。在ViewGroup初始化的时候，它调用了一个私有方法：initViewGroup，它里面会有一句setFlags(WILLL_NOT_DRAW，DRAW_MASK)；相当于调用了setWillNotDraw(true)，所以说，对于ViewGroup，它就认为是透明的了。所以ViewGroup 一般不会绘制自身，只会绘制子 View，所以不会回调 onDraw()，这也是出于性能和效率的考虑。 如果希望调用，则需要存在背景或者需要在初始化时候手动调用setWillNotDraw(false)。

# 一个 measure 的例子

> 参考：https://juejin.cn/post/6893699917458604046

例如一个 tagLayout 的例子：

```
package com.example.littletest

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import java.util.logging.Logger

class TagLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TagLayout"
    }

    // 设置view 支持 layout margin 属性
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    private var childBoundsList = mutableListOf<Rect>()


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        //用了多少宽度
        var widthUsed = 0
        //用了多少高度
        var heightUsed = 0

        //每一行用了多少宽度
        var lineWidthUsed = 0
        //每一行用了多少高度
        var lineHeight = 0
        //取出自己的宽度限制
        var widthMode = MeasureSpec.getMode(widthMeasureSpec)
        var widthSize = MeasureSpec.getSize(widthMeasureSpec)
        Log.i(TAG, "[------------------------] onMeasure start, ")

        if (childBoundsList.isEmpty()) {
            for (index in 0 until childCount) {
                childBoundsList.add(Rect())
            }
        }
        for (x in 0 until childCount) {
            val child = getChildAt(x)
            Log.i(TAG, "[----] 1. in child=$child, widthMode=$widthMode, widthSize=$widthSize")
            // 先测量一次 算一下这个child的 measureWidth，谷歌提供的方案
            measureChildWithMargins(
                child,
                widthMeasureSpec,
                0,
                heightMeasureSpec,
                heightUsed
            )
            Log.i(TAG, "[----] 2. in child=$child, measuredWidth=${child.measuredWidth}")
            // 如果算出来的 宽度 比自己的宽度还大那就要重新测量 准备换行
            if (widthMode != MeasureSpec.UNSPECIFIED && lineWidthUsed + child.measuredWidth > widthSize) {
                // 既然是重新测量了 那显然 每行已经用掉的宽度就是0了
                lineWidthUsed = 0
                // 计算一下 已经用了多少高度了 因为既然换行了 heightUsed 就要增加了
                heightUsed += lineHeight
                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    0,
                    heightMeasureSpec,
                    heightUsed
                )
            }
            //测量结束以后开始设置 bounds
            val childBounds = childBoundsList[x]

            //起点的left和top 很好理解 就是 这一行 已经用了多少 你就从这个位置开 layout
            // right和bottom 也就是加上自己的宽高 即可
            childBounds.set(
                lineWidthUsed,
                heightUsed,
                lineWidthUsed + child.measuredWidth,
                heightUsed + child.measuredHeight
            )
            Log.i(TAG, "[----] 3. in child=$child, childBounds=${childBoundsList[x]}")
            Log.i(TAG, "[----] left=${childBounds.left}, right=${childBounds.right}, top=${childBounds.top}, bottom=${childBounds.bottom}")

            //每一行已经用的 当然是加上这个child的宽度
            lineWidthUsed += child.measuredWidth
            //计算一下最大宽度 到时候自己要用
            widthUsed = Math.max(lineWidthUsed, widthUsed)
            //每一行的高度 就等于这一行里面 高度最大的那个view
            lineHeight = Math.max(lineHeight, child.measuredHeight)
            Log.i(TAG, "[----] 4. in child=$child, widthUsed=$widthUsed, lineHeight=${lineHeight}")
        }

        //子view 都算出来了 那我自己也肯定就算出来了吧
        val measureWidth = widthUsed
        val measureHeight = (heightUsed + lineHeight)
        Log.i(TAG, "in father, measureWidth=$measureWidth, measureHeight=$measureHeight")
        //算完了以后 直接调用这个方法 到这里测量就全部结束了
        setMeasuredDimension(measureWidth, measureHeight)

        Log.i(TAG, "[------------------------] onMeasure end")
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childBoundsList.isNotEmpty()) {
            for (index in 0 until childCount step 1) {
                val child = getChildAt(index)
                val childBounds = childBoundsList[index]
                child.layout(
                    childBounds.left,
                    childBounds.top,
                    childBounds.right,
                    childBounds.bottom
                )
            }
        }
    }
}
```

在 xml 中这么写：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity"
    android:background="@color/material_dynamic_neutral30"
    >

    <TextView
        android:id="@+id/main_tv"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        android:background="@color/white"
        />

    <Button
        android:id="@+id/button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="显示悬浮窗"
        app:layout_constraintVertical_bias="0"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        />

    <Button
        android:id="@+id/to_second_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="跳到另一个 activity"
        app:layout_constraintVertical_bias="0"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        />

    <Button
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="开启 taglayout"
        app:layout_constraintVertical_bias="0.8"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        />

    <com.example.littletest.TagLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="@color/cardview_light_background"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@+id/main_tv"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        >

        <TextView
            android:layout_width="100dp"
            android:layout_height="100dp"
            android:text="nihao o!"
            android:gravity="center"
            android:background="@color/cardview_shadow_start_color"
            android:textColor="@color/white"
            />

        <TextView
            android:layout_width="100dp"
            android:layout_height="100dp"
            android:text="hello world!"
            android:gravity="center"
            android:background="@color/cardview_shadow_start_color"
            android:textColor="@color/white"
            />


    </com.example.littletest.TagLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

可以得到结果：

![image-20230818171332670](/Users/bytedance/Library/Application Support/typora-user-images/image-20230818171332670.png)
