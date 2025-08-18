package com.bigphil.mergehell;

import com.bigphil.mergehell.model.ObstacleManager;
import com.bigphil.mergehell.model.Player;
import com.bigphil.mergehell.model.ScoreBoardRenderer;
import com.bigphil.mergehell.model.ScoreManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GamePanel extends JPanel implements ActionListener {

    private enum GameState {
        START_MENU, RUNNING, PAUSED, GAME_OVER
    }

    private GameState gameState = GameState.START_MENU;
    private final Timer timer;

    private final Player player;
    private final ObstacleManager obstacleManager;
    private final ScoreManager scoreManager;
    private final ScoreBoardRenderer scoreBoardRenderer;

    private boolean moveLeft = false;
    private boolean moveRight = false;

    public GamePanel() {
        setPreferredSize(new Dimension(800, 400));
        setBackground(Color.BLACK);
        setFocusable(true);

        player = new Player(100, getPreferredSize().height);
        obstacleManager = new ObstacleManager();
        scoreManager = new ScoreManager();
        scoreBoardRenderer = new ScoreBoardRenderer(scoreManager);

        timer = new Timer(20, this);

        setupKeyBindings();
    }

    private void setupKeyBindings() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> {
                        if (gameState == GameState.START_MENU || gameState == GameState.GAME_OVER) {
                            startGame();
                        } else if (gameState == GameState.PAUSED) {
                            resumeGame();
                        }
                    }
                    case KeyEvent.VK_ESCAPE -> {
                        if (gameState == GameState.RUNNING) {
                            pauseGame();
                        }
                    }
                    case KeyEvent.VK_SPACE -> {
                        if (gameState == GameState.RUNNING) {
                            player.jump();
                        }
                    }
                    case KeyEvent.VK_LEFT -> moveLeft = true;
                    case KeyEvent.VK_RIGHT -> moveRight = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> moveLeft = false;
                    case KeyEvent.VK_RIGHT -> moveRight = false;
                    case KeyEvent.VK_SPACE -> player.releaseJump();
                }
            }
        });

        // 自动暂停：插件窗口失焦或隐藏
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (!isShowing() && gameState == GameState.RUNNING) {
                    pauseGame();
                }
            }
        });
    }

    private void startGame() {
        player.reset(); // ← reset player position and state
        obstacleManager.reset(getHeight());
        scoreManager.reset();
        gameState = GameState.RUNNING;
        timer.start();
        SwingUtilities.invokeLater(() -> {
            if (isShowing()) {
                requestFocusInWindow();
            }
        });
    }


    private void pauseGame() {
        gameState = GameState.PAUSED;
        timer.stop();
    }

    private void resumeGame() {
        gameState = GameState.RUNNING;
        timer.start();
        requestFocusInWindow();
    }

    private void gameOver() {
        gameState = GameState.GAME_OVER;
        timer.stop();
        scoreManager.finalizeScore();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState != GameState.RUNNING) return;

        player.update(getWidth(), getHeight(), moveLeft, moveRight);
        obstacleManager.update(getWidth(), getHeight());

        if (player.getX() > getWidth() - player.getWidth()) {
            player.update(getWidth(), getHeight(), moveLeft, moveRight); // 阻止越界
        }

        if (obstacleManager.checkCollision(player.getBounds())) {
            gameOver();
        } else {
            scoreManager.increase();
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        switch (gameState) {
            case START_MENU -> {
                drawTitle(g);
                drawHint(g, "Press ENTER to Start");
            }
            case RUNNING -> {
                player.draw(g);
                obstacleManager.draw(g);
                scoreBoardRenderer.drawScore(g, getWidth());
            }
            case PAUSED -> {
                player.draw(g);
                obstacleManager.draw(g);
                scoreBoardRenderer.drawScore(g, getWidth());
                drawHint(g, "Paused - Press ENTER to Resume");
            }
            case GAME_OVER -> {
                player.draw(g);
                obstacleManager.draw(g);
                scoreBoardRenderer.drawScore(g, getWidth());
                drawHint(g, "Game Over - Press ENTER to Restart");
                scoreBoardRenderer.drawTopScores(g, getWidth(), getHeight());
            }
        }
    }

    private void drawTitle(Graphics g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 32));
        drawCentered(g, "Merge Hell Runner", getHeight() / 2 - 40);
    }

    private void drawHint(Graphics g, String message) {
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        drawCentered(g, message, getHeight() / 2 + 60); // 原来是 getHeight() - 50
    }


    private void drawCentered(Graphics g, String text, int y) {
        FontMetrics fm = g.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }
}
