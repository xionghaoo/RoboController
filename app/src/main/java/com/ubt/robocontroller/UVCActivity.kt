package com.ubt.robocontroller

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageFormat
import android.graphics.PointF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.serenegiant.common.BaseActivity
import com.serenegiant.usb.UVCCamera
import com.ubt.robocontroller.databinding.ActivityMainBinding
import com.ubt.robocontroller.databinding.ActivityUvcactivityBinding
import com.ubt.robocontroller.uvc.UsbCameraFragment
import com.ubt.robocontroller.uvc.UvcFragment
import kotlinx.coroutines.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
import xh.zero.core.replaceFragment
import xh.zero.core.utils.SystemUtil
import xh.zero.core.utils.ToastUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class UVCActivity : BaseActivity(), UvcFragment.OnFragmentActionListener {

    companion object {
        private const val DEBUG = true
        private const val TAG = "MainActivity"

        private const val ACTION_USB_PERMISSION = "com.ubt.robocontroller.USB_PERMISSION"

        private const val CAPTURE_STOP = 0
        private const val CAPTURE_PREPARE = 1
        private const val CAPTURE_RUNNING = 2

        private const val REQUEST_CODE_ALL_PERMISSION = 1

        private fun getCaptureFile(type: String, ext: String): String? {
            val dir = File(Environment.getExternalStoragePublicDirectory(type), "USBCameraTest")
            dir.mkdirs() // create directories if they do not exist
            return if (dir.canWrite()) {
                File(dir, getDateTimeString() + ext).toString()
            } else null
        }

        private val sDateTimeFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
        private fun getDateTimeString(): String {
            val now = GregorianCalendar()
            return sDateTimeFormat.format(now.time)
        }

    }

    private var mUsbManager: UsbManager? = null
    private lateinit var binding: ActivityUvcactivityBinding
    private lateinit var fragment: UvcFragment

    private val touchManager = TouchManager.instance()
    private var lastTime: Long = 0
    private var cameraId: String = "0"
    private lateinit var cameraFragment: CameraXPreviewFragment

    private val w = 1920
    private val h = 1080
    private var currentMarkIndex = 0

    private val activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
//        SystemUtil.toFullScreenMode(this)
        super.onCreate(savedInstanceState)
        binding = ActivityUvcactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Timber.d("onNewIntent")
//        if (fragment.isAdded) {
//            fragment.addSurface()
//        }
    }

    override fun onMarking(index: Int, code: Int) {
        Timber.d("onMarking: $index, $code")

        runOnUiThread {
            binding.btnLog.text = "onMarking: index=$index, code=$code"

            when(code) {
                1600 -> {
                    when(currentMarkIndex) {
                        0 -> binding.vMark0.marking()
                        1 -> binding.vMark1.marking()
                        2 -> binding.vMark2.marking()
                        3 -> binding.vMark3.marking()
                    }
                }
                1 -> {
                    if (currentMarkIndex == 3) {
                        binding.tvMarkInfo.text = "标定完成"
                        touchManager.setCurrentMode(2)
                    } else {
                        touchManager.setMarkIndex(++currentMarkIndex)
                        binding.tvMarkInfo.text = "当前标定点：$currentMarkIndex"
                    }
                }
            }
        }
    }

    override fun onFpsChange(fps: Int, fpsHandle: Int) {
        Timber.d("onFpsChange: $fps, $fpsHandle")

        runOnUiThread {
            binding.tvFps.text = "相机fps: $fps"
            binding.tvFpsHandle.text = "算法处理后的fps: $fpsHandle"
        }
    }

    private fun initial(pid: Int) {
        fragment = UvcFragment.newInstance(pid)
        fragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment)
            .commit()

        val points = arrayListOf<PointF>(
            PointF(w * 0.052f, h * 0.092f),
            PointF(w * 0.052f, h * 0.907f),
            PointF(w * 0.948f, h * 0.092f),
            PointF(w * 0.948f, h * 0.907f)
        )

        val markSize = resources.getDimension(R.dimen.mark_view_size) / 2f

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

        // 初始化触控程序
        touchManager.initialTouchPanel(points, w, h)

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val f1 = File(downloadDir, "module/touchscreen/userdata/Homography.dat")
        val f2 = File(downloadDir, "module/touchscreen/userdata/ThresholdTemplate.dat")
        if (f1.exists() && f2.exists()) {
            touchManager.setCurrentMode(2)
        } else {
            touchManager.setCurrentMode(1)
        }

        // 标定第一个点
        touchManager.setMarkIndex(currentMarkIndex)
        binding.tvMarkInfo.text = "当前标定点：0"

        // 测试
        binding.btnMark.visibility = View.GONE
        binding.btnMark.setOnClickListener {
            touchManager.setMarkIndex(0)
        }

