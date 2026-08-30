package com.tj90.prioritytodo;

import android.graphics.Color;

final class PriorityPalette {
    static final int IMMEDIATE = 0xFFE82729;
    static final int NEXT_WEEK = 0xFFDFA700;
    static final int SOMEDAY = 0xFF006CE5;
    static final int GREEN_DONE_DAY = 0xFF00A448;
    static final int GREEN_DONE_NIGHT = 0xFF00B054;
    static final int GREEN_REVEAL = 0xFF3D9C5E;
    static final int CIRCLE_DONE_BORDER = 0xFF00A635;
    static final int CIRCLE_DONE_BG = 0xFFBDFAC8;

    static final String IMMEDIATE_LABEL = "Immediate";
    static final String NEXT_WEEK_LABEL = "Next week";
    static final String SOMEDAY_LABEL = "Someday";

    final int bg;
    final int surface;
    final int ink;
    final int sub;
    final int line;
    final int accent;
    final int accentInk;
    final int done;
    final int later;
    final int heroInk;
    final int heroSub;

    private PriorityPalette(int bg, int surface, int ink, int sub, int line, int accent,
                            int accentInk, int done, int later, int heroInk, int heroSub) {
        this.bg = bg;
        this.surface = surface;
        this.ink = ink;
        this.sub = sub;
        this.line = line;
        this.accent = accent;
        this.accentInk = accentInk;
        this.done = done;
        this.later = later;
        this.heroInk = heroInk;
        this.heroSub = heroSub;
    }

    static PriorityPalette day() {
        return new PriorityPalette(0xFFF6F7FB, 0xFFFFFFFF, 0xFF1B1F2E, 0xFF787E93, 0xFFEDEDF3,
                0xFF3A3D49, 0xFFFFFFFF, GREEN_DONE_DAY, 0xFFAB9356, 0xFF1B1F2E, 0xFF5C6276);
    }

    static PriorityPalette night() {
        return new PriorityPalette(0xFF14151B, 0xFF1D2027, 0xFFF1F3F8, 0xFF969CB0, 0xFF2A2D38,
                0xFFC8CBD6, 0xFF0E0F16, GREEN_DONE_NIGHT, 0xFFC2A865, 0xFFF1F3F8, 0xFFA9ADC8);
    }

    static String bucket(double score) {
        if (score >= 1000) {
            return IMMEDIATE_LABEL;
        }
        if (score >= 500) {
            return NEXT_WEEK_LABEL;
        }
        return SOMEDAY_LABEL;
    }

    static int catColor(String bucket) {
        if (IMMEDIATE_LABEL.equals(bucket)) {
            return IMMEDIATE;
        }
        if (NEXT_WEEK_LABEL.equals(bucket)) {
            return NEXT_WEEK;
        }
        return SOMEDAY;
    }

    static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * VIBGYOR priority spectrum keyed to a task's position in the sorted list.
     * rank 0 (the top / most important task) is red; the last task is violet.
     * This is deliberately rank-based, not score-based: raw scores clump at
     * 10-90 with urgent tasks at 1000+, so an absolute mapping would collapse
     * the spectrum. Ranking spreads the full spectrum across whatever tasks
     * exist. Urgent tasks sort to the top on their own, so they land in red.
     */
    static int spectrumColor(int rank, int total, boolean night) {
        float f = total <= 1 ? 0f : (float) rank / (float) (total - 1);
        if (f < 0f) {
            f = 0f;
        }
        if (f > 1f) {
            f = 1f;
        }
        float hue = f * 280f; // 0 = red (top), 280 = violet (bottom)
        float sat = night ? 0.60f : 0.75f;
        float val = night ? 0.98f : 0.82f;
        return Color.HSVToColor(new float[]{hue, sat, val});
    }

    static int catSoft(String bucket) {
        return withAlpha(catColor(bucket), 0x26);
    }

    static int catOutline(String bucket) {
        return withAlpha(catColor(bucket), 0xB3);
    }
}
