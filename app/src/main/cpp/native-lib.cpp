#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "stable-diffusion.h"

#define LOG_TAG "OfflineAIArt-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_example_offlineai_MainActivity_generateImageFromJNI(
        JNIEnv* env,
        jobject thiz,
        jstring model_path,
        jstring prompt,
        jstring negative_prompt,
        jint steps,
        jfloat cfg_scale,
        jint width,
        jint height,
        jlong seed
) {
    const char* c_model_path = env->GetStringUTFChars(model_path, nullptr);
    const char* c_prompt = env->GetStringUTFChars(prompt, nullptr);
    const char* c_neg_prompt = env->GetStringUTFChars(negative_prompt, nullptr);

    LOGI("Inicializando contexto de Stable Diffusion...");
    LOGI("Model: %s", c_model_path);
    LOGI("Prompt: %s", c_prompt);
    LOGI("Steps: %d, CFG: %f, Width: %d, Height: %d, Seed: %lld", steps, cfg_scale, width, height, (long long)seed);

    // 1. Crear el contexto de Stable Diffusion
    sd_ctx_t* ctx = new_sd_ctx(
            c_model_path,
            "", // VAE
            "", // TAESD
            "", // ControlNet
            "", // LoRA dir
            "", // Embeddings dir
            "", // Stacked ID dir
            true, // vae_decode_only
            false, // vae_tiling
            false, // free_params_immediately
            4, // n_threads (4 hilos es ideal para el S22 Ultra)
            SD_TYPE_F16, // wtype
            (rng_type_t)0, // rng_type (STD_DEFAULT_RNG = 0)
            (schedule_t)0, // schedule (DEFAULT = 0)
            false, // keep_clip_on_cpu
            false, // keep_control_net_cpu
            false  // keep_vae_on_cpu
    );

    if (ctx == nullptr) {
        LOGE("Error al inicializar el contexto (new_sd_ctx devolvió null).");
        env->ReleaseStringUTFChars(model_path, c_model_path);
        env->ReleaseStringUTFChars(prompt, c_prompt);
        env->ReleaseStringUTFChars(negative_prompt, c_neg_prompt);
        return nullptr;
    }

    LOGI("Generando imagen (txt2img)...");
    
    // 2. Generar la imagen usando txt2img.
    // Usamos EULER_A_SAMPLE_METHOD (valor = 1 en el enum sample_method_t)
    // batch_count = 1
    sd_image_t* result = txt2img(
            ctx,
            c_prompt,
            c_neg_prompt,
            0, // clip_skip
            cfg_scale,
            width,
            height,
            (sample_method_t)1, // EULER_A_SAMPLE_METHOD
            steps,
            seed,
            1 // batch_count
    );

    env->ReleaseStringUTFChars(model_path, c_model_path);
    env->ReleaseStringUTFChars(prompt, c_prompt);
    env->ReleaseStringUTFChars(negative_prompt, c_neg_prompt);

    if (result == nullptr) {
        LOGE("Error en la generación (txt2img devolvió null).");
        free_sd_ctx(ctx);
        return nullptr;
    }

    LOGI("Imagen generada con éxito. Tamaño: %dx%d, Canales: %d", result->width, result->height, result->channel);

    // 3. Convertir píxeles RGB de 3 canales a RGBA de 4 canales para Android
    int num_pixels = result->width * result->height;
    jbyteArray jdata = env->NewByteArray(num_pixels * 4);
    if (jdata != nullptr) {
        jbyte* elements = env->GetByteArrayElements(jdata, nullptr);
        if (elements != nullptr) {
            for (int i = 0; i < num_pixels; i++) {
                elements[i * 4 + 0] = (jbyte)result->data[i * 3 + 0]; // R
                elements[i * 4 + 1] = (jbyte)result->data[i * 3 + 1]; // G
                elements[i * 4 + 2] = (jbyte)result->data[i * 3 + 2]; // B
                elements[i * 4 + 3] = (jbyte)255;                    // A (opaco)
            }
            env->ReleaseByteArrayElements(jdata, elements, 0);
        }
    }

    // 4. Liberar memoria nativa
    free(result->data);
    free(result);
    free_sd_ctx(ctx);

    LOGI("Memoria liberada y datos RGBA listos.");
    return jdata;
}

}
