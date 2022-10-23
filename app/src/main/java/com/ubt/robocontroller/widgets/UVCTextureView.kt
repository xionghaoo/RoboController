package com.ubt.robocontroller.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class UVCTextureView : TextureView {

    private var mRequestedAspect = -1.0

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    fun setAspectRatio(aspectRatio: Double) {
        require(aspectRatio >= 0)
        if (mRequestedAspect != aspectRatio) {
            mRequestedAspect = aspectRatio
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var wSpec: Int = widthMeasureSpec
        var hSpec: Int = heightMeasureSpec
        if (mRequestedAspect > 0) {
            var initialWidth = MeasureSpec.getSize(widthMeasureSpec)
            var initialHeight = MeasureSpec.getSize(heightMeasureSpec)
            val horizPadding = paddingLeft + paddingRight
            val vertPadding = paddingTop + paddingBottom
            initialWidth -= horizPadding
            initialHeight -= vertPadding
            val viewAspectRatio = initialWidth.toDouble() / initialHeight
            val aspectDiff = mRequestedAspect / viewAspectRatio - 1
            if (Math.abs(aspectDiff) > 0.01) {
                if (aspectDiff > 0) {
                    // width priority decision
                    initialHeight = (initialWidth / mRequestedAspect).toInt()
                } else {
                    // height priority decison
                    initialWidth = (initialHeight * mRequestedAspect).toInt()
                }
                initialWidth += horizPadding
                initialHeight += vertPadding
                wSpec = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY)
                hSpec = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY)
            }
        }
        super.onMeasure(wSpec, hSpec)
    }

}