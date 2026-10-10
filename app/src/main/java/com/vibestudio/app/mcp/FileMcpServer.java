package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileMcpServer implements McpServer {

    private static final String TAG = "FileMcpServer";
    private static final String NAME = "file";
    private static final String DESCRIPTION = "Allows traversing filesystem, listing directory contents, creating files/directories, moving, copying, and deleting files within the workspace.";

    private boolean mIsActive = false;
    private final List<McpTool> mTools;

    public FileMcpServer() {
        mTools = new ArrayList<>();

        // Tool 1: file_list_directory
        mTools.add(McpTool.builder("file_list_directory", "List files and subdirectories in a workspace target directory.")
                .addProperty("path", PropertyType.STRING, "Directory path to list (defaults to workspace home dir).", false)
                .build());

        // Tool 2: file_create_file
        mTools.add(McpTool.builder("file_create_file", "Create a new file at the specified path with optional initial text content.")
                .addProperty("path", PropertyType.STRING, "File path to create.", true)
                .addProperty("content", PropertyType.STRING, "Optional text content to write into the new file.", false)
                .build());

        // Tool 3: file_create_directory
        mTools.add(McpTool.builder("file_create_directory", "Create a new directory (and parent directories if missing).")
                .addProperty("path", PropertyType.STRING, "Directory path to create.", true)
                .build());

        // Tool 4: file_delete
        mTools.add(McpTool.builder("file_delete", "Delete a file or directory.")
                .addProperty("path", PropertyType.STRING, "File or directory path to delete.", true)
                .addProperty("recursive", PropertyType.BOOLEAN, "Recursive deletion if path is a non-empty directory (default: false).", false)
                .build());

        // Tool 5: file_move
        mTools.add(McpTool.builder("file_move", "Move or rename a file or directory.")
                .addProperty("source", PropertyType.STRING, "Source path.", true)
                .addProperty("destination", PropertyType.STRING, "Destination path.", true)
                .build());

        // Tool 6: file_copy
        mTools.add(McpTool.builder("file_copy", "Copy a file or directory to a destination path.")
                .addProperty("source", PropertyType.STRING, "Source path to copy.", true)
                .addProperty("destination", PropertyType.STRING, "Destination path.", true)
                .build());

        // Tool 7: file_get_info
        mTools.add(McpTool.builder("file_get_info", "Get metadata, size, and status of a file or directory.")
                .addProperty("path", PropertyType.STRING, "Target path.", true)
                .build());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public void startServer() {
        mIsActive = true;
        LogViewerService.getInstance().i(TAG, "File MCP Server started.");
    }

    @Override
    public void stopServer() {
        mIsActive = false;
        LogViewerService.getInstance().i(TAG, "File MCP Server stopped.");
    }

    @Override
    public boolean isActive() {
        return mIsActive;
    }

    @Override
    public List<McpTool> getTools() {
        return mTools;
    }

    @Override
    public boolean handlesTool(String toolName) {
        if (toolName == null) return false;
        for (McpTool tool : mTools) {
            if (tool.getName().equals(toolName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public McpToolResult callTool(String toolName, Map<String, Object> arguments, Context context) {
        try {
            if ("file_list_directory".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                File dir = resolveFile(pathArg, context);
                if (!dir.exists()) {
                    return McpToolResult.error("Directory does not exist: " + dir.getAbsolutePath());
                }
                if (!dir.isDirectory()) {
                    return McpToolResult.error("Path is not a directory: " + dir.getAbsolutePath());
                }

                File[] children = dir.listFiles();
                JSONArray items = new JSONArray();
                if (children != null) {
                    for (File child : children) {
                        JSONObject item = new JSONObject();
                        item.put("name", child.getName());
                        item.put("type", child.isDirectory() ? "directory" : "file");
                        item.put("size", child.length());
                        item.put("last_modified", child.lastModified());
                        items.put(item);
                    }
                }

                JSONObject res = new JSONObject();
                res.put("path", dir.getAbsolutePath());
                res.put("count", children != null ? children.length : 0);
                res.put("entries", items);
                return McpToolResult.success(res.toString(2));

            } else if ("file_create_file".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File file = resolveFile(pathArg, context);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                String content = getStringArg(arguments, "content", "");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(content.getBytes("UTF-8"));
                }
                return McpToolResult.success("File created successfully: " + file.getAbsolutePath());

            } else if ("file_create_directory".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File dir = resolveFile(pathArg, context);
                if (dir.exists()) {
                    return McpToolResult.error("Directory already exists: " + pathArg);
                }
                if (dir.mkdirs()) {
                    return McpToolResult.success("Directory created: " + dir.getAbsolutePath());
                } else {
                    return McpToolResult.error("Failed to create directory: " + pathArg);
                }

            } else if ("file_delete".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File target = resolveFile(pathArg, context);
                if (!target.exists()) return McpToolResult.error("Path does not exist: " + pathArg);

                boolean recursive = getBooleanArg(arguments, "recursive", false);
                boolean deleted = deleteRecursive(target, recursive);
                if (deleted) {
                    return McpToolResult.success("Deleted: " + pathArg);
                } else {
                    return McpToolResult.error("Failed to delete (use recursive=true for non-empty dirs): " + pathArg);
                }

            } else if ("file_move".equals(toolName)) {
                String srcArg = getStringArg(arguments, "source", "");
                String dstArg = getStringArg(arguments, "destination", "");
                if (srcArg.isEmpty() || dstArg.isEmpty()) {
                    return McpToolResult.error("Missing 'source' or 'destination' parameter.");
                }

                File src = resolveFile(srcArg, context);
                File dst = resolveFile(dstArg, context);
                if (!src.exists()) return McpToolResult.error("Source path does not exist: " + srcArg);

                File parent = dst.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                if (src.renameTo(dst)) {
                    return McpToolResult.success("Moved " + srcArg + " -> " + dstArg);
                } else {
                    return McpToolResult.error("Failed to move/rename from " + srcArg + " to " + dstArg);
                }

            } else if ("file_copy".equals(toolName)) {
                String srcArg = getStringArg(arguments, "source", "");
                String dstArg = getStringArg(arguments, "destination", "");
                if (srcArg.isEmpty() || dstArg.isEmpty()) {
                    return McpToolResult.error("Missing 'source' or 'destination' parameter.");
                }

                File src = resolveFile(srcArg, context);
                File dst = resolveFile(dstArg, context);
                if (!src.exists()) return McpToolResult.error("Source path does not exist: " + srcArg);

                copyRecursive(src, dst);
                return McpToolResult.success("Copied " + srcArg + " -> " + dstArg);

            } else if ("file_get_info".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File target = resolveFile(pathArg, context);
                if (!target.exists()) return McpToolResult.error("Path does not exist: " + pathArg);

                JSONObject info = new JSONObject();
                info.put("path", target.getAbsolutePath());
                info.put("name", target.getName());
                info.put("exists", target.exists());
                info.put("is_directory", target.isDirectory());
                info.put("is_file", target.isFile());
                info.put("size_bytes", target.length());
                info.put("can_read", target.canRead());
                info.put("can_write", target.canWrite());
                info.put("can_execute", target.canExecute());
                info.put("last_modified", target.lastModified());

                return McpToolResult.success(info.toString(2));

            } else {
                return McpToolResult.error("Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error in File MCP tool " + toolName, e);
            return McpToolResult.error("File MCP Error: " + e.getMessage());
        }
    }

    private boolean deleteRecursive(File file, boolean recursive) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null && children.length > 0) {
                if (!recursive) return false;
                for (File child : children) {
                    deleteRecursive(child, true);
                }
            }
        }
        return file.delete();
    }

    private void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursive(child, new File(dst, child.getName()));
                }
            }
        } else {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileChannel inChannel = new FileInputStream(src).getChannel();
                 FileChannel outChannel = new FileOutputStream(dst).getChannel()) {
                inChannel.transferTo(0, inChannel.size(), outChannel);
            }
        }
    }

    private File resolveFile(String pathArg, Context context) {
        String homePath = null;
        try {
            homePath = System.getenv("HOME");
        } catch (Exception ignored) {}

        if (homePath == null || homePath.trim().isEmpty()) {
            if (context != null) {
                homePath = new File(context.getFilesDir(), "home").getAbsolutePath();
            } else {
                homePath = "/data/data/com.termux/files/home";
            }
        }

        if (pathArg == null || pathArg.trim().isEmpty()) {
            return new File(homePath);
        }

        String trimmed = pathArg.trim();
        if (trimmed.startsWith("~")) {
            trimmed = homePath + trimmed.substring(1);
        }

        File f = new File(trimmed);
        if (!f.isAbsolute()) {
            f = new File(homePath, trimmed);
        }
        return f;
    }

    private String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return (val != null && !"null".equalsIgnoreCase(val.toString())) ? String.valueOf(val) : defaultValue;
    }

    private boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object val = args.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return defaultValue;
    }
}
