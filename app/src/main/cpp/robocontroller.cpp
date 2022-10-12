#include <jni.h>
#include "LogUtil.h"
#include "touchscreen.h"
#include "android_utils.h"

extern "C" {
#include "apple.h"
}

using namespace std;


int BUTTON_NUM = 4;

int markMode = 0;
int markIndex = 0;

void callback_func(int index, int code) {
    LOGCATD("-------------- callback_func: index: %i, code: %i", index, code);
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
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_TouchManager_test(JNIEnv *env, jobject thiz) {
    LOGCATD("name = %s", appleName());
    auto f = std::bind(&callback_func, std::placeholders::_1, std::placeholders::_2);
    f(1, 2);
    // 回调Java方法
    jclass cls = env->GetObjectClass(thiz);
    jmethodID onMarking_method_id = env->GetMethodID(cls, "onMarking", "(II)V");
    env->CallVoidMethod(thiz, onMarking_method_id, 1, 2);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_ubt_robocontroller_TouchManager_initialTouchPanel(JNIEnv *env, jobject thiz,
                                                           jobject list, jint pxWidth,
                                                           jint pxHeight) {
    LOGCATD("-------------- initial touch panel ----------------");
    jclass list_cls = env->FindClass("java/util/ArrayList");
    if (list_cls == nullptr) {
        LOGCATE("can not found ArrayList Class");
        return -1;
    }
    jmethodID list_get = env->GetMethodID(list_cls, "get", "(I)Ljava/lang/Object;");
    jmethodID list_size = env->GetMethodID(list_cls, "size", "()I");
    jclass jcls = env->FindClass("android/graphics/Point");
    if (jcls == nullptr) {
        LOGCATE("can not found Point Class");
        return -1;
    }
    jfieldID x_field = env->GetFieldID(jcls, "x", "I");
    jfieldID y_field = env->GetFieldID(jcls, "y", "I");
    int size = env->CallIntMethod(list, list_size);
    vector<MarkPoint> srcPoints;
    for (int i = 0; i < size; ++i) {
        jobject item = env->CallObjectMethod(list, list_get, i);
        int x = env->GetIntField(item, x_field);
        int y = env->GetIntField(item, y_field);
        srcPoints.emplace_back(MarkPoint(x, y));
    }

    // 测试
    //（5.2%，9.2%），（5.2%，90.7%），（94.8%，9.2%），（94.8%，90.7%）
    srcPoints[0] = MarkPoint(pxWidth*0.052, pxHeight*0.092);
    srcPoints[1] = MarkPoint(pxWidth*0.052, pxHeight*0.907);
    srcPoints[2] = MarkPoint(pxWidth*0.948, pxHeight*0.092);
    srcPoints[3] = MarkPoint(pxWidth*0.948, pxHeight*0.907);

    for (auto p : srcPoints) {
        LOGCATD("point: ( %f, %f )", p.m_xPoint, p.m_yPoint);
    }
    LOGCATD("screen: %i x %i", pxWidth, pxHeight);

    InTouchParam param;
    param.m_pxWidth = pxWidth;
    param.m_pxHeight = pxHeight;
//    param.m_maxTouch = 1;
    param.m_markCallBack = std::bind(&callback_func, std::placeholders::_1, std::placeholders::_2);
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
    cv::Mat frame;
    bitmap_to_mat(env, image, frame);
    cv::Mat dst;
    cvtColor(frame, dst, CV_BGRA2GRAY);
    switch (markMode) {
        case 0:
            break;
        case 1:
            LOGCATD("ProcessMarking %i", markIndex);
            ProcessMarking(markIndex, dst);
            break;
        case 2:
            LOGCATD("PorcessTouchData %i", markIndex);
            PorcessTouchData(dst);
            break;
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_TouchManager_setMarkIndex(JNIEnv *env, jobject thiz, jint index) {
    markIndex = index;
}