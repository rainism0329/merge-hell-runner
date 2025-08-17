package com.bigphil.mergehell;

import com.intellij.openapi.wm.ToolWindow;

import javax.swing.*;
import java.awt.*;

public class GameContainerPanel extends JPanel {
    private final CardLayout layout = new CardLayout();
    private final JPanel mainPanel = new JPanel(layout);
    private final GamePanel gamePanel;
    private final JButton startButton = new JButton("Start Game");
    private final JButton pauseButton = new JButton("Pause");
    private boolean isPaused = false;

    public GameContainerPanel(ToolWindow toolWindow) {
        setLayout(new BorderLayout());

        gamePanel = new GamePanel();
        JPanel startScreen = new JPanel(new GridBagLayout());
        startScreen.add(startButton);
        mainPanel.add(startScreen, "start");
        mainPanel.add(gamePanel, "game");

        add(mainPanel, BorderLayout.CENTER);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(pauseButton);
        pauseButton.setEnabled(false);
        add(topBar, BorderLayout.NORTH);

        startButton.addActionListener(e -> {
            layout.show(mainPanel, "game");
            gamePanel.startGame();
            pauseButton.setEnabled(true);
            SwingUtilities.invokeLater(() -> gamePanel.requestFocusInWindow());
        });

        pauseButton.addActionListener(e -> {
            if (isPaused) {
                gamePanel.resumeGame();
                pauseButton.setText("Pause");
            } else {
                gamePanel.pauseGame();
                pauseButton.setText("Resume");
            }
            isPaused = !isPaused;
        });
    }
}