//        binding.btnTest.visibility = View.GONE
        binding.btnTest.setOnClickListener {

        }

        // 设置曝光参数
        binding.btnSetExposureMode.setOnClickListener {
            fragment.setExposureMode(UVCCamera.EXPOSURE_MODE_AUTO_OFF)
            updateExposure()
            ToastUtil.show(this, "设置为手动曝光模式")
        }

//        binding.sbExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                Timber.d("set progress: $progress")
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar?) {
//
//            }
//
//            override fun onStopTrackingTouch(seekBar: SeekBar?) {
//                fragment.setExposure(seekBar?.progress ?: 0)
//                CoroutineScope(Dispatchers.Default).launch {
//                    delay(200)
//                    withContext(Dispatchers.Main) {
//                        updateExposure()
//                    }
//                }
//            }
//        })

        binding.btnSetExposureValue.setOnClickListener {
            val percent = binding.edtExposure.text.toString().toInt()
            fragment.setExposure(percent)
            CoroutineScope(Dispatchers.Default).launch {
                delay(200)
                withContext(Dispatchers.Main) {
                    updateExposure()
                }
            }
        }

//        cameraFragment.setExposure(1)

//        initMarkViews()

//        val sb = StringBuffer()
//        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
//        val characteristic = cameraManager.getCameraCharacteristics(cameraId)
//        val orientation = characteristic.get(CameraCharacteristics.SENSOR_ORIENTATION)
//        sb.append("摄像头角度：$orientation").append("\n")
//        // 打开第一个摄像头
//        val configurationMap = characteristic.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//        configurationMap?.getOutputSizes(ImageFormat.JPEG)?.forEach { size ->
//            sb.append("camera size: ${size.width} x ${size.height}").append("\n")
//        }
//        binding.tvCameraInfo.text = sb.toString()
    }

    private fun updateExposure() {
        if (fragment.isAdded) {
            val exposure = fragment.getExposure()
            binding.tvExposure.text = "曝光值：$exposure"
        }
    }

    @AfterPermissionGranted(REQUEST_CODE_ALL_PERMISSION)
    private fun permissionTask() {
        if (hasPermission()) {
            tryGetUsbPermission()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "App需要相机和麦克风权限",
                REQUEST_CODE_ALL_PERMISSION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    private fun hasPermission() : Boolean {
        return EasyPermissions.hasPermissions(
            this,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    // 下面是USB权限申请，暂时不需要
    private fun tryGetUsbPermission() {
        mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // 注册权限接收广播
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(mUsbPermissionActionReceiver, filter)

        val mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), FLAG_IMMUTABLE)

        mUsbManager?.deviceList?.values?.forEach { usbDevice ->
            if (mUsbManager!!.hasPermission(usbDevice)) {
                afterGetUsbPermission(usbDevice)
            } else {
                // 请求USB权限
                mUsbManager!!.requestPermission(usbDevice, mPermissionIntent)
            }
        }
    }

    private val mUsbPermissionActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ACTION_USB_PERMISSION == intent?.action) {
                synchronized(this) {
                    val usbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbDevice != null) {
                            afterGetUsbPermission(usbDevice)
                        }
                    } else {
                        Toast.makeText(this@UVCActivity, "Usb权限未授予", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun afterGetUsbPermission(usbDevice: UsbDevice) {
        Timber.d("afterGetUsbPermission: ${usbDevice.deviceId}")
        Toast.makeText(this@UVCActivity, "Usb权限已获得", Toast.LENGTH_SHORT).show()
        initial(usbDevice.productId)

    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setMessage("确认退出应用？")
            .setPositiveButton("确认") { p0, p1 ->
                super.onBackPressed()
            }
            .setNegativeButton("取消", null)
            .show()

    }
}