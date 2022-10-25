package com.ubt.robocontroller.uvc

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.serenegiant.common.BaseFragment
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.DeviceFilter
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.ubt.robocontroller.R
import com.ubt.robocontroller.databinding.FragmentUvcBinding
import com.ubt.robocontroller.uvc.service.UVCService
import com.ubt.robocontroller.uvc.serviceclient.CameraClient
import com.ubt.robocontroller.uvc.serviceclient.ICameraClient
import com.ubt.robocontroller.uvc.serviceclient.ICameraClientCallback
import timber.log.Timber
import xh.zero.core.utils.ToastUtil


class UvcFragment : BaseFragment() {

    private lateinit var binding: FragmentUvcBinding
    private var mUSBMonitor: USBMonitor? = null
    private var mCameraClient: ICameraClient? = null

    private val mOnDeviceConnectListener: USBMonitor.OnDeviceConnectListener =
        object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice) {
                if (!updateCameraDialog() && binding.cameraView.hasSurface()) {
                    tryOpenUVCCamera(true)
                }
            }

            override fun onConnect(
                device: UsbDevice,
                ctrlBlock: USBMonitor.UsbControlBlock,
                createNew: Boolean
            ) {
                Timber.d("onConnect")
            }

            override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
                Timber.d("onDisconnect")
            }

            override fun onDettach(device: UsbDevice) {
                Timber.d("onDettach")
                queueEvent({
                    if (mCameraClient != null) {
                        mCameraClient?.disconnect()
                        mCameraClient?.release()
                        mCameraClient = null
                    }
                }, 0)
                updateCameraDialog()
            }

            override fun onCancel(device: UsbDevice) {
                Timber.d("onCancel")
            }
        }

    private val mCameraListener: ICameraClientCallback = object : ICameraClientCallback {
        override fun onConnect() {
            mCameraClient!!.addSurface(binding.cameraView.surface, false)
//            mCameraClient!!.addSurface(binding.cameraViewSub!!.holder.surface, false)
//            isSubView = true
//            enableButtons(true)
//            setPreviewButton(true)
            // start UVCService
            val intent = Intent(activity, UVCService::class.java)
            activity.startService(intent)
            activity?.runOnUiThread {
                ToastUtil.show(context, "启动服务2")
            }
        }

        override fun onDisconnect() {
//            setPreviewButton(false)
//            enableButtons(false)
        }

        override fun onMarking(index: Int, code: Int) {
            Timber.d("onMarking: $index, $code")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (mUSBMonitor == null) {
            mUSBMonitor = USBMonitor(activity.applicationContext, mOnDeviceConnectListener)
            val filters = DeviceFilter.getDeviceFilters(activity, R.xml.device_filter)
            mUSBMonitor?.setDeviceFilter(filters)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentUvcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cameraView.setAspectRatio((UVCCamera.DEFAULT_PREVIEW_WIDTH / UVCCamera.DEFAULT_PREVIEW_HEIGHT.toFloat()).toDouble())
    }

    override fun onResume() {
        super.onResume()
        mUSBMonitor?.register()
    }

    override fun onPause() {
        mCameraClient?.removeSurface(binding.cameraView.surface)
        mUSBMonitor!!.unregister()
        super.onPause()
    }

    override fun onDestroy() {
        mCameraClient?.release()
        super.onDestroy()
    }

    private fun updateCameraDialog(): Boolean {
        val fragment = fragmentManager.findFragmentByTag("CameraDialog")
        if (fragment is CameraDialog) {
            fragment.updateDevices()
            return true
        }
        return false
    }

    private fun tryOpenUVCCamera(requestPermission: Boolean) {
        openUVCCamera(CAMERA_INDEX)
    }

    private fun openUVCCamera(index: Int) {
        if (!mUSBMonitor!!.isRegistered) return
        val list = mUSBMonitor!!.deviceList
        if (list.size > index) {
            if (mCameraClient == null) mCameraClient = CameraClient(activity, mCameraListener)
            mCameraClient!!.select(list[index])
            mCameraClient!!.resize(CAMERA_WIDTH, CAMERA_HEIGHT)
            mCameraClient!!.connect()
        }
    }

    companion object {
        private const val CAMERA_WIDTH = 640
        private const val CAMERA_HEIGHT = 480

        private const val CAMERA_INDEX = 1
        
        fun newInstance() = UvcFragment()
    }
}