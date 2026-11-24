#include <jni.h>
#include <string>
#include <vector>
#include <cmath>

extern "C" {

JNIEXPORT jdouble JNICALL
Java_com_waqar_security_advanced_NativeSecurityBridge_computeChaoticOrbit(
    JNIEnv* env,
    jclass clazz,
    jdouble initialX,
    jint iterations
) {
    double x = initialX;
    constexpr double r = 3.999999999999999;
    for (int i = 0; i < iterations; ++i) {
        x = r * x * (1.0 - x);
    }
    return static_cast<jdouble>(x);
}

}
