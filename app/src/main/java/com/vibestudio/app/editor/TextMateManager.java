package com.vibestudio.app.editor;

import android.content.Context;
import android.util.Log;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;

import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TextMateManager {
    private static final String TAG = "TextMateManager";
    private static TextMateManager instance;
    private boolean isInitialized = false;
    private final Map<String, String> extensionToScopeMap = new HashMap<>();
    private final Map<String, String> exactFileNameToScopeMap = new HashMap<>();

    private TextMateManager() {
        initMaps();
    }

    public static synchronized TextMateManager getInstance() {
        if (instance == null) {
            instance = new TextMateManager();
        }
        return instance;
    }

    private void initMaps() {
        // Java
        extensionToScopeMap.put("java", "source.java");

        // JavaScript / TypeScript / React / Dart
        extensionToScopeMap.put("js", "source.js");
        extensionToScopeMap.put("mjs", "source.js");
        extensionToScopeMap.put("cjs", "source.js");
        extensionToScopeMap.put("jsx", "source.js");
        extensionToScopeMap.put("ts", "source.ts");
        extensionToScopeMap.put("mts", "source.ts");
        extensionToScopeMap.put("cts", "source.ts");
        extensionToScopeMap.put("tsx", "source.tsx");
        extensionToScopeMap.put("dart", "source.dart");

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

        // Common exact filenames & dotfiles
        exactFileNameToScopeMap.put("dockerfile", "source.shell");
        exactFileNameToScopeMap.put("makefile", "source.shell");
        exactFileNameToScopeMap.put(".gitignore", "source.shell");
        exactFileNameToScopeMap.put(".gitattributes", "source.shell");
        exactFileNameToScopeMap.put(".env", "source.shell");
        exactFileNameToScopeMap.put(".bashrc", "source.shell");
        exactFileNameToScopeMap.put(".zshrc", "source.shell");
        exactFileNameToScopeMap.put(".profile", "source.shell");
        exactFileNameToScopeMap.put(".eslintrc", "source.json");
        exactFileNameToScopeMap.put(".prettierrc", "source.json");
        exactFileNameToScopeMap.put(".babelrc", "source.json");
        exactFileNameToScopeMap.put("package.json", "source.json");
        exactFileNameToScopeMap.put("tsconfig.json", "source.json");
        exactFileNameToScopeMap.put("angular.json", "source.json");
        exactFileNameToScopeMap.put(".angular-config.json", "source.json");
    }

    public synchronized void ensureInitialized(Context context) {
        if (isInitialized) return;

        try {
            Context appContext = context.getApplicationContext();
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(appContext.getAssets()));

            InputStream themeStream = appContext.getAssets().open("textmate/themes/darcula.json");
            IThemeSource themeSource = IThemeSource.fromInputStream(themeStream, "darcula.json", StandardCharsets.UTF_8);
            
            ThemeRegistry themeRegistry = ThemeRegistry.getInstance();
            themeRegistry.loadTheme(themeSource);
            themeRegistry.setTheme("darcula");

            ThemeModel currentTheme = themeRegistry.getCurrentThemeModel();
            if (currentTheme != null) {
                GrammarRegistry.getInstance().setTheme(currentTheme);
            }

            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");

            isInitialized = true;
            Log.i(TAG, "TextMate syntax highlighting initialized successfully!");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize TextMate syntax highlighting: " + t.getMessage(), t);
        }
    }

    public void applyLanguageAndTheme(Context context, CodeEditor editor, String filePath) {
        if (context == null || editor == null) return;

        try {
            ensureInitialized(context);

            if (isInitialized) {
                ThemeModel currentTheme = ThemeRegistry.getInstance().getCurrentThemeModel();
                if (currentTheme != null) {
                    TextMateColorScheme colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance(), currentTheme);
                    editor.setColorScheme(colorScheme);
                } else {
                    editor.setColorScheme(ThemeManager.getInstance().createEditorColorScheme());
                }
            } else {
                editor.setColorScheme(ThemeManager.getInstance().createEditorColorScheme());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to apply TextMate color scheme, falling back to ThemeManager default", t);
            try {
                editor.setColorScheme(ThemeManager.getInstance().createEditorColorScheme());
            } catch (Throwable ignored) {}
        }

        String scopeName = getScopeForFile(filePath);
        if (scopeName != null && isInitialized) {
            try {
                TextMateLanguage language = TextMateLanguage.create(
                    scopeName,
                    GrammarRegistry.getInstance(),
                    ThemeRegistry.getInstance(),
                    true
                );
                editor.setEditorLanguage(language);
                Log.i(TAG, "Applied TextMate language: " + scopeName + " for " + filePath);
                return;
            } catch (Throwable t) {
                Log.e(TAG, "Failed to create TextMate language for scope: " + scopeName + " - " + t.getMessage(), t);
            }
        }

        try {
            editor.setEditorLanguage(new EmptyLanguage());
        } catch (Throwable ignored) {}
    }

    public String getScopeForFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;

        String fileName = new File(filePath).getName().toLowerCase();

        // 1. Check exact match first
        if (exactFileNameToScopeMap.containsKey(fileName)) {
            return exactFileNameToScopeMap.get(fileName);
        }

        // 2. Extract last extension after dot
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            String ext = fileName.substring(lastDot + 1);
            String scope = extensionToScopeMap.get(ext);
            if (scope != null) return scope;
        }

        // 3. Fallback for hidden files / dotfiles without recognized extension (e.g. .bashrc, .eslintrc)
        if (fileName.startsWith(".")) {
            String stripDot = fileName.substring(1);
            if (extensionToScopeMap.containsKey(stripDot)) {
                return extensionToScopeMap.get(stripDot);
            }
            if (stripDot.endsWith("rc")) {
                String rcName = stripDot.substring(0, stripDot.length() - 2);
                if ("bash".equals(rcName) || "zsh".equals(rcName) || "sh".equals(rcName)) {
                    return "source.shell";
                }
                if ("eslint".equals(rcName) || "prettier".equals(rcName) || "babel".equals(rcName)) {
                    return "source.json";
                }
            }
        }

        return null;
    }
}
