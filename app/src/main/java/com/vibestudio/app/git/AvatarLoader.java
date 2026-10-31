package com.vibestudio.app.git;

import com.vibestudio.app.git.callbacks.AvatarCallback;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves a commit author's avatar the way VS Code extensions (GitLens, GitHub) do: the GitHub account for
 * GitHub remotes / noreply emails, then Gravatar by email hash. A null bitmap means "use initials".
 */
public final class AvatarLoader {
    private static final int TIMEOUT_MS = 6000;
    private static final Bitmap NONE = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(64);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final java.util.Map<String, List<AvatarCallback>> IN_FLIGHT = new java.util.HashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AvatarLoader() {}

    /** Circle with the author's initial, shown until (or instead of) a real avatar. */
    public static Bitmap initialsBitmap(String author, int size) {
        String initial = (author != null && !author.trim().isEmpty())
                ? author.trim().substring(0, 1).toUpperCase() : "?";
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF3A3D52);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(0xFFE0E0E0);
        paint.setTextSize(size * 0.5f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        canvas.drawText(initial, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint);
        return bitmap;
    }

    /** @param remoteUrl origin URL, may be empty; @param commitHash full hash used for the GitHub commit lookup */
    public static void load(String email, String remoteUrl, String commitHash, AvatarCallback callback) {
        String key = (email == null ? "" : email.toLowerCase());
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            callback.onAvatar(cached == NONE ? null : cached);
            return;
        }
        List<AvatarCallback> waiting = IN_FLIGHT.get(key);
        if (waiting != null) {
            waiting.add(callback);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(callback);
        IN_FLIGHT.put(key, waiting);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            for (String url : candidateUrls(email, remoteUrl, commitHash)) {
                bitmap = download(url);
                if (bitmap != null) break;
            }
            CACHE.put(key, bitmap != null ? bitmap : NONE);
            Bitmap result = bitmap;
            MAIN.post(() -> {
                List<AvatarCallback> done = IN_FLIGHT.remove(key);
                if (done != null) for (AvatarCallback c : done) c.onAvatar(result);
            });
        });
    }

    private static List<String> candidateUrls(String email, String remoteUrl, String commitHash) {
        List<String> urls = new ArrayList<>();
        String safeEmail = email == null ? "" : email.trim().toLowerCase();

        java.util.regex.Matcher noreply = java.util.regex.Pattern
                .compile("^(?:(\\d+)\\+)?([^@]+)@users\\.noreply\\.github\\.com$").matcher(safeEmail);
        if (noreply.matches()) {
            urls.add(noreply.group(1) != null
                    ? "https://avatars.githubusercontent.com/u/" + noreply.group(1) + "?s=96"
                    : "https://github.com/" + noreply.group(2) + ".png?size=96");
        }

        java.util.regex.Matcher glNoreply = java.util.regex.Pattern
                .compile("^(?:(\\d+)-)?([^@]+)@users\\.noreply\\.gitlab\\.com$").matcher(safeEmail);
        if (glNoreply.matches()) {
            urls.add("https://gitlab.com/" + glNoreply.group(2) + ".png");
        }

        String gitlabHost = gitlabHost(remoteUrl);
        if (gitlabHost != null && !safeEmail.isEmpty()) {
            String avatar = gitlabAvatarForEmail(gitlabHost, safeEmail);
            if (avatar != null) urls.add(avatar + (avatar.contains("?") ? "&" : "?") + "width=96");
        }

        String[] slug = githubSlug(remoteUrl);
        if (slug != null && commitHash != null && !commitHash.isEmpty()) {
            String avatar = githubCommitAuthorAvatar(slug[0], slug[1], commitHash);
            if (avatar != null) urls.add(avatar + (avatar.contains("?") ? "&" : "?") + "s=96");
        }

        if (!safeEmail.isEmpty()) {
            urls.add("https://www.gravatar.com/avatar/" + md5(safeEmail) + "?s=96&d=404");
        }
        return urls;
    }

    /** @return {owner, repo} for a github.com remote, otherwise null */
    private static String[] githubSlug(String remoteUrl) {
        if (remoteUrl == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("github\\.com[:/]+([^/\\s]+)/([^/\\s]+?)(?:\\.git)?/?$").matcher(remoteUrl.trim());
        return m.find() ? new String[]{m.group(1), m.group(2)} : null;
    }

    /** @return the host of a GitLab remote (gitlab.com or a self-hosted "gitlab.*" host), otherwise null */
    private static String gitlabHost(String remoteUrl) {
        if (remoteUrl == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(?:[a-z+]+://)?(?:[^@/]+@)?([^/:]+)[:/]").matcher(remoteUrl.trim());
        if (!m.find()) return null;
        String host = m.group(1).toLowerCase();
        return host.startsWith("gitlab.") || host.contains(".gitlab.") ? host : null;
    }

    /** GitLab's public avatar lookup by email; it needs no token. */
    private static String gitlabAvatarForEmail(String host, String email) {
        try {
            HttpURLConnection conn = open("https://" + host + "/api/v4/avatar?size=96&email="
                    + java.net.URLEncoder.encode(email, "UTF-8"));
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return new org.json.JSONObject(body).optString("avatar_url", null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String githubCommitAuthorAvatar(String owner, String repo, String hash) {
        try {
            HttpURLConnection conn = open("https://api.github.com/repos/" + owner + "/" + repo + "/commits/" + hash);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                org.json.JSONObject author = new org.json.JSONObject(body).optJSONObject("author");
                return author != null ? author.optString("avatar_url", null) : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap download(String url) {
        try {
            HttpURLConnection conn = open(url);
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "VibeStudio");
        return conn;
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
