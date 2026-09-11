/*
 * Decompiled with CFR 0.152.
 */
package se.anders.tunerstudio.pidautotune.ui;

import java.awt.Color;

final class UiTheme {
    private static Side side = Side.LIGHT_SIDE;

    private UiTheme() {
    }

    static Side side() {
        return side;
    }

    static boolean isDark() {
        return side == Side.DARK_SIDE;
    }

    static void set(Side side) {
        UiTheme.side = side == null ? Side.LIGHT_SIDE : side;
    }

    static void toggle() {
        side = UiTheme.isDark() ? Side.LIGHT_SIDE : Side.DARK_SIDE;
    }

    static String displayName() {
        return UiTheme.isDark() ? "Dark Side" : "Light Side";
    }

    static Color bg() {
        return UiTheme.isDark() ? new Color(24, 28, 34) : new Color(242, 244, 247);
    }

    static Color focusBg() {
        return UiTheme.isDark() ? new Color(24, 29, 36) : new Color(242, 246, 250);
    }

    static Color card() {
        return UiTheme.isDark() ? new Color(34, 40, 48) : Color.WHITE;
    }

    static Color panel() {
        return UiTheme.isDark() ? new Color(29, 34, 41) : new Color(235, 238, 243);
    }

    static Color border() {
        return UiTheme.isDark() ? new Color(69, 78, 90) : new Color(207, 213, 221);
    }

    static Color text() {
        return UiTheme.isDark() ? new Color(232, 237, 243) : new Color(28, 33, 40);
    }

    static Color focusText() {
        return UiTheme.isDark() ? new Color(232, 238, 246) : new Color(23, 38, 60);
    }

    static Color muted() {
        return UiTheme.isDark() ? new Color(160, 172, 187) : new Color(96, 105, 118);
    }

    static Color focusMuted() {
        return UiTheme.isDark() ? new Color(155, 173, 195) : new Color(78, 100, 128);
    }

    static Color navy() {
        return UiTheme.isDark() ? new Color(136, 181, 225) : new Color(35, 72, 108);
    }

    static Color blue() {
        return UiTheme.isDark() ? new Color(88, 157, 255) : new Color(42, 108, 214);
    }

    static Color focusBlue() {
        return UiTheme.isDark() ? new Color(78, 153, 255) : new Color(24, 101, 220);
    }

    static Color blue2() {
        return UiTheme.isDark() ? new Color(109, 176, 255) : new Color(69, 145, 239);
    }

    static Color green() {
        return UiTheme.isDark() ? new Color(69, 190, 108) : new Color(41, 157, 84);
    }

    static Color focusGreen() {
        return UiTheme.isDark() ? new Color(70, 194, 108) : new Color(30, 164, 81);
    }

    static Color amber() {
        return UiTheme.isDark() ? new Color(255, 197, 67) : new Color(255, 197, 54);
    }

    static Color focusAmber() {
        return UiTheme.isDark() ? new Color(246, 172, 50) : new Color(229, 143, 0);
    }

    static Color amberDark() {
        return UiTheme.isDark() ? new Color(176, 119, 18) : new Color(168, 111, 0);
    }

    static Color red() {
        return UiTheme.isDark() ? new Color(241, 103, 96) : new Color(213, 66, 58);
    }

    static Color purple() {
        return UiTheme.isDark() ? new Color(157, 137, 255) : new Color(98, 75, 200);
    }

    static Color softBlue() {
        return UiTheme.isDark() ? new Color(31, 49, 72) : new Color(229, 237, 252);
    }

    static Color focusSoftBlue() {
        return UiTheme.isDark() ? new Color(31, 51, 78) : new Color(235, 244, 255);
    }

    static Color softGreen() {
        return UiTheme.isDark() ? new Color(31, 59, 43) : new Color(226, 246, 233);
    }

    static Color focusSoftGreen() {
        return UiTheme.isDark() ? new Color(31, 62, 44) : new Color(232, 249, 237);
    }

    static Color softAmber() {
        return UiTheme.isDark() ? new Color(70, 55, 28) : new Color(255, 246, 221);
    }

    static Color softRed() {
        return UiTheme.isDark() ? new Color(72, 38, 38) : new Color(255, 235, 232);
    }

    static Color softPurple() {
        return UiTheme.isDark() ? new Color(49, 42, 76) : new Color(239, 235, 255);
    }

    static Color disabled() {
        return UiTheme.isDark() ? new Color(27, 32, 39) : new Color(229, 232, 236);
    }

