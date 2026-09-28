# Qwen3.5 Local Android App

Projeto inicial do aplicativo Android para conversar diretamente com:

Qwen3.5-0.8B.Q4_K_M.gguf

## Arquitetura

Android UI
  -> QwenEngine (Java)
  -> JNI bridge (C++)
  -> llama.cpp / libqwen_engine.so
  -> GGUF local

O NEXUS NÃO faz parte desta versão.

## Modelo

O aplicativo procura o modelo em:

/storage/emulated/0/Download/NEXUS/modelos/Qwen3.5-0.8B.Q4_K_M.gguf

Também aceita seleção manual do arquivo pelo seletor Android.

## Estado desta entrega

A interface, descoberta do modelo, permissões, configuração de ABI ARMv7 e a ponte JNI estão preparadas.

A pasta:

app/src/main/cpp/llama/

é reservada para o código/fonte do llama.cpp Android ou uma biblioteca pré-compilada ARMv7.

O `native_engine.cpp` já define a interface JNI. Ele informa claramente quando o backend nativo ainda não foi incluído.

## Build

Requer Android SDK + Android NDK + CMake + Gradle/Android Gradle Plugin.

O NDK/CMake são necessários para compilar a biblioteca nativa Android.

ABI alvo:
armeabi-v7a

Min SDK:
29 (Android 10)

Target SDK:
35

## Próxima etapa

Adicionar o llama.cpp Android ARMv7 real em:

app/src/main/cpp/llama/

e ligar sua API de inferência ao `native_engine.cpp`.

Não coloque o modelo GGUF dentro do APK. Use o arquivo externo de aproximadamente 503 MB.
