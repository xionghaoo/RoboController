package com.ubt.robocontroller.uvc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
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
    private var listener: OnFragmentActionListener? = null

    private val pid: Int by lazy { arguments?.getInt(ARG_PID) ?: 0 }

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
            Timber.d("mCameraListener -> onConnect: add surface")
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
            listener?.onMarking(index, code)
        }

        override fun onFpsChange(fps: Int, fpsHandle: Int) {
            listener?.onFpsChange(fps, fpsHandle)
        }
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        if (activity is OnFragmentActionListener) {
            listener = activity
        }
    }

    override fun onDetach() {
        listener = null
        super.onDetach()
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

        binding.cameraView.aspectRatio = (UVCCamera.DEFAULT_PREVIEW_WIDTH / UVCCamera.DEFAULT_PREVIEW_HEIGHT.toFloat()).toDouble()

    }

    override fun onResume() {
        super.onResume()
        mUSBMonitor?.register()
    }

    override fun onPause() {
        mCameraClient?.removeSurface(binding.cameraView.surface)
        super.onPause()
    }

    override fun onDestroy() {
        mUSBMonitor!!.unregister()
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
        openUVCCamera()
    }

    private fun openUVCCamera() {
        if (!mUSBMonitor!!.isRegistered) return
//        val list = mUSBMonitor!!.deviceList
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val list = usbManager.deviceList.values
        list.forEach { device ->
            if (device.productId == pid) {
                ToastUtil.show(context, "打开相机")

                if (mCameraClient == null) mCameraClient = CameraClient(activity, mCameraListener)
                mCameraClient!!.select(device)
                mCameraClient!!.resize(CAMERA_WIDTH, CAMERA_HEIGHT)
                mCameraClient!!.connect()
            }
        }
//        if (list.size > index) {
//            if (mCameraClient == null) mCameraClient = CameraClient(activity, mCameraListener)
//            mCameraClient!!.select(list[index])
//            mCameraClient!!.resize(CAMERA_WIDTH, CAMERA_HEIGHT)
//            mCameraClient!!.connect()
//        }
    }

    fun setExposureMode(mode: Int) {
        mCameraClient?.setExposureMode(mode);
    }

    fun setExposure(exposure: Int) {
        mCameraClient?.exposure = exposure
    }

    fun getExposure(): Int {
        return mCameraClient?.exposure ?: -1
    }

    fun addSurface() {
        mCameraClient?.addSurface(binding.cameraView.surface, false)
    }

    interface OnFragmentActionListener {
        fun onMarking(index: Int, code: Int)
        fun onFpsChange(fps: Int, fpsHandle: Int)
    }

    companion object {
        private const val CAMERA_WIDTH = 640
        private const val CAMERA_HEIGHT = 480

//        private const val CAMERA_INDEX = 1

        private const val ARG_PID = "ARG_PID"
        
        fun newInstance(pid: Int) = UvcFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_PID, pid)
            }
        }
    }
}