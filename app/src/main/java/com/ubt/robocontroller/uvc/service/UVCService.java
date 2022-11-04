/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.ubt.robocontroller.uvc.service;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_MUTABLE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.PointF;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;

import androidx.core.app.NotificationCompat;

import com.serenegiant.common.BaseService;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.ubt.robocontroller.Config;
import com.ubt.robocontroller.IUVCService;
import com.ubt.robocontroller.IUVCServiceCallback;
import com.ubt.robocontroller.IUVCServiceOnFrameAvailable;
import com.ubt.robocontroller.IUVCSlaveService;
import com.ubt.robocontroller.R;
import com.ubt.robocontroller.UVCActivity;
import com.ubt.robocontroller.uvc.serviceclient.ICameraClient;

import java.util.ArrayList;
import java.util.List;

public class UVCService extends BaseService {
	private static final boolean DEBUG = true;
	private static final String TAG = "UVCService";
	private static final int NOTIFICATION = R.string.app_name;
	public static final String EXTRA_POINTS = "com.ubt.robocontroller.UVCService.EXTRA_POINTS";
	public static final String EXTRA_BOOT_CMD = "com.ubt.robocontroller.UVCService.EXTRA_BOOT_CMD";

	private USBMonitor mUSBMonitor;
	private NotificationManager mNotificationManager;
	private ArrayList<PointF> points;
	private Boolean isBoot = false;
	private ICameraClient cameraClient = null;

	public UVCService() {
		if (DEBUG) Log.d(TAG, "Constructor:");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (DEBUG) Log.d(TAG, "onCreate:");

		if (mUSBMonitor == null) {
			mUSBMonitor = new USBMonitor(getApplicationContext(), mOnDeviceConnectListener);
			List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
			mUSBMonitor.setDeviceFilter(filters);
			mUSBMonitor.register();
		}

		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		showNotification(getString(R.string.app_name));
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand: ");
		boolean isBoot = intent.getBooleanExtra(EXTRA_BOOT_CMD, false);
		this.isBoot = isBoot;
		if (isBoot) {
//			List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
//			UsbManager mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
//			List<UsbDevice> devices = us
//			for (int i = 0; i < ; i++) {
//
//			}
//			for (int i = 0; i < filters.size(); i++) {
//				DeviceFilter filter = filters.get(i);
//				if (filter.mProductId == )
//			}
//			mUSBMonitor.requestPermission()
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.d(TAG, "onDestroy:");
		if (mUSBMonitor != null) {
			mUSBMonitor.unregister();
			mUSBMonitor = null;
		}
		stopForeground(true/*removeNotification*/);
		if (mNotificationManager != null) {
			mNotificationManager.cancel(NOTIFICATION);
			mNotificationManager = null;
		}
		super.onDestroy();
	}

	@Override
	public IBinder onBind(final Intent intent) {
		if (DEBUG) Log.d(TAG, "onBind:" + intent);

		points = intent.getParcelableArrayListExtra(EXTRA_POINTS);

		final String action = intent != null ? intent.getAction() : null;
		if (IUVCService.class.getName().equals(action)) {
			Log.i(TAG, "return mBasicBinder");
			return mBasicBinder;
		}
		if (IUVCSlaveService.class.getName().equals(action)) {
			Log.i(TAG, "return mSlaveBinder");
			return mSlaveBinder;
		}
		return null;
	}

	@Override
	public void onRebind(final Intent intent) {
		if (DEBUG) Log.d(TAG, "onRebind:" + intent);
	}

	@Override
	public boolean onUnbind(final Intent intent) {
		if (DEBUG) Log.d(TAG, "onUnbind:" + intent);
		if (checkReleaseService()) {
			stopSelf();
		}
		if (DEBUG) Log.d(TAG, "onUnbind:finished");
		return true;
	}

//********************************************************************************
	/**
	 * helper method to show/change message on notification area
	 * and set this service as foreground service to keep alive as possible as this can.
	 * @param text
	 */
	private void showNotification(final CharSequence text) {
		if (DEBUG) Log.v(TAG, "showNotification:" + text);
		Intent intent = new Intent(this, UVCActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_IMMUTABLE);
        // Set the info for the views that show in the notification panel.
        final Notification notification = new NotificationCompat.Builder(this, "robo_uvc_camera_channel")
			.setSmallIcon(R.mipmap.ic_launcher)  // the status icon
			.setTicker(text)  // the status text
			.setWhen(System.currentTimeMillis())  // the time stamp
			.setContentTitle(getText(R.string.app_name))  // the label of the entry
			.setContentText(text)  // the contents of the entry
			.setContentIntent(pendingIntent)  // The intent to send when the entry is clicked
			.build();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Create the NotificationChannel, but only on API 26+ because
			// the NotificationChannel class is new and not in the support library
			String name = "robo_uvc_camera";
			String description = "robo_uvc_camera";
			NotificationChannel channel = new NotificationChannel("robo_uvc_camera_channel", name, NotificationManager.IMPORTANCE_DEFAULT);
			channel.setSound(null, null);
			channel.setDescription(description);
			// Register the channel with the system
			mNotificationManager.createNotificationChannel(channel);
		}

		startForeground(NOTIFICATION, notification);
        // Send the notification.
		mNotificationManager.notify(NOTIFICATION, notification);
    }

