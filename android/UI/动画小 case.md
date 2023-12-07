# 安卓动画实现

在安卓中实现一个动画效果，主要是通过一个`ValueAnimator`库完成的。

# 简介

可以看一下 GPT 对其的简介：

ValueAnimator 是 Android 提供的一个动画类，可以用来创建和控制动画效果。下面是使用 ValueAnimator 的一般步骤：

1. 创建一个 ValueAnimator 对象：

   ```java
   ValueAnimator animator = ValueAnimator.ofInt(startValue, endValue);
   ```

   其中， `startValue` 是动画的起始值， `endValue` 是动画的结束值。

2. 为动画设置一些属性，例如动画的持续时间、插值器、重复次数等：

   ```java
   animator.setDuration(duration);
   animator.setInterpolator(interpolator);
   animator.setRepeatCount(repeatCount);
   ```

3. 为动画添加一个值更新的监听器：

   ```java
   animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
       @Override
       public void onAnimationUpdate(ValueAnimator animation) {
           int animatedValue = (int) animation.getAnimatedValue();
           // 在这里更新动画的数值
       }
   });
   ```

4. 开始动画：

   ```java
   animator.start();
   ```

需要注意的是，**由于 ValueAnimator 只负责生成和更新数值**，你需要在 `onAnimationUpdate` 方法中实现数值的使用和更新。例如，你可以使用动画的数值更新 View 的属性。

# 使用

可以看到，ValueAnimator 设计的关键只是给到了我们一个便于实现业务的时间流以及相应的数据等。具体的动画实现效果还是需要我们自己来做。

由于工作原因不能贴图，可以简单的介绍一下实现的动画效果：

1. 一个长方形位于页面底部的 toolBar
2. 在 toolBar 侧面有一个方向向左的按钮：<
3. 点击该按钮之后，toolBar 会逐渐缩到底部，底部留下一个往上的箭头: ^

实现代码：

```kotlin
 /**
 * 收起动画，最终为收起操作栏
 */
private fun collapseToolBar() {
  	// 拿到整个 toolBar 包含收起按钮的宽高
    bottomOpContainerWidth = bottomOpContainer.width
    bottomOpContainerHeight = bottomOpContainer.height
		
  	// 初始化 valueAnimator，其取值从 0-1，动画时间为 ANIMATION_DURATION_TOTAL
    valueAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(ANIMATION_DURATION_TOTAL).apply {
        addUpdateListener { animation ->
            // 拿到动画当前执行的比例，例如缩小到了 30% 时间处
            val progress = animation.animatedFraction

            // 拿到整个 toolBar 包含收起按钮的 param
            val layoutParams = bottomOpContainer.layoutParams as FrameLayout.LayoutParams
            // 按照比例去设置整个 toolBar 包含收起按钮的宽高变化
            layoutParams.width = (bottomOpContainerWidth - progress * (bottomOpContainerWidth - dp100)).toInt()
            layoutParams.height = (bottomOpContainerHeight - progress * (bottomOpContainerHeight - dp40)).toInt()
            // 同时将整个 toolBar 包含收起按钮不断的下移
						layoutParams.bottomMargin = (dp172 - progress * (dp172 - dp12)).toInt()
            bottomOpContainer.layoutParams = layoutParams

            val alpha = 1 - progress
            // 不断的添加不透明度
            otherOpContainer.alpha = alpha

            if (progress in COLLAPSE_PROGRESS_A..1.0f) {
              	// 设置其他部分为不可见的同时，不断的调整收起按钮图片的旋转方向
                otherOpContainer.visibility = View.GONE
                val angle = ANGEL_90 * (progress - COLLAPSE_PROGRESS_A) / (1 - COLLAPSE_PROGRESS_A)
                collapseIcon.rotation = angle
            }
        }
      	// 在动画执行过程中添加一些回调，这个是 valueAnimator 提供的接口
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                Logger.i(TAG, "[collapseToolBar] onAnimationStart")
                callback.onAnimationStart(true)
                collapseViewDivider.visibility = View.GONE
            }

            override fun onAnimationEnd(animation: Animator?) {
                Logger.i(TAG, "[collapseToolBar] onAnimationEnd")
                callback.onAnimationEnd(true)
                targetView.setBackgroundResource(R.drawable.bg_toolbar_collapsed_op_sel)
                if (isSubtitleOn) {
                    handler.postDelayed(adjustToolbarAlphaRunnable, SECOND_5)
                }
            }
        })
      	
      	// 开始动画
        start()
    }
}
```

