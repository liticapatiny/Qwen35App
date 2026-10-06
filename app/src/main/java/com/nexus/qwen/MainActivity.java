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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private LinearLayout messages;
    private EditText input;
    private EditText systemPrompt;
    private TextView status;

    private LinearLayout configPanel;
    private Button configButton;
    private Button savePromptButton;
    private Button webToggle;
    private boolean pesquisaWebAtiva = false;

    private static class FontePesquisa {
        String titulo;
        String url;

        FontePesquisa(String titulo, String url) {
            this.titulo = titulo;
            this.url = url;
        }
    }

    private static class ResultadoPesquisa {
        String contexto;
        List<FontePesquisa> fontes;

        ResultadoPesquisa(String contexto, List<FontePesquisa> fontes) {
            this.contexto = contexto;
            this.fontes = fontes;
        }
    }

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
        webToggle = findViewById(R.id.webToggle);

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

        webToggle.setOnClickListener(v -> {
            pesquisaWebAtiva = !pesquisaWebAtiva;
            webToggle.setText(
                    pesquisaWebAtiva
                            ? "🔎 Pesquisa: LIGADA"
                            : "🔎 Pesquisa: DESLIGADA"
            );
        });

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

    private ResultadoPesquisa pesquisarWeb(String consulta) {

        List<FontePesquisa> fontes = new ArrayList<>();
        StringBuilder contexto = new StringBuilder();

        HttpURLConnection conexao = null;

        try {
            String urlBusca =
                    "https://html.duckduckgo.com/html/?q=" +
                    URLEncoder.encode(consulta, "UTF-8");

            URL url = new URL(urlBusca);

            conexao = (HttpURLConnection) url.openConnection();
            conexao.setRequestMethod("GET");
            conexao.setConnectTimeout(10000);
            conexao.setReadTimeout(10000);
            conexao.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android) NEXUS"
            );

            int codigo = conexao.getResponseCode();

            if (codigo != HttpURLConnection.HTTP_OK) {
                return new ResultadoPesquisa("", fontes);
            }

            BufferedReader leitor =
                    new BufferedReader(
                            new InputStreamReader(
                                    conexao.getInputStream(),
                                    "UTF-8"
                            )
                    );

            StringBuilder html = new StringBuilder();
            String linha;

            while ((linha = leitor.readLine()) != null) {
                html.append(linha).append('\n');
            }

            leitor.close();

            String pagina = html.toString();

            String marcadorResultado =
                    "class=\"result__a\"";

            int posicao = 0;

            while (fontes.size() < 3) {

                int inicioResultado =
                        pagina.indexOf(
                                marcadorResultado,
                                posicao
                        );

                if (inicioResultado < 0) {
                    break;
                }

                int inicioTag =
                        pagina.lastIndexOf(
                                "<a",
                                inicioResultado
                        );

                int fimTag =
                        pagina.indexOf(
                                "</a>",
                                inicioResultado
                        );

                if (inicioTag < 0 || fimTag < 0) {
                    break;
                }

                String tag =
                        pagina.substring(
                                inicioTag,
                                fimTag + 4
                        );

                int hrefInicio =
                        tag.indexOf("href=\"");

                if (hrefInicio < 0) {
                    posicao = fimTag + 4;
                    continue;
                }

                hrefInicio += 6;

                int hrefFim =
                        tag.indexOf("\"", hrefInicio);

                if (hrefFim < 0) {
                    posicao = fimTag + 4;
                    continue;
                }

                String urlFonte =
                        tag.substring(
                                hrefInicio,
                                hrefFim
                        );

                int tituloInicio =
                        tag.indexOf(
                                ">",
                                hrefFim
                        );

                int tituloFim =
                        tag.lastIndexOf("</a>");

                if (tituloInicio < 0 ||
                        tituloFim <= tituloInicio) {
                    posicao = fimTag + 4;
                    continue;
                }

                String titulo =
                        tag.substring(
                                tituloInicio + 1,
                                tituloFim
                        )
                        .replaceAll("<[^>]*>", "")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .trim();

                int inicioSnippet =
                        pagina.indexOf(
                                "result__snippet",
                                fimTag
                        );

                String snippet = "";

                if (inicioSnippet >= 0) {

                    int inicioTexto =
                            pagina.indexOf(
                                    ">",
                                    inicioSnippet
                            );

                    int fimTexto =
                            pagina.indexOf(
                                    "</",
                                    inicioTexto
                            );

                    if (inicioTexto >= 0 &&
                            fimTexto > inicioTexto) {

                        snippet =
                                pagina.substring(
                                        inicioTexto + 1,
                                        fimTexto
                                )
                                .replaceAll("<[^>]*>", " ")
                                .replace("&amp;", "&")
                                .replace("&quot;", "\"")
                                .replace("&#x27;", "'")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replaceAll("\\s+", " ")
                                .trim();
                    }
                }

                if (snippet.length() > 600) {
                    snippet = snippet.substring(0, 600);
                }

                if (!titulo.isEmpty() &&
                        !urlFonte.isEmpty()) {

                    fontes.add(
                            new FontePesquisa(
                                    titulo,
                                    urlFonte
                            )
                    );

                    if (!snippet.isEmpty() &&
                            contexto.length() < 1800) {

                        contexto
                                .append(snippet)
                                .append("\n\n");
                    }
                }

                posicao = fimTag + 4;
            }

            return new ResultadoPesquisa(
                    contexto.toString().trim(),
                    fontes
            );

        } catch (Throwable e) {

            return new ResultadoPesquisa(
                    "",
                    fontes
            );

        } finally {

            if (conexao != null) {
                conexao.disconnect();
            }
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
        final boolean pesquisaAtiva = pesquisaWebAtiva;

        new Thread(() -> {

            String answer;
            ResultadoPesquisa pesquisa = null;

            try {

                if (pesquisaAtiva) {
                    pesquisa = pesquisarWeb(perguntaFinal);
                }

                String promptParaQwen =
                        systemPromptFinal;

                if (pesquisaAtiva &&
                        pesquisa != null &&
                        !pesquisa.contexto.isEmpty()) {

                    promptParaQwen +=
                            "\n\nCONTEXTO DA PESQUISA:\n" +
                            pesquisa.contexto;
                }

                promptParaQwen +=
                        "\n\nPERGUNTA DO USUÁRIO:\n" +
                        perguntaFinal;

                answer = nativeChat(promptParaQwen);

            } catch (Throwable e) {
                answer = "Backend Qwen ainda não carregado.";
            }

            final String finalAnswer = answer;
            final ResultadoPesquisa pesquisaFinal = pesquisa;

            runOnUiThread(() -> {

                addMessage("Qwen3.5", finalAnswer);

                if (pesquisaAtiva) {

                    if (pesquisaFinal != null &&
                            !pesquisaFinal.fontes.isEmpty()) {

                        StringBuilder fontesTexto =
                                new StringBuilder();

                        fontesTexto.append(
                                "Fontes da pesquisa:\n\n"
                        );

                        for (FontePesquisa fonte :
                                pesquisaFinal.fontes) {

                            fontesTexto
                                    .append(fonte.titulo)
                                    .append("\n")
                                    .append(fonte.url)
                                    .append("\n\n");
                        }

                        addMessage(
                                "Fontes",
                                fontesTexto.toString().trim()
                        );

                    } else {

                        addMessage(
                                "Pesquisa",
                                "Não foi possível obter resultados da web."
                        );
                    }
                }
            });

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