	private void openCamera(UsbDevice device) {
		mUSBMonitor.requestPermission(device);
	};

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onAttach:");
			if (isBoot) {
				openCamera(device);
			}
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onConnect:");

			queueEvent(new Runnable() {
				@Override
				public void run() {
					final int key = device.hashCode();
					CameraServer service;
					synchronized (sServiceSync) {
						service = sCameraServers.get(key);
						if (service == null) {
							service = CameraServer.createServer(
									UVCService.this,
									ctrlBlock,
									device.getVendorId(),
									device.getProductId(),
									points
							);
							sCameraServers.append(key, service);
						} else {
							Log.w(TAG, "service already exist before connection");
						}
						sServiceSync.notifyAll();

					}

					if (isBoot) {
						service.connect();
						isBoot = false;
					}
				}
			}, 0);
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onDisconnect:");
			queueEvent(new Runnable() {
				@Override
				public void run() {
					removeService(device);
				}
			}, 0);
		}

		@Override
		public void onDettach(final UsbDevice device) {
			if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onDettach:");
		}

		@Override
		public void onCancel(final UsbDevice device) {
			if (DEBUG) Log.d(TAG, "OnDeviceConnectListener#onCancel:");
			synchronized (sServiceSync) {
				sServiceSync.notifyAll();
			}
		}
	};

	private void removeService(final UsbDevice device) {
		final int key = device.hashCode();
		synchronized (sServiceSync) {
			final CameraServer service = sCameraServers.get(key);
			if (service != null)
				service.release();
			sCameraServers.remove(key);
			sServiceSync.notifyAll();
		}
		if (checkReleaseService()) {
			stopSelf();
		}
	}
//********************************************************************************
	private static final Object sServiceSync = new Object();
	private static final SparseArray<CameraServer> sCameraServers = new SparseArray<CameraServer>();

	/**
	 * get CameraService that has specific ID<br>
	 * if zero is provided as ID, just return top of CameraServer instance(non-blocking method) if exists or null.<br>
	 * if non-zero ID is provided, return specific CameraService if exist. block if not exists.<br>
	 * return null if not exist matched specific ID<br>
	 * @param serviceId
	 * @return
	 */
	private static CameraServer getCameraServer(final int serviceId) {
		synchronized (sServiceSync) {
			CameraServer server = null;
			if ((serviceId == 0) && (sCameraServers.size() > 0)) {
				server = sCameraServers.valueAt(0);
			} else {
				server = sCameraServers.get(serviceId);
				if (server == null)
					try {
						Log.i(TAG, "waiting for service is ready");
						sServiceSync.wait();
					} catch (final InterruptedException e) {
					}
					server = sCameraServers.get(serviceId);
			}
			return server;
		}
	}

	/**
	 * @return true if there are no camera connection
	 */
	private static boolean checkReleaseService() {
		CameraServer server = null;
		synchronized (sServiceSync) {
			final int n = sCameraServers.size();
			if (DEBUG) Log.d(TAG, "checkReleaseService:number of service=" + n);
			for (int i = 0; i < n; i++) {
				server = sCameraServers.valueAt(i);
				Log.i(TAG, "checkReleaseService:server=" + server + ",isConnected=" + (server != null && server.isConnected()));
				if (server != null && !server.isConnected()) {
					sCameraServers.removeAt(i);
					server.release();
				}
			}
			return sCameraServers.size() == 0;
		}
	}

