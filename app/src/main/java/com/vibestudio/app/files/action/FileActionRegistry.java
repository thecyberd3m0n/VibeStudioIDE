package com.vibestudio.app.files.action;

import com.vibestudio.app.files.model.FileEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileActionRegistry {
    private static final List<FileAction> ACTIONS = new ArrayList<>();

    static {
        ACTIONS.add(new OpenTerminalAction());
        ACTIONS.add(new RenameAction());
        ACTIONS.add(new DeleteAction());
    }

    public static List<FileAction> getAvailableActionsFor(FileEntry entry) {
        if (entry == null) return Collections.emptyList();
        List<FileAction> available = new ArrayList<>();
        for (FileAction action : ACTIONS) {
            if (action.isAvailableFor(entry)) {
                available.add(action);
            }
        }
        return available;
    }
}