    static Color taskAvailableBg() {
        return UiTheme.isDark() ? new Color(45, 54, 66) : UiTheme.card();
    }

    static Color taskAvailableText() {
        return UiTheme.isDark() ? new Color(238, 242, 247) : UiTheme.text();
    }

    static Color taskStatusText() {
        return UiTheme.isDark() ? new Color(184, 197, 212) : UiTheme.muted();
    }

    static Color taskAvailableBorder() {
        return UiTheme.isDark() ? new Color(78, 91, 107) : UiTheme.border();
    }

    static Color taskDisabledBg() {
        return UiTheme.isDark() ? new Color(18, 23, 29) : UiTheme.disabled();
    }

    static Color taskDisabledText() {
        return UiTheme.isDark() ? new Color(74, 90, 107) : new Color(184, 207, 229);
    }

    static Color taskDisabledStatusText() {
        return UiTheme.isDark() ? new Color(57, 70, 84) : new Color(176, 201, 223);
    }

    static Color taskDisabledBorder() {
        return UiTheme.isDark() ? new Color(34, 41, 49) : UiTheme.border();
    }

    static Color taskSelectedBg() {
        return UiTheme.isDark() ? new Color(31, 64, 96) : new Color(178, 207, 232);
    }

    static Color taskSelectedText() {
        return UiTheme.isDark() ? Color.WHITE : new Color(22, 39, 58);
    }

    static Color taskSelectedStatusText() {
        return UiTheme.isDark() ? new Color(216, 232, 247) : new Color(58, 82, 105);
    }

    static Color taskSelectedBorder() {
        return UiTheme.isDark() ? new Color(86, 149, 211) : new Color(121, 159, 191);
    }

    static Color taskGroupOff() {
        return UiTheme.isDark() ? new Color(126, 142, 158) : UiTheme.muted();
    }

    static Color button() {
        return UiTheme.isDark() ? new Color(45, 52, 62) : new Color(238, 240, 243);
    }

    static Color buttonAlt() {
        return UiTheme.isDark() ? new Color(43, 51, 62) : new Color(242, 245, 249);
    }

    static Color buttonBorder() {
        return UiTheme.isDark() ? new Color(78, 90, 105) : new Color(187, 193, 202);
    }

    static Color focusButtonBorder() {
        return UiTheme.isDark() ? new Color(82, 101, 122) : new Color(171, 187, 204);
    }

    static Color chartGrid() {
        return UiTheme.isDark() ? new Color(58, 67, 79) : new Color(226, 233, 241);
    }

    static Color chartGridSoft() {
        return UiTheme.isDark() ? new Color(53, 61, 72) : new Color(232, 237, 243);
    }

    static Color tableHeader() {
        return UiTheme.isDark() ? new Color(43, 50, 59) : new Color(238, 241, 245);
    }

    static Color neutralSoft() {
        return UiTheme.isDark() ? new Color(39, 45, 54) : new Color(246, 248, 251);
    }

    static Color selection() {
        return UiTheme.isDark() ? new Color(103, 165, 255) : new Color(16, 71, 160);
    }

    static Color inactiveStep() {
        return UiTheme.isDark() ? new Color(59, 66, 76) : new Color(228, 231, 236);
    }

    static Color mapGrid() {
        return UiTheme.isDark() ? new Color(73, 86, 101) : new Color(174, 190, 207);
    }

    static Color legendBorder() {
        return UiTheme.isDark() ? new Color(86, 99, 114) : new Color(145, 155, 168);
    }

    static Color thresholdFill() {
        return UiTheme.isDark() ? new Color(45, 63, 84) : new Color(215, 229, 246);
    }

    static Color clusterFill() {
        return UiTheme.isDark() ? new Color(39, 59, 82) : new Color(218, 234, 252);
    }

    static Color overlay() {
        return UiTheme.isDark() ? new Color(130, 145, 164, 55) : new Color(190, 200, 212, 70);
    }

    static Color referenceLine() {
        return UiTheme.isDark() ? new Color(156, 170, 190) : new Color(130, 145, 164);
    }

    static Color noiseLine() {
        return UiTheme.isDark() ? new Color(132, 173, 222) : new Color(150, 180, 220);
    }

    static Color milestone() {
        return UiTheme.isDark() ? new Color(132, 172, 220) : new Color(65, 100, 145);
    }

    static enum Side {
        LIGHT_SIDE,
        DARK_SIDE;

    }
}

