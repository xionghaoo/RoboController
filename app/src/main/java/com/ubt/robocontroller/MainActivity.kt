package com.ubt.robocontroller

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.ubt.robocontroller.databinding.ActivityMainBinding
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
import xh.zero.camera.BaseCameraActivity
import xh.zero.core.utils.SystemUtil
import java.io.IOException
import java.io.InputStream

class MainActivity : BaseCameraActivity<ActivityMainBinding>(), CameraXPreviewFragment.OnFragmentActionListener {

    companion object {
        private const val REQUEST_CODE_ALL_PERMISSION = 1
    }

    private val touchManager = TouchManager()
    private var lastTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
    }

    override fun getBindingView(): ActivityMainBinding {
        SystemUtil.toFullScreenMode(this)
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun getCameraFragmentLayout(): ViewGroup? = binding.cameraContainer

    override fun getPreviewSize(): Size? = Size(200, 200)

    override fun onCameraAreaCreated(
        cameraId: String,
        previewArea: Size,
        screen: Size,
        supportImage: Size
    ) {
        permissionTask()
        supportFragmentManager.beginTransaction()
            .replace(R.id.camera_container, CameraXPreviewFragment.newInstance(cameraId))
            .commit()
    }

    override fun selectCameraId(cameraIds: Array<String>): String = cameraIds[0]

    override fun showAnalysisResult(result: Bitmap) {
        if (System.currentTimeMillis() - lastTime > 1000) {
            touchManager.process(result)
            lastTime = System.currentTimeMillis()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    @AfterPermissionGranted(REQUEST_CODE_ALL_PERMISSION)
    private fun permissionTask() {
        if (hasPermission()) {
//            val points = arrayListOf<Point>(
//                Point(0, 0),
//                Point(0, 1080),
//                Point(1920, 0),
//                Point(1920, 1080)
//            )
//            val result = g_touchObj.initialTouchPanel(points, 1920, 1080)

            // 1280 x 1024
//            val points = arrayListOf<Point>(
//                Point(0, 0),
//                Point(0, 1024),
//                Point(1280, 0),
//                Point(1280, 1024)
//            )
//            val result = g_touchObj.initialTouchPanel(points, 1280, 1024)

            val points = arrayListOf<Point>(
                Point(0, 0),
                Point(0, 640),
                Point(480, 0),
                Point(480, 640)
            )
            val result = touchManager.initialTouchPanel(points, 480, 640)

            binding.btnMark.setOnClickListener {
//                if (image != null) {
//                    g_touchObj.marking(0, image!!)
//                } else {
//                    Toast.makeText(this, "未捕获图片", Toast.LENGTH_SHORT).show();
//                }
//                image = getImageBitmap("touch_test.jpg")
//                g_touchObj.marking(0, image!!)
                touchManager.setCurrentMode(1)
                touchManager.setMarkIndex(0)
            }

            binding.btnTest.setOnClickListener {
                touchManager.test()
            }
        } else {
            EasyPermissions.requestPermissions(
                this,
                "App需要相关权限，请授予",
                REQUEST_CODE_ALL_PERMISSION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    private fun hasPermission() : Boolean {
        return EasyPermissions.hasPermissions(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private fun getImageBitmap(img: String): Bitmap {
        var inStream: InputStream? = null
        try {
            inStream = assets.open(img)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return BitmapFactory.decodeStream(inStream)
    }

}