#include <jni.h>
#include <string>
#include <fstream>

static std::string g_model_path;
static bool g_backend_ready = false;

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nexus_qwen_MainActivity_nativeStatus(
        JNIEnv* env,
        jobject) {

    const char* text =
        g_backend_ready
        ? "Qwen3.5 • motor local pronto"
        : "Qwen3.5 • backend nativo aguardando";

    return env->NewStringUTF(text);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nexus_qwen_MainActivity_nativeLoadModel(
        JNIEnv* env,
        jobject,
        jstring path) {

    const char* raw = env->GetStringUTFChars(path, nullptr);

    if (!raw) {
        return env->NewStringUTF("Erro ao receber caminho do modelo");
    }

    g_model_path = raw;

    env->ReleaseStringUTFChars(path, raw);

    std::ifstream file(g_model_path, std::ios::binary);

    if (!file.good()) {
        return env->NewStringUTF("GGUF não encontrado ou inacessível");
    }

    /*
     * Ponte preparada.
     *
     * Aqui será ligado o llama.cpp Android ARMv7.
     *
     * O projeto NÃO chama o llama-server do Pydroid:
     * o objetivo final é executar a biblioteca nativa
     * diretamente dentro do processo do aplicativo.
     */

    g_backend_ready = false;

    return env->NewStringUTF(
        "GGUF encontrado • llama.cpp Android ainda não incorporado"
    );
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nexus_qwen_MainActivity_nativeChat(
        JNIEnv* env,
        jobject,
        jstring prompt) {

    if (!g_backend_ready) {
        return env->NewStringUTF(
            "O modelo foi localizado, mas o backend llama.cpp "
            "ainda precisa ser incorporado ao APK."
        );
    }

    return env->NewStringUTF(
        "Backend pronto."
    );
}
