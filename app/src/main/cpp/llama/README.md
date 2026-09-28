# llama.cpp Android ARMv7

Coloque aqui a versão Android do llama.cpp / bibliotecas nativas ARMv7.

O projeto atual usa ABI:

armeabi-v7a

O llama.cpp que foi compilado anteriormente no Pydroid é um binário Linux/Android-Pydroid e não deve ser simplesmente copiado para `jniLibs`.

Para o APK final, a biblioteca deve ser compilada pelo Android NDK e empacotada como:

app/src/main/jniLibs/armeabi-v7a/lib*.so

ou ligada pelo CMake diretamente a este projeto.

Depois disso, `native_engine.cpp` deverá chamar a API real do llama.cpp para:

1. carregar o GGUF;
2. criar o contexto;
3. receber o prompt;
4. gerar tokens;
5. devolver o texto ao Java;
6. liberar o contexto.

O aplicativo foi deliberadamente separado do NEXUS nesta versão.
