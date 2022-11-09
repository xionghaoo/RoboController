package com.ubt.robocontroller

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PointF
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.serenegiant.common.BaseActivity
import com.serenegiant.usb.DeviceFilter
import com.serenegiant.usb.UVCCamera
import com.ubt.robocontroller.databinding.ActivityUvcactivityBinding
import com.ubt.robocontroller.utils.MarkUtil
import com.ubt.robocontroller.uvc.UvcFragment
import kotlinx.coroutines.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
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

    private val w = 1920
    private val h = 1080

    private val activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SystemUtil.toFullScreenMode(this)
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
        fragment.addSurfaceWithCheck()
    }

    override fun onMarking(index: Int, code: Int) {
        Timber.d("onMarking: $index, $code")

        runOnUiThread {
            binding.btnMarking.text = "onMarking: index=$index, code=$code"

            when(code) {
                1606 -> {
                    //清除标定缓存
                    //停止显示动画
                    //设置 正常工作 模式
//                    binding.vMark0.visibility = View.INVISIBLE
//                    binding.vMark1.visibility = View.INVISIBLE
//                    binding.vMark2.visibility = View.INVISIBLE
//                    binding.vMark3.visibility = View.INVISIBLE
                    binding.tvMarkInfo.text = "工作模式"
                }
                1600 -> {
                    // 显示采集动画，时间：300ms
                    when(index) {
                        0 -> binding.vMark0.marking()
                        1 -> binding.vMark1.marking()
                        2 -> binding.vMark2.marking()
                        3 -> binding.vMark3.marking()
                    }
                }
                1 -> {
                    if (index == 3) {
                        // 4个点标定完成
                        // 显示等待动画
                        binding.tvMarkInfo.text = "标定完成, 正在进入工作模式"
                    } else {
                        binding.tvMarkInfo.text = "当前标定点：$index"
                        when(index) {
                            0 -> binding.vMark1.visibility = View.VISIBLE
                            1 -> binding.vMark2.visibility = View.VISIBLE
                            2 -> binding.vMark3.visibility = View.VISIBLE
                            else -> {}
                        }
                    }
                }
                2 -> {
                    // 显示错误信息，等待返回码 102
                    binding.tvMarkInfo.text = "标定错误"
                }
                102 -> {
                    // 继续采集标定数据
                    binding.tvMarkInfo.text = "继续标定"
                }
                -1 -> {
                    // 标定出现错误，退出整个流程
                    binding.tvMarkInfo.text = "标定失败"
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
        binding.vMark0.setShowTime(300)
        binding.vMark1.setShowTime(300)
        binding.vMark2.setShowTime(300)
        binding.vMark3.setShowTime(300)

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

        if (MarkUtil.isRunMode()) {
            // 当前为运行模式
            binding.tvMarkInfo.text = "运行模式"
        } else {
            binding.tvMarkInfo.text = "当前标定点：0"
        }

        // 设置曝光模式
        binding.btnSetExposureMode.setOnClickListener {
            fragment.setExposureMode(UVCCamera.EXPOSURE_MODE_AUTO_OFF)
            updateExposure()
            ToastUtil.show(this, "设置为手动曝光模式")
        }
        // 设置曝光参数
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

        fragment = UvcFragment.newInstance(pid, points)
        fragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment)
            .commit()
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

    private fun tryGetUsbPermission() {
        mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // 注册权限接收广播
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(mUsbPermissionActionReceiver, filter)

        requestUsbPermission()
    }

    private fun requestUsbPermission() {
        val mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), FLAG_IMMUTABLE)
        val filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter)
        var foundDevice = false
        mUsbManager?.deviceList?.values?.forEach { usbDevice ->
            filters.forEach { filiter ->
                if (filiter.mProductId == usbDevice.productId && !filiter.isExclude) {
                    foundDevice = true
                    if (mUsbManager!!.hasPermission(usbDevice)) {
                        afterGetUsbPermission(usbDevice)
                    } else {
                        // 请求USB权限
                        mUsbManager!!.requestPermission(usbDevice, mPermissionIntent)
                    }
                }
            }
        }
        if (!foundDevice) {
            ToastUtil.show(this, "未找到指定设备")
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
                        requestUsbPermission()
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