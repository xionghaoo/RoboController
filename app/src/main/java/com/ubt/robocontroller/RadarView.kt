//package com.ubt.robocontroller
//
//import android.content.Context
//import android.content.res.TypedArray
//import android.graphics.Canvas
//import android.graphics.Paint
//import android.util.AttributeSet
//import android.view.View
//import kotlin.math.roundToInt
//
///**
// * 雷达图
// */
//class RadarView : View {
//
//    companion object {
//        private var WRAP_WIDTH = 200
//        private var WRAP_HEIGHT = 200
//
//        // 每次渲染增加的半径值
//        private const val STEP = 5
//        // 两个圆之间的间隙
//        private const val CIRCLE_GAP = 200
//        // onDraw刷新时间间隔ms
//        private const val DELAY = 30L
//        // 起始圆半径
//        private const val MIN_RADIUS = 50f
//    }
//
//    private var isStart: Boolean = false
//    private var currentMaxRadius: Float = MIN_RADIUS
//    private var circleNum: Int = 1
//    private var paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
//
//    constructor(context: Context) : super(context) {}
//    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
//        var ta: TypedArray? = null
//        try {
//            ta = context.theme.obtainStyledAttributes(attrs, R.styleable.RadarView, 0, 0)
//            val color = ta.getColor(R.styleable.RadarView_rv_color, resources.getColor(android.R.color.holo_orange_light))
//            paint.style = Paint.Style.FILL_AND_STROKE
//            paint.color = color
//        } catch (e: Exception) {
//            ta?.recycle()
//        }
//    }
//
//    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
//        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
//        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
//        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
//        if (widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST) {
//            setMeasuredDimension(WRAP_WIDTH, WRAP_HEIGHT)
//        } else if (widthMode == MeasureSpec.AT_MOST) {
//            setMeasuredDimension(WRAP_WIDTH, heightSize)
//        } else if (heightMode == MeasureSpec.AT_MOST) {
//            setMeasuredDimension(widthSize, WRAP_HEIGHT)
//        } else {
//            setMeasuredDimension(widthSize, heightSize)
//        }
//    }
//
//    override fun onDraw(canvas: Canvas?) {
//        super.onDraw(canvas)
//        val topPadding = paddingTop
//        val bottomPadding = paddingBottom
//        val leftPadding = paddingLeft
//        val rightPadding = paddingRight
//        val width = (width - leftPadding - rightPadding).toFloat()
//        val height = (height - topPadding - bottomPadding).toFloat()
//        val maxRadius = width / 2 + CIRCLE_GAP
//        circleNum = ((maxRadius - MIN_RADIUS) / CIRCLE_GAP).toInt()
//
//        // 半径到达最大时，往后退一格再重新绘制
//        if (currentMaxRadius > maxRadius) {
//            currentMaxRadius -= CIRCLE_GAP
//        }
//
//        // 以一定步长增加半径值
//        currentMaxRadius += STEP
//        // 根据当前半径绘制剩余圆形
//        for (i in 0..circleNum) {
//            val r = currentMaxRadius - (i + 1) * CIRCLE_GAP
//            // 大于最小显示半径的圆才能绘制出来
//            if (r > MIN_RADIUS) {
//                // 计算透明度
//                val alpha = (150 * (maxRadius - CIRCLE_GAP - r) / (maxRadius - CIRCLE_GAP - MIN_RADIUS)).roundToInt()
//                paint.alpha = if (alpha > 0) alpha else 0
//                canvas?.drawCircle(width / 2, height / 2, r, paint)
//            }
//        }
//
//        // 刷新UI
//        if (isStart) {
//            postInvalidateDelayed(DELAY)
//        }
//    }
//
//    fun start() {
//        if (!isStart) {
//            isStart = true
//            currentMaxRadius = MIN_RADIUS
//            invalidate()
//        }
//    }
//
//    fun stop() {
//        isStart = false
//    }
//}