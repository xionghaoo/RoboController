#include <jni.h>
#include "LogUtil.h"
#include "touchscreen.h"
#include "android_utils.h"

using namespace std;

int BUTTON_NUM = 4;

int markMode = 0;
int markIndex = -2;

jobject g_touchObj;
JavaVM *m_pJvm;
JNIEnv *m_pJniEnv;
bool m_bIsAttachedOnAThread = false;

JNIEnv* GetJniEnv() {

    // This method might have been called from a different thread than the one that created
    // this handler. Check to make sure that the JNI is attached and if not attach it to the
    // new thread.

    // double check it's all ok
    int nEnvStat = m_pJvm->GetEnv(reinterpret_cast<void**>(&m_pJniEnv), JNI_VERSION_1_6);
    LOGCATD("env status: %i", nEnvStat);
    if (nEnvStat == JNI_EDETACHED) {

        std::cout << "GetEnv: not attached. Attempting to attach" << std::endl;

        JavaVMAttachArgs args;
        args.version = JNI_VERSION_1_6; // choose your JNI version
        args.name = NULL; // you might want to give the java thread a name
        args.group = NULL; // you might want to assign the java thread to a ThreadGroup

        if (m_pJvm->AttachCurrentThread(&m_pJniEnv, &args) != 0) {
            std::cout << "Failed to attach" << std::endl;
            return nullptr;
        }

        thread_local struct DetachJniOnExit {
            ~DetachJniOnExit() {
                m_pJniEnv->DeleteGlobalRef(g_touchObj);
                m_pJvm->DetachCurrentThread();
            }
        };


        m_bIsAttachedOnAThread = true;

    }
    else if (nEnvStat == JNI_OK) {
        //
    }
    else if (nEnvStat == JNI_EVERSION) {
        std::cout << "GetEnv: version not supported" << std::endl;
        return nullptr;
    }


    return m_pJniEnv;
}

extern "C"
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    m_pJvm = vm;
    JNIEnv *env = GetJniEnv();
    if (env == nullptr) {
        return JNI_FALSE;
    }
    return JNI_VERSION_1_6;
}

void on_marking(int index, int code) {
    GetJniEnv();
    // 回调Java方法
    jclass cls = m_pJniEnv->GetObjectClass(g_touchObj);
    jmethodID onMarking_method_id = m_pJniEnv->GetMethodID(cls, "onMarking", "(II)V");
    m_pJniEnv->CallVoidMethod(g_touchObj, onMarking_method_id, index, code);
}

// 触控日志回调
//tackingId ：触控ID
//x	        : x像素坐标
//y		    : y像素坐标
//bDown     : true->按下事件，false->抬起事件
void log_callback(const char* msg) {
    LOGCATD("log_callback: %s", msg);
}

