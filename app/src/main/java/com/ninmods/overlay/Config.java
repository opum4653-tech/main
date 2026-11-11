package com.ninmods.overlay;

public class Config {
    // Visuals / ESP
    public static boolean espBox = true;
    public static boolean espLine = false;
    public static boolean espHealth = true;
    public static boolean espName = true;
    public static boolean espDistance = true;
    public static boolean espSkeleton = false;

    // Combat / Aim
    public static boolean aimEnable = false;
    public static boolean scopeAim = true;
    public static boolean sniperAim = true;
    public static boolean showFov = true;
    public static float fovRadius = 180.0f;
    public static float headshotRate = 100.0f;
    public static int targetBone = 0;       // 0 = Head, 1 = Neck/Chest, 2 = Pelvis/Body
    public static int triggerMode = 0;      // 0 = When Firing, 1 = Always In FOV

    // Theme Colors (Electric Orchid Palette)
    public static final int COLOR_PRIMARY       = 0xFFE10098; // Vibrant Orchid Neon
    public static final int COLOR_PRIMARY_LIGHT = 0xFFFF5ACD; // Light Orchid Accent
    public static final int COLOR_DARK_BG       = 0xF0120E1A; // Deep Glass Charcoal
    public static final int COLOR_CARD_BG       = 0xF5161122; // Sub-card Violet Glass
    public static final int COLOR_BORDER        = 0xFF322448; // Subtle Violet Border
    public static final int COLOR_TEXT_WHITE    = 0xFFFFFFFF;
    public static final int COLOR_TEXT_MUTED    = 0xFF8E88A0;
    public static final int COLOR_ONLINE_GREEN  = 0xFF00F08C;
    public static final int COLOR_OFFLINE_RED   = 0xFFFF5A5A;
}
