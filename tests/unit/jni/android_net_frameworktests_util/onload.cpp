/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <nativehelper/JNIHelp.h>
#include "jni.h"

#define LOG_TAG "NetFrameworkTestsJni"
#include <android/log.h>

namespace android {

int register_com_android_net_module_util_BpfMap(JNIEnv* env, char const* class_name);
int register_com_android_net_module_util_TcUtils(JNIEnv* env, char const* class_name);
int register_com_android_net_module_util_ServiceConnectivityJni(JNIEnv *env,
                                                      char const *class_name);

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, "ERROR: GetEnv failed");
        return JNI_ERR;
    }

    if (register_com_android_net_module_util_BpfMap(env,
            "android/net/frameworktests/util/BpfMap") < 0) return JNI_ERR;

    if (register_com_android_net_module_util_TcUtils(env,
            "android/net/frameworktests/util/TcUtils") < 0) return JNI_ERR;

    if (register_com_android_net_module_util_ServiceConnectivityJni(
            env, "android/net/frameworktests/util/ServiceConnectivityJni") < 0)
      return JNI_ERR;

    return JNI_VERSION_1_6;
}

}; // namespace android
