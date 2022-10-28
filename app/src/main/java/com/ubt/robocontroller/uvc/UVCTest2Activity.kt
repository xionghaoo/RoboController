package com.ubt.robocontroller.uvc

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.*
import android.graphics.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.serenegiant.common.BaseActivity
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.CameraDialog.CameraDialogParent
import com.serenegiant.usb.DeviceFilter
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener
import com.serenegiant.usb.USBMonitor.UsbControlBlock
import com.serenegiant.usb.UVCCamera
import com.ubt.robocontroller.CameraXPreviewFragment
import com.ubt.robocontroller.Config
import com.ubt.robocontroller.R
import com.ubt.robocontroller.TouchManager
import com.ubt.robocontroller.databinding.ActivityUvcTest22Binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
import xh.zero.core.utils.SystemUtil
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * 红外相机
 * vid:0x05A3;
 * Pid:0x9230;
 */
class UVCTest2Activity : BaseActivity(), CameraDialogParent {

    companion object {
        private const val DEBUG = true
        private const val TAG = "MainActivity"

        private const val CAPTURE_STOP = 0
        private const val CAPTURE_PREPARE = 1
        private const val CAPTURE_RUNNING = 2

        private const val CAMERA_WIDTH = 640
        private const val CAMERA_HEIGHT = 480

        private const val FPS_MIN = 30
        private const val FPS_MAX = 30
        private const val FACTOR = 1f

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

    private var mUSBMonitor: USBMonitor? = null
    private var mUVCCamera: UVCCamera? = null
    private var mCaptureState = 0
    private var mPreviewSurface: Surface? = null
    private val mSync = Any()
//    private var mUVCCameraView: SimpleUVCCameraTextureView? = null

    // for open&start / stop&close camera preview
//    private var mCameraButton: ToggleButton? = null

    // for start & stop movie capture
//    private var mCaptureButton: ImageButton? = null
    private var framebuffer: Bitmap? = null

    private val touchManager = TouchManager.instance()
    private var lastTime: Long = 0
    private var cameraId: String = "0"
    private lateinit var cameraFragment: CameraXPreviewFragment

    private val w = 1920
    private val h = 1080
    private var currentMarkIndex = 0

    private var lastFrameTime = 0L
    private var lastHandleTime = 0L
    private var frameCount = 0
    private var frameCountHandle = 0
    private var fps = 0
    private var fpsHandle = 0

    private lateinit var binding: ActivityUvcTest22Binding

    private val mOnDeviceConnectListener: OnDeviceConnectListener =
        object : OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice) {
                Toast.makeText(this@UVCTest2Activity, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show()
                mUSBMonitor?.requestPermission(device)
            }

