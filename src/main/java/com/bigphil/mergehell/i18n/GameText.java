package com.bigphil.mergehell.i18n;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * Presentation-only localization. A scoped language belongs to one published frame, never the
 * process or simulation. Canonical messages already held by controllers/logs remain unchanged,
 * so changing language also refreshes their existing text without restarting a run.
 * New UI can address message keys directly; the source index adapts older display contracts.
 */
public final class GameText {
    // Optional package-local diagnostic collector; it never controls rendering or game state.
    static final ThreadLocal<java.util.function.Consumer<String>> DISPLAY_OBSERVER = new ThreadLocal<>();
    private static final ThreadLocal<GameLanguage> CURRENT = ThreadLocal.withInitial(() -> GameLanguage.ENGLISH);
    private static final String CHINESE_FONT = chineseFont();
    private static final Map<GameLanguage, Properties> BUNDLES = loadBundles();
    private static final Map<String, String> EXACT = new HashMap<>();
    private static final List<Template> TEMPLATES = new ArrayList<>();
    private static final Pattern SLOT = Pattern.compile("\\{(\\d+)\\}");
    private static final Pattern TIMESTAMP = Pattern.compile("^(\\[\\d{2}:\\d{2}:\\d{2}\\] )(.+)$", Pattern.DOTALL);
    private static final ThreadLocal<Map<GameLanguage, Map<String, String>>> CACHE = ThreadLocal.withInitial(() -> {
        Map<GameLanguage, Map<String, String>> cache = new EnumMap<>(GameLanguage.class);
        for (GameLanguage language : GameLanguage.values()) cache.put(language, new LinkedHashMap<>(128, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, String> entry) { return size() > 1024; }
        });
        return cache;
    });
    static {
        Properties english = BUNDLES.get(GameLanguage.ENGLISH);
        for (String key : new TreeSet<>(english.stringPropertyNames())) {
            String source = english.getProperty(key);
            if (SLOT.matcher(source).find()) TEMPLATES.add(new Template(key, source));
            else EXACT.putIfAbsent(source.toLowerCase(Locale.ROOT), key);
        }
        TEMPLATES.sort(Comparator.comparingInt(Template::specificity).reversed());
    }

    public static Scope use(GameLanguage language) {
        GameLanguage previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(language));
        return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final GameLanguage previous;
        private boolean closed;
        private Scope(GameLanguage previous) { this.previous = previous; }
        @Override public void close() { if (!closed) { CURRENT.set(previous); closed = true; } }
    }
    public static GameLanguage language() { return CURRENT.get(); }
    public static void draw(Graphics2D graphics, String source, int x, int y) {
        String displayed = text(source);
        var observer = DISPLAY_OBSERVER.get();
        if (observer != null) observer.accept(displayed);
        graphics.drawString(displayed, x, y);
    }
    public static void fitFont(Graphics2D graphics, String source, int width, int minimumSize) {
        String displayed = text(source);
        Font current = graphics.getFont();
        while (current.getSize() > minimumSize && graphics.getFontMetrics().stringWidth(displayed) > width) {
            current = current.deriveFont((float) current.getSize() - 1);
            graphics.setFont(current);
        }
    }
    public static Font font(Font source) {
        return language() == GameLanguage.SIMPLIFIED_CHINESE && !source.getFamily().equals(CHINESE_FONT)
                ? new Font(CHINESE_FONT, source.getStyle(), source.getSize()).deriveFont(source.getSize2D()) : source;
    }
    private static String chineseFont() {
        Set<String> available = Set.of(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String font : List.of("Microsoft YaHei UI", "Microsoft YaHei", "Noto Sans CJK SC", "PingFang SC"))
            if (available.contains(font)) return font;
        return Font.DIALOG;
    }
    public static String message(String key, Object... args) {
        String pattern = BUNDLES.get(language()).getProperty(key);
        if (pattern == null) throw new IllegalArgumentException("Unknown UI message: " + key);
        return substitute(pattern, Arrays.stream(args).map(String::valueOf).map(GameText::text).toArray(String[]::new));
    }
    public static String text(String canonical) {
        if (canonical == null || canonical.isEmpty()) return canonical;
        // Numbers, key symbols and already translated CJK need no template scan.
        if (canonical.codePoints().noneMatch(Character::isLetter)
                || canonical.codePoints().anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN))
            return canonical;
        return CACHE.get().get(language()).computeIfAbsent(canonical, value -> resolve(value, 0));
    }
    private static String resolve(String source, int depth) {
        if (source == null || source.isEmpty() || depth > 8) return source;
        if (source.codePoints().noneMatch(Character::isLetter)) return source;
        String key = EXACT.get(source.toLowerCase(Locale.ROOT));
        if (key != null) return BUNDLES.get(language()).getProperty(key);
        Matcher time = TIMESTAMP.matcher(source);
        if (time.matches()) return time.group(1) + resolve(time.group(2), depth + 1);
        for (Template template : TEMPLATES) {
            Matcher match = template.pattern.matcher(source);
            if (!match.matches()) continue;
            String[] args = new String[template.slots.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1];
            for (int index = 0; index < template.slots.size(); index++)
                args[template.slots.get(index)] = resolve(match.group(index + 1), depth + 1);
            return substitute(BUNDLES.get(language()).getProperty(template.key), args);
        }
        return source;
    }
    private static String substitute(String pattern, String[] args) {
        Matcher matcher = SLOT.matcher(pattern);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= args.length || args[index] == null) throw new IllegalArgumentException("Missing message argument: " + pattern);
            matcher.appendReplacement(result, Matcher.quoteReplacement(args[index]));
        }
        matcher.appendTail(result);
        return result.toString();
    }
    /** Unicode-aware wrapping: preserves words when possible, also handles CJK without spaces. */
    public static List<String> wrap(FontMetrics metrics, String source, int width) {
        String text = text(source);
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\n", -1)) {
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                int end = 0, wordBreak = -1;
                while (end < remaining.length()) {
                    int next = remaining.offsetByCodePoints(end, 1);
                    if (metrics.stringWidth(remaining.substring(0, next)) > width && end > 0) break;
                    if (Character.isWhitespace(remaining.codePointAt(end))) wordBreak = end;
                    end = next;
                }
                if (end < remaining.length() && wordBreak > 0) end = wordBreak;
                lines.add(remaining.substring(0, end).stripTrailing());
                remaining = remaining.substring(end).stripLeading();
            }
            if (paragraph.isEmpty()) lines.add("");
        }
        return List.copyOf(lines);
    }
    private record Template(String key, Pattern pattern, List<Integer> slots, int specificity) {
        Template(String key, String source) {
            this(key, compile(key, source), slotNumbers(source), SLOT.matcher(source).replaceAll("").length());
        }
        private static Pattern compile(String key, String source) {
            Matcher matcher = SLOT.matcher(source);
            StringBuilder pattern = new StringBuilder("^"); int start = 0;
            while (matcher.find()) {
                boolean numeric = key.startsWith("unit.")
                        || Set.of("hud.weapon.ammo", "hud.weapon.level", "boss.stage.short").contains(key) && matcher.group(1).equals("1")
                        || key.equals("upgrade.tag") && matcher.group(1).equals("0");
                pattern.append(Pattern.quote(source.substring(start, matcher.start())))
                        .append(numeric ? "([-+]?\\d+(?:\\.\\d+)?)" : "(.+?)");
                start = matcher.end();
            }
            return Pattern.compile(pattern.append(Pattern.quote(source.substring(start))).append("$").toString(),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
        }
        private static List<Integer> slotNumbers(String source) {
            List<Integer> slots = new ArrayList<>(); Matcher matcher = SLOT.matcher(source);
            while (matcher.find()) slots.add(Integer.parseInt(matcher.group(1)));
            return slots;
        }
    }
    private static Map<GameLanguage, Properties> loadBundles() {
        Map<GameLanguage, Properties> bundles = new EnumMap<>(GameLanguage.class);
        for (GameLanguage language : GameLanguage.values()) {
            String path = "/game/i18n/messages_" + language.tag() + ".properties";
            try (var stream = GameText.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing UI language resource: " + path);
                Properties values = new Properties();
                values.load(new InputStreamReader(stream, StandardCharsets.UTF_8)); bundles.put(language, values);
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load " + path, error); }
        }
        return Map.copyOf(bundles);
    }
    private GameText() { }
}
