package com.ubt.robocontroller

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF

class TouchManager {
    companion object {
        init {
            System.loadLibrary("opencv_java3")
            System.loadLibrary("robocontroller")
        }
    }

    external fun test()

    /**
     * 触屏模块初始化
     */
    external fun initialTouchPanel(points: ArrayList<Point>, pxWidth: Int, pxHeight: Int): Int

    /**
     * 标定
     */
    external fun marking(index: Int, image: Bitmap)

    /**
     * 正常工作接口
     */
    external fun processTouchData(image: Bitmap)

    /**
     * 设置模式
     */
    external fun setCurrentMode(mode: Int)

    external fun setMarkIndex(index: Int)

    /**
     * 图像处理
     */
    external fun process(image: Bitmap)

    /**
     * 清除缓存
     */
    external fun clearMarking()

    /**
     * 清理资源
     */
    external fun exitTouchPanel()
}