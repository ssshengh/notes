## 一个带边框的自定义圆角 Layout 实现

自定义 View 代码如下：

```kotlin
open class RoundableLayout @JvmOverloads constructor(context: Context,
                                                         attrs: AttributeSet? = null,
                                                         defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val MODE_OVERLAY: Int = 0
        const val MODE_INSET: Int = 1

        @JvmStatic
        private val sPathCache = object : LinkedHashMap<String, Path>() {
            override fun removeEldestEntry(eldest: MutableEntry<String, Path>?): Boolean {
                return size > 5
            }
        }

    }

    private var radiusLeftTop: Float
    private var radiusRightTop: Float
    private var radiusLeftBottom: Float
    private var radiusRightBottom: Float


    private var originPaddingLeft: Int
    private var originPaddingTop: Int
    private var originPaddingRight: Int
    private var originPaddingBottom: Int

    private var borderWidth: Float

    private var boarderColor: Int

    private val boardPaint: Paint by lazy {
        val paint = Paint()
        paint.style = Style.STROKE
        paint.isAntiAlias = true
        return@lazy paint
    }

    private val clearPaint: Paint by lazy {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        paint.xfermode = PorterDuffXfermode(CLEAR)
        return@lazy paint
    }

    private var clearPath: Path = Path()


    private var borderMode: Int

    init {
        val array = context.obtainStyledAttributes(attrs, R.styleable.RoundableLayout, 0, 0)
        val radius = array.getDimension(R.styleable.RoundableLayout_rl_radius, 0f)

        radiusLeftTop = array.getDimension(R.styleable.RoundableLayout_rl_radius_left_top,
            radius)
        radiusLeftBottom = array.getDimension(R.styleable.RoundableLayout_rl_radius_left_bottom,
            radius)
        radiusRightTop = array.getDimension(R.styleable.RoundableLayout_rl_radius_right_top,
            radius)
        radiusRightBottom = array.getDimension(
            R.styleable.RoundableLayout_rl_radius_right_bottom, radius)


        borderWidth = array.getDimension(R.styleable.RoundableLayout_rl_border_width, 0f)
        boarderColor = array.getColor(R.styleable.RoundableLayout_rl_border_color,
            ContextCompat.getColor(context, R.color._N200))
        borderMode = array.getInt(R.styleable.RoundableLayout_rl_border_mode, MODE_OVERLAY)


        array.recycle()

        originPaddingLeft = paddingLeft
        originPaddingTop = paddingTop
        originPaddingRight = paddingRight
        originPaddingBottom = paddingBottom

        if (borderWidth > 0 && borderMode == MODE_INSET) {
            amendPadding()
        }
    }

    private fun hasBorder() = borderWidth > 0 && boarderColor != Color.TRANSPARENT

    override fun dispatchDraw(canvas: Canvas) {

        if (!clipToOutline && isVeryLargeThanScreen()) {
            canvas.clipPath(getBorderPath(0F, 0F, width.toFloat(), height.toFloat()))
        }

        try {
            super.dispatchDraw(canvas)

            if (hasBorder()) {
                boardPaint.color = boarderColor
                /**
                 * 乘2是因为绘制path时，它的宽度是通过以下逻辑绘制的：
                 * 设 path 的宽度为w, path是垂直X轴的: 当绘制点（x, y）时, 向左边延伸绘制到x-w/2, 向右边延伸绘制到x+w/2。
                 * 因为这个path是绘制在view 的边缘，因此会被切去一半的宽度
                 */
                boardPaint.strokeWidth = borderWidth * 2
                canvas?.drawPath(getBorderPath(0F, 0F, width.toFloat(), height.toFloat()),
                    boardPaint)
            }

            //xfermode的方式实现
            if (!clipToOutline && !isVeryLargeThanScreen()) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                canvas.drawPath(getClearCanvasPath(), clearPaint)
            } else {
                setLayerType(View.LAYER_TYPE_NONE, null)
            }

        } catch (e: RuntimeException) { //temp solution to avoid crash
            if (e.message?.contains("recycled bitmap") == false) {
                throw e
            }
        }

    }

    //判断该view 尺寸是不是远大于屏幕,尺寸过大时，会存在以下两个问题:
    // 1. path 超出屏幕太多时，不会被绘制;
    // 2. 硬件加速缓存区大小有限制，view 太大，缓存区会溢出导致程序挂掉
    private fun isVeryLargeThanScreen(): Boolean {
        val screeSize = UiUtils.getScreenSize(context)
        return width > 2 * screeSize.width || height > 2 * screeSize.height
    }

    private fun getClearCanvasPath(): Path {
        clearPath.reset()
        clearPath.set(getBorderPath(0F, 0F, width.toFloat(), height.toFloat()))
        clearPath.toggleInverseFillType()
        return clearPath
    }

    private fun setClipOutline() {
        if (radiusLeftTop == radiusRightTop && radiusLeftTop == radiusLeftBottom && radiusLeftTop == radiusRightBottom) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View?, outline: Outline?) {
                    outline?.setRoundRect(0, 0, width, height, radiusLeftTop)
                }
            }
            clipToOutline = true
        } else {
            clipToOutline = false
            outlineProvider = null
        }
        invalidate()
    }

    fun setPadding(padding: Int) {
        setPadding(padding, padding, padding, padding)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        originPaddingLeft = left
        originPaddingTop = top
        originPaddingRight = right
        originPaddingBottom = bottom
        amendPadding()
    }

    private fun amendPadding() {
        val bwInt: Int = if (borderMode == MODE_INSET) borderWidth.toInt() else 0
        super.setPadding(originPaddingLeft + bwInt, originPaddingTop + bwInt,
            originPaddingRight + bwInt, originPaddingBottom + bwInt)
    }

    fun setBorderMode(mode: Int) {
        val isChange: Boolean = borderMode != mode
        borderMode = mode
        amendPadding()
        if (isChange) {
            setClipOutline()
        }
    }

    fun setBorderWidth(widthInDp: Float) {
        val newWidth: Float = Utils.dp2px(context, widthInDp)
        setBorderWidthInPx(newWidth)
    }

    fun setBorderWidthInPx(widthInPx: Float) {
        val isChange: Boolean = borderWidth != widthInPx
        borderWidth = widthInPx
        amendPadding()
        if (isChange) {
            setClipOutline()
        }
    }

    fun setBorderColor(@ColorInt color: Int) {
        boarderColor = color
        invalidate()
    }

    fun setRadius(radiusInDp: Float) {
        setRadiusInPx(Utils.dp2px(context, radiusInDp).toInt())
    }

    fun setRadiusInPx(radiusInPx: Int) {
        val radius = radiusInPx.toFloat()
        radiusLeftTop = radius
        radiusLeftBottom = radius
        radiusRightTop = radius
        radiusRightBottom = radius
        setClipOutline()
    }

    fun setRadius(leftTopRadiusInDp: Float,
                  leftBottomRadiusInDp: Float,
                  rightTopRadiusInDp: Float,
                  rightBottomRadiusInDp: Float) {
        setRadiusInPx(Utils.dp2px(context, leftTopRadiusInDp),
            Utils.dp2px(context, leftBottomRadiusInDp),
            Utils.dp2px(context, rightTopRadiusInDp),
            Utils.dp2px(context, rightBottomRadiusInDp))
    }

    fun setRadiusInPx(leftTopRadius: Float,
                      leftBottomRadius: Float,
                      rightTopRadius: Float,
                      rightBottomRadius: Float) {
        radiusLeftTop = leftTopRadius
        radiusLeftBottom = leftBottomRadius
        radiusRightTop = rightTopRadius
        radiusRightBottom = rightBottomRadius
        setClipOutline()
    }

    fun getRadius(): Float = radiusLeftTop

    fun getLeftTopRadius(): Float = radiusLeftTop

    fun getLeftBottomRadius(): Float = radiusLeftBottom

    fun getRightTopRadius(): Float = radiusRightTop

    fun getRightBottomRadius(): Float = radiusRightBottom


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setClipOutline()
    }


    private fun getBorderPath(left: Float, top: Float, right: Float, bottom: Float): Path {
        val cachePath = sPathCache[createPathKey(left, top, right, bottom, radiusLeftTop,
            radiusLeftBottom, radiusRightTop, radiusRightBottom)]
        if (cachePath != null) {
            return cachePath
        }

        val path = Path()
        path.reset()
        path.moveTo(left + radiusLeftTop, top)
        path.lineTo(right - radiusRightTop, top) //右上角圆弧
        path.arcTo(right - radiusRightTop, top + radiusRightTop, radiusRightTop, -1)
        path.lineTo(right, bottom - radiusRightBottom) //右下角圆弧
        path.arcTo(right - radiusRightBottom, bottom - radiusRightBottom, radiusRightBottom, 0)
        path.lineTo(left + radiusLeftBottom, bottom) //左下角圆弧
        path.arcTo(left + radiusLeftBottom, bottom - radiusLeftBottom, radiusLeftBottom, 1)
        path.lineTo(left, top + radiusLeftTop) //左上角圆弧
        path.arcTo(left + radiusLeftTop, top + radiusLeftTop, radiusLeftTop, 2)
        path.lineTo(left + radiusLeftTop, top)
        path.close()

        sPathCache[createPathKey(left, top, right, bottom, radiusLeftTop, radiusLeftBottom,
            radiusRightTop, radiusRightBottom)] = path

        return path
    }

    private fun createPathKey(left: Float,
                              top: Float,
                              right: Float,
                              bottom: Float,
                              leftTopRadiusInDp: Float,
                              leftBottomRadiusInDp: Float,
                              rightTopRadiusInDp: Float,
                              rightBottomRadiusInDp: Float): String {
        return "l:${left.toInt()} ,t:${top.toInt()} ,r:${right.toInt()} ,b:${bottom.toInt()} ,lt:${leftTopRadiusInDp.toInt()}, lb = ${leftBottomRadiusInDp.toInt()}, rt = ${rightTopRadiusInDp.toInt()} , rb = ${rightBottomRadiusInDp.toInt()}"
    }

    /**
     *
     * @param x 弧的圆心x坐标
     * @param y 弧的圆心y坐标
     */
    private fun Path.arcTo(x: Float, y: Float, radius: Float, rotations: Int) {
        val perpendicularAngle = 90f
        arcTo(x - radius, y - radius, x + radius, y + radius, rotations * perpendicularAngle,
            perpendicularAngle, false)
    }
}
```

