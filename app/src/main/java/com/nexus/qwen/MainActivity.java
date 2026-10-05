package com.nexus.qwen;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

import java.io.File;

public class MainActivity extends Activity {

    private LinearLayout messages;
    private EditText input;
    private EditText systemPrompt;
    private TextView status;

    private LinearLayout configPanel;
    private Button configButton;
    private Button savePromptButton;

    private static final int PICK_MODEL = 7001;
    private static final int STORAGE_ACCESS = 7002;

    private static final String DEFAULT_MODEL =
            "/storage/emulated/0/Download/NEXUS/modelos/" +
            "Qwen3.5-0.8B.Q4_K_M.gguf";

    private static final String DEFAULT_SYSTEM_PROMPT =
            "Você é o Qwen3.5, um assistente local executado no aparelho. " +
            "Responda de forma clara, útil e objetiva. " +
            "Não invente informações.";

    private static final String PREFS = "nexus_config";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";

    private static String nativeLoadError = null;

    static {
        try {
            System.loadLibrary("qwen_engine");
        } catch (Throwable e) {
            nativeLoadError = e.getClass().getSimpleName() + " • " + e.getMessage();
        }
    }

    public native String nativeStatus();
    public native String nativeLoadModel(String path);
    public native String nativeChat(String prompt);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        messages = findViewById(R.id.messages);
        input = findViewById(R.id.input);
        systemPrompt = findViewById(R.id.systemPrompt);
        status = findViewById(R.id.status);

        configPanel = findViewById(R.id.configPanel);
        configButton = findViewById(R.id.config);
        savePromptButton = findViewById(R.id.savePrompt);

        Button send = findViewById(R.id.send);

        carregarSystemPrompt();

        addMessage("Qwen3.5",
                "Olá! Eu sou o Qwen3.5 0.8B.\n\n" +
                "Este aplicativo foi preparado para executar o modelo " +
                "localmente no aparelho.");

        prepareModelAccess();

        configButton.setOnClickListener(v -> {

            if (configPanel.getVisibility() == View.VISIBLE) {
                configPanel.setVisibility(View.GONE);
            } else {
                configPanel.setVisibility(View.VISIBLE);
                systemPrompt.requestFocus();
            }

        });

        savePromptButton.setOnClickListener(v -> salvarSystemPrompt());

        send.setOnClickListener(v -> sendMessage());
    }

    private void carregarSystemPrompt() {

        android.content.SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        String prompt = prefs.getString(
                KEY_SYSTEM_PROMPT,
                DEFAULT_SYSTEM_PROMPT
        );

        systemPrompt.setText(prompt);
    }

    private void salvarSystemPrompt() {

        String prompt = systemPrompt.getText().toString().trim();

        if (prompt.isEmpty()) {
            prompt = DEFAULT_SYSTEM_PROMPT;
            systemPrompt.setText(prompt);
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_SYSTEM_PROMPT, prompt)
                .apply();

        savePromptButton.setText("SALVO");

        savePromptButton.postDelayed(
                () -> savePromptButton.setText("SALVAR"),
                1200
        );
    }

    private void prepareModelAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            status.setText("Permissão de arquivos necessária");
            addMessage("Sistema", "Abra Acesso a todos os arquivos e permita o acesso para o Qwen3.5.");

            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, STORAGE_ACCESS);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, STORAGE_ACCESS);
            }
            return;
        }

        loadDefaultModel();
    }

    private void loadDefaultModel() {
        File model = new File(DEFAULT_MODEL);

        if (model.exists()) {
            String result = safeLoadModel(DEFAULT_MODEL);
            status.setText(result);
        } else {
            status.setText("Modelo não encontrado • toque no botão abaixo para selecionar");
            addModelButton();
        }
    }

    private void sendMessage() {

        String text = input.getText().toString().trim();

        if (text.isEmpty()) return;

        addMessage("Você", text);
        input.setText("");

        String promptConfigurado =
                systemPrompt.getText().toString().trim();

        if (promptConfigurado.isEmpty()) {
            promptConfigurado = DEFAULT_SYSTEM_PROMPT;
        }

        final String systemPromptFinal = promptConfigurado;
        final String perguntaFinal = text;

        new Thread(() -> {

            String answer;

            try {

                String promptParaQwen =
                        systemPromptFinal +
                        "\n\nPERGUNTA DO USUÁRIO:\n" +
                        perguntaFinal;

                answer = nativeChat(promptParaQwen);

            } catch (Throwable e) {
                answer = "Backend Qwen ainda não carregado.";
            }

            final String finalAnswer = answer;

            runOnUiThread(() ->
                    addMessage("Qwen3.5", finalAnswer)
            );

        }).start();
    }

    private String safeLoadModel(String path) {

        if (nativeLoadError != null) {
            return "ERRO CARREGANDO JNI: " + nativeLoadError;
        }

        try {
            return nativeLoadModel(path);
        } catch (Throwable e) {
            return "ERRO JNI: " + e.getClass().getSimpleName() + " • " + e.getMessage();
        }
    }

    private void addModelButton() {

        Button pick = new Button(this);
        pick.setText("Selecionar modelo GGUF");

        pick.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_MODEL);
        });

        messages.addView(pick);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == STORAGE_ACCESS) {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                    Environment.isExternalStorageManager()) {

                loadDefaultModel();

            } else {

                status.setText("Permissão de arquivos não concedida");
            }

            return;
        }

        if (requestCode == PICK_MODEL &&
                resultCode == RESULT_OK &&
                data != null) {

            Uri uri = data.getData();

            if (uri != null) {

                status.setText(
                        "GGUF selecionado • preparando motor local"
                );

                addMessage(
                        "Sistema",
                        "Arquivo selecionado:\n" + uri.toString()
                );
            }
        }
    }

    private void addMessage(String author, String text) {

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(20, 18, 20, 12);

        if ("Você".equals(author)) {
            container.setBackgroundColor(Color.rgb(31, 91, 190));
        } else {
            container.setBackgroundColor(Color.rgb(17, 26, 40));
        }

        TextView view = new TextView(this);

        view.setText(author + "\n\n" + text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setTextIsSelectable(true);
        view.setPadding(0, 0, 0, 12);

        container.addView(
                view,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        if ("Qwen3.5".equals(author) || "Você".equals(author)) {

            android.widget.Button copiar =
                    new android.widget.Button(this);

            copiar.setText("COPIAR");

            copiar.setOnClickListener(v -> {

                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);

                android.content.ClipData clip =
                        android.content.ClipData.newPlainText(
                                "Qwen3.5",
                                text
                        );

                clipboard.setPrimaryClip(clip);

                copiar.setText("COPIADO");

                copiar.postDelayed(
                        () -> copiar.setText("COPIAR"),
                        1200
                );
            });

            container.addView(copiar);
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 7, 0, 7);

        messages.addView(container, params);

        ScrollView scroll = findViewById(R.id.scroll);

        scroll.post(() ->
                scroll.fullScroll(View.FOCUS_DOWN)
        );
    }
}
