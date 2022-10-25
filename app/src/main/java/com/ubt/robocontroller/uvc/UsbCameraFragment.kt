package com.ubt.robocontroller.uvc

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ToggleButton
import com.serenegiant.common.BaseFragment
import com.serenegiant.encoder.MediaMuxerWrapper
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.DeviceFilter
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener
import com.serenegiant.usb.USBMonitor.UsbControlBlock
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.CameraViewInterface
import com.ubt.robocontroller.R
import com.ubt.robocontroller.databinding.FragmentUsbCameraBinding
import com.ubt.robocontroller.uvc.service.UVCService
import com.ubt.robocontroller.uvc.serviceclient.CameraClient
import com.ubt.robocontroller.uvc.serviceclient.ICameraClient
import com.ubt.robocontroller.uvc.serviceclient.ICameraClientCallback
import timber.log.Timber
import xh.zero.core.utils.ToastUtil

class UsbCameraFragment : BaseFragment() {

    private lateinit var binding: FragmentUsbCameraBinding
    private var mUSBMonitor: USBMonitor? = null
    private var mCameraClient: ICameraClient? = null

    private var isSubView = false

    private val mOnDeviceConnectListener: OnDeviceConnectListener =
        object : OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice) {
                if (!updateCameraDialog() && binding.cameraView.hasSurface()) {
                    tryOpenUVCCamera(true)
                }
            }

            override fun onConnect(
                device: UsbDevice,
                ctrlBlock: UsbControlBlock,
                createNew: Boolean
            ) {
                Timber.d("onConnect")
            }

            override fun onDisconnect(device: UsbDevice, ctrlBlock: UsbControlBlock) {
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
                enableButtons(false)
                updateCameraDialog()
            }

            override fun onCancel(device: UsbDevice) {
                enableButtons(false)
                Timber.d("onCancel")
            }
        }

    private val mCameraListener: ICameraClientCallback = object : ICameraClientCallback {
        override fun onConnect() {
            mCameraClient!!.addSurface(binding.cameraView!!.surface, false)
            mCameraClient!!.addSurface(binding.cameraViewSub!!.holder.surface, false)
            isSubView = true
            enableButtons(true)
            setPreviewButton(true)
            // start UVCService
            val intent = Intent(activity, UVCService::class.java)
            activity.startService(intent)
            activity?.runOnUiThread {
                ToastUtil.show(context, "启动服务2")
            }
        }

        override fun onDisconnect() {
            setPreviewButton(false)
            enableButtons(false)
        }

        override fun onMarking(index: Int, code: Int) {
            Timber.d("onMarking: $index, $code")
        }
    }

    private val mOnClickListener =
        View.OnClickListener { v ->
            when (v.id) {
                R.id.start_button -> {
                    ToastUtil.show(context, "启动服务")
                    // start service
                    val list = mUSBMonitor!!.deviceList
                    if (list.size > 0) {
                        if (mCameraClient == null) mCameraClient =
                            CameraClient(activity, mCameraListener)
                        mCameraClient!!.select(list[CAMERA_INDEX])
                        mCameraClient!!.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
                        mCameraClient!!.connect()
                        setPreviewButton(false)
                    }
                }
                R.id.stop_button -> {
                    // stop service
                    if (mCameraClient != null) {
                        mCameraClient!!.disconnect()
                        mCameraClient!!.release()
                        mCameraClient = null
                    }
                    enableButtons(false)
                }
                R.id.camera_view_sub -> {
                    if (isSubView) {
                        mCameraClient!!.removeSurface(binding.cameraViewSub!!.holder.surface)
                    } else {
                        mCameraClient!!.addSurface(binding.cameraViewSub!!.holder.surface, false)
                    }
                    isSubView = !isSubView
                }
                R.id.record_button -> {
                    if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
                        queueEvent({
                            if (mCameraClient!!.isRecording) {
                                mCameraClient!!.stopRecording()
                                runOnUiThread({ binding.recordButton!!.setColorFilter(0) }, 0)
                            } else {
                                mCameraClient!!.startRecording()
                                runOnUiThread({ binding.recordButton!!.setColorFilter(0x7fff0000) }, 0)
                            }
                        }, 0)
                    }
                }
                R.id.still_button -> {
                    if (mCameraClient != null && checkPermissionWriteExternalStorage()) {
                        queueEvent({
                            mCameraClient!!.captureStill(
                                MediaMuxerWrapper.getCaptureFile(
                                    Environment.DIRECTORY_DCIM, ".jpg"
                                ).toString()
                            )
                        }, 0)
                    }
                }
            }
        }

    private val mOnCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                mCameraClient!!.addSurface(binding.cameraView!!.surface, false)
                //				mCameraClient.addSurface(mCameraViewSub.getHolder().getSurface(), false);
            } else {
                mCameraClient!!.removeSurface(binding.cameraView!!.surface)
                //				mCameraClient.removeSurface(mCameraViewSub.getHolder().getSurface());
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
        binding = FragmentUsbCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.startButton.setOnClickListener(mOnClickListener)
        binding.stopButton.setOnClickListener(mOnClickListener)
        setPreviewButton(false)
        binding.previewButton.isEnabled = false
        binding.recordButton.setOnClickListener(mOnClickListener)
        binding.recordButton.isEnabled = false
        binding.stillButton.setOnClickListener(mOnClickListener)
        binding.stillButton.isEnabled = false

        binding.cameraView.setAspectRatio((UVCCamera.DEFAULT_PREVIEW_WIDTH / UVCCamera.DEFAULT_PREVIEW_HEIGHT.toFloat()).toDouble())
        binding.cameraViewSub.setOnClickListener(mOnClickListener)
//        mUSBMonitor = USBMonitor(context, mOnDeviceConnectListener)

    }

    override fun onResume() {
        super.onResume()
        mUSBMonitor!!.register()
    }

    override fun onPause() {
        if (mCameraClient != null) {
            mCameraClient!!.removeSurface(binding.cameraView!!.surface)
            mCameraClient!!.removeSurface(binding.cameraViewSub!!.holder.surface)
            isSubView = false
        }
        mUSBMonitor!!.unregister()
        enableButtons(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (mCameraClient != null) {
            mCameraClient!!.release()
            mCameraClient = null
        }
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
            enableButtons(false)
            if (mCameraClient == null) mCameraClient = CameraClient(activity, mCameraListener)
            mCameraClient!!.select(list[index])
            mCameraClient!!.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
            mCameraClient!!.connect()
        }
    }

    fun getUSBMonitor(): USBMonitor? {
        return mUSBMonitor
    }

    private fun setPreviewButton(onoff: Boolean) {
        activity.runOnUiThread {
            binding.previewButton!!.setOnCheckedChangeListener(null)
            try {
                binding.previewButton.isChecked = onoff
            } finally {
                binding.previewButton.setOnCheckedChangeListener(mOnCheckedChangeListener)
            }
        }
    }

    private fun enableButtons(enable: Boolean) {
        setPreviewButton(false)
        activity.runOnUiThread {
            binding.previewButton!!.isEnabled = enable
            binding.recordButton!!.isEnabled = enable
            binding.stillButton!!.isEnabled = enable
            if (enable && mCameraClient!!.isRecording) binding.recordButton.setColorFilter(0x7fff0000) else binding.recordButton.setColorFilter(
                0
            )
        }
    }

    companion object {
        private const val CAPTURE_STOP = 0
        private const val CAPTURE_PREPARE = 1
        private const val CAPTURE_RUNNING = 2
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480

        private const val CAMERA_INDEX = 1

        fun newInstance() = UsbCameraFragment()
    }
}