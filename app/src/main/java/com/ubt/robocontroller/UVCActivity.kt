package com.ubt.robocontroller

import android.Manifest
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import com.serenegiant.common.BaseActivity
import com.ubt.robocontroller.databinding.ActivityMainBinding
import com.ubt.robocontroller.databinding.ActivityUvcactivityBinding
import com.ubt.robocontroller.uvc.UsbCameraFragment
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import xh.zero.core.replaceFragment
import xh.zero.core.utils.SystemUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class UVCActivity : BaseActivity() {

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

    var mUsbManager: UsbManager? = null
    private lateinit var binding: ActivityUvcactivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
//        SystemUtil.toFullScreenMode(this)
        super.onCreate(savedInstanceState)
        binding = ActivityUvcactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionTask()
    }

    @AfterPermissionGranted(REQUEST_CODE_ALL_PERMISSION)
    private fun permissionTask() {
        if (hasPermission()) {
//            tryGetUsbPermission()
            fragmentManager.beginTransaction()
                .add(R.id.fragment_container, UsbCameraFragment.newInstance())
                .commit()
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

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(mUsbPermissionActionReceiver, filter)

        val mPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), FLAG_IMMUTABLE)

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
                        Toast.makeText(this@UVCActivity, "Usb权限未授予", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun afterGetUsbPermission(usbDevice: UsbDevice) {
        Toast.makeText(this@UVCActivity, "Usb权限已获得", Toast.LENGTH_SHORT).show()

//        fragmentManager.beginTransaction()
//            .add(R.id.fragment_container, UsbCameraFragment.newInstance())
//            .commit()

    }
}