package com.ubt.robocontroller.uvc

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.Toast
import android.widget.ToggleButton
import com.serenegiant.common.BaseActivity
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.CameraDialog.CameraDialogParent
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener
import com.serenegiant.usb.USBMonitor.UsbControlBlock
import com.serenegiant.usb.UVCCamera
import com.ubt.robocontroller.R
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * VID_1E45 PID_8022
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
    private var mUVCCameraView: SimpleUVCCameraTextureView? = null

    // for open&start / stop&close camera preview
    private var mCameraButton: ToggleButton? = null

    // for start & stop movie capture
    private var mCaptureButton: ImageButton? = null
    private var framebuffer: Bitmap? = null

    private val mOnDeviceConnectListener: OnDeviceConnectListener =
        object : OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice) {
                Toast.makeText(this@UVCTest2Activity, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show()
            }

            override fun onConnect(
                device: UsbDevice,
                ctrlBlock: UsbControlBlock,
                createNew: Boolean
            ) {
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
                            UVCCamera.DEFAULT_PREVIEW_WIDTH,
                            UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                            UVCCamera.FRAME_FORMAT_MJPEG
                        )

                    } catch (e: IllegalArgumentException) {
                        try {
                            // fallback to YUV mode
                            camera.setPreviewSize(
                                UVCCamera.DEFAULT_PREVIEW_WIDTH,
                                UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                                UVCCamera.DEFAULT_PREVIEW_MODE
                            )
                        } catch (e1: IllegalArgumentException) {
                            camera.destroy()
                            return@Runnable
                        }
                    }
                    val st = mUVCCameraView!!.surfaceTexture
                    if (st != null) {
                        mPreviewSurface = Surface(st)
                        camera.setPreviewDisplay(mPreviewSurface)
                        camera.startPreview()
                    }

                    camera.setFrameCallback({ buffer ->
//                        framebuffer = yuv2Bmp(buffer.array(), CAMERA_WIDTH, CAMERA_HEIGHT)
                        if (framebuffer == null) {
                            framebuffer = Bitmap.createBitmap(CAMERA_WIDTH, CAMERA_HEIGHT, Bitmap.Config.RGB_565)
                        }
                        framebuffer?.copyPixelsFromBuffer(buffer)
                        Timber.d("on frame: ${Thread.currentThread()}, ${framebuffer?.width} x ${framebuffer?.height}")
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uvc_test2)

        permissionTask()

        mUVCCameraView =
            findViewById<View>(R.id.UVCCameraTextureView1) as SimpleUVCCameraTextureView
        mUVCCameraView?.setAspectRatio((UVCCamera.DEFAULT_PREVIEW_WIDTH / UVCCamera.DEFAULT_PREVIEW_HEIGHT.toFloat()).toDouble())
        mUVCCameraView?.surfaceTextureListener = mSurfaceTextureListener

        mUSBMonitor = USBMonitor(this, mOnDeviceConnectListener)

        mCameraButton = findViewById<View>(R.id.camera_button) as ToggleButton
        mCameraButton?.setOnCheckedChangeListener(mOnCheckedChangeListener)

        mCaptureButton = findViewById<View>(R.id.capture_button) as ImageButton
        mCaptureButton?.setOnClickListener(mOnClickListener)

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
        mCameraButton = null
        mCaptureButton = null
        mUVCCameraView = null
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
            if (mCameraButton != null) {
                try {
                    mCameraButton?.setOnCheckedChangeListener(null)
                    mCameraButton?.setChecked(isOn)
                } finally {
                    mCameraButton?.setOnCheckedChangeListener(mOnCheckedChangeListener)
                }
            }
            if (!isOn && mCaptureButton != null) {
                mCaptureButton?.setVisibility(View.INVISIBLE)
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
            mCaptureButton!!.visibility =
                if (mCameraButton!!.isChecked) View.VISIBLE else View.INVISIBLE
            mCaptureButton!!.setColorFilter(if (mCaptureState == CAPTURE_STOP) 0 else -0x10000)
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

        val mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)

        mUsbManager?.deviceList?.values?.forEach { usbDevice ->
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
        Toast.makeText(this@UVCTest2Activity, "Usb权限已获得", Toast.LENGTH_SHORT).show()
    }

    fun yuv2Bmp(data: ByteArray?, width: Int, height: Int): Bitmap? {
        val rawImage: ByteArray
        val bitmap: Bitmap
        val newOpts = BitmapFactory.Options()
        newOpts.inJustDecodeBounds = true
        val yuvimage = YuvImage(data, ImageFormat.NV21, width, height, null)
        val baos: ByteArrayOutputStream = ByteArrayOutputStream()
        yuvimage.compressToJpeg(Rect(0, 0, width, height), 100, baos)
        rawImage = baos.toByteArray()
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        bitmap = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.size, options)
        return bitmap
    }

}