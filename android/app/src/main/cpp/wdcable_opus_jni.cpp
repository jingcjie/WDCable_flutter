#include <jni.h>
#include <cstdint>

extern "C" {
struct OpusEncoder;
struct OpusDecoder;

const char *opus_get_version_string();
OpusEncoder *opus_encoder_create(int Fs, int channels, int application, int *error);
void opus_encoder_destroy(OpusEncoder *st);
int opus_encode(OpusEncoder *st, const int16_t *pcm, int frame_size, unsigned char *data, int max_data_bytes);
int opus_encoder_ctl(OpusEncoder *st, int request, ...);
OpusDecoder *opus_decoder_create(int Fs, int channels, int *error);
void opus_decoder_destroy(OpusDecoder *st);
int opus_decode(OpusDecoder *st, const unsigned char *data, int len, int16_t *pcm, int frame_size, int decode_fec);
}

namespace {
constexpr int OPUS_OK = 0;
constexpr int OPUS_APPLICATION_VOIP = 2048;
constexpr int OPUS_SET_BITRATE_REQUEST = 4002;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jingcjie_wifi_1direct_1cable_audio_NativeOpus_nativeVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF(opus_get_version_string());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jingcjie_wifi_1direct_1cable_audio_NativeOpus_nativeCreateEncoder(
    JNIEnv *, jobject, jint sample_rate, jint channels, jint bitrate_bps) {
    int error = 0;
    OpusEncoder *encoder = opus_encoder_create(sample_rate, channels, OPUS_APPLICATION_VOIP, &error);
    if (error != OPUS_OK || encoder == nullptr) {
        return 0;
    }
    opus_encoder_ctl(encoder, OPUS_SET_BITRATE_REQUEST, bitrate_bps);
    return reinterpret_cast<jlong>(encoder);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jingcjie_wifi_1direct_1cable_audio_NativeOpus_nativeDestroyEncoder(
    JNIEnv *, jobject, jlong handle) {
    if (handle != 0) {
        opus_encoder_destroy(reinterpret_cast<OpusEncoder *>(handle));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_jingcjie_wifi_1direct_1cable_audio_NativeOpus_nativeEncode(
    JNIEnv *env,
    jobject,
    jlong handle,
    jbyteArray pcm,
    jint frame_size,
    jbyteArray output,
    jint max_output_bytes) {
    if (handle == 0 || pcm == nullptr || output == nullptr) {
        return -1;
    }

    jbyte *pcm_bytes = env->GetByteArrayElements(pcm, nullptr);
    jbyte *output_bytes = env->GetByteArrayElements(output, nullptr);
    if (pcm_bytes == nullptr || output_bytes == nullptr) {
        if (pcm_bytes != nullptr) env->ReleaseByteArrayElements(pcm, pcm_bytes, JNI_ABORT);
        if (output_bytes != nullptr) env->ReleaseByteArrayElements(output, output_bytes, JNI_ABORT);
        return -1;
    }

    int encoded = opus_encode(
        reinterpret_cast<OpusEncoder *>(handle),
        reinterpret_cast<const int16_t *>(pcm_bytes),
        frame_size,
        reinterpret_cast<unsigned char *>(output_bytes),
        max_output_bytes
    );
    env->ReleaseByteArrayElements(pcm, pcm_bytes, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_bytes, 0);
    return encoded;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jingcjie_wifi_1direct_1cable_audio_NativeOpus_nativeCreateDecoder(
    JNIEnv *, jobject, jint sample_rate, jint channels) {
    int error = 0;
    OpusDecoder *decoder = opus_decoder_create(sample_rate, channels, &error);
    if (error != OPUS_OK || decoder == nullptr) {
        return 0;
    }
    return reinterpret_cast<jlong>(decoder);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jingcjie_wifi_1direct_1cable_audio_NativeOpus_nativeDestroyDecoder(
    JNIEnv *, jobject, jlong handle) {
    if (handle != 0) {
        opus_decoder_destroy(reinterpret_cast<OpusDecoder *>(handle));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_jingcjie_wifi_1direct_1cable_audio_NativeOpus_nativeDecode(
    JNIEnv *env,
    jobject,
    jlong handle,
    jbyteArray packet,
    jint packet_length,
    jint frame_size,
    jbyteArray pcm,
    jboolean decode_fec) {
    if (handle == 0 || pcm == nullptr) {
        return -1;
    }

    jbyte *packet_bytes = nullptr;
    if (packet != nullptr && packet_length > 0) {
        packet_bytes = env->GetByteArrayElements(packet, nullptr);
        if (packet_bytes == nullptr) {
            return -1;
        }
    }

    jbyte *pcm_bytes = env->GetByteArrayElements(pcm, nullptr);
    if (pcm_bytes == nullptr) {
        if (packet_bytes != nullptr) env->ReleaseByteArrayElements(packet, packet_bytes, JNI_ABORT);
        return -1;
    }

    int decoded = opus_decode(
        reinterpret_cast<OpusDecoder *>(handle),
        reinterpret_cast<const unsigned char *>(packet_bytes),
        packet_bytes == nullptr ? 0 : packet_length,
        reinterpret_cast<int16_t *>(pcm_bytes),
        frame_size,
        decode_fec ? 1 : 0
    );
    if (packet_bytes != nullptr) {
        env->ReleaseByteArrayElements(packet, packet_bytes, JNI_ABORT);
    }
    env->ReleaseByteArrayElements(pcm, pcm_bytes, 0);
    return decoded;
}