            override fun onConnect(
                device: UsbDevice,
                ctrlBlock: UsbControlBlock,
                createNew: Boolean
            ) {
                Timber.d("onConnect")
                synchronized(mSync) {
                    if (mUVCCamera != null) {
                        mUVCCamera!!.destroy()
                        mUVCCamera = null
                    }
                }
                queueEvent(Runnable {
                    val camera = UVCCamera()
                    camera.open(ctrlBlock)
                    if (DEBUG) Log.i(TAG, "supportedSize:" + camera.supportedSize)
                    if (mPreviewSurface != null) {
                        mPreviewSurface!!.release()
                        mPreviewSurface = null
                    }
                    try {
                        camera.setPreviewSize(
                            CAMERA_WIDTH,
                            CAMERA_HEIGHT,
                            UVCCamera.FRAME_FORMAT_MJPEG,
                            FPS_MIN, FPS_MAX, FACTOR
                        )

                    } catch (e: IllegalArgumentException) {
                        try {
                            // fallback to YUV mode
                            camera.setPreviewSize(
                                CAMERA_WIDTH,
                                CAMERA_HEIGHT,
                                UVCCamera.DEFAULT_PREVIEW_MODE,
                                FPS_MIN, FPS_MAX, FACTOR
                            )
                        } catch (e1: IllegalArgumentException) {
                            camera.destroy()
                            return@Runnable
                        }
                    }
                    val st = binding.UVCCameraTextureView1!!.surfaceTexture
                    if (st != null) {
                        mPreviewSurface = Surface(st)
                        camera.setPreviewDisplay(mPreviewSurface)
                        camera.startPreview()
                    }
                    runOnUiThread {
                        showSizeList(camera.supportedSizeList.map { size -> Size(size.width, size.height) })
                    }
//                    camera.powerlineFrequency = 60
//                    camera.whiteBlance
//                    camera.gamma
//                    Timber.d("powerlineFrequency: ${camera.powerlineFrequency}")
                    camera.setFrameCallback({ buffer ->
                        Timber.d("onFrame -----------------------")
                        if (lastHandleTime == 0L) lastHandleTime = System.currentTimeMillis()

                        // 计算处理前的帧数
                        if (lastFrameTime == 0L) lastFrameTime = System.currentTimeMillis()
                        frameCount ++
                        if (frameCount >= 30) {
                            val curTime = System.currentTimeMillis()
                            fps = (frameCount.toFloat() / (curTime - lastFrameTime) * 1000).roundToInt()
                            frameCount = 0
                            lastFrameTime = 0L
                        }
                        // ---------处理业务-------------
                        if (framebuffer == null) {
                            framebuffer = Bitmap.createBitmap(CAMERA_WIDTH, CAMERA_HEIGHT, Bitmap.Config.RGB_565)
                        }
                        framebuffer?.copyPixelsFromBuffer(buffer)

//                        CoroutineScope(Dispatchers.Main).launch {
//                            binding.ivResult.setImageBitmap(framebuffer)
//                        }
                        touchManager.process(framebuffer!!)
//                        Thread.sleep(50)
                        // -----------------------------
                        // 计算处理后的帧数
                        frameCountHandle ++
                        if (frameCountHandle >= 30) {
                            val curTime = System.currentTimeMillis()
                            fpsHandle = (frameCountHandle.toFloat() / (curTime - lastHandleTime) * 1000).roundToInt()
                            frameCountHandle = 0
                            lastHandleTime = 0
                        }

                        runOnUiThread {
                            binding.tvFps.text = "相机fps: $fps"
                            binding.tvFpsHandle.text = "算法处理后的fps: $fpsHandle"
                        }

                    }, UVCCamera.PIXEL_FORMAT_RGB565)

                    synchronized(mSync) { mUVCCamera = camera }
                }, 0)
            }

            override fun onDisconnect(device: UsbDevice, ctrlBlock: UsbControlBlock) {
                // XXX you should check whether the comming device equal to camera device that currently using
                queueEvent({
                    synchronized(mSync) {
                        if (mUVCCamera != null) {
                            mUVCCamera!!.close()
                        }
                    }
                    if (mPreviewSurface != null) {
                        mPreviewSurface!!.release()
                        mPreviewSurface = null
                    }
                }, 0)
                setCameraButton(false)
            }

