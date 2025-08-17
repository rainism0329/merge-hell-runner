package com.bigphil.mergehell;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class GamePanel extends JPanel implements ActionListener, KeyListener {
    private Timer timer;
    private Player player;
    private List<Obstacle> obstacles;
    private final Set<Integer> pressedKeys = new HashSet<>();

    private boolean isRunning = false;
    private boolean isPaused = false;

    public GamePanel() {
        setFocusable(true);
        addKeyListener(this);

        // 如果面板变为可见，重新请求焦点
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                requestFocusInWindow();
            }
        });

        player = new Player();
        obstacles = new ArrayList<>();
        timer = new Timer(30, this);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(800, 400);
    }

    public void startGame() {
        isRunning = true;
        isPaused = false;
        player.reset();
        obstacles.clear();
        timer.start();
        requestFocusInWindow();
    }

    public void pauseGame() {
        isPaused = true;
        timer.stop();
    }

    public void resumeGame() {
        isPaused = false;
        timer.start();
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        player.draw(g);
        for (Obstacle ob : obstacles) ob.draw(g);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!this.isShowing() || !this.isVisible()) {
            if (!isPaused && isRunning) {
                pauseGame();
            }
            return;
        }

        if (pressedKeys.contains(KeyEvent.VK_LEFT)) player.moveLeft();
        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) player.moveRight();
        if (pressedKeys.contains(KeyEvent.VK_SPACE)) player.jump();

        player.update();
        obstacles.removeIf(ob -> ob.getX() + ob.getWidth() < 0);
        for (Obstacle ob : obstacles) {
            ob.moveLeft();
            if (player.collidesWith(ob)) {
                timer.stop();
                JOptionPane.showMessageDialog(this, "💥 Merge Hell crashed! Game Over.");
                startGame(); // restart
                return;
            }
        }
        if (Math.random() < 0.02) obstacles.add(new Obstacle(getWidth()));
        repaint();
    }

    @Override public void keyPressed(KeyEvent e) { pressedKeys.add(e.getKeyCode()); }
    @Override public void keyReleased(KeyEvent e) { pressedKeys.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) {}
}
