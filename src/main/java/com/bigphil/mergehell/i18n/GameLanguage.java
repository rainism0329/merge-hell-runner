package com.bigphil.mergehell.i18n;

import java.util.Locale;

public enum GameLanguage {
    ENGLISH("en", "English"), SIMPLIFIED_CHINESE("zh-CN", "简体中文");

    private final String tag;
    private final String nativeName;
    GameLanguage(String tag, String nativeName) { this.tag = tag; this.nativeName = nativeName; }
    public String tag() { return tag; }
    public String nativeName() { return nativeName; }
    public static GameLanguage systemDefault() {
        return "zh".equals(Locale.getDefault().getLanguage()) ? SIMPLIFIED_CHINESE : ENGLISH;
    }
    public static GameLanguage fromTag(String tag) {
        if (tag != null) {
            if (tag.equalsIgnoreCase("en")) return ENGLISH;
            if (tag.equalsIgnoreCase("zh-CN") || tag.equalsIgnoreCase("zh_CN")) return SIMPLIFIED_CHINESE;
        }
        return systemDefault();
    }
}
