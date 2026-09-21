package com.vibestudio.app.activity;

import com.vibestudio.app.R;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.logging.CrashHandler;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.fragments.*;

import com.libtermux.LibTermux;
import com.libtermux.TermuxConfig;
import com.libtermux.LogLevel;
import com.libtermux.bootstrap.InstallState;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowCollector;

public class OnboardingActivity extends Activity {
    
    
    private void deployAssetDirectory(String assetSubDir, File targetDir) {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        try {
            String[] list = getAssets().list(assetSubDir);
            if (list != null) {
                for (String fileName : list) {
                    File outFile = new File(targetDir, fileName);
                    try (InputStream in = getAssets().open(assetSubDir + "/" + fileName);
                         FileOutputStream out = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            appendLog("[warning] Failed to deploy asset directory " + assetSubDir + ": " + t.getMessage());
        }
    }

    private static void cleanProfileFile(File file) {
        if (file != null && file.exists()) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                List<String> filtered = new ArrayList<>();
                for (String line : lines) {
                    if (!line.contains("fallback run") && !line.contains("termux-bootstrap")) {
                        filtered.add(line);
                    }
                }
                Files.write(file.toPath(), filtered);
            } catch (Throwable ignored) {}
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory != null && fileOrDirectory.exists()) {
            if (fileOrDirectory.isDirectory()) {
                File[] children = fileOrDirectory.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            fileOrDirectory.delete();
        }
    }


    private static final String TAG = "OnboardingActivity";

    private int mCurrentStep = 1;
    private boolean mIsInstalling = false;
    private boolean mIsInstalled = false;

    private View mStep1Layout;
    private View mStep2Layout;
    private View mStep3Layout;

    private TextView mStep1Indicator;
    private TextView mStep2Indicator;
    private TextView mStep3Indicator;

    private TextView mStep1Title;
    private TextView mStep2Title;
    private TextView mStep3Title;

    private TextView mStatusMessage;
    private TextView mInstallLogText;
    private ScrollView mInstallLogScroll;

    private Button mBtnBack;
    private Button mBtnNext;

    private Handler mHandler;
    private DatabaseHelper mDbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            mDbHelper = new DatabaseHelper(this);

            if (mDbHelper.isEnvInitialized()) {
                String storedPrefix = mDbHelper.getSetting("env_prefix");
                File expectedUsrDir = new File(getFilesDir(), "libtermux/usr");
                if (storedPrefix != null && new File(storedPrefix).getAbsolutePath().equals(expectedUsrDir.getAbsolutePath())) {
                    navigateToMain();
                    return;
                }
                // Invalid or legacy path (e.g., files/usr), invalidate readiness flag so onboarding completes migration
                mDbHelper.setEnvInitialized(false);
            }

            setContentView(R.layout.activity_onboarding);

            mHandler = new Handler(Looper.getMainLooper());

            mStep1Layout = findViewById(R.id.step1_layout);
            mStep2Layout = findViewById(R.id.step2_layout);
            mStep3Layout = findViewById(R.id.step3_layout);

            mStep1Indicator = (TextView) findViewById(R.id.step1_indicator);
            mStep2Indicator = (TextView) findViewById(R.id.step2_indicator);
            mStep3Indicator = (TextView) findViewById(R.id.step3_indicator);

            mStep1Title = (TextView) findViewById(R.id.step1_title);
            mStep2Title = (TextView) findViewById(R.id.step2_title);
            mStep3Title = (TextView) findViewById(R.id.step3_title);

            mStatusMessage = (TextView) findViewById(R.id.status_message);
            mInstallLogText = (TextView) findViewById(R.id.install_log_text);
            mInstallLogScroll = (ScrollView) findViewById(R.id.install_log_scroll);

            mBtnBack = (Button) findViewById(R.id.btn_back);
            mBtnNext = (Button) findViewById(R.id.btn_next);

            updateStepUi();

            mBtnNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCurrentStep < 3) {
                        mCurrentStep++;
                        updateStepUi();
                        if (mCurrentStep == 3 && !mIsInstalled && !mIsInstalling) {
                            startEnvironmentInstallation();
                        }
                    } else {
                        if (mIsInstalled) {
                            navigateToMain();
                        }
                    }
                }
            });

            mBtnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCurrentStep > 1 && !mIsInstalling) {
                        mCurrentStep--;
                        updateStepUi();
                    }
                }
            });
        } catch (Throwable t) {
            CrashHandler.getInstance().logError("OnboardingActivity", "Error in onCreate", t);
            throw t;
        }
    }

    private void updateStepUi() {
        mStep1Layout.setVisibility(mCurrentStep == 1 ? View.VISIBLE : View.GONE);
        mStep2Layout.setVisibility(mCurrentStep == 2 ? View.VISIBLE : View.GONE);
        mStep3Layout.setVisibility(mCurrentStep == 3 ? View.VISIBLE : View.GONE);

        updateIndicator(mStep1Indicator, mStep1Title, mCurrentStep == 1, mCurrentStep > 1);
        updateIndicator(mStep2Indicator, mStep2Title, mCurrentStep == 2, mCurrentStep > 2);
        updateIndicator(mStep3Indicator, mStep3Title, mCurrentStep == 3, mIsInstalled);

        mBtnBack.setVisibility(mCurrentStep > 1 && !mIsInstalling ? View.VISIBLE : View.INVISIBLE);

        if (mCurrentStep < 3) {
            mBtnNext.setText("Next");
            mBtnNext.setEnabled(true);
        } else {
            if (mIsInstalled) {
                mBtnNext.setText("Finish & Launch");
                mBtnNext.setEnabled(true);
            } else {
                mBtnNext.setText("Installing...");
                mBtnNext.setEnabled(false);
            }
        }
    }

    private void updateIndicator(TextView indicator, TextView title, boolean isActive, boolean isDone) {
        if (isActive) {
            indicator.setBackgroundResource(R.drawable.bg_step_circle_active);
            indicator.setTextColor(Color.WHITE);
            title.setTextColor(Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
        } else if (isDone) {
            indicator.setBackgroundResource(R.drawable.bg_step_circle_active);
            indicator.setTextColor(Color.WHITE);
            title.setTextColor(Color.parseColor("#AAAAAA"));
            title.setTypeface(null, Typeface.NORMAL);
        } else {
            indicator.setBackgroundResource(R.drawable.bg_step_circle_inactive);
            indicator.setTextColor(Color.parseColor("#888888"));
            title.setTextColor(Color.parseColor("#666666"));
            title.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void appendLog(final String text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mInstallLogText.append(text + "\n");
                if (mInstallLogScroll != null) {
                    mInstallLogScroll.post(new Runnable() {
                        @Override
                        public void run() {
                            mInstallLogScroll.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        });
    }

    private void setStatusMessage(final String message, final String colorHex) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mStatusMessage.setText(message);
                mStatusMessage.setTextColor(Color.parseColor(colorHex));
            }
        });
    }

    private void startEnvironmentInstallation() {
        mIsInstalling = true;
        mBtnBack.setVisibility(View.INVISIBLE);
        mBtnNext.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File filesDir = getFilesDir();
                    File usrDir = new File(filesDir, "libtermux/usr");
                    File homeDir = new File(filesDir, "libtermux/home");
                    File usrBin = new File(usrDir, "bin");
                    File bashFile = new File(usrBin, "bash");
                    File pkgFile = new File(usrBin, "pkg");

                    appendLog("[libtermux] Preparing APT environment structure...");
                    setupAptEnvironment(usrDir);
                    File aptConfFile = new File(usrDir, "etc/apt/apt.conf");

                    TermuxConfig config = TermuxConfig.Companion.builder()
                            .autoInstall(true)
                            .logLevel(LogLevel.DEBUG)
                            .addEnv("TERMUX_APP_PACKAGE_MANAGER", "apt")
                            .addEnv("TERMUX_MAIN_PACKAGE_FORMAT", "debian")
                            .addEnv("TERMUX_PKG_NO_MIRROR_SELECT", "1")
                            .addEnv("APT_CONFIG", aptConfFile.getAbsolutePath())
                            .build();

                    LibTermux libTermux = LibTermux.Companion.init(getApplicationContext(), config);

                    boolean isInstalled = libTermux.isInstalled();
                    boolean binariesExist = usrBin.exists() && bashFile.exists() && pkgFile.exists();

                    appendLog("[libtermux] Initializing installation (isInstalled=" + isInstalled + ", binariesExist=" + binariesExist + ")...");
                    boolean forceReinstall = !binariesExist;
                    Flow<InstallState> flow = libTermux.install(forceReinstall);

                    BuildersKt.runBlocking(
                        Dispatchers.getIO(),
                        (scope, continuation) -> flow.collect(new FlowCollector<InstallState>() {
                            @Nullable
                            @Override
                            public Object emit(InstallState state, @NonNull Continuation<? super Unit> $completion) {
                                if (state instanceof InstallState.Downloading) {
                                    InstallState.Downloading d = (InstallState.Downloading) state;
                                    int pct = (int) (d.getProgress() * 100);
                                    appendLog("[libtermux] Downloading bootstrap: " + pct + "%");
                                    setStatusMessage("Downloading bootstrap: " + pct + "%", "#3B82F6");
                                } else if (state instanceof InstallState.Extracting) {
                                    InstallState.Extracting e = (InstallState.Extracting) state;
                                    int pct = (int) (e.getProgress() * 100);
                                    appendLog("[libtermux] Extracting bootstrap: " + pct + "%");
                                    setStatusMessage("Extracting bootstrap: " + pct + "%", "#3B82F6");
                                } else if (state instanceof InstallState.Completed) {
                                    appendLog("[libtermux] Bootstrap extraction completed.");
                                } else if (state instanceof InstallState.Failed) {
                                    InstallState.Failed f = (InstallState.Failed) state;
                                    appendLog("[error] Bootstrap installation failed: " + f.getError());
                                    throw new RuntimeException("Bootstrap installation failed: " + f.getError());
                                }
                                return Unit.INSTANCE;
                            }
                        }, continuation)
                    );

                    appendLog("[libtermux] Overriding Termux hardcoded paths...");
                    overrideSTermuxPaths(usrDir, homeDir);
                    setupSymlinksAndPermissions(usrDir);
                    fixPermissionsRecursively(usrDir);
                    runBootstrapScript(usrDir, homeDir, aptConfFile);

                    File secondStageDir = new File(usrDir, "etc/termux/termux-bootstrap/second-stage");
                    secondStageDir.mkdirs();
                    File secondStageScript = new File(secondStageDir, "termux-bootstrap-second-stage.sh");
                    try {
                        FileWriter writer = new FileWriter(secondStageScript);
                        writer.write("#!/system/bin/sh\nexit 0\n");
                        writer.close();
                        Os.chmod(secondStageScript.getAbsolutePath(), 0755);
                    } catch (Throwable ignored) {}

                    File profileDBootstrap = new File(usrDir, "etc/profile.d/termux-bootstrap.sh");
                    if (profileDBootstrap.exists()) {
                        profileDBootstrap.delete();
                    }

                    // Deploy environment configuration files from app assets
                    appendLog("[libtermux] Deploying termux-etc configuration files from assets...");
                    deployAssetDirectory("termux-etc", new File(usrDir, "etc"));

                    appendLog("[libtermux] Storing LibTermux settings in database...");
                    mDbHelper.setSetting("env_prefix", usrDir.getAbsolutePath());
                    mDbHelper.setSetting("env_home", homeDir.getAbsolutePath());
                    mDbHelper.setSetting("env_shell", bashFile.getAbsolutePath());
                    mDbHelper.setEnvInitialized(true);

                    appendLog("[libtermux] LibTermux Environment setup complete!");

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mIsInstalling = false;
                            mIsInstalled = true;
                            mStatusMessage.setText("LibTermux Linux Environment installed!");
                            mStatusMessage.setTextColor(Color.parseColor("#10B981"));
                            updateStepUi();
                        }
                    });

                } catch (Exception e) {
                    final String err = e.getMessage();
                    CrashHandler.getInstance().logError("OnboardingActivity", "Error during environment installation", e);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mIsInstalling = false;
                            mStatusMessage.setText("Installation error: " + err);
                            mStatusMessage.setTextColor(Color.parseColor("#EF4444"));
                            appendLog("[error] " + err);
                        }
                    });
                }
            }
        }).start();
    }

                private void overrideSTermuxPaths(File usrDir, File homeDir) {
        if (usrDir == null || !usrDir.exists() || !usrDir.isDirectory()) return;

        // 1. Create symlinks /u -> usrDir and /h -> homeDir across all candidate app directories
        List<File> targetDirs = new ArrayList<>();
        try { targetDirs.add(getDataDir()); } catch (Throwable ignored) {}
        targetDirs.add(new File("/data/data/com.vibestudio.app"));
        targetDirs.add(new File("/data/user/0/com.vibestudio.app"));
        if (usrDir.getParentFile() != null) {
            targetDirs.add(usrDir.getParentFile());
            if (usrDir.getParentFile().getParentFile() != null) {
                targetDirs.add(usrDir.getParentFile().getParentFile());
            }
        }

        for (File dir : targetDirs) {
            if (dir == null) continue;
            if (!dir.exists()) { try { dir.mkdirs(); } catch (Throwable ignored) {} }
            File uLink = new File(dir, "u");
            File hLink = new File(dir, "h");
            try { Os.remove(uLink.getAbsolutePath()); } catch (Throwable ignored) {}
            try { Os.remove(hLink.getAbsolutePath()); } catch (Throwable ignored) {}
            try { Os.symlink(usrDir.getAbsolutePath(), uLink.getAbsolutePath()); } catch (Throwable ignored) {}
            try {
                if (homeDir != null) {
                    Os.symlink(homeDir.getAbsolutePath(), hLink.getAbsolutePath());
                }
            } catch (Throwable ignored) {}
        }
        appendLog("[libtermux] Created symlinks pointing to " + usrDir.getAbsolutePath());

        byte[] defaultUsrBytes = "/data/data/com.termux/files/usr".getBytes(StandardCharsets.UTF_8); // 31 bytes
        byte[] targetUsrBytes  = "/data/data/com.vibestudio.app/u".getBytes(StandardCharsets.UTF_8);   // 31 bytes

        byte[] defaultHomeBytes = "/data/data/com.termux/files/home".getBytes(StandardCharsets.UTF_8); // 32 bytes
        byte[] targetHomeBytes  = "/data/data/com.vibestudio.app/h\0".getBytes(StandardCharsets.UTF_8);  // 32 bytes (with trailing nul)

        int count = processDirectoryForTermuxPaths(usrDir, defaultUsrBytes, targetUsrBytes, defaultHomeBytes, targetHomeBytes, 0);
        LogViewerService.getInstance().i(TAG, "overrideSTermuxPaths completed. Overrode hardcoded termux paths in " + count + " files.");

        setupAptEnvironment(usrDir);
    }

    private void setupAptEnvironment(File usrDir) {
        try {
            File dpkgDir = new File(usrDir, "var/lib/dpkg");
            if (!dpkgDir.exists()) dpkgDir.mkdirs();

            new File(dpkgDir, "updates").mkdirs();
            new File(dpkgDir, "info").mkdirs();
            new File(dpkgDir, "triggers").mkdirs();
            new File(dpkgDir, "alternatives").mkdirs();

            File statusFile = new File(dpkgDir, "status");
            if (!statusFile.exists()) {
                statusFile.createNewFile();
            }

            File availableFile = new File(dpkgDir, "available");
            if (!availableFile.exists()) {
                availableFile.createNewFile();
            }

            File aptListsDir = new File(usrDir, "var/lib/apt/lists/partial");
            if (!aptListsDir.exists()) aptListsDir.mkdirs();

            File aptArchivesDir = new File(usrDir, "var/cache/apt/archives/partial");
            if (!aptArchivesDir.exists()) aptArchivesDir.mkdirs();

            File aptLogDir = new File(usrDir, "var/log/apt");
            if (!aptLogDir.exists()) aptLogDir.mkdirs();

            File dpkgEtcDir = new File(usrDir, "etc/dpkg/dpkg.cfg.d");
            if (!dpkgEtcDir.exists()) dpkgEtcDir.mkdirs();
            File dpkgCfgFile = new File(usrDir, "etc/dpkg/dpkg.cfg");
            if (!dpkgCfgFile.exists()) { try { dpkgCfgFile.createNewFile(); } catch (Exception ignored) {} }

            File aptEtcDir = new File(usrDir, "etc/apt");
            if (!aptEtcDir.exists()) aptEtcDir.mkdirs();

            new File(aptEtcDir, "apt.conf.d").mkdirs();
            new File(aptEtcDir, "preferences.d").mkdirs();
            new File(aptEtcDir, "sources.list.d").mkdirs();
            new File(aptEtcDir, "trusted.gpg.d").mkdirs();

            File aptConfFile = new File(aptEtcDir, "apt.conf");
            String aptConfContent = "Dir \"" + usrDir.getAbsolutePath() + "\";\n" +
                    "Dir::State \"" + new File(usrDir, "var/lib/apt").getAbsolutePath() + "\";\n" +
                    "Dir::State::status \"" + statusFile.getAbsolutePath() + "\";\n" +
                    "Dir::Cache \"" + new File(usrDir, "var/cache/apt").getAbsolutePath() + "\";\n" +
                    "Dir::Etc \"" + aptEtcDir.getAbsolutePath() + "\";\n" +
                    "Dir::Log \"" + aptLogDir.getAbsolutePath() + "\";\n" +
                    "Dir::Bin::methods \"" + new File(usrDir, "lib/apt/methods").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::solvers \"" + new File(usrDir, "lib/apt/solvers").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::solvers:: \"" + new File(usrDir, "lib/apt/solvers").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::planners \"" + new File(usrDir, "lib/apt/planners").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::planners:: \"" + new File(usrDir, "lib/apt/planners").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::dpkg \"" + new File(usrDir, "bin/dpkg").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::gzip \"" + new File(usrDir, "bin/gzip").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::bzip2 \"" + new File(usrDir, "bin/bzip2").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::xz \"" + new File(usrDir, "bin/xz").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::lz4 \"" + new File(usrDir, "bin/lz4").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::zstd \"" + new File(usrDir, "bin/zstd").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::lzma \"" + new File(usrDir, "bin/xz").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::apt-key \"" + new File(usrDir, "bin/apt-key").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::gpg \"" + new File(usrDir, "bin/gpg").getAbsolutePath() + "\";\n" +
                    "Dir::Bin::gpgv \"" + new File(usrDir, "bin/gpgv").getAbsolutePath() + "\";\n" +
                    "DPKG::Options { \"--root=" + usrDir.getAbsolutePath() + "\"; \"--admindir=" + dpkgDir.getAbsolutePath() + "\"; \"--force-confdef\"; \"--force-confold\"; };\n" +
                    "APT::System \"Debian dpkg interface\";\n" +
                    "APT::Get::AllowUnauthenticated \"true\";\n" +
                    "Acquire::AllowInsecureRepositories \"true\";\n" +
                    "Acquire::AllowDowngradeToInsecureRepositories \"true\";\n" +
                    "Acquire::https::Verify-Peer \"false\";\n" +
                    "Acquire::ssl::Verify-Peer \"false\";\n";

            Files.write(aptConfFile.toPath(), aptConfContent.getBytes(StandardCharsets.UTF_8));
            LogViewerService.getInstance().i(TAG, "Configured apt.conf at " + aptConfFile.getAbsolutePath());

            setupDefaultMirrors(usrDir);
            fixSourcesListFiles(usrDir);
        } catch (Exception e) {
            LogViewerService.getInstance().w(TAG, "Failed to setup APT environment", e);
            throw new RuntimeException("Failed to setup APT environment", e);
        }
    }

    private void fixSourcesListFiles(File usrDir) {
        try {
            File aptEtcDir = new File(usrDir, "etc/apt");
            List<File> sourcesFiles = new ArrayList<>();
            File mainSources = new File(aptEtcDir, "sources.list");
            if (mainSources.exists()) sourcesFiles.add(mainSources);

            File sourcesListDir = new File(aptEtcDir, "sources.list.d");
            if (sourcesListDir.exists() && sourcesListDir.isDirectory()) {
                File[] listFiles = sourcesListDir.listFiles();
                if (listFiles != null) {
                    for (File f : listFiles) {
                        if (f.isFile() && f.getName().endsWith(".list")) {
                            sourcesFiles.add(f);
                        }
                    }
                }
            }

            for (File f : sourcesFiles) {
                String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                String[] lines = content.split("\n");
                StringBuilder sb = new StringBuilder();
                boolean modified = false;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("deb ") && !trimmed.contains("[trusted=yes]")) {
                        line = line.replaceFirst("deb\\s+", "deb [trusted=yes] ");
                        modified = true;
                    }
                    sb.append(line).append("\n");
                }
                if (modified) {
                    Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
                    LogViewerService.getInstance().i(TAG, "Updated sources file with trusted=yes: " + f.getName());
                }
            }
        } catch (Exception e) {
            LogViewerService.getInstance().w(TAG, "Failed to fix sources list files", e);
        }
    }

            private int processDirectoryForTermuxPaths(File dir, byte[] matchUsr, byte[] replaceUsr, byte[] matchHome, byte[] replaceHome, int depth) {
        if (depth > 8 || dir == null) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;

        int count = 0;
        for (File file : files) {
            try {
                if (Files.isSymbolicLink(file.toPath())) {
                    Path targetPath = Files.readSymbolicLink(file.toPath());
                    String targetStr = targetPath.toString();
                    boolean modified = false;
                    String defaultUsrStr = new String(matchUsr, StandardCharsets.UTF_8);
                    String targetUsrStr = new String(replaceUsr, StandardCharsets.UTF_8);
                    if (targetStr.contains(defaultUsrStr)) {
                        targetStr = targetStr.replace(defaultUsrStr, targetUsrStr);
                        modified = true;
                    }
                    if (modified) {
                        Files.delete(file.toPath());
                        Files.createSymbolicLink(file.toPath(), Paths.get(targetStr));
                        count++;
                    }
                    continue;
                }
            } catch (Exception e) {
                continue;
            }

            if (file.isDirectory()) {
                count += processDirectoryForTermuxPaths(file, matchUsr, replaceUsr, matchHome, replaceHome, depth + 1);
            } else if (file.isFile() && file.canRead() && file.length() > 0 && file.length() < 10 * 1024 * 1024) {
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    boolean modifiedUsr = replaceByteSequenceInPlace(bytes, matchUsr, replaceUsr);
                    boolean modifiedHome = replaceByteSequenceInPlace(bytes, matchHome, replaceHome);

                    if (modifiedUsr || modifiedHome) {
                        Files.write(file.toPath(), bytes);
                        if (file.getParentFile() != null) {
                            String parentName = file.getParentFile().getName();
                            if ("bin".equals(parentName) || "libexec".equals(parentName)) {
                                try { Os.chmod(file.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
                            }
                        }
                        count++;
                    }
                } catch (Exception e) {
                    LogViewerService.getInstance().w(TAG, "Failed to process path for " + file.getName(), e);
                }
            }
        }
        return count;
    }

    private boolean replaceByteSequenceInPlace(byte[] src, byte[] match, byte[] replace) {
        if (src == null || match == null || replace == null || match.length != replace.length || src.length < match.length) {
            return false;
        }
        boolean modified = false;
        int maxIndex = src.length - match.length;
        for (int i = 0; i <= maxIndex; i++) {
            boolean isMatch = true;
            for (int j = 0; j < match.length; j++) {
                if (src[i + j] != match[j]) {
                    isMatch = false;
                    break;
                }
            }
            if (isMatch) {
                for (int j = 0; j < replace.length; j++) {
                    src[i + j] = replace[j];
                }
                i += match.length - 1;
                modified = true;
            }
        }
        return modified;
    }

    private void navigateToMain() {
        Intent intent = new Intent(OnboardingActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void runBootstrapScript(File usrDir, File homeDir, File aptConfFile) throws Exception {
        appendLog("[libtermux] Deploying and running vibestudio-bootstrap.sh...");
        File scriptFile = new File(usrDir, "tmp/vibestudio-bootstrap.sh");
        if (scriptFile.getParentFile() != null) { scriptFile.getParentFile().mkdirs(); }
        byte[] scriptBytes = null;
        try (InputStream in = getAssets().open("vibestudio-bootstrap.sh")) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int read;
            while ((read = in.read(buffer)) != -1) { baos.write(buffer, 0, read); }
            scriptBytes = baos.toByteArray();
        } catch (FileNotFoundException fnfe) {
            appendLog("[warning] Asset vibestudio-bootstrap.sh not found, using default script...");
            String defaultScript = "#!/system/bin/sh\nset -x\n";
            scriptBytes = defaultScript.getBytes(StandardCharsets.UTF_8);
        }
        try (FileOutputStream out = new FileOutputStream(scriptFile)) { out.write(scriptBytes); }
        try { Os.chmod(scriptFile.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
        File shBin = new File(usrDir, "bin/sh");
        String shellPath = shBin.exists() ? shBin.getAbsolutePath() : "/system/bin/sh";
        ProcessBuilder pb = new ProcessBuilder(shellPath, scriptFile.getAbsolutePath());
        pb.environment().put("PREFIX", usrDir.getAbsolutePath());
        pb.environment().put("HOME", homeDir.getAbsolutePath());
        pb.environment().put("PATH", new File(usrDir, "bin").getAbsolutePath() + ":" + new File(usrDir, "bin/applets").getAbsolutePath() + ":/system/bin:/system/xbin");
        pb.environment().put("LD_LIBRARY_PATH", new File(usrDir, "lib").getAbsolutePath());
        pb.environment().put("TMPDIR", new File(usrDir, "tmp").getAbsolutePath());
        pb.environment().put("TERM", "xterm-256color"); pb.environment().put("TERMUX_PKG_NO_MIRROR_SELECT", "true"); pb.environment().put("DPKG_ADMINDIR", new File(usrDir, "var/lib/dpkg").getAbsolutePath());
        if (aptConfFile != null && aptConfFile.exists()) { pb.environment().put("APT_CONFIG", aptConfFile.getAbsolutePath()); }
        pb.directory(usrDir); pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String l; while ((l = reader.readLine()) != null) { appendLog(l); }
        }
        int exitCode = process.waitFor();
        appendLog("[libtermux] vibestudio-bootstrap.sh finished with exit code " + exitCode);
        if (exitCode != 0) { throw new RuntimeException("vibestudio-bootstrap.sh failed with exit code " + exitCode); }
    }

    private void setupSymlinksAndPermissions(File usrDir) {
        if (usrDir == null || !usrDir.exists()) return;
        File binDir = new File(usrDir, "bin");
        if (!binDir.exists()) binDir.mkdirs();

        // 1. Force executable permissions on binary directories FIRST
        makeDirectoryExecutable(new File(usrDir, "bin"));
        makeDirectoryExecutable(new File(usrDir, "libexec"));
        makeDirectoryExecutable(new File(usrDir, "lib/apt/methods"));
        makeDirectoryExecutable(new File(usrDir, "lib/apt/solvers"));
        makeDirectoryExecutable(new File(usrDir, "lib/apt/planners"));

        // 2. Wrap dpkg binary to force --root and --admindir to VibeStudio prefix
        File dpkgFile = new File(binDir, "dpkg");
        File dpkgRealFile = new File(binDir, "dpkg.real");
        if (dpkgFile.exists() && !dpkgRealFile.exists()) {
            boolean isRegularFile = !Files.isSymbolicLink(dpkgFile.toPath());
            if (isRegularFile) {
                if (dpkgFile.renameTo(dpkgRealFile)) {
                    try { Os.chmod(dpkgRealFile.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
                    String wrapperContent = "#!/system/bin/sh\n" +
                            "PREFIX=\"${PREFIX:-" + usrDir.getAbsolutePath() + "}\"\n" +
                            "exec \"$PREFIX/bin/dpkg.real\" --root=\"$PREFIX\" --admindir=\"$PREFIX/var/lib/dpkg\" --force-script-chrootless --force-unsafe-io \"$@\"\n";
                    try (FileOutputStream out = new FileOutputStream(dpkgFile)) {
                        out.write(wrapperContent.getBytes(StandardCharsets.UTF_8));
                    } catch (Throwable ignored) {}
                    try { Os.chmod(dpkgFile.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
                    appendLog("[libtermux] Created dpkg wrapper script pointing to --root=" + usrDir.getAbsolutePath());
                }
            }
        }

        // 3. Process SYMLINKS.txt if present
        File symlinksFile = new File(usrDir, "SYMLINKS.txt");
        if (symlinksFile.exists()) {
            appendLog("[libtermux] Processing SYMLINKS.txt with POSIX symlink...");
            try (BufferedReader reader = new BufferedReader(new FileReader(symlinksFile))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("←");
                    if (parts.length == 2) {
                        String target = parts[0].trim();
                        String relPath = parts[1].trim();
                        if (relPath.startsWith("./")) relPath = relPath.substring(2);

                        if (target.startsWith("/data/data/com.termux/files/usr/")) {
                            target = usrDir.getAbsolutePath() + "/" + target.substring("/data/data/com.termux/files/usr/".length());
                        } else if (target.startsWith("/data/data/com.termux/files/usr")) {
                            target = usrDir.getAbsolutePath() + target.substring("/data/data/com.termux/files/usr".length());
                        }

                        File linkFile = new File(usrDir, relPath);
                        File parent = linkFile.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();

                        try { Os.remove(linkFile.getAbsolutePath()); } catch (Throwable ignored) {}
                        linkFile.delete();

                        try {
                            Os.symlink(target, linkFile.getAbsolutePath());
                            try { Os.chmod(linkFile.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
                            count++;
                        } catch (Throwable t) {
                            Log.w("OnboardingActivity", "Failed symlink: " + linkFile + " -> " + target + ": " + t.getMessage());
                        }
                    }
                }
                appendLog("[libtermux] Created " + count + " symlinks from SYMLINKS.txt");
            } catch (Throwable t) {
                appendLog("[warning] Error reading SYMLINKS.txt: " + t.getMessage());
            }
        }

        // 4. Guarantee all 6 required dpkg binaries are executable
        ensureExecutableTool(binDir, "sh", "dash", "bash");
        ensureExecutableTool(binDir, "rm", "coreutils", "busybox");
        ensureExecutableTool(binDir, "tar", "busybox", "coreutils");
        ensureExecutableTool(binDir, "diff", "busybox", "coreutils");
        ensureExecutableTool(binDir, "dpkg-deb", "dpkg.real", "dpkg", "busybox");
        ensureExecutableTool(binDir, "start-stop-daemon", "dpkg.real", "dpkg", "busybox");

        // 5. Ensure other common utility tools
        String[] commonTools = new String[]{
            "cat", "ls", "cp", "mv", "ln", "chmod", "mkdir", "echo", "touch", "chown", "shred"
        };
        for (String tool : commonTools) {
            ensureExecutableTool(binDir, tool, "coreutils", "busybox");
        }

        // 6. Re-run permission check on executable directories
        makeDirectoryExecutable(new File(usrDir, "bin"));
        makeDirectoryExecutable(new File(usrDir, "libexec"));
        makeDirectoryExecutable(new File(usrDir, "lib/apt/methods"));

        // 7. Verify and log status of all 6 expected dpkg binaries
        String[] required = new String[]{"sh", "rm", "tar", "diff", "dpkg-deb", "start-stop-daemon"};
        for (String req : required) {
            File reqFile = new File(binDir, req);
            boolean exists = reqFile.exists();
            boolean canExec = reqFile.canExecute();
            appendLog("[libtermux] Binary check: bin/" + req + " (exists=" + exists + ", canExecute=" + canExec + ")");
        }
    }

    private boolean ensureExecutableTool(File binDir, String toolName, String... fallbackTargets) {
        File toolFile = new File(binDir, toolName);
        if (toolFile.exists()) {
            try { Os.chmod(toolFile.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
        }
        if (toolFile.exists() && toolFile.canExecute()) {
            return true;
        }

        try { Os.remove(toolFile.getAbsolutePath()); } catch (Throwable ignored) {}
        toolFile.delete();

        for (String targetName : fallbackTargets) {
            File targetFile = new File(binDir, targetName);
            if (!targetFile.exists()) {
                targetFile = new File(binDir.getParentFile(), targetName);
            }
            if (targetFile.exists()) {
                try { Os.chmod(targetFile.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
                if (targetFile.canExecute()) {
                    createSymlink(binDir, toolName, targetName);
                    try { Os.chmod(toolFile.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
                    if (toolFile.exists() && toolFile.canExecute()) {
                        appendLog("[libtermux] Fixed tool " + toolName + " -> " + targetName);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void makeDirectoryExecutable(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                makeDirectoryExecutable(f);
            } else {
                try {
                    Os.chmod(f.getAbsolutePath(), 0755);
                } catch (Throwable ignored) {}
            }
        }
    }

    private void createSymlinkIfNotExists(File dir, String symlinkName, String targetName) {
        File linkFile = new File(dir, symlinkName);
        if (!linkFile.exists()) {
            createSymlink(dir, symlinkName, targetName);
        }
    }

    private void createSymlink(File dir, String symlinkName, String target) {
        File linkFile = new File(dir, symlinkName);
        try {
            try { Os.remove(linkFile.getAbsolutePath()); } catch (Throwable ignored) {}
            linkFile.delete();
            Os.symlink(target, linkFile.getAbsolutePath());
            Os.chmod(linkFile.getAbsolutePath(), 0755);
        } catch (Throwable t) {
            Log.w("OnboardingActivity", "Failed to create symlink " + symlinkName + " -> " + target + ": " + t.getMessage());
        }
    }

    private void fixPermissionsRecursively(File file) {
        if (file == null) return;
        try {
            boolean isDir = file.isDirectory();
            File parent = file.getParentFile();
            String parentName = (parent != null) ? parent.getName() : "";
            boolean isExecDir = "bin".equals(parentName) || "libexec".equals(parentName) || "applets".equals(parentName) || "methods".equals(parentName);
            boolean isExec = isDir || isExecDir || file.canExecute();
            try {
                Os.chmod(file.getAbsolutePath(), isExec ? 0755 : 0644);
            } catch (Throwable ignored) {}
            if (isDir) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        fixPermissionsRecursively(child);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void setupDefaultMirrors(File usrDir) {
        try {
            File chosenMirrors = new File(usrDir, "etc/termux/chosen_mirrors");
            File defaultMirror = new File(usrDir, "etc/termux/mirrors/default");
            if (defaultMirror.exists()) {
                try {
                    if (chosenMirrors.exists() || chosenMirrors.isAbsolute()) { chosenMirrors.delete(); }
                    Os.symlink(defaultMirror.getAbsolutePath(), chosenMirrors.getAbsolutePath());
                    LogViewerService.getInstance().i(TAG, "Linked chosen_mirrors to default mirror");
                } catch (Throwable t) {
                    LogViewerService.getInstance().w(TAG, "Failed to symlink chosen_mirrors", t);
                }
            }
        } catch (Exception e) {
            LogViewerService.getInstance().w(TAG, "Failed to setup default mirrors", e);
        }
    }

    private boolean replaceBytesInFile(File file, byte[] pattern, byte[] replacement) {
        if (pattern == null || replacement == null || pattern.length != replacement.length) return false;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            boolean modified = false;
            for (int i = 0; i <= data.length - pattern.length; i++) {
                boolean match = true;
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) { match = false; break; }
                }
                if (match) {
                    System.arraycopy(replacement, 0, data, i, replacement.length);
                    modified = true;
                    i += pattern.length - 1;
                }
            }
            if (modified) {
                Files.write(file.toPath(), data);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
