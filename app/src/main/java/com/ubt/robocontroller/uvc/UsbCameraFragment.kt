package com.ubt.robocontroller

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import com.serenegiant.common.BaseFragment
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.ubt.robocontroller.databinding.FragmentUsbCameraBinding
import com.ubt.robocontroller.uvc.Encoder

class UsbCameraFragment : BaseFragment() {

    private lateinit var binding: FragmentUsbCameraBinding
    private var mPreviewSurface: Surface? = null
    private var mEncoder: Encoder? = null
    private var mCaptureState = 0
    private var mUSBMonitor: USBMonitor? = null
    private var mUVCCamera: UVCCamera? = null


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

    private val mOnDeviceConnectListener: USBMonitor.OnDeviceConnectListener =
        object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice) {
                Toast.makeText(context, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show()
            }

            override fun onConnect(
                device: UsbDevice,
                ctrlBlock: USBMonitor.UsbControlBlock,
                createNew: Boolean
            ) {
                synchronized(mSync) {
                    if (mUVCCamera != null) {
                        mUVCCamera?.destroy()
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
                    val st = binding.cameraView.surfaceTexture
                    if (st != null) {
                        mPreviewSurface = Surface(st)
                        camera.setPreviewDisplay(mPreviewSurface)
                        camera.startPreview()
                    }
                    synchronized(mSync) { mUVCCamera = camera }
                }, 0)
            }

            override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
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
                Toast.makeText(context, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show()
            }

            override fun onCancel(device: UsbDevice) {
                setCameraButton(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        binding.cameraView.setAspectRatio((UVCCamera.DEFAULT_PREVIEW_WIDTH / UVCCamera.DEFAULT_PREVIEW_HEIGHT.toFloat()).toDouble())
        binding.cameraView.surfaceTextureListener = mSurfaceTextureListener

        mUSBMonitor = USBMonitor(context, mOnDeviceConnectListener)

    }

    fun getUSBMonitor(): USBMonitor? {
        return mUSBMonitor
    }

    companion object {
        private const val CAPTURE_STOP = 0
        private const val CAPTURE_PREPARE = 1
        private const val CAPTURE_RUNNING = 2

        fun newInstance() = UsbCameraFragment()
    }
}