            override fun onDettach(device: UsbDevice) {
                Toast.makeText(this@UVCTest2Activity, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show()
            }

            override fun onCancel(device: UsbDevice) {
                setCameraButton(false)
            }
        }
    private var mEncoder: Encoder? = null
    private val mSurfaceTextureListener: TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                if (mPreviewSurface != null) {
                    mPreviewSurface!!.release()
                    mPreviewSurface = null
                }
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                if (mEncoder != null && mCaptureState == CAPTURE_RUNNING) {
                    mEncoder?.frameAvailable()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        SystemUtil.toFullScreenMode(this)
        super.onCreate(savedInstanceState)
        binding = ActivityUvcTest22Binding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionTask()

//        mUVCCameraView =
//            findViewById<View>(R.id.UVCCameraTextureView1) as SimpleUVCCameraTextureView
        binding.UVCCameraTextureView1?.setAspectRatio((UVCCamera.DEFAULT_PREVIEW_WIDTH / UVCCamera.DEFAULT_PREVIEW_HEIGHT.toFloat()).toDouble())
        binding.UVCCameraTextureView1?.surfaceTextureListener = mSurfaceTextureListener

        mUSBMonitor = USBMonitor(this, mOnDeviceConnectListener)
        val filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter)
        mUSBMonitor?.setDeviceFilter(filters)
//        mCameraButton = findViewById<View>(R.id.camera_button) as ToggleButton
        binding.cameraButton?.setOnCheckedChangeListener(mOnCheckedChangeListener)

//        mCaptureButton = findViewById<View>(R.id.capture_button) as ImageButton
        binding.captureButton?.setOnClickListener(mOnClickListener)

    }

    override fun onStart() {
        super.onStart()

        synchronized(mSync) {
            if (mUSBMonitor != null) {
                mUSBMonitor!!.register()
            }
            if (mUVCCamera != null) mUVCCamera!!.startPreview()

        }
        setCameraButton(false)
        updateItems()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setMessage("确认退出应用？")
            .setPositiveButton("确认") { p0, p1 ->
                finish()
            }
            .setNegativeButton("取消", null)
            .show()

    }

//    override fun onStop() {
//        synchronized(mSync) {
//            if (mUVCCamera != null) {
//                stopCapture()
//                mUVCCamera!!.stopPreview()
//            }
//            mUSBMonitor!!.unregister()
//        }
//        setCameraButton(false)
//        super.onStop()
//    }

    override fun onDestroy() {
        synchronized(mSync) {
            if (mUVCCamera != null) {
                mUVCCamera!!.destroy()
                mUVCCamera = null
            }
            if (mUSBMonitor != null) {
                mUSBMonitor!!.destroy()
                mUSBMonitor = null
            }
        }
//        mCameraButton = null
//        mCaptureButton = null
//        mUVCCameraView = null
        super.onDestroy()
    }

    override fun getUSBMonitor(): USBMonitor = mUSBMonitor!!

    override fun onDialogResult(canceled: Boolean) {
        if (canceled) {
            setCameraButton(false)
        }
    }

    private fun setCameraButton(isOn: Boolean) {
        runOnUiThread({
            if (binding.cameraButton != null) {
                try {
                    binding.cameraButton?.setOnCheckedChangeListener(null)
                    binding.cameraButton?.setChecked(isOn)
                } finally {
                    binding.cameraButton?.setOnCheckedChangeListener(mOnCheckedChangeListener)
                }
            }
            if (!isOn) {
                binding.captureButton.setVisibility(View.INVISIBLE)
            }
        }, 0)
    }

