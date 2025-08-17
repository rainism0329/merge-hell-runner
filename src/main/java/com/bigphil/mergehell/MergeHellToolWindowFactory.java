package com.bigphil.mergehell;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MergeHellToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        GameContainerPanel container = new GameContainerPanel(toolWindow);
        container.setPreferredSize(new Dimension(800, 420)); // 保证初始高度
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(container, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
