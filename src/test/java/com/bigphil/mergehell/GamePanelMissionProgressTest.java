package com.bigphil.mergehell;

import com.bigphil.mergehell.engine.TickScheduler;
import com.bigphil.mergehell.persistence.MergeHellState;
import com.bigphil.mergehell.persistence.MergeHellStateService;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;

import static org.junit.jupiter.api.Assertions.*;

class GamePanelMissionProgressTest {
    @Test void userPauseFreezesTheRouteAndResumeConsumesOnlyOneStageTick() throws Exception {
        var storage = MergeHellStateService.getInstance();
        MergeHellState original = storage.getState();
        storage.loadState(new MergeHellState());
        GamePanel[] created = new GamePanel[1];
        SwingUtilities.invokeAndWait(() -> created[0] = new GamePanel(new TickScheduler() {
            public void scheduleAtFixedRate(Runnable tick, long period) { }
            public void dispose() { }
        }, () -> 0L));
        GamePanel panel = created[0];
        try {
            var advance = GamePanel.class.getDeclaredMethod("advanceSimulation"); advance.setAccessible(true);
            key(panel, "START"); advance.invoke(panel);
            key(panel, "PAUSE_P"); advance.invoke(panel);
            var before = panel.getSession().routeProgress();
            long worldTick = panel.getSession().worldTick();
            for (int i = 0; i < 500; i++) advance.invoke(panel);
            assertEquals(worldTick, panel.getSession().worldTick());
            assertEquals(before, panel.getSession().routeProgress());
            key(panel, "PAUSE_P"); advance.invoke(panel);
            assertEquals(worldTick + 1, panel.getSession().worldTick());
            assertEquals(before.stageTicksRemaining() - 1, panel.getSession().routeProgress().stageTicksRemaining());
        } finally { panel.dispose(); storage.loadState(original); }
    }

    private static void key(GamePanel panel, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.getActionMap().get(name).actionPerformed(new ActionEvent(panel, 0, name));
            panel.getActionMap().get(name + "_RELEASE").actionPerformed(new ActionEvent(panel, 0, name));
        });
    }
}
