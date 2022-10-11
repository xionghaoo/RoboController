package com.ubt.robocontroller

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.view.ViewGroup
import com.ubt.robocontroller.databinding.ActivityMainBinding
import xh.zero.camera.BaseCameraActivity

class MainActivity : BaseCameraActivity<ActivityMainBinding>(), CameraXPreviewFragment.OnFragmentActionListener {

    private val aiManager = AiManager()

    override fun getBindingView() = ActivityMainBinding.inflate(layoutInflater)

    override fun getCameraFragmentLayout(): ViewGroup? = binding.cameraContainer

    override fun getPreviewSize(): Size? = Size(200, 200)

    override fun onCameraAreaCreated(
        cameraId: String,
        previewArea: Size,
        screen: Size,
        supportImage: Size
    ) {
        aiManager.test()
        supportFragmentManager.beginTransaction()
            .replace(R.id.camera_container, CameraXPreviewFragment.newInstance(cameraId))
            .commit()
    }

    override fun selectCameraId(cameraIds: Array<String>): String = cameraIds[0]

    override fun showAnalysisResult(result: Bitmap?) {

    }
}