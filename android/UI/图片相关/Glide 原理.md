## 参考文档

> 1. https://juejin.cn/post/6882536990400020494#heading-15
> 2. https://juejin.cn/post/6900436304341762056

## 导入及基础使用

导入 Glide 很简单：

```kotlin
// glide 本体
implemention('com.github.bumptech.glide:glide:4.9.0')
// okhttp 支持
implemention('com.github.bumptech.glide:okhttp3-integration:4.9.0')
```

除此之外还有几个常用的库：

```
// glide okhttp 支持
com.github.bumptech.glide:okhttp3-integration:4.9.0
// glide 编译器，使用集成库或配置 glide 才使用
com.github.bumptech.glide:compiler:4.9.0
```

使用可以使用三个例子说明：

1. 基础使用：

```kotlin
// 需要注意的是，占位符不是异步加载的
RequestOptions options = new RequestOptions()
        .placeholder(R.drawable.ic_launcher_background)		// 在加载尚未成功时的占位符
        .error(R.drawable.error)													// 加载错误情况下的图片
        .diskCacheStrategy(DiskCacheStrategy.NONE);				// 硬盘储存策略
        
Glide.with(contex)				// 可以是 context，Activity 啥的
     .load(url)					  // 加载的图片，可以是 url 或者本地路径啥的
     .apply(options)			// 可选项
     .into(imageView);		// 图片加载的目标 View
```

2. 仅获取资源，不展示以及对加载过程进行监听

```kotlin
Glidewith(context)
    .load(url)
		.listener(object : RequestListener<Drawable> {		// 加载监听
        override fun onLoadFailed(
            e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean
        ): Boolean {
            Logger.e(TAG, "[bindData] on request load failed, show error image")
            ThreadUtils.runOnUIThread {
                onFail()
            }
            return true
        }

        override fun onResourceReady(
            resource: Drawable?,
            model: Any?,
            target: Target<Drawable>?,
            dataSource: DataSource?,
            isFirstResource: Boolean
        ): Boolean {
            return false
        }

    })
    .diskCacheStrategy(DiskCacheStrategy.NONE)
    .into(object : CustomTarget<Drawable>() {				// 如果不期望直接加载到 View 中，这里会拿到 bitMap
        override fun onLoadFailed(errorDrawable: Drawable?) {
            Logger.e(TAG, "[bindData] on Target load failed, show error image")
            ThreadUtils.runOnUIThread {
                onFail()
            }
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            Logger.e(TAG, "[bind] on Target load cleared, show error image")
            ThreadUtils.runOnUIThread {
                onFail()
            }
        }

        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
            ThreadUtils.runOnUIThread {
                pictureView.setImageDrawable(resource)		// 可以在这里转换到主线程，设置 bitMap
                onSuccess()
            }
        }
    })
```

3. 如果需要在后台线程加载，可以使用 submit 获取一个 FutureTarget

```kotlin
FutureTarget<Bitmap> futureTarget = Glide.with(context)
    .asBitmap()
    .load(url)
    .submit(width, height);

Bitmap bitmap = futureTarget.get();

// Do something with the Bitmap and then when you're done with it:
Glide.with(context).clear(futureTarget);
```

## 代码架构

Glide 主要分为几大部分：

1. 门面类：Glide，用于提供统一的对外接口
2. 图片加载以及现有图片（已经 Active 的或者缓存了的）管理，Engin 类：
   1. Request，用于代表一个图片加载请求，相关的类有 RequestOptions（用于指定圆角、占位图等选项）、RequestListener（用于监听执行结果）、RequestCoordinator（用于协调主图、缩略图、出错图的加载工作）、RequestManager（用于在生命周期回调时控制图片加载请求的执行）等。
   2. DataFecher，用于获取数据，数据源可能是本地文件、Asset 文件、服务器文件等
   3. Resource，代表一个具体的图片资源，比如 Bitmap、Drawable 等
   4. ResourceDecoder，用于读取数据并转为对应的资源类型，例如将文件转化为 Bitmap
   5. Target，图片加载的目标，比如 ImageViewTarget
3. 缓存相关
   1. MemoryCache，内存缓存，使用 LruCache 实现
   2. DiskCache，本地文件缓存，使用 DiskLruCache 实现
   3. BitmapPool，用于缓存 Bitmap 对象
   4. ArrayPool，用于缓存数据对象
   5. Encoder，用于将数据持久化，例如将 Bitmap 写到本地文件
4. MemorySizeCalculator，用于根据设备分辨率计算内存缓存、BitmapPool、ArrayPool 的大小
5. LifeCycleListener，用于监听 Activity/Fragment 的生命周期，以便 Target 控制动画效果、清除资源
6. ConnectivityListener，用于监听设备网络情况，以执行对应的操作，比如重新开始加载图片
7. Transformation，用于执行圆角、旋转等图片变换操作，需要注意的是，这些变换不会应用到占位符中
8. Transition，用于执行过渡动画效果
9. ResourceTranscoder，用于将转换资源类型，比如将 Bitmap 转换为 Drawable



## 核心流程图

> 来源于[探索 glide 原理中大佬的配图](https://juejin.cn/post/6882536990400020494#heading-2)，十分清晰易懂。

### 图片加载三阶段

![ILWLYfpng](./assets/d149cabebf394341991358669ee0ebb0~tplv-k3u1fbpfcp-zoom-in-crop-mark:1512:0:0:0.awebp)

### 缓存机制图

![ILWdpepng](./assets/61e9d21ed2104feb856da2a233931bc5~tplv-k3u1fbpfcp-zoom-in-crop-mark:1512:0:0:0.awebp)
