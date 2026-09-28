package com.nexus.qwen;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
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
    private TextView status;

    private static final int PICK_MODEL = 7001;

    private static final String DEFAULT_MODEL =
            "/storage/emulated/0/Download/NEXUS/modelos/" +
            "Qwen3.5-0.8B.Q4_K_M.gguf";

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
        status = findViewById(R.id.status);
        Button send = findViewById(R.id.send);

        addMessage("Qwen3.5",
                "Olá! Eu sou o Qwen3.5 0.8B.\n\n" +
                "Este aplicativo foi preparado para executar o modelo " +
                "localmente no aparelho.");

        File model = new File(DEFAULT_MODEL);

        if (model.exists()) {
            String result = safeLoadModel(DEFAULT_MODEL);
            status.setText(result);
        } else {
            status.setText("Modelo não encontrado • toque no botão abaixo para selecionar");
            addModelButton();
        }

        send.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();

        if (text.isEmpty()) return;

        addMessage("Você", text);
        input.setText("");

        new Thread(() -> {
            String answer;

            try {
                answer = nativeChat(text);
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

        TextView view = new TextView(this);

        view.setText(author + "\n\n" + text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setPadding(20, 18, 20, 18);

        if ("Você".equals(author)) {
            view.setBackgroundColor(Color.rgb(31, 91, 190));
        } else {
            view.setBackgroundColor(Color.rgb(17, 26, 40));
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 7, 0, 7);

        messages.addView(view, params);

        ScrollView scroll = findViewById(R.id.scroll);

        scroll.post(() ->
                scroll.fullScroll(View.FOCUS_DOWN)
        );
    }
}