void callback_func(int index, int code) {
    LOGCATD("-------------- callback_func: index: %i, code: %i", index, code);
    on_marking(index, code);
    switch (code) {
        //标定完成 可以使用
        case 1606:
        {
            //清除标定缓存
            //停止显示动画
            //设置 正常工作 模式

        }
            break;
            //显示采集动画，时间：300ms
        case 1600:
            break;
            //显示下一个点
        case 1:
        {
            if(index >= 0)
            {
                if( index == (BUTTON_NUM-1))	//4个点标定完成
                {
                    //显示等待动画
                }

                else if( index >= 0)
                {
                    //显示下一个标定点
                }

            }
        }
            break;

        case 2:
        {
            //显示错误信息，等待返回码 102

        }
            break;

        case 102:
        {
            //继续采集标定数据
        }
            break;


            //标定出现错误，退出整个流程
        case -1:
        {
        }
            break;


        default:
            break;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_ubt_robocontroller_TouchManager_initialTouchPanel(JNIEnv *env, jobject thiz,
                                                           jobject list, jint pxWidth,
                                                           jint pxHeight) {
    LOGCATD("-------------- initial touch panel start ----------------");
    g_touchObj = env->NewGlobalRef(thiz);

    jclass list_cls = env->FindClass("java/util/ArrayList");
    if (list_cls == nullptr) {
        LOGCATE("can not found ArrayList Class");
        return -1;
    }
    jmethodID list_get = env->GetMethodID(list_cls, "get", "(I)Ljava/lang/Object;");
    jmethodID list_size = env->GetMethodID(list_cls, "size", "()I");
    jclass jcls = env->FindClass("android/graphics/PointF");
    if (jcls == nullptr) {
        LOGCATE("can not found Point Class");
        return -1;
    }
    jfieldID x_field = env->GetFieldID(jcls, "x", "F");
    jfieldID y_field = env->GetFieldID(jcls, "y", "F");
    int size = env->CallIntMethod(list, list_size);
    vector<MarkPoint> srcPoints;
    for (int i = 0; i < size; ++i) {
        jobject item = env->CallObjectMethod(list, list_get, i);
        float x = env->GetFloatField(item, x_field);
        float y = env->GetFloatField(item, y_field);
        srcPoints.emplace_back(MarkPoint(x, y));
    }

    for (auto p : srcPoints) {
        LOGCATD("point: ( %f, %f )", p.m_xPoint, p.m_yPoint);
    }
    LOGCATD("screen: %i x %i", pxWidth, pxHeight);

    InTouchParam param;
    param.m_pxWidth = pxWidth;
    param.m_pxHeight = pxHeight;
//    param.m_maxTouch = 1;
    param.m_markCallBack = std::bind(&callback_func, std::placeholders::_1, std::placeholders::_2);
    param.m_logCallBack = std::bind(&log_callback, std::placeholders::_1);
    param.m_markPoints = srcPoints;
    param.m_dataPath = "/sdcard/Download";
    int ret = InitTouchScreen(param);
    LOGCATD("InitTouchScreen: %i", ret);
    LOGCATD("-------------- initial touch panel end ----------------");
    return ret;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_TouchManager_marking(JNIEnv *env, jobject thiz, jint index,
                                                 jobject image) {

    cv::Mat frame;
    bitmap_to_mat(env, image, frame);
    cv::Mat dst;
    cvtColor(frame, dst, CV_BGRA2GRAY);
    int ret = ProcessMarking(index, dst);
    LOGCATD("ProcessMarking: %i", ret);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_TouchManager_setCurrentMode(JNIEnv *env, jobject thiz, jint mode) {
    int ret = SetCurMode(mode);
    markMode = mode;
    LOGCATD("SetCurMode: %i", ret);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_TouchManager_process(JNIEnv *env, jobject thiz, jobject image) {
    LOGCATD("TouchManager_process: %i %i", markMode, markIndex);
    cv::Mat frame;
    bitmap_to_mat(env, image, frame);
    cv::Mat dst;
    cvtColor(frame, dst, CV_BGRA2GRAY);
    switch (markMode) {
        case 0:
            break;
        case 8:
        case 1:
//            LOGCATD("ProcessMarking index = %i，image size: %i x %i", markIndex, dst.cols, dst.rows);
            if (markIndex >= -2) {
                int ret = ProcessMarking(markIndex, dst);
                LOGCATD("ProcessMarking ret = %i", ret);
            }
            break;
        case 2:
            PorcessTouchData(dst);
            break;
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_TouchManager_setMarkIndex(JNIEnv *env, jobject thiz, jint index) {
    markIndex = index;
    LOGCATD("setMarkIndex: %i", index);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_TouchManager_setMaskArea(JNIEnv *env, jobject thiz, jint x, jint y,
                                                     jint width, jint height, jboolean isMask) {
    GenMask(x, y, width, height, isMask);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_TouchManager_yuvToRbga(JNIEnv *env, jobject thiz, jint width,
                                                   jint height, jbyteArray yuvData_,
                                                   jobject rbgaImg) {
    // yuv to rbga
    jbyte *yuvData = env->GetByteArrayElements(yuvData_, NULL);
    Mat dstMat(height, width, CV_8UC1);
    Mat mYuv(height + height/2, width, CV_8UC1, (uchar *)yuvData);
    cvtColor(mYuv, dstMat, COLOR_YUV2BGRA_NV21);
    env->ReleaseByteArrayElements(yuvData_, yuvData, 0);
    if (dstMat.cols == 0) return;
    mat_to_bitmap(env, dstMat, rbgaImg);
}