    private val mOnCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            synchronized(mSync) {
                if (isChecked && mUVCCamera == null) {
                    CameraDialog.showDialog(this@UVCTest2Activity)
                } else if (mUVCCamera != null) {
                    mUVCCamera!!.destroy()
                    mUVCCamera = null
                } else {

                }
            }
            updateItems()
        }

    private val mOnClickListener = View.OnClickListener {
        if (checkPermissionWriteExternalStorage()) {
            if (mCaptureState == CAPTURE_STOP) {
                startCapture()
            } else {
                stopCapture()
            }
        }
    }

    private fun updateItems() {
        this.runOnUiThread {
            binding.captureButton!!.visibility =
                if (binding.cameraButton!!.isChecked) View.VISIBLE else View.INVISIBLE
            binding.captureButton!!.setColorFilter(if (mCaptureState == CAPTURE_STOP) 0 else -0x10000)
        }
    }

    /**
     * start capturing
     */
    private fun startCapture() {
        if (DEBUG) Log.v(TAG, "startCapture:")
        if (mEncoder == null && mCaptureState == CAPTURE_STOP) {
            mCaptureState = CAPTURE_PREPARE
            queueEvent({
                val path: String? = getCaptureFile(Environment.DIRECTORY_MOVIES, ".mp4")
                if (!TextUtils.isEmpty(path)) {
                    mEncoder = SurfaceEncoder(path)
                    mEncoder?.setEncodeListener(mEncodeListener)
                    try {
                        mEncoder?.prepare()
                        mEncoder?.startRecording()
                    } catch (e: IOException) {
                        mCaptureState = CAPTURE_STOP
                    }
                } else throw RuntimeException("Failed to start capture.")
            }, 0)
            updateItems()
        }
    }

    /**
     * stop capture if capturing
     */
    private fun stopCapture() {
        if (DEBUG) Log.v(TAG, "stopCapture:")
        queueEvent({
            synchronized(mSync) {
                if (mUVCCamera != null) {
                    mUVCCamera!!.stopCapture()
                }
            }
            if (mEncoder != null) {
                mEncoder?.stopRecording()
                mEncoder = null
            }
        }, 0)
    }

    private val mEncodeListener: Encoder.EncodeListener = object : Encoder.EncodeListener {
        override fun onPreapared(encoder: Encoder) {
            if (DEBUG) Log.v(TAG, "onPreapared:")
            synchronized(mSync) {
                if (mUVCCamera != null) {
                    mUVCCamera!!.startCapture((encoder as SurfaceEncoder).inputSurface)
                }
            }
            mCaptureState = CAPTURE_RUNNING
        }

        override fun onRelease(encoder: Encoder?) {
            if (DEBUG) Log.v(TAG, "onRelease:")
            synchronized(mSync) {
                if (mUVCCamera != null) {
                    mUVCCamera!!.stopCapture()
                }
            }
            mCaptureState = CAPTURE_STOP
            updateItems()
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

    var mUsbManager: UsbManager? = null
    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    private fun tryGetUsbPermission() {
        mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(mUsbPermissionActionReceiver, filter)

        val mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), FLAG_IMMUTABLE)

        mUsbManager?.deviceList?.values?.forEach { usbDevice ->
//            usbDevice.productId
            if (mUsbManager!!.hasPermission(usbDevice)) {
                afterGetUsbPermission(usbDevice)
            } else {
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
                        Toast.makeText(this@UVCTest2Activity, "Usb权限未授予", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun afterGetUsbPermission(usbDevice: UsbDevice) {

        initial(null)

        Toast.makeText(this@UVCTest2Activity, "Usb权限已获得", Toast.LENGTH_SHORT).show()
    }

    private fun initial(config: Config?) {
//        cameraFragment = CameraXPreviewFragment.newInstance(cameraId, config?.exposure ?: 0)
//        supportFragmentManager.beginTransaction()
//            .replace(R.id.camera_container, cameraFragment)
//            .commit()

//        fragmentManager.beginTransaction()
//            .add(R.id.fragment_container, UvcFragment.newInstance())
//            .commit()

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
//        binding.btnTest.setOnClickListener {
//
//        }

        // 设置曝光参数
//        cameraFragment.setExposure(1)

        initMarkViews()

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

    private fun showSizeList(sizeList: List<Size>) {
        val sb = StringBuffer()
        sizeList.forEach { size ->
            sb.append("support size: ${size.width} x ${size.height}").append("\n")
        }
        binding.tvCameraInfo.text = sb.toString()
    }

    private fun initMarkViews() {
        touchManager.setCallback(object : TouchManager.Callback {
            override fun onMarking(index: Int, code: Int) {
                Timber.d("index: $index, code: $code")
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
        })

//        binding.vMark0.setOnTouchListener { view, e ->
//            if (e.action == MotionEvent.ACTION_DOWN) {
//                touchManager.setMarkIndex(0)
//                binding.vMark0.marking()
//            }
//            return@setOnTouchListener true
//        }
    }


}