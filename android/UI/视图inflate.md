# 视图 inflate 过程



在开发中，对于 `LayoutInflater` 的 `inflate()` 方法，它的作用是把 xml 布局转换为对应的 `View` 对象，我们几乎天天在用。但是，对于 `inflate()` 方法的参数，是比较令人迷惑的。即便是看了文档的解释，依然不能解开迷惑。其实最关键的问题是：

- `ViewGroup root` 参数的作用是什么，为什么有时候可以传 `null`，有时候却不可以？
- `boolean attachToRoot` 参数什么时候传 `true`，什么时候传 `false`？为什么有时候传递 `true` 会崩溃？
- 为什么有的时候 xml 中根节点设置的布局参数却不生效？

而测量过程也是如此，`onMesure`函数以及`mesure`函数几乎是我们尽可能不去使用的函数。

# inflate

> 参考自：大佬的[文章](https://blog.csdn.net/willway_wang/article/details/107879127)

在 `LayoutInflater` 类中，有几个重载的 `inflate()` 方法：

```java
public View inflate(@LayoutRes int resource, @Nullable ViewGroup root)
public View inflate(@LayoutRes int resource, @Nullable ViewGroup root, boolean attachToRoot)
public View inflate(XmlPullParser parser, @Nullable ViewGroup root)
public View inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot)
```

大部分情况下使用的是前两个，但是从调用图(如下)来看，都是走到了最后一个：![img](https://img-blog.csdnimg.cn/20200808145549429.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dpbGx3YXlfd2FuZw==,size_16,color_FFFFFF,t_70)

因此我们核心是去看最后一个函数的实现。

## 函数结构

```java
public View inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot)
```

> | `parser`       | `XmlPullParser`: XML dom node containing the description of the view hierarchy. |
> | -------------- | ------------------------------------------------------------ |
> | `root`         | `ViewGroup`: Optional view to be the parent of the generated hierarchy (if *attachToRoot* is true), or else simply an object that provides a set of LayoutParams values for root of the returned hierarchy (if *attachToRoot* is false.) This value may be `null`. |
> | `attachToRoot` | `boolean`: Whether the inflated hierarchy should be attached to the root parameter? If false, root is only used to create the correct subclass of LayoutParams for the root view in the XML. |

官方的文档中其实隐藏了较多的信息，我们能够很快的读懂其意思，但是不懂其含义。这里通过源码先进行一个简单的理解。

这个方法中的第一个参数 `XmlPullParser parser`，查看源码，可以看到：

```java
final Resources res = getContext().getResources();
XmlResourceParser parser = res.getLayout(resource);
```

是由 xml 转换而来的，用来对 xml 进行解析的一个类。

接着看后面的两个参数：`@Nullable ViewGroup root` 和 `boolean attachToRoot`。需要注意的是 `ViewGroup root` 前面有一个注解 `@Nullable`，表示 `ViewGroup root` 这个参数可以为 `null`。而这两个参数分别为“空”和“非空”，组合起来一共有四种情况。

为了理解，我们需要去查看一下 `inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot)` 方法的源码(去掉了 DEBUG 下的一些内容)：

```java
public View inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot) {
    synchronized (mConstructorArgs) {
        final Context inflaterContext = mContext;
        final AttributeSet attrs = Xml.asAttributeSet(parser);
      	
      	// 传入的 root 给到了 result！
        View result = root;
        advanceToRootNode(parser);
        // 获取根节点的名字，比如 LinearLayout, FrameLayout 等。
        final String name = parser.getName();
        
        if (TAG_MERGE.equals(name)) {
        		// 根节点的名字是 merge，对应 <merge> 布局，此时必须被 attach 到一个 root 上
            if (root == null || !attachToRoot) {
                throw new InflateException("<merge /> can be used only with a valid "
                        + "ViewGroup root and attachToRoot=true");
            }
            rInflate(parser, root, inflaterContext, attrs, false);
        } else {
            // Temp is the root view that was found in the xml
            // 获取 xml 布局的根 View 对象，比如 LinearLayout 对象，FrameLayout 对象等。
            final View temp = createViewFromTag(root, name, inflaterContext, attrs);
          
          	// 这里符合了官方文档的描述：root 可以提供一个 LayoutParams
            ViewGroup.LayoutParams params = null;
            if (root != null) {
                // Create layout params that match root, if supplied
                params = root.generateLayoutParams(attrs);
                if (!attachToRoot) {
                    // Set the layout params for temp if we are not
                    // attaching. (If we are, we use addView, below)
                    temp.setLayoutParams(params);
                }
            }
            // Inflate all children under temp against its context.
            rInflateChildren(parser, temp, attrs, true);
            // We are supposed to attach all the views we found (int temp)
            // to root. Do that now.
            if (root != null && attachToRoot) {
                root.addView(temp, params);
            }
            // Decide whether to return the root that was passed in or the
            // top view found in xml.
            if (root == null || !attachToRoot) {
                result = temp;
            }
        }
        return result;
    }
}

```

### case1: 根结点不为 merge，root 与 attachToRoot 均为 null

顺着来看：

1. `View result = root;` 把 root 的值赋值给 `View result`，此时 result 的值是 notNull。

2. `if (root != null)` 的判断语句判断为 true，能进入 if 语句。

3. `params = root.generateLayoutParams(attrs);`，通过 root 来获取**根节点的布局参数 ViewGroup.LayoutParams 对象**，也就是说，把 xml 中的根节点的 layout_ 开头的属性，如layout_width 和 layout_height 对应的值转为布局参数对象中的字段值，如width 和 height 值。对应的源码在 ViewGroup 中如下：

```java
public LayoutParams generateLayoutParams(AttributeSet attrs) {
    return new LayoutParams(getContext(), attrs);
}

public LayoutParams(Context c, AttributeSet attrs) {
    TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.ViewGroup_Layout);
    setBaseAttributes(a,
            R.styleable.ViewGroup_Layout_layout_width,
            R.styleable.ViewGroup_Layout_layout_height);
    a.recycle();
}
```

其中参数`attrs`从解析 xml 文件得到。这个方法被 ViewGroup 的子类重写后，还会解析 xml 中更多的布局参数，例如在 LinearLayout 中重写后，还会解析 `layout_weight` 和 `layout_gravity` 参数。

4. 接下来`if (!attachToRoot)` 判断，因为这里的 attachToRoot 取值为 false，所以判断为 true，进入 if 分支，

5. `temp.setLayoutParams(params);`，把布局参数设置给了**根节点控件对象**(temp, 实际上是 xml 中的 root 对象)。

6. `if (root != null && attachToRoot)`判断，由于 attachToRoot 为 false，所以判断为 false，不会进入 if 语句，也就是说**不会把根节点控件对象(temp)以及布局参数设置给 root**。

7. `if (root == null || !attachToRoot)` 判断，由于 attachToRoot 为 false，所以判断为 true，进入 if 语句，到达 `result = temp;`，也就是把根**节点控件对象(temp)赋值给了 result 变量**。

8. `return result;`，返回的就是根节点对象（result == temp）。

结论：返回的是 xml 布局的**根节点 View 对象**，并且对象上拥有根节点上的布局参数。

### case2-case5

按照同样的分析方法，我们抓住

1. temp 是 xml 中的 root。
2. 而 result 是传入的 root。
3. params 是 root 的 layout 参数。

这个关键点，可以得到以下结论：

| 根节点是否是 merge | ViewGroup root  | boolean attachToRoot | 返回值                                                       |
| :----------------- | --------------- | -------------------- | ------------------------------------------------------------ |
| 否                 | notNull         | false                | 返回的是 xml 布局的根节点 View 对象，并且对象上拥有根节点上的布局参数。 |
| 否                 | notNull         | true                 | 返回的是添加了根节点 View 对象以及布局参数的 root 对象。     |
| 否                 | null            | false                | 返回的是没有布局参数信息的根节点 View 对象。                 |
| 否                 | null            | true                 | 返回的是没有布局参数信息的根节点 View 对象。                 |
| **是**             | notNull（必须） | true（必须）         | 返回的是 root 对象。                                         |

## 实际例子

> 这里全部摘抄大佬的[文章](https://blog.csdn.net/willway_wang/article/details/107879127)

### 2.2.1 自定义控件填充布局

需要填充的布局`custom_view_layout.xml`如下：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:orientation="horizontal" android:layout_width="match_parent"
    android:layout_height="wrap_content">
    
<ImageView
    android:layout_margin="16dp"
    android:id="@+id/icon"
    android:layout_gravity="center_vertical"
    app:srcCompat="@mipmap/ic_launcher"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />

<TextView
    android:id="@+id/title"
    android:text="标题"
    android:textColor="@android:color/black"
    android:textSize="16sp"
    android:layout_gravity="center_vertical"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />

<View
    android:layout_weight="1"
    android:layout_width="0dp"
    android:layout_height="0dp" />
<Switch
    android:layout_marginEnd="16dp"
    android:layout_gravity="center_vertical|end"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
</LinearLayout>
```
CustomView 类如下：


```java
public class CustomView extends LinearLayout {
    public CustomView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.custom_view_layout, this);
    }
}
```

这里的 inflate() 方法是 View 类的静态方法：

```java
public static View inflate(Context context, @LayoutRes int resource, ViewGroup root) {
    LayoutInflater factory = LayoutInflater.from(context);
    return factory.inflate(resource, root);
}
```

内部调用的是 LayoutInflater 的第一个 inflate() 方法：

```java
public View inflate(@LayoutRes int resource, @Nullable ViewGroup root) {
    return inflate(resource, root, root != null);
}
```

ViewGroup root 不为 null 且 boolean attachToRoot 为 true，根节点不是 merge 标签，所以对应的是表格里的第二组情况，返回的是添加了根节点 View 对象以及布局参数的 root 对象，也就是说根节点 View 对象已经添加进入了 root 对象里面。

这里，我们使用 Android Studio 的 Layout Inspector 工具（在 Tools -> Layout Inspector 开启）来查看一下布局：

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200808191859957.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dpbGx3YXlfd2FuZw==,size_16,color_FFFFFF,t_70)

可以看到出现了重复布局。我们知道，**merge 标签可以用于优化重复布局**。

现在我们修改布局文件为 custom_merge_view_layout.xml：

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

<ImageView
    android:layout_margin="16dp"
    android:id="@+id/icon"
    android:layout_gravity="center_vertical"
    app:srcCompat="@mipmap/ic_launcher"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />

<TextView
    android:id="@+id/title"
    android:text="标题"
    android:textColor="@android:color/black"
    android:textSize="16sp"
    android:layout_gravity="center_vertical"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />

<View
    android:layout_weight="1"
    android:layout_width="0dp"
    android:layout_height="0dp" />
<Switch
    android:layout_marginEnd="16dp"
    android:layout_gravity="center_vertical|end"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
</merge>
```
代码中填充修改后的布局：

```java
public class CustomMergeView extends LinearLayout {
    public CustomMergeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.custom_merge_view_layout, this);
    }
}
```

再次使用布局查看器查看布局：

![在这里插入图片描述](https://img-blog.csdnimg.cn/2020080819262012.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dpbGx3YXlfd2FuZw==,size_16,color_FFFFFF,t_70)

可以看到使用 merge 标签消除了重复布局。

### 2.2.2 Fragment 填充布局
新建一个 FragmentInflateActivity.java 文件：

```java
public class FragmentInflateActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_inflate_activity);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fl_container, MyFragment.newInstance())
                .commit();
    }
    public static void start(Context context) {
        Intent starter = new Intent(context, FragmentInflateActivity.class);
        context.startActivity(starter);
    }
}
```
对应的 fragment_inflate_activity.xml：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:background="@android:color/holo_purple"
    android:id="@+id/fl_container"
    android:padding="8dp"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />

```

MyFragment.java 如下：

```java
public class MyFragment extends Fragment {
    private static final String TAG = "MyFragment";
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: container=" + container);
         return inflater.inflate(R.layout.my_fragment, container, false);
    }
    public static MyFragment newInstance() {
        Bundle args = new Bundle();
        MyFragment fragment = new MyFragment();
        fragment.setArguments(args);
        return fragment;
    }    
}
```
my_fragment.xml 如下：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="200dp"
    android:gravity="center"
    android:background="@android:color/holo_green_light"
    android:orientation="vertical">
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="MyFragment"
    android:textAllCaps="false"
    android:textSize="24sp" />
</LinearLayout>	    
```

运行后效果：<img src="https://img-blog.csdnimg.cn/20200808201439193.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dpbGx3YXlfd2FuZw==,size_16,color_FFFFFF,t_70" alt="img" style="zoom:50%;" />


注意看到在 onCreateView() 方法中打印的一行日志：

```
D/MyFragment: onCreateView: container=android.widget.FrameLayout{d613ebe V.E...... ......ID 0,0-0,0 #7f07005c app:id/fl_container}
```

打印信息显示 container 是一个 FrameLayout，它的 id 是 R.id.fl_container，这说明：在 LayoutInflater 的 `inflate(int resource, ViewGroup root, boolean attachToRoot)` 方法中的第二个参数对应的就是 FragmentInflateActivity 布局中的 FrameLayout。

这里的填充方式对应的是第二组的情况，返回的是 xml 布局的根节点 View 对象，并且对象上拥有根节点上的布局参数。

如果把 onCreateView 中的 inflate 方法的第三个参数 attachToRoot 改为 true 会怎么样？

```java
return inflater.inflate(R.layout.my_fragment, container, true);
```

运行后会崩溃：

```java
E/AndroidRuntime: FATAL EXCEPTION: main
    Process: com.example.layoutinflaterinflateparamstudy, PID: 23076
    java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
        at android.view.ViewGroup.addViewInner(ViewGroup.java:5168)
        at android.view.ViewGroup.addView(ViewGroup.java:4997)
        at android.view.ViewGroup.addView(ViewGroup.java:4937)
        at android.view.ViewGroup.addView(ViewGroup.java:4910)
        at androidx.fragment.app.FragmentManagerImpl.moveToState(FragmentManagerImpl.java:887)

```

为什么会崩溃呢？我们具体来看一看：

`inflater.inflate(R.layout.my_fragment, container, true);` 对应的是第一组取值情况，返回的是添加了根节点 View 对象以及布局参数的 root 对象。也就是说返回的填充了根节点对象的 container，就是 id 为 R.id.fl_container 的 FrameLayout 对象。

实际上，FragmentManager 会负责把 onCreateView() 方法返回的 View 对象加入到 id 为 R.id.fl_container 的 FrameLayout 对象里面。

而我们这里返回的是 id 为 R.id.fl_container 的 FrameLayout 对象，它自然是有一个 parent 的，再把它添加给自己，就报错了：`The specified child already has a parent. You must call removeView() on the child’s parent first.` 在 Android 中，一个 View 只能有一个 parent。

如果我们把 onCreateView() 方法中的 inflate() 方法改成对应第三组情况，会是什么效果：

```java
return inflater.inflate(R.layout.my_fragment, null, false);
```


细心查看的话，在 null 的地方有黄色的警告信息：

```java
Avoid passing null as the view root (needed to resolve layout parameters on the inflated layout's root element)
```


运行后的效果：<img src="https://img-blog.csdnimg.cn/20200808193720794.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dpbGx3YXlfd2FuZw==,size_16,color_FFFFFF,t_70" alt="img" style="zoom:33%;" />


我们确实设置在 my_fragment.xml 中的根节点 LinearLayout 设置了高度为 200dp，为什么没有生效呢？

因为第三组情况，返回的是没有布局参数信息的根节点 View 对象，也就是说我们这里设置的高度为 200dp 的布局参数信息是没有设置给填充完毕的根节点 View 对象的。这也是会报出黄色警告信息的原因。

既然没有布局参数，为什么填充完毕后根节点 View 对象的宽高会充满屏幕呢？

这是因为在ViewGroup类的 addView() 方法中，

```java
public void addView(View child, int index) {
    if (child == null) {
        throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
    }
    LayoutParams params = child.getLayoutParams();
    if (params == null) {
        params = generateDefaultLayoutParams();
        if (params == null) {
            throw new IllegalArgumentException("generateDefaultLayoutParams() cannot return null");
        }
    }
    addView(child, index, params);
}
```

在第 6 行，发现子 View 的布局参数 params 为 null，就会走第 7 行，由 generateDefaultLayoutParams() 生成默认的布局参数；而这里我们的 ViewGroup 其实是 FrameLayout，FrameLayout 重写了 generateDefaultLayoutParams() 方法如下：

```java
protected LayoutParams generateDefaultLayoutParams() {
    return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
}
```

### 2.2.3 RecyclerView 条目填充布局
这部分不进行详细介绍了，和 Fragment 填充布局很类似。下面只进行一下要点说明。

在 RecyclerView 的适配器的 onCreateViewHolder() 方法中：

```java
public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    Log.d(TAG, "onCreateViewHolder: parent=" + parent);
    View view = layoutInflater.inflate(R.layout.recycle_item, parent, false);
    return new ViewHolder(view);
}
```

打印信息如下：

```
onCreateViewHolder: parent=androidx.recyclerview.widget.RecyclerView{2e97f7b VFED..... .F....ID 0,0-1440,2048 #7f070081 app:id/recycler_view}
```

打印信息说明：ViewGroup parent 就是 RecyclerView 对象。

如果把 inflate() 方法的第三个参数 attachToRoot 改为 true，程序会崩溃：

```
E/AndroidRuntime: FATAL EXCEPTION: main
    Process: com.example.layoutinflaterinflateparamstudy, PID: 26322
    java.lang.IllegalStateException: ViewHolder views must not be attached when created. Ensure that you are not passing 'true' to the attachToRoot parameter of LayoutInflater.inflate(..., boolean attachToRoot)
        at androidx.recyclerview.widget.RecyclerView$Adapter.createViewHolder(RecyclerView.java:7080)
        at androidx.recyclerview.widget.RecyclerView$Recycler.tryGetViewHolderForPositionByDeadline(RecyclerView.java:6235)
        at androidx.recyclerview.widget.RecyclerView$Recycler.getViewForPosition(RecyclerView.java:6118)
        at androidx.recyclerview.widget.RecyclerView$Recycler.getViewForPosition(RecyclerView.java:6114)
        at androidx.recyclerview.widget.LinearLayoutManager$LayoutState.next(LinearLayoutManager.java:2303)
        at androidx.recyclerview.widget.LinearLayoutManager.layoutChunk(LinearLayoutManager.java:1627)
        at androidx.recyclerview.widget.LinearLayoutManager.fill(LinearLayoutManager.java:1587)
        at androidx.recyclerview.widget.LinearLayoutManager.onLayoutChildren(LinearLayoutManager.java:665)
        at androidx.recyclerview.widget.RecyclerView.dispatchLayoutStep2(RecyclerView.java:4134)
        at androidx.recyclerview.widget.RecyclerView.dispatchLayout(RecyclerView.java:3851)
        at androidx.recyclerview.widget.RecyclerView.onLayout(RecyclerView.java:4404)
```

实际上，什么时候把子 View 添加到 RecyclerView 中，是由 RecyclerView 来负责的，开发者只需要创建出子 View 交给 RecyclerView 即可。

### 2.2.4 AlertDialog 填充自定义布局
之前的例子中，inflate() 方法的第二个参数 ViewGroup parent 传递为 null，会产生布局参数丢失的问题。不过，在 AlertDialog 的自定义布局中，确实没有 ViewParent 的存在，这时不得不传递为 null。

```java
View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog, null);
AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this)
        .setTitle("AlertDialog")
        .setView(view)
        .create();
alertDialog.show();
```

这时对应的是第三组情况，返回的是没有布局参数信息的根节点 View 对象，也就是说，在 R.layout.dialog 中根节点的布局参数信息都是丢失。AlertDialog 会负责创建布局参数信息。





