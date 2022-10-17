package com.ubt.robocontroller

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import com.ubt.robocontroller.databinding.FragmentCameraXPreviewBinding
import timber.log.Timber
import xh.zero.camera.CameraXFragment
import xh.zero.camera.widgets.BaseSurfaceView

class CameraXPreviewFragment : CameraXFragment<FragmentCameraXPreviewBinding>() {

    override var captureSize: Size? = Size(720, 1280)
//    override var captureSize: Size? = Size(1080, 1920)
    override val surfaceRatio: Size = Size(9, 16)
    private var listener: OnFragmentActionListener? = null

    override val cameraId: String by lazy { arguments?.getString("cameraId") ?: "0" }
    override val initialExposureIndex: Int by lazy { arguments?.getInt("exposureIndex") ?: 0 }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentActionListener) {
            listener = context
        } else {
            throw IllegalArgumentException("Activity must implement OnFragmentActionListener")
        }
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
    }

    override fun getBindingView(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCameraXPreviewBinding {
        return FragmentCameraXPreviewBinding.inflate(inflater, container, false)
    }

    override fun getSurfaceView(): BaseSurfaceView = binding.viewfinder

    override fun onFocusTap(x: Float, y: Float) {
        binding.focusCricleView.show(x, y)
    }

    override fun onAnalysisImage(bitmap: Bitmap) {
        listener?.showAnalysisResult(bitmap)
    }

    interface OnFragmentActionListener {
        fun showAnalysisResult(result: Bitmap)
    }

    companion object {
        fun newInstance(id: String, exposureIndex: Int) = CameraXPreviewFragment().apply {
            arguments = Bundle().apply {
                putString("cameraId", id)
                putInt("exposureIndex", exposureIndex)
            }
        }
    }
}