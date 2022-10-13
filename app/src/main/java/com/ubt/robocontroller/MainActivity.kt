package com.ubt.robocontroller

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.ubt.robocontroller.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
import xh.zero.camera.BaseCameraActivity
import xh.zero.core.utils.SystemUtil
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt

class MainActivity : BaseCameraActivity<ActivityMainBinding>(), CameraXPreviewFragment.OnFragmentActionListener {

    companion object {
        private const val REQUEST_CODE_ALL_PERMISSION = 1
    }

    private val touchManager = TouchManager()
    private var lastTime: Long = 0

    private val w = 1920
    private val h = 1080

    private val activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionTask()
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + packageName))
                    activityLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activityLauncher.launch(intent)

                }
            } else {
                permissionTask()
            }
        } else {
            // 权限申请
            permissionTask()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.camera_container, CameraXPreviewFragment.newInstance(cameraId))
            .commit()
    }

    override fun selectCameraId(cameraIds: Array<String>): String = cameraIds[1]

    override fun showAnalysisResult(result: Bitmap) {
        if (System.currentTimeMillis() - lastTime > 1000) {
            touchManager.process(result)
            lastTime = System.currentTimeMillis()

            CoroutineScope(Dispatchers.Main).launch {
                binding.ivResultImage.setImageBitmap(result)
            }
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
            val points = arrayListOf<PointF>(
                PointF(w * 0.052f, h * 0.092f),
                PointF(w * 0.052f, h * 0.907f),
                PointF(w * 0.948f, h * 0.092f),
                PointF(w * 0.948f, h * 0.907f)
            )

            val markSize = resources.getDimension(R.dimen.mark_view_size)

            // 设置四个中心点
            val lp0 = binding.vMark0.layoutParams as ConstraintLayout.LayoutParams
            lp0.leftMargin = (points[0].x - markSize).roundToInt()
            lp0.topMargin = (points[0].y - markSize).roundToInt()

            val lp1 = binding.vMark1.layoutParams as ConstraintLayout.LayoutParams
            lp1.leftMargin = (points[1].x - markSize).roundToInt()
            lp1.bottomMargin = ((h - points[1].y) - markSize).roundToInt()

            val lp2 = binding.vMark2.layoutParams as ConstraintLayout.LayoutParams
            lp2.rightMargin = ((w - points[2].x) - markSize).roundToInt()
            lp2.topMargin = (points[2].y - markSize).roundToInt()

            val lp3 = binding.vMark3.layoutParams as ConstraintLayout.LayoutParams
            lp3.rightMargin = ((w - points[3].x) - markSize).roundToInt()
            lp3.bottomMargin = ((h - points[3].y) - markSize).roundToInt()

            points.forEach { p ->
                Timber.d("point: ${p.x}, ${p.y}")
            }

            Timber.d("mark size: ${markSize}")
            Timber.d("vMark0: ${binding.vMark0.marginLeft}, ${binding.vMark0.marginTop}")
            Timber.d("vMark1: ${binding.vMark1.marginLeft}, ${binding.vMark1.marginBottom}")

            val result = touchManager.initialTouchPanel(points, 1920, 1080)

            // 1280 x 1024
//            val points = arrayListOf<Point>(
//                Point(0, 0),
//                Point(0, 1024),
//                Point(1280, 0),
//                Point(1280, 1024)
//            )
//            val result = g_touchObj.initialTouchPanel(points, 1280, 1024)

//            val points = arrayListOf<Point>(
//                Point(0, 0),
//                Point(0, 640),
//                Point(480, 0),
//                Point(480, 640)
//            )
//            val result = touchManager.initialTouchPanel(points, 480, 640)
            // 设置为标定模式
            touchManager.setCurrentMode(1)

            binding.btnMark.setOnClickListener {
//                if (image != null) {
//                    g_touchObj.marking(0, image!!)
//                } else {
//                    Toast.makeText(this, "未捕获图片", Toast.LENGTH_SHORT).show();
//                }
//                image = getImageBitmap("touch_test.jpg")
//                g_touchObj.marking(0, image!!)
                touchManager.setMarkIndex(0)
            }

            binding.btnTest.setOnClickListener {
                touchManager.test()
            }

            initMarkViews()
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

    private fun initMarkViews() {
        touchManager.setCallback(object : TouchManager.Callback {
            override fun onMarking(index: Int, code: Int) {
                Timber.d("index: $index, code: $code")
                runOnUiThread {
                    binding.btnLog.text = "onMarking: index=$index, code=$code"
                }
            }
        })

        binding.vMark0.setOnTouchListener { view, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                touchManager.setMarkIndex(0)
                binding.vMark0.marking()
            }
            return@setOnTouchListener true
        }
    }

    private fun setMarkViewCenter(v: MarkView, center: PointF) {
        val lp = v.layoutParams as ConstraintLayout.LayoutParams
        val markSize = resources.getDimension(R.dimen.mark_view_size)
        lp.leftMargin = (center.x - markSize).roundToInt()
        lp.topMargin = (center.y - markSize).roundToInt()
        lp.rightMargin = ((w - center.x) - markSize).roundToInt()
        lp.bottomMargin = (center.x - markSize).roundToInt()
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