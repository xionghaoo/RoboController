# UVC摄像头

## UVCService

### IUVCService.Stub
+ `int select(final UsbDevice device, final IUVCServiceCallback callback)`: 
    选择设备，会先申请权限`mUSBMonitor.requestPermission(device)`

+ `void resize(final int serviceId, final int width, final int height)`
    设置图片尺寸

+ `void connect(final int serviceId)`
```java
final CameraServer server = getCameraServer(serviceId);
if (server == null) {
    throw new IllegalArgumentException("invalid serviceId");
}
server.connect();  
```

### CameraServer
> 相机最终实现

+ `void handleOpen()`: 开启相机，设置帧捕获回调
```java
mUVCCamera = new UVCCamera();
mUVCCamera.open(mCtrlBlock);

mUVCCamera.setFrameCallback(new IFrameCallback() {
    @Override
    public void onFrame(ByteBuffer frame) {
        Timber.d("on image avaliable: " + frame);
    }
}, UVCCamera.PIXEL_FORMAT_YUV420SP);
```


+ `void handleStartPreview(final int width, final int height, final Surface surface)`:
开启预览画面
```java
try {
    mUVCCamera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG);
} catch (final IllegalArgumentException e) {
    try {
        // fallback to YUV mode
        mUVCCamera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE);
    } catch (final IllegalArgumentException e1) {
        mUVCCamera.destroy();
        mUVCCamera = null;
    }
}
if (mUVCCamera == null) return;
// mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_YUV);
mFrameWidth = width;
mFrameHeight = height;
mUVCCamera.setPreviewDisplay(surface);
mUVCCamera.startPreview();
```

## USBMonitor

+ `deviceList`: 获取USB设备列表


## rk3588和手机不一致的地方
1. 手机帧率30帧
2. device_filter.xml 指定相机
3. TouchManager手机上没有权限处理uinput设备

## 问题记录

### 1. 打开相机后预览失败，也没有帧回调

原因：帧率不匹配

### 2. 没有打开相机

原因: 过滤文件的pid不对