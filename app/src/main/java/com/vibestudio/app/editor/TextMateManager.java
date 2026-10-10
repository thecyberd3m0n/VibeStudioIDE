package com.vibestudio.app.editor;

import android.content.Context;
import android.util.Log;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;

import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TextMateManager {
    private static final String TAG = "TextMateManager";
    private static TextMateManager instance;
    private boolean isInitialized = false;
    private final Map<String, String> extensionToScopeMap = new HashMap<>();

    private TextMateManager() {
        initExtensionMap();
    }

    public static synchronized TextMateManager getInstance() {
        if (instance == null) {
            instance = new TextMateManager();
        }
        return instance;
    }

    private void initExtensionMap() {
        // Java
        extensionToScopeMap.put("java", "source.java");

        // JavaScript / TypeScript / React
        extensionToScopeMap.put("js", "source.js");
        extensionToScopeMap.put("mjs", "source.js");
        extensionToScopeMap.put("cjs", "source.js");
        extensionToScopeMap.put("jsx", "source.js");
        extensionToScopeMap.put("ts", "source.ts");
        extensionToScopeMap.put("mts", "source.ts");
        extensionToScopeMap.put("cts", "source.ts");
        extensionToScopeMap.put("tsx", "source.tsx");

        // Python
        extensionToScopeMap.put("py", "source.python");
        extensionToScopeMap.put("pyw", "source.python");

        // C / C++
        extensionToScopeMap.put("c", "source.c");
        extensionToScopeMap.put("h", "source.c");
        extensionToScopeMap.put("cpp", "source.cpp");
        extensionToScopeMap.put("hpp", "source.cpp");
        extensionToScopeMap.put("cc", "source.cpp");
        extensionToScopeMap.put("cxx", "source.cpp");
        extensionToScopeMap.put("hh", "source.cpp");

        // C#
        extensionToScopeMap.put("cs", "source.cs");

        // Go
        extensionToScopeMap.put("go", "source.go");

        // Rust
        extensionToScopeMap.put("rs", "source.rust");

        // Kotlin
        extensionToScopeMap.put("kt", "source.kotlin");
        extensionToScopeMap.put("kts", "source.kotlin");

        // PHP
        extensionToScopeMap.put("php", "source.php");

        // Ruby
        extensionToScopeMap.put("rb", "source.ruby");

        // Swift
        extensionToScopeMap.put("swift", "source.swift");

        // Web / Styling
        extensionToScopeMap.put("html", "text.html.basic");
        extensionToScopeMap.put("htm", "text.html.basic");
        extensionToScopeMap.put("css", "source.css");
        extensionToScopeMap.put("scss", "source.css.scss");
        extensionToScopeMap.put("sass", "source.css.scss");

        // Data / Markup / Config
        extensionToScopeMap.put("json", "source.json");
        extensionToScopeMap.put("jsonc", "source.json");
        extensionToScopeMap.put("yaml", "source.yaml");
        extensionToScopeMap.put("yml", "source.yaml");
        extensionToScopeMap.put("xml", "text.xml");
        extensionToScopeMap.put("xsd", "text.xml");
        extensionToScopeMap.put("svg", "text.xml");
        extensionToScopeMap.put("md", "text.html.markdown");
        extensionToScopeMap.put("markdown", "text.html.markdown");
        extensionToScopeMap.put("sh", "source.shell");
        extensionToScopeMap.put("bash", "source.shell");
        extensionToScopeMap.put("zsh", "source.shell");
        extensionToScopeMap.put("sql", "source.sql");
    }

    public synchronized void ensureInitialized(Context context) {
        if (isInitialized) return;

        try {
            Context appContext = context.getApplicationContext();
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(appContext.getAssets()));

            InputStream themeStream = appContext.getAssets().open("textmate/themes/darcula.json");
            IThemeSource themeSource = IThemeSource.fromInputStream(themeStream, "darcula.json", StandardCharsets.UTF_8);
            ThemeRegistry.getInstance().loadTheme(themeSource);
            ThemeRegistry.getInstance().setTheme("darcula");

            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");

            isInitialized = true;
            Log.i(TAG, "TextMate syntax highlighting initialized successfully!");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize TextMate syntax highlighting", e);
        }
    }

    public void applyLanguageAndTheme(Context context, CodeEditor editor, String filePath) {
        if (context == null || editor == null) return;
        ensureInitialized(context);

        try {
            if (isInitialized) {
                TextMateColorScheme colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
                editor.setColorScheme(colorScheme);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply TextMate color scheme", e);
        }

        String scopeName = getScopeForFile(filePath);
        if (scopeName != null && isInitialized) {
            try {
                TextMateLanguage language = TextMateLanguage.create(scopeName, true);
                editor.setEditorLanguage(language);
                Log.i(TAG, "Applied TextMate language: " + scopeName + " for " + filePath);
                return;
            } catch (Exception e) {
                Log.e(TAG, "Failed to create TextMate language for scope: " + scopeName, e);
            }
        }

        editor.setEditorLanguage(new EmptyLanguage());
    }

    public String getScopeForFile(String filePath) {
        if (filePath == null) return null;
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= filePath.length() - 1) return null;

        String ext = filePath.substring(dotIndex + 1).toLowerCase();
        return extensionToScopeMap.get(ext);
    }
}
