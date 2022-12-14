package com.ubt.robocontroller

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.example.xgesturelibrary.XGestureInterface
import com.google.gson.Gson
import com.ubt.robocontroller.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
import xh.zero.camera.BaseCameraActivity
import xh.zero.camera.utils.StorageUtil
import xh.zero.core.utils.FileUtil
import xh.zero.core.utils.SystemUtil
import xh.zero.core.utils.ToastUtil
import java.io.*
import kotlin.math.roundToInt

class MainActivity : BaseCameraActivity<ActivityMainBinding>(), CameraXPreviewFragment.OnFragmentActionListener {

    companion object {
        private const val REQUEST_CODE_ALL_PERMISSION = 1
        const val TAG = "MainActivity"
    }

    private val touchManager = TouchManager()
    private var lastTime: Long = 0
    private var cameraId: String = "0"
    private lateinit var cameraFragment: CameraXPreviewFragment
    private val gestureApi = XGestureInterface()


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

        gestureApi.recognize_space_gesture_new(this)
        gestureApi.recognize_space_gesture_image {  gestureModels ->
            CoroutineScope(Dispatchers.Main).launch {
                if (gestureModels.size > 0) {
                    val model = gestureModels.first()
//                    gestureResultCallback?.success(model.gestureType.name)
                    Log.d(TAG, "recognizeSpaceGesture: ${model.gestureType}, ${model.points.size}")
                } else {
//                    gestureResultCallback?.failure("no gesture model")
                }
            }
        }

        this.cameraId = cameraId
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
            // ????????????
            permissionTask()
        }
    }

    override fun selectCameraId(cameraIds: Array<String>): String = cameraIds[0]

    override fun showAnalysisResult(result: Bitmap) {
//        if (System.currentTimeMillis() - lastTime > 1000) {
//
//        }

        touchManager.process(result)
//        lastTime = System.currentTimeMillis()
        gestureApi.recognize_space_gesture_image_send(result, System.currentTimeMillis())

        CoroutineScope(Dispatchers.Main).launch {
            binding.ivResult.setImageBitmap(result)
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

            CoroutineScope(Dispatchers.Default).launch {
                val configFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "config.json")
                var config: Config? = null
                if (configFile.exists()) {
                    try {
                        val configStr = readFile(configFile)
                        config = Gson().fromJson(configStr, Config::class.java)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            ToastUtil.show(this@MainActivity, "????????????????????????")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    initial(config)
                }
            }

        } else {
            EasyPermissions.requestPermissions(
                this,
                "App??????????????????????????????",
                REQUEST_CODE_ALL_PERMISSION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    private fun initial(config: Config?) {
        cameraFragment = CameraXPreviewFragment.newInstance(cameraId, config?.exposure ?: 0)
        supportFragmentManager.beginTransaction()
            .replace(R.id.camera_container, cameraFragment)
            .commit()

        val points = arrayListOf<PointF>(
            PointF(w * 0.052f, h * 0.092f),
            PointF(w * 0.052f, h * 0.907f),
            PointF(w * 0.948f, h * 0.092f),
            PointF(w * 0.948f, h * 0.907f)
        )

        val markSize = resources.getDimension(R.dimen.mark_view_size)

        // ?????????????????????
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

        // ?????????????????????
        touchManager.initialTouchPanel(points, 1920, 1080)

        // ?????????????????????
        touchManager.setCurrentMode(1)
        // ??????????????????
        touchManager.setMarkIndex(0)
        binding.tvMarkInfo.text = "??????????????????0"

        // ??????
        binding.btnMark.visibility = View.GONE
        binding.btnMark.setOnClickListener {
            touchManager.setMarkIndex(0)
        }

//        binding.btnTest.visibility = View.GONE
        binding.btnTest.setOnClickListener {

        }

        // ??????????????????
//        cameraFragment.setExposure(1)

        initMarkViews()

        val sb = StringBuffer()
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristic = cameraManager.getCameraCharacteristics(cameraId)
        val orientation = characteristic.get(CameraCharacteristics.SENSOR_ORIENTATION)
        sb.append("??????????????????$orientation").append("\n")
        // ????????????????????????
        val configurationMap = characteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        configurationMap?.getOutputSizes(ImageFormat.JPEG)?.forEach { size ->
            sb.append("camera size: ${size.width} x ${size.height}").append("\n")
        }
        binding.tvCameraInfo.text = sb.toString()
    }

    private fun initMarkViews() {
        touchManager.setCallback(object : TouchManager.Callback {
            override fun onMarking(index: Int, code: Int) {
                Timber.d("index: $index, code: $code")
                runOnUiThread {
                    binding.btnLog.text = "onMarking: index=$index, code=$code"
                }

                when(code) {
                    1600 -> {
                        if (index == 0) {
                            binding.vMark0.marking()
                        }
                    }
                    1 -> {
                        touchManager.setMarkIndex(1)
                        binding.tvMarkInfo.text = "??????????????????1"
                    }
                }
            }
        })

//        binding.vMark0.setOnTouchListener { view, e ->
//            if (e.action == MotionEvent.ACTION_DOWN) {
//                touchManager.setMarkIndex(0)
//                binding.vMark0.marking()
//            }
//            return@setOnTouchListener true
//        }
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

    fun readFile(file: File): String {
        val result = StringBuffer()
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(FileReader(file))
            var line = reader.readLine()
            while (line != null) {
                result.append(line).append("\n")
                line = reader.readLine()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                reader?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        return result.toString()
    }

}