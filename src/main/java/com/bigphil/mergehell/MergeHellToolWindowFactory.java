package com.bigphil.mergehell;

import com.bigphil.mergehell.GamePanel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.event.HierarchyEvent;

public class MergeHellToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        GamePanel gamePanel = new GamePanel();
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(gamePanel, "", false);
        toolWindow.getContentManager().addContent(content);

        // Automatically request focus so keyboard input works
        toolWindow.getComponent().addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && toolWindow.isVisible()) {
                gamePanel.requestFocusInWindow();
            }
        });
    }
}
