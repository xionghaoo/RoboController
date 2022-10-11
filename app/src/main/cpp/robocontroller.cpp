#include <jni.h>
#include "LogUtil.h"

extern "C" {
#include "apple.h"
}


extern "C"
JNIEXPORT void JNICALL
Java_com_ubt_robocontroller_AiManager_test(JNIEnv *env, jobject thiz) {
    LOGCATD("name = %s", appleName());
}