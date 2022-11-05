package com.ubt.robocontroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import timber.log.Timber

class MarkView : View {

    companion object {
        private var WRAP_WIDTH = 200
        private var WRAP_HEIGHT = 200

        // 每次渲染增加的半径值
        private const val STEP = 5
        // 两个圆之间的间隙
        private const val CIRCLE_GAP = 200
        // 起始圆半径
        private const val MIN_RADIUS = 50f
    }

    enum class Status {
        NONE, MARKING, FINISH
    }

    private var status: Status = Status.NONE

    private var paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paintMarking: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paintFinish: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var radiusMarking = 0f
    // onDraw刷新时间间隔ms
    private var delay = 50L

    constructor(context: Context?) : super(context) {

    }
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        paint.style = Paint.Style.FILL
        paint.color = Color.GRAY

        paintMarking.style = Paint.Style.FILL
        paintMarking.color = Color.BLUE

        paintFinish.style = Paint.Style.FILL
        paintFinish.color = Color.GREEN
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        if (widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST) {
            setMeasuredDimension(WRAP_WIDTH, WRAP_HEIGHT)
        } else if (widthMode == MeasureSpec.AT_MOST) {
            setMeasuredDimension(WRAP_WIDTH, heightSize)
        } else if (heightMode == MeasureSpec.AT_MOST) {
            setMeasuredDimension(widthSize, WRAP_HEIGHT)
        } else {
            setMeasuredDimension(widthSize, heightSize)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val topPadding = paddingTop
        val bottomPadding = paddingBottom
        val leftPadding = paddingLeft
        val rightPadding = paddingRight
        val width = (width - leftPadding - rightPadding).toFloat()
        val height = (height - topPadding - bottomPadding).toFloat()
        val maxRadius = width / 2
        val centerX = width / 2f
        val centerY = height / 2f
        canvas?.drawCircle(centerX, centerY, maxRadius, paint)
        when (status) {
            Status.NONE -> {}
            Status.MARKING -> {
                radiusMarking += STEP
                Timber.d("radiusMarking: $radiusMarking")
                if (radiusMarking >= maxRadius) {
                    finish()
                } else {
                    canvas?.drawCircle(centerX, centerY, radiusMarking, paintMarking)
                    postInvalidateDelayed(delay)
                }
            }
            Status.FINISH -> {
                canvas?.drawCircle(centerX, centerY, maxRadius, paintFinish)
            }
        }
    }

    fun setShowTime(time: Int) {
        val width = (width - paddingTop - paddingRight).toFloat()
        val maxRadius = width / 2f
        delay = (time / (maxRadius / STEP)).toLong()
    }

    fun marking() {
        if (status != Status.MARKING) {
            status = Status.MARKING
            postInvalidate()
        }
    }

    fun finish() {
        if (status != Status.FINISH) {
            status = Status.FINISH
            postInvalidate()
        }
    }


}