package com.bigphil.mergehell;

import com.bigphil.mergehell.i18n.*;
import com.bigphil.mergehell.persistence.*;
import com.bigphil.mergehell.settings.SettingsEditor;
import org.junit.jupiter.api.Test;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelLanguageTest {
    @Test void settingsKeysSwitchTheWholePausedFrameSavePreferenceAndPreserveTheWorld() throws Exception {
        MergeHellState stored = new MergeHellState(); stored.settings.language = "en"; stored.settings.muted = true;
        MergeHellStateService.getInstance().loadState(stored);
        try (HeapGameHarness h = new HeapGameHarness()) {
            h.place(745, 450); h.key("PAUSE_P"); h.tick();
            long tick = h.panel.getSession().worldTick(); var heap = h.heap().snapshot();
            int hp = h.player().getHp(), score = h.context().score;
            h.key("SETTINGS"); h.tick(); h.key("MENU_UP"); h.tick();
            assertEquals(SettingsEditor.Option.LANGUAGE, ((SettingsEditor) h.get("settingsEditor")).snapshot().selected());
            h.key("RIGHT"); h.key("RIGHT_R"); h.tick();
            assertEquals("zh-CN", MergeHellStateService.getInstance().getState().settings.language);
            var chinese = frame(h).lines();
            assertTrue(chinese.contains("系统设置")); assertTrue(chinese.contains("界面语言"));
            assertTrue(chinese.contains("游戏已暂停")); assertFalse(chinese.contains("SYSTEM SETTINGS"));
            h.key("LEFT"); h.key("LEFT_R"); h.tick();
            var english = frame(h).lines();
            assertTrue(english.contains("SYSTEM SETTINGS")); assertTrue(english.contains("LANGUAGE"));
            assertTrue(english.contains("RUN PAUSED"));
            assertTrue(english.stream().noneMatch(GamePanelLanguageTest::hasHan));
            assertEquals(tick, h.panel.getSession().worldTick()); assertEquals(heap, h.heap().snapshot());
            assertEquals(hp, h.player().getHp()); assertEquals(score, h.context().score);
            assertEquals("en", MergeHellStateService.getInstance().getState().settings.language);
        }
    }

    @Test void separatelyConfiguredPanelsCannotOverwriteEachOthersLanguage() throws Exception {
        MergeHellState stored = new MergeHellState(); stored.settings.muted = true; stored.settings.language = "en";
        MergeHellStateService.getInstance().loadState(stored);
        try (HeapGameHarness en = new HeapGameHarness(); HeapGameHarness zh = new HeapGameHarness()) {
            en.set("state", GameState.MENU); zh.set("state", GameState.MENU);
            ((MergeHellState.Settings) zh.get("settings")).language = "zh-CN";
            assertTrue(frame(zh).lines().contains("开始战役"));
            assertTrue(frame(en).lines().contains("Campaign"));
            assertTrue(frame(zh).lines().contains("开始战役"));
            assertEquals(GameLanguage.ENGLISH, GameText.language());
        }
    }

    record Capture(BufferedImage image, List<String> lines) { }
    static Capture frame(HeapGameHarness h) throws Exception {
        BufferedImage image = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try (DisplayTextCapture capture = new DisplayTextCapture()) {
            Method method = GamePanel.class.getDeclaredMethod("renderLogicalFrame", Graphics2D.class);
            method.setAccessible(true); method.invoke(h.panel, graphics);
            return new Capture(image, capture.lines());
        } finally { graphics.dispose(); }
    }
    static boolean hasHan(String text) {
        return text.codePoints().anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN);
    }
}
