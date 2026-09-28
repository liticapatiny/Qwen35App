#include <jni.h>
#include <string>
#include <vector>
#include <mutex>

#include "llama.h"

static std::mutex g_mutex;

static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static llama_sampler * g_sampler = nullptr;

static std::string g_model_path;
static bool g_backend_ready = false;


static void liberar_backend()
{
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    g_backend_ready = false;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_nexus_qwen_MainActivity_nativeStatus(
        JNIEnv* env,
        jobject)
{
    std::lock_guard<std::mutex> lock(g_mutex);

    const char* texto =
        g_backend_ready
        ? "Qwen3.5 • motor local pronto"
        : "Qwen3.5 • backend nativo aguardando";

    return env->NewStringUTF(texto);
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_nexus_qwen_MainActivity_nativeLoadModel(
        JNIEnv* env,
        jobject,
        jstring path)
{
    std::lock_guard<std::mutex> lock(g_mutex);

    const char* raw =
        env->GetStringUTFChars(path, nullptr);

    if (!raw) {
        return env->NewStringUTF(
            "Erro ao receber caminho do modelo"
        );
    }

    g_model_path = raw;

    env->ReleaseStringUTFChars(path, raw);

    liberar_backend();

    llama_backend_init();

    llama_model_params model_params =
        llama_model_default_params();

    model_params.n_gpu_layers = 0;

    g_model =
        llama_model_load_from_file(
            g_model_path.c_str(),
            model_params
        );

    if (!g_model) {
        llama_backend_free();

        return env->NewStringUTF(
            "Falha ao carregar o modelo GGUF"
        );
    }

    llama_context_params context_params =
        llama_context_default_params();

    context_params.n_ctx = 512;
    context_params.n_batch = 512;

    g_context =
        llama_init_from_model(
            g_model,
            context_params
        );

    if (!g_context) {
        llama_model_free(g_model);
        g_model = nullptr;

        llama_backend_free();

        return env->NewStringUTF(
            "Modelo carregado, mas contexto Qwen falhou"
        );
    }

    g_sampler =
        llama_sampler_chain_init(
            llama_sampler_chain_default_params()
        );

    if (!g_sampler) {
        llama_free(g_context);
        g_context = nullptr;

        llama_model_free(g_model);
        g_model = nullptr;

        llama_backend_free();

        return env->NewStringUTF(
            "Modelo carregado, mas sampler falhou"
        );
    }

    llama_sampler_chain_add(
        g_sampler,
        llama_sampler_init_greedy()
    );

    g_backend_ready = true;

    return env->NewStringUTF(
        "GGUF carregado • llama.cpp ARMv7 pronto"
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_nexus_qwen_MainActivity_nativeChat(
        JNIEnv* env,
        jobject,
        jstring prompt)
{
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_backend_ready ||
        !g_model ||
        !g_context ||
        !g_sampler) {

        return env->NewStringUTF(
            "O backend Qwen ainda não carregou."
        );
    }

    const char* raw =
        env->GetStringUTFChars(prompt, nullptr);

    if (!raw) {
        return env->NewStringUTF(
            "Erro ao receber a mensagem."
        );
    }

    std::string texto(raw);

    env->ReleaseStringUTFChars(prompt, raw);

    const llama_vocab* vocab =
        llama_model_get_vocab(g_model);

    if (!vocab) {
        return env->NewStringUTF(
            "Erro ao acessar o vocabulário Qwen."
        );
    }

    int n_tokens =
        -llama_tokenize(
            vocab,
            texto.c_str(),
            texto.size(),
            nullptr,
            0,
            true,
            true
        );

    if (n_tokens <= 0) {
        return env->NewStringUTF(
            "Não foi possível tokenizar a mensagem."
        );
    }

    std::vector<llama_token> tokens(n_tokens);

    int result =
        llama_tokenize(
            vocab,
            texto.c_str(),
            texto.size(),
            tokens.data(),
            tokens.size(),
            true,
            true
        );

    if (result < 0) {
        return env->NewStringUTF(
            "Falha na tokenização."
        );
    }

    llama_batch batch =
        llama_batch_get_one(
            tokens.data(),
            tokens.size()
        );

    if (llama_decode(g_context, batch) != 0) {
        return env->NewStringUTF(
            "Falha ao executar llama_decode."
        );
    }

    std::string resposta;

    for (int i = 0; i < 64; ++i) {

        llama_token token =
            llama_sampler_sample(
                g_sampler,
                g_context,
                -1
            );

        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        char buffer[256];

        int len =
            llama_token_to_piece(
                vocab,
                token,
                buffer,
                sizeof(buffer),
                0,
                true
            );

        if (len > 0) {
            resposta.append(buffer, len);
        }

        llama_sampler_accept(
            g_sampler,
            token
        );

        llama_batch next =
            llama_batch_get_one(
                &token,
                1
            );

        if (llama_decode(g_context, next) != 0) {
            break;
        }
    }

    if (resposta.empty()) {
        resposta =
            "O Qwen executou, mas não retornou texto.";
    }

    return env->NewStringUTF(
        resposta.c_str()
    );
}
