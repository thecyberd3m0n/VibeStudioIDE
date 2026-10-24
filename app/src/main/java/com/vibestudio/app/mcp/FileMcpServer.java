package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.chat.safety.WorkspaceChangesetTracker;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.view.BaseToolMessageView;
import com.vibestudio.app.view.FileToolMessage;

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

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public CharSequence prepareToolCall(String toolName, JSONObject args, Context context, WorkspaceChangesetTracker tracker) throws Exception {
        if (args == null) args = new JSONObject();
        String path = args.optString("path", args.optString("src", ""));

        boolean ok = true;
        if (tracker != null) {
            if ("file_create_file".equalsIgnoreCase(toolName) || "file_create_directory".equalsIgnoreCase(toolName)) {
                if (!path.isEmpty()) ok = tracker.recordPreEditState(path);
            } else if ("file_move".equalsIgnoreCase(toolName)) {
                String src = args.optString("src", null);
                String dst = args.optString("dst", null);
                if (src != null) ok = tracker.recordPreDeleteState(src) && ok;
                if (dst != null) ok = tracker.recordPreEditState(dst) && ok;
            } else if ("file_copy".equalsIgnoreCase(toolName)) {
                String dst = args.optString("dst", null);
                if (dst != null) ok = tracker.recordPreEditState(dst);
            } else if ("file_delete".equalsIgnoreCase(toolName)) {
                if (!path.isEmpty()) ok = tracker.recordPreDeleteState(path);
            }
        }

        if (!ok) {
            throw new IllegalStateException("Failed to create restorable backup before executing " + toolName + ".");
        }

        if ("file_list_directory".equalsIgnoreCase(toolName)) {
            return "Listing directory: " + path;
        } else if ("file_create_file".equalsIgnoreCase(toolName)) {
            return "Creating file: " + path;
        } else if ("file_delete".equalsIgnoreCase(toolName)) {
            return "Deleting: " + path;
        } else if ("file_get_info".equalsIgnoreCase(toolName)) {
            return "Getting info: " + path;
        } else if ("file_move".equalsIgnoreCase(toolName) || "file_copy".equalsIgnoreCase(toolName)) {
            return toolName + ": " + args.optString("src", "") + " -> " + args.optString("dst", "");
        }

        return toolName + "(" + args.toString() + ")";
    }

    @Override
    public BaseToolMessageView createToolMessageView(Context context, String toolName) {
        return new FileToolMessage(context);
    }

    private static final String TAG = "FileMcpServer";
    private static final String NAME = "file";
    private static final String DESCRIPTION = "Provides workspace file management tools: listing directory contents, creating files or directories, deleting, moving, copying, and retrieving file metadata.";

    private boolean mIsActive = false;
    private final List<McpTool> mTools;

    public FileMcpServer() {
        mTools = new ArrayList<>();

        // Tool 1: file_list_directory
        mTools.add(McpTool.builder("file_list_directory", "List contents of a directory in the workspace.")
                .addProperty("path", PropertyType.STRING, "Directory path relative to workspace home (or absolute path). Default: '.'", false)
                .build());

        // Tool 2: file_create_file
        mTools.add(McpTool.builder("file_create_file", "Create a new empty file at specified path.")
                .addProperty("path", PropertyType.STRING, "File path to create.", true)
                .build());

        // Tool 3: file_create_directory
        mTools.add(McpTool.builder("file_create_directory", "Create a directory (and parent directories if missing).")
                .addProperty("path", PropertyType.STRING, "Directory path to create.", true)
                .build());

        // Tool 4: file_delete
        mTools.add(McpTool.builder("file_delete", "Delete a file or directory.")
                .addProperty("path", PropertyType.STRING, "File or directory path to delete.", true)
                .addProperty("recursive", PropertyType.BOOLEAN, "Set true to delete non-empty directories. Default: false.", false)
                .build());

        // Tool 5: file_move
        mTools.add(McpTool.builder("file_move", "Move or rename a file or directory.")
                .addProperty("src", PropertyType.STRING, "Source path.", true)
                .addProperty("dst", PropertyType.STRING, "Destination path.", true)
                .build());

        // Tool 6: file_copy
        mTools.add(McpTool.builder("file_copy", "Copy a file or directory.")
                .addProperty("src", PropertyType.STRING, "Source path.", true)
                .addProperty("dst", PropertyType.STRING, "Destination path.", true)
                .build());

        // Tool 7: file_get_info
        mTools.add(McpTool.builder("file_get_info", "Get metadata for a file or directory (size, permissions, timestamps).")
                .addProperty("path", PropertyType.STRING, "Path to query.", true)
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
                String pathArg = getStringArg(arguments, "path", ".");
                File dir = resolveFile(pathArg, context);
                if (!dir.exists()) {
                    return McpToolResult.error("Directory does not exist: " + pathArg);
                }
                if (!dir.isDirectory()) {
                    return McpToolResult.error("Path is not a directory: " + pathArg);
                }

                JSONArray items = new JSONArray();
                File[] children = dir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        JSONObject item = new JSONObject();
                        item.put("name", child.getName());
                        item.put("is_directory", child.isDirectory());
                        item.put("size", child.length());
                        item.put("last_modified", child.lastModified());
                        items.put(item);
                    }
                }

                JSONObject res = new JSONObject();
                res.put("status", "success");
                res.put("path", dir.getAbsolutePath());
                res.put("items", items);
                return McpToolResult.success(res.toString(2));

            } else if ("file_create_file".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File file = resolveFile(pathArg, context);
                if (file.exists() && !file.isFile()) {
                    return McpToolResult.error("Path is not a file: " + pathArg);
                }
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                if (!file.exists()) {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        // Empty file creation
                    }
                }
                return McpToolResult.success("File created successfully: " + file.getAbsolutePath());

            } else if ("file_create_directory".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File dir = resolveFile(pathArg, context);
                if (dir.exists()) {
                    if (!dir.isDirectory()) {
                        return McpToolResult.error("Path is not a directory: " + pathArg);
                    }
                } else if (!dir.mkdirs() && !dir.isDirectory()) {
                    return McpToolResult.error("Failed to create directory: " + pathArg);
                }
                return McpToolResult.success("Directory created successfully: " + dir.getAbsolutePath());

            } else if ("file_delete".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                boolean recursive = getBooleanArg(arguments, "recursive", false);
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File target = resolveFile(pathArg, context);
                if (!target.exists()) return McpToolResult.error("Target does not exist: " + pathArg);

                boolean deleted = deleteRecursive(target, recursive);
                if (deleted) {
                    return McpToolResult.success("Deleted successfully: " + target.getAbsolutePath());
                } else {
                    return McpToolResult.error("Failed to delete: " + pathArg + " (try setting recursive: true if directory)");
                }

            } else if ("file_move".equals(toolName)) {
                String srcArg = getStringArg(arguments, "src", "");
                String dstArg = getStringArg(arguments, "dst", "");
                if (srcArg.isEmpty() || dstArg.isEmpty()) return McpToolResult.error("Missing 'src' or 'dst' parameter.");

                File src = resolveFile(srcArg, context);
                File dst = resolveFile(dstArg, context);
                if (!src.exists()) return McpToolResult.error("Source does not exist: " + srcArg);

                File parent = dst.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                boolean renamed = src.renameTo(dst);
                if (renamed) {
                    return McpToolResult.success("Moved successfully to: " + dst.getAbsolutePath());
                } else {
                    return McpToolResult.error("Failed to move file/directory.");
                }

            } else if ("file_copy".equals(toolName)) {
                String srcArg = getStringArg(arguments, "src", "");
                String dstArg = getStringArg(arguments, "dst", "");
                if (srcArg.isEmpty() || dstArg.isEmpty()) return McpToolResult.error("Missing 'src' or 'dst' parameter.");

                File src = resolveFile(srcArg, context);
                File dst = resolveFile(dstArg, context);
                if (!src.exists()) return McpToolResult.error("Source does not exist: " + srcArg);

                copyRecursive(src, dst);
                return McpToolResult.success("Copied successfully to: " + dst.getAbsolutePath());

            } else if ("file_get_info".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File target = resolveFile(pathArg, context);
                if (!target.exists()) return McpToolResult.error("Target does not exist: " + pathArg);

                JSONObject info = new JSONObject();
                info.put("path", target.getAbsolutePath());
                info.put("exists", true);
                info.put("is_directory", target.isDirectory());
                info.put("is_file", target.isFile());
                info.put("size_bytes", target.length());
                info.put("last_modified_ms", target.lastModified());
                info.put("can_read", target.canRead());
                info.put("can_write", target.canWrite());
                info.put("can_execute", target.canExecute());

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
            if (children != null && recursive) {
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
        return PathResolver.resolveFile(pathArg, context);
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