//********************************************************************************
	private final IUVCService.Stub mBasicBinder = new IUVCService.Stub() {
		private IUVCServiceCallback mCallback;

		@Override
		public int select(final UsbDevice device, final IUVCServiceCallback callback) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#select:device=" + (device !=null ? device.getDeviceName() : null));
			mCallback = callback;
			final int serviceId = device.hashCode();
			CameraServer server = null;
			synchronized (sServiceSync) {
				server = sCameraServers.get(serviceId);
				if (server == null) {
					Log.i(TAG, "request permission");
					mUSBMonitor.requestPermission(device);
					Log.i(TAG, "wait for getting permission");
					try {
						sServiceSync.wait();
					} catch (final Exception e) {
						Log.e(TAG, "connect:", e);
					}
					Log.i(TAG, "check service again");
					server = sCameraServers.get(serviceId);
					if (server == null) {
						throw new RuntimeException("failed to open USB device(has no permission)");
					}
				}
			}
			if (server != null) {
				Log.i(TAG, "success to get service:serviceId=" + serviceId);
				server.registerCallback(callback);
			}
			return serviceId;
		}

		@Override
		public void release(final int serviceId) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#release:");
			synchronized (sServiceSync) {
				final CameraServer server = sCameraServers.get(serviceId);
				if (server != null) {
					if (server.unregisterCallback(mCallback)) {
						if (!server.isConnected()) {
							sCameraServers.remove(serviceId);
							if (server != null) {
								server.release();
							}
							final CameraServer srv = sCameraServers.get(serviceId);
							Log.w(TAG, "srv=" + srv);
						}
					}
				}
			}
			mCallback = null;
		}

		@Override
		public boolean isSelected(final int serviceId) throws RemoteException {
			return getCameraServer(serviceId) != null;
		}

		@Override
		public void releaseAll() throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#releaseAll:");
			CameraServer server;
			synchronized (sServiceSync) {
				final int n = sCameraServers.size();
				for (int i = 0; i < n; i++) {
					server = sCameraServers.valueAt(i);
					sCameraServers.removeAt(i);
					if (server != null) {
						server.release();
					}
				}
			}
		}

		@Override
		public void resize(final int serviceId, final int width, final int height) {
			if (DEBUG) Log.d(TAG, "mBasicBinder#resize:");
			final CameraServer server = getCameraServer(serviceId);
			if (server == null) {
				throw new IllegalArgumentException("invalid serviceId");
			}
			server.resize(width, height);
		}

		@Override
		public void connect(final int serviceId) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#connect:");
			final CameraServer server = getCameraServer(serviceId);
			if (server == null) {
				throw new IllegalArgumentException("invalid serviceId");
			}
			server.connect();
		}

		@Override
		public void disconnect(final int serviceId) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#disconnect:");
			final CameraServer server = getCameraServer(serviceId);
			if (server == null) {
				throw new IllegalArgumentException("invalid serviceId");
			}
			server.disconnect();
		}

		@Override
		public boolean isConnected(final int serviceId) throws RemoteException {
			final CameraServer server = getCameraServer(serviceId);
			return (server != null) && server.isConnected();
		}

		@Override
		public void addSurface(final int serviceId, final int id_surface, final Surface surface, final boolean isRecordable) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#addSurface:id=" + id_surface + ",surface=" + surface);
			final CameraServer server = getCameraServer(serviceId);
			if (server != null)
				server.addSurface(id_surface, surface, isRecordable, null);
		}

		@Override
		public void removeSurface(final int serviceId, final int id_surface) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#removeSurface:id=" + id_surface);
			final CameraServer server = getCameraServer(serviceId);
			if (server != null)
				server.removeSurface(id_surface);
		}

		@Override
		public boolean isRecording(final int serviceId) throws RemoteException {
			final CameraServer server = getCameraServer(serviceId);
			return server != null && server.isRecording();
		}

		@Override
		public void startRecording(final int serviceId) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#startRecording:");
			final CameraServer server = getCameraServer(serviceId);
			if ((server != null) && !server.isRecording()) {
				server.startRecording();
			}
		}

		@Override
		public void stopRecording(final int serviceId) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#stopRecording:");
			final CameraServer server = getCameraServer(serviceId);
			if ((server != null) && server.isRecording()) {
				server.stopRecording();
			}
		}

		@Override
		public void captureStillImage(final int serviceId, final String path) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#captureStillImage:" + path);
			final CameraServer server = getCameraServer(serviceId);
			if (server != null) {
				server.captureStill(path);
			}
		}

		@Override
		public void setExposureMode(int serviceId, int mode) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#setExposureMode:" + mode);
			final CameraServer server = getCameraServer(serviceId);
			if (server != null) {
				server.setExposureMode(mode);
			}
		}

		@Override
		public void setExposure(int serviceId, int exposure) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mBasicBinder#setExposure:" + exposure);
			final CameraServer server = getCameraServer(serviceId);
			if (server != null) {
				server.setExposure(exposure);
			}
		}

		@Override
		public int getExposure(int serviceId) throws RemoteException {
			final CameraServer server = getCameraServer(serviceId);
			if (server != null) {
				return server.getExposure();
			} else {
				return -1;
			}
		}
};

//********************************************************************************
	private final IUVCSlaveService.Stub mSlaveBinder = new IUVCSlaveService.Stub() {
		@Override
		public boolean isSelected(final int serviceID) throws RemoteException {
			return getCameraServer(serviceID) != null;
		}

		@Override
		public boolean isConnected(final int serviceID) throws RemoteException {
			final CameraServer server = getCameraServer(serviceID);
			return server != null && server.isConnected();
		}

		@Override
		public void addSurface(final int serviceID, final int id_surface, final Surface surface, final boolean isRecordable, final IUVCServiceOnFrameAvailable callback) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mSlaveBinder#addSurface:id=" + id_surface + ",surface=" + surface);
			final CameraServer server = getCameraServer(serviceID);
			if (server != null) {
				server.addSurface(id_surface, surface, isRecordable, callback);
			} else {
				Log.e(TAG, "failed to get CameraServer:serviceID=" + serviceID);
			}
		}

		@Override
		public void removeSurface(final int serviceID, final int id_surface) throws RemoteException {
			if (DEBUG) Log.d(TAG, "mSlaveBinder#removeSurface:id=" + id_surface);
			final CameraServer server = getCameraServer(serviceID);
			if (server != null) {
				server.removeSurface(id_surface);
			} else {
				Log.e(TAG, "failed to get CameraServer:serviceID=" + serviceID);
			}
		}
	};

}
