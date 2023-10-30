# TextView 相关坑

# 获取到 Tv 的宽度

在之前做面板时（包含三个 tv），遇到一个问题，三个 tv 横向排布，过长则折行处理：

1. 在切换为其他语言的时候，例如葡语，会有一个 tv 特别长，折行了，但是其他两个 tv 没有折行
2. 此时我们需要让没有折行的另外两个 tv 的容器高度等于折行的这个 tv 的容器高度

在网上查了许久，包括运行时（通过 mesure 然后获取 measureHeight）去获取到 tv 的高度，发现都不奏效，似乎是因为 tv 没有绘制出来之前只能拿到 xml 中的高度，因此只能在绘制前对字符进行一次测算了：

```kotlin
  /**
   * 在没有 draw 出来之前获取到 text 信息以获取其宽度
   */
  private fun measureTextLength(textView: LineHeightTextView): Int {
      val dt = textView.text.toString()
      val bounds = Rect()
      val paint = textView.paint
      paint.getTextBounds(dt, 0, dt.length, bounds)
      return bounds.width()
  }
```

然后再动态的去更新它：

```kotlin
private fun updateHeightWhenLongLanguage() {
    val widthSpecMode = MeasureSpec.makeMeasureSpec(
        0, MeasureSpec.EXACTLY
    )
    val heightSpecMode = MeasureSpec.makeMeasureSpec(
        0, MeasureSpec.EXACTLY
    )

    // 注意需要先 measure 一下组件的宽度
    firstLayout.measure(widthSpecMode, heightSpecMode)
    secondLayout.measure(widthSpecMode, heightSpecMode)
    thirdLayout.measure(widthSpecMode, heightSpecMode)

    // measure 文案的宽度
    val gWidth = measureTextLength(galleryTv)
    val tWidth = measureTextLength(thumbnailTv)
    val sWidth = measureTextLength(speakerTv)

    // 一行显示的最大宽度
    val safeTextWidth = UIUtils.dp2px(context, SAFE_TEXT_LAYOUT_WIDTH_DP)

    // 某些语言，例如葡语下宽度会超过最大宽度，此时需要将所有的组件都拉长
    if (gWidth > safeTextWidth || tWidth > safeTextWidth || sWidth > safeTextWidth) {
        var params = firstLayout.layoutParams
        params.height = UIUtils.dp2px(context, DOUBLE_LINE_TV_FATHER_HEIGHT_DP)
        galleryLayout.layoutParams = params

        params = secondLayout.layoutParams
        params.height = UIUtils.dp2px(context, DOUBLE_LINE_TV_FATHER_HEIGHT_DP)
        thumbnailLayout.layoutParams = params

        params = thirdLayout.layoutParams
        params.height = UIUtils.dp2px(context, DOUBLE_LINE_TV_FATHER_HEIGHT_DP)
        speakerLayout.layoutParams = params
    }
}
```

最后在初始化这个 view 的时候就去做这件事：

```kotlin
init {
    rootView = ...
    // ...
    //  	实时计算一下
    updateHeightWhenLongLanguage()
}
```

# 动态设置 tv 后需要及时 layout 一下

之前还遇到过一次特别的 bug，在运行时动态的设置一次 tv，但是发现实时塞进去的 tv 没有使得 wrap_contant 的父布局拓展以容纳它，在源码里看到，设置 tv 的时候不会主动 layout，解决方法就是：

```kotlin
nameplate_name.apply {
    visibility = VISIBLE
    text = descName // textView 在其 layout 不为空时，不会执行重新 layout，需要手动处理一下
    requestLayout()
}
```