### 使用方法

1. xml 中包裹：

```xml
    <RoundableLayout
        android:id="@+id/picture_container"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:maxWidth="436dp"
        android:maxHeight="240dp"
        android:visibility="gone"
        tools:visibility="visible"
        android:layout_marginTop="8dp"
        app:layout_constraintLeft_toLeftOf="@id/name"
        app:layout_constraintTop_toBottomOf="@id/name"
        >

        <ImageView
            android:id="@+id/picture"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@drawable/bg_solid_sn300_fill_stroker_no_corner"
            />
      <RoundableLayout>
```

2. 设置边框颜色和大小

```kotlin
pictureContainer.setBorderColor(ContextCompat.getColor(context, R.color.s_token_fill_stroke))
pictureContainer.setBorderWidth(STROKE_WIDTH.dp2px(context).toFloat())
```

3. 设置特殊的圆角

```kotlin
    /**
     * 最上面，左下不需要圆角
     */
    private fun setTopMessageCorner() {
        pictureContainer.setRadius(
            RADIUS_DP.dp2px(context).toFloat(),
            0f,
            RADIUS_DP.dp2px(context).toFloat(),
            RADIUS_DP.dp2px(context).toFloat(),
        )
    }

    /**
     * 中间，只有右边两个圆角
     */
    private fun setMiddleMessageCorner() {
        pictureContainer.setRadius(
            0f,
            0f,
            RADIUS_DP.dp2px(context).toFloat(),
            RADIUS_DP.dp2px(context).toFloat(),
        )
    }

    /**
     * 最下面，左上不需要圆角
     */
    private fun setBottomMessageCorner() {
        pictureContainer.setRadius(
            0f,
            RADIUS_DP.dp2px(context).toFloat(),
            RADIUS_DP.dp2px(context).toFloat(),
            RADIUS_DP.dp2px(context).toFloat()
        )
    }

    private fun setFullCorner() {
        imPictureContainer.setRadius(
            RADIUS_DP.dp2px(context).toFloat(),
            RADIUS_DP.dp2px(context).toFloat(),
            RADIUS_DP.dp2px(context).toFloat(),
            RADIUS_DP.dp2px(context).toFloat()
        )
    }
```

