package com.bigphil.mergehell.i18n;

import com.bigphil.mergehell.combat.WeaponCatalog;
import com.bigphil.mergehell.combat.WeaponId;
import com.bigphil.mergehell.progression.UpgradeCatalog;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class GameTextTest {
    @Test void resourceKeysAndPlaceholdersMatchAndEveryEnglishSourceCanResolve() throws Exception {
        Properties en = bundle("en"), zh = bundle("zh-CN");
        assertEquals(en.stringPropertyNames(), zh.stringPropertyNames());
        assertTrue(en.size() >= 490);
        for (String key : en.stringPropertyNames()) {
            assertEquals(slots(en.getProperty(key)), slots(zh.getProperty(key)), key);
            assertFalse(zh.getProperty(key).isBlank(), key);
            String sample = en.getProperty(key).replaceAll("\\{\\d+\\}", "7");
            try (var language = GameText.use(GameLanguage.SIMPLIFIED_CHINESE)) {
                String translated = GameText.text(sample);
                assertFalse(translated.contains("{0}"), key);
                assertEquals(translated, GameText.text(translated), "Translation must be idempotent: " + key);
                if (zh.getProperty(key).codePoints().anyMatch(GameTextTest::han))
                    assertTrue(translated.codePoints().anyMatch(GameTextTest::han), key + " => " + translated);
            }
        }
        try (var language = GameText.use(GameLanguage.SIMPLIFIED_CHINESE)) {
            assertTrue(GameText.text("[←/→] MOVE   [SPACE] JUMP   [C] FIRE   [P] PAUSE").contains("移动"));
            assertEquals("操作说明", GameText.message("controls.guideLabel"));
        }
    }

    @Test void existingLogAndNestedNamesSwitchWithoutChangingTheirSource() {
        String log = "[12:34:56] Installed upgrade: AI Pair Programmer";
        try (var language = GameText.use(GameLanguage.SIMPLIFIED_CHINESE)) {
            assertEquals("[12:34:56] 已安装升级：智能无人机搭档", GameText.text(log));
            assertEquals("内存泄漏守护者 / 阶段 2", GameText.text("MEMORY LEAK DAEMON / P2"));
            assertEquals("每架 14 伤害 / 1.15秒", GameText.text("14 DMG / 1.15s EACH"));
            assertEquals("净化破防 // 伤害 +50%", GameText.text("GC WINDOW // DAMAGE +50%"));
            assertEquals("起始武器：$5 \\ 备份", GameText.message("log.weapon", "$5 \\ 备份"));
        }
        assertEquals(log, GameText.text(log));
        assertEquals("Invincibility disabled", GameText.text("Invincibility disabled"));
    }

    @Test void scopesRestoreAfterExceptionsAndConcurrentWindowsStayIndependent() throws Exception {
        try (var outer = GameText.use(GameLanguage.SIMPLIFIED_CHINESE)) {
            assertEquals("生命", GameText.text("HP"));
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = GameText.use(GameLanguage.ENGLISH)) {
                    assertEquals("HP", GameText.text("HP")); throw new IllegalStateException();
                }
            });
            var worker = Executors.newSingleThreadExecutor();
            try { assertEquals("HP", worker.submit(() -> GameText.text("HP")).get()); }
            finally { worker.shutdownNow(); }
            assertEquals("生命", GameText.text("HP"));
        }
        assertEquals(GameLanguage.ENGLISH, GameText.language());
    }

    @Test void codeNotationAndNumbersNeverBecomeDurationTranslations() {
        try (var language = GameText.use(GameLanguage.SIMPLIFIED_CHINESE)) {
            for (String value : java.util.List.of("0xA13F", "404.jar", "git push -f", "J>", "-450", "100 / 100", "unknowns",
                    "[ L ]", "[ G ]", "[ T ]", "[ ENTER / SPACE ]"))
                assertEquals(value, GameText.text(value));
            assertEquals("1.25秒", GameText.text("1.25s"));
            assertEquals("0.77秒", GameText.text("0.77s"));
        }
    }

    @Test void allWeaponUpgradeAndRankSpecificDescriptionsHaveChineseTranslations() {
        try (var language = GameText.use(GameLanguage.SIMPLIFIED_CHINESE)) {
            for (WeaponId id : WeaponId.values()) {
                var definition = WeaponCatalog.definition(id);
                assertChinese(definition.displayName()); assertChinese(definition.evolutionName());
                for (String tag : definition.tags()) assertChinese(tag);
            }
            for (var upgrade : UpgradeCatalog.all()) {
                assertChinese(upgrade.title()); assertChinese(upgrade.description()); assertChinese(upgrade.tag().name());
            }
            for (int rank = 1; rank <= 3; rank++) for (String line : UpgradeCatalog.droneDescriptionLines(rank)) assertChinese(line);
        }
    }

    @Test void cjkAndEnglishWrapByMeasuredWidthWithoutLosingContentOrSplittingSurrogates() {
        Graphics2D graphics = new BufferedImage(960, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            for (GameLanguage language : GameLanguage.values()) try (var scope = GameText.use(language)) {
                graphics.setFont(GameText.font(new Font("JetBrains Mono", Font.PLAIN, 18)));
                String source = "E Purge: safer / F Salvage: more reward, more leaks";
                var lines = GameText.wrap(graphics.getFontMetrics(), source, 300);
                assertTrue(lines.size() <= 3);
                assertEquals(GameText.text(source).replace(" ", ""), String.join("", lines).replace(" ", ""));
                for (String line : lines) assertTrue(graphics.getFontMetrics().stringWidth(line) <= 300);
                assertEquals(-1, graphics.getFont().canDisplayUpTo(GameText.text("SYSTEM SETTINGS")));
            }
            String unicode = "安全节点🚀净化完成🚀";
            var lines = GameText.wrap(graphics.getFontMetrics(), unicode, 35);
            assertEquals(unicode, String.join("", lines));
            for (String line : lines) assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)));
        } finally { graphics.dispose(); }
    }

    private static void assertChinese(String source) {
        String translated = GameText.text(source);
        assertTrue(translated.codePoints().anyMatch(GameTextTest::han), source + " => " + translated);
        assertFalse(Pattern.compile("[A-Za-z]{3,}").matcher(translated).find(), source + " => " + translated);
    }
    private static boolean han(int point) { return Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN; }
    private static Set<String> slots(String value) {
        Set<String> slots = new TreeSet<>(); var matcher = Pattern.compile("\\{\\d+\\}").matcher(value);
        while (matcher.find()) slots.add(matcher.group()); return slots;
    }
    private static Properties bundle(String language) throws Exception {
        Properties values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                assertFalse(containsKey(key), "Duplicate locale key: " + key);
                return super.put(key, value);
            }
        };
        try (var reader = new InputStreamReader(Objects.requireNonNull(GameText.class.getResourceAsStream(
                "/game/i18n/messages_" + language + ".properties")), StandardCharsets.UTF_8)) { values.load(reader); }
        return values;
    }
}
