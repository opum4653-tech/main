package com.ninmods.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class FloatingMenuService extends Service {

    private WindowManager windowManager;
    private DisplayMetrics metrics;

    // 1. Fullscreen ESP Overlay View
    private ESPOverlayView espOverlayView;
    private WindowManager.LayoutParams espOverlayParams;

    // 2. Floating Circular Logo ('N')
    private FloatingLogoView floatingLogoView;
    private WindowManager.LayoutParams logoParams;

    // 3. Floating Menu Window
    private LinearLayout menuRootView;
    private WindowManager.LayoutParams menuParams;

    private boolean isMenuOpen = false;
    private int activeTab = 0; // 0 = Visuals, 1 = Combat, 2 = Settings

    private LinearLayout tabContentContainer;
    private TextView statusBadgeText;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        // Update screen resolution in C++ native engine
        NativeBridge.setScreenDimensions(metrics.widthPixels, metrics.heightPixels);

        // Initialize background C++ memory & aim engine
        NativeBridge.initEngine("com.dts.freefireth");

        initESPOverlay();
        initFloatingLogo();
        initFloatingMenu();

        startStatusPolling();
    }

    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    // 1. FULLSCREEN TRANSPARENT ESP OVERLAY (PASS-THROUGH TOUCH)
    private void initESPOverlay() {
        espOverlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        espOverlayParams.gravity = Gravity.TOP | Gravity.LEFT;

        espOverlayView = new ESPOverlayView(this);
        windowManager.addView(espOverlayView, espOverlayParams);
    }

    // 2. FLOATING CIRCULAR LOGO ('N') — CLEAN & SIMPLE (NO 4-SIDED ROTATING ARCS)
    private void initFloatingLogo() {
        final int logoSize = (int) (52 * getResources().getDisplayMetrics().density);

        logoParams = new WindowManager.LayoutParams(
                logoSize,
                logoSize,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        logoParams.gravity = Gravity.TOP | Gravity.LEFT;
        logoParams.x = (int) (20 * getResources().getDisplayMetrics().density);
        logoParams.y = metrics.heightPixels / 4;

        floatingLogoView = new FloatingLogoView(this);
        floatingLogoView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = logoParams.x;
                        initialY = logoParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoving = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                            isMoving = true;
                        }
                        logoParams.x = Math.max(0, Math.min(metrics.widthPixels - logoSize, initialX + dx));
                        logoParams.y = Math.max(0, Math.min(metrics.heightPixels - logoSize, initialY + dy));
                        windowManager.updateViewLayout(floatingLogoView, logoParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isMoving) {
                            toggleMenu();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingLogoView, logoParams);
    }

    // 3. MAIN FLOATING ELECTRIC ORCHID GLASS MENU
    private void initFloatingMenu() {
        final float density = getResources().getDisplayMetrics().density;
        final int menuWidth = (int) Math.min(metrics.widthPixels * 0.85f, 480 * density);
        final int menuHeight = (int) Math.min(metrics.heightPixels * 0.85f, 380 * density);

        menuParams = new WindowManager.LayoutParams(
                menuWidth,
                menuHeight,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.CENTER;

        menuRootView = new LinearLayout(this);
        menuRootView.setOrientation(LinearLayout.VERTICAL);

        // Electric Orchid Glass Background
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Config.COLOR_DARK_BG);
        bg.setCornerRadius(14 * density);
        bg.setStroke((int) (1.5f * density), Config.COLOR_BORDER);
        menuRootView.setBackground(bg);
        menuRootView.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));

        // HEADER (DRAGGABLE BAR)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, (int) (8 * density));

        TextView titleText = new TextView(this);
        titleText.setText("NINMODS");
        titleText.setTextColor(Config.COLOR_TEXT_WHITE);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setTextSize(16);
        header.addView(titleText);

        statusBadgeText = new TextView(this);
        statusBadgeText.setText(" [WAITING]");
        statusBadgeText.setTextColor(Config.COLOR_OFFLINE_RED);
        statusBadgeText.setTextSize(12);
        header.addView(statusBadgeText);

        View spacer = new View(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1.0f);
        header.addView(spacer, spacerParams);

        // Close Button ('X')
        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Config.COLOR_TEXT_WHITE);
        closeBtn.setTextSize(18);
        closeBtn.setPadding((int) (8 * density), 0, (int) (4 * density), 0);
        closeBtn.setOnClickListener(v -> closeMenu());
        header.addView(closeBtn);

        // Draggable Header Handler
        header.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = menuParams.x;
                        initialY = menuParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        menuParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        menuParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(menuRootView, menuParams);
                        return true;
                }
                return false;
            }
        });

        menuRootView.addView(header);

        // TAB BAR (VISUALS | COMBAT | SETTINGS)
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(0, 0, 0, (int) (10 * density));

        Button tabVisuals = createTabButton("Visuals", 0);
        Button tabCombat  = createTabButton("Combat", 1);
        Button tabSettings = createTabButton("Settings", 2);

        tabBar.addView(tabVisuals, new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f));
        tabBar.addView(tabCombat, new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f));
        tabBar.addView(tabSettings, new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f));
        menuRootView.addView(tabBar);

        // SCROLLABLE TAB CONTENT CONTAINER
        ScrollView scrollView = new ScrollView(this);
        tabContentContainer = new LinearLayout(this);
        tabContentContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(tabContentContainer);
        menuRootView.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        updateTabContent();
    }

    private Button createTabButton(String text, int tabIndex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        btn.setTextColor(activeTab == tabIndex ? Config.COLOR_TEXT_WHITE : Config.COLOR_TEXT_MUTED);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(activeTab == tabIndex ? Config.COLOR_PRIMARY : Config.COLOR_CARD_BG);
        bg.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        btn.setBackground(bg);

        btn.setOnClickListener(v -> {
            activeTab = tabIndex;
            initFloatingMenuContentOnly();
        });
        return btn;
    }

    private void initFloatingMenuContentOnly() {
        if (menuRootView == null) return;
        menuRootView.removeAllViews();

        final float density = getResources().getDisplayMetrics().density;

        // Recreate Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, (int) (8 * density));

        TextView titleText = new TextView(this);
        titleText.setText("NINMODS");
        titleText.setTextColor(Config.COLOR_TEXT_WHITE);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setTextSize(16);
        header.addView(titleText);

        statusBadgeText = new TextView(this);
        statusBadgeText.setText(NativeBridge.isAttached() ? " [ACTIVE]" : " [WAITING]");
        statusBadgeText.setTextColor(NativeBridge.isAttached() ? Config.COLOR_ONLINE_GREEN : Config.COLOR_OFFLINE_RED);
        statusBadgeText.setTextSize(12);
        header.addView(statusBadgeText);

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Config.COLOR_TEXT_WHITE);
        closeBtn.setTextSize(18);
        closeBtn.setPadding((int) (8 * density), 0, (int) (4 * density), 0);
        closeBtn.setOnClickListener(v -> closeMenu());
        header.addView(closeBtn);
        menuRootView.addView(header);

        // Recreate Tab Bar
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(0, 0, 0, (int) (10 * density));
        tabBar.addView(createTabButton("Visuals", 0), new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f));
        tabBar.addView(createTabButton("Combat", 1), new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f));
        tabBar.addView(createTabButton("Settings", 2), new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f));
        menuRootView.addView(tabBar);

        // Recreate Content
        ScrollView scrollView = new ScrollView(this);
        tabContentContainer = new LinearLayout(this);
        tabContentContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(tabContentContainer);
        menuRootView.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        updateTabContent();
    }

    private void updateTabContent() {
        tabContentContainer.removeAllViews();
        final float density = getResources().getDisplayMetrics().density;

        if (activeTab == 0) {
            // TAB 0: VISUALS / ESP
            addCheckbox("ESP Box", Config.espBox, (btn, checked) -> Config.espBox = checked);
            addCheckbox("ESP Health Bar", Config.espHealth, (btn, checked) -> Config.espHealth = checked);
            addCheckbox("ESP Laser Line", Config.espLine, (btn, checked) -> Config.espLine = checked);
            addCheckbox("ESP Distance", Config.espDistance, (btn, checked) -> Config.espDistance = checked);
            addCheckbox("ESP Player Name", Config.espName, (btn, checked) -> Config.espName = checked);

        } else if (activeTab == 1) {
            // TAB 1: COMBAT & AIMBOT
            addCheckbox("Enable Aim Assist", Config.aimEnable, (btn, checked) -> {
                Config.aimEnable = checked;
                NativeBridge.setAimEnabled(checked);
            });

            addCheckbox("Scope Aim (Instant Head)", Config.scopeAim, (btn, checked) -> {
                Config.scopeAim = checked;
                NativeBridge.setAimScopeLock(checked);
            });

            addCheckbox("Sniper Headshot Lock (AWM/Kar/M82B)", Config.sniperAim, (btn, checked) -> {
                Config.sniperAim = checked;
                NativeBridge.setAimSniperLock(checked);
            });

            addCheckbox("Draw FOV Circle", Config.showFov, (btn, checked) -> Config.showFov = checked);

            // Headshot Rate Percentage Slider (0% - 100%)
            addSlider("Headshot Rate", (int) Config.headshotRate, 0, 100, "%d%%", value -> {
                Config.headshotRate = value;
                NativeBridge.setAimHeadshotRate(value);
            });

            // FOV Radius Slider (30px - 650px)
            addSlider("FOV Circle Radius", (int) Config.fovRadius, 30, 650, "%d px", value -> {
                Config.fovRadius = value;
                NativeBridge.setAimFov(value);
            });

        } else if (activeTab == 2) {
            // TAB 2: SETTINGS & TARGET STATS
            TextView statsTitle = new TextView(this);
            statsTitle.setText("ENGINE STATUS");
            statsTitle.setTextColor(Config.COLOR_PRIMARY_LIGHT);
            statsTitle.setTypeface(null, Typeface.BOLD);
            statsTitle.setPadding(0, (int) (6 * density), 0, (int) (4 * density));
            tabContentContainer.addView(statsTitle);

            TextView pidText = new TextView(this);
            int pid = NativeBridge.getTargetPid();
            pidText.setText("Game PID: " + (pid > 0 ? pid : "Waiting for game..."));
            pidText.setTextColor(Config.COLOR_TEXT_MUTED);
            tabContentContainer.addView(pidText);

            TextView attachText = new TextView(this);
            attachText.setText("Session: " + (NativeBridge.isAttached() ? "Connected (libil2cpp.so bound)" : "Standby"));
            attachText.setTextColor(Config.COLOR_TEXT_MUTED);
            tabContentContainer.addView(attachText);

            Button exitBtn = new Button(this);
            exitBtn.setText("Exit Mod Overlay");
            exitBtn.setTextColor(0xFFFF5A5A);
            exitBtn.setOnClickListener(v -> stopSelf());
            tabContentContainer.addView(exitBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density)));
        }
    }

    private void addCheckbox(String label, boolean initial, CheckBox.OnCheckedChangeListener listener) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(Config.COLOR_TEXT_WHITE);
        cb.setChecked(initial);
        cb.setOnCheckedChangeListener(listener);
        tabContentContainer.addView(cb);
    }

    private void addSlider(String label, int initial, int min, int max, String format, SliderCallback callback) {
        TextView labelView = new TextView(this);
        labelView.setText(String.format(label + ": " + format, initial));
        labelView.setTextColor(Config.COLOR_TEXT_WHITE);
        labelView.setPadding(0, 8, 0, 2);
        tabContentContainer.addView(labelView);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(initial - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = min + progress;
                labelView.setText(String.format(label + ": " + format, val));
                callback.onChanged(val);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        tabContentContainer.addView(seekBar);
    }

    private interface SliderCallback {
        void onChanged(int value);
    }

    private void toggleMenu() {
        if (isMenuOpen) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    private void openMenu() {
        if (!isMenuOpen) {
            windowManager.addView(menuRootView, menuParams);
            isMenuOpen = true;
        }
    }

    private void closeMenu() {
        if (isMenuOpen) {
            windowManager.removeView(menuRootView);
            isMenuOpen = false;
        }
    }

    private void startStatusPolling() {
        statusHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (statusBadgeText != null) {
                    boolean attached = NativeBridge.isAttached();
                    statusBadgeText.setText(attached ? " [ACTIVE]" : " [WAITING]");
                    statusBadgeText.setTextColor(attached ? Config.COLOR_ONLINE_GREEN : Config.COLOR_OFFLINE_RED);
                }
                statusHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        statusHandler.removeCallbacksAndMessages(null);
        if (isMenuOpen && menuRootView != null) {
            windowManager.removeView(menuRootView);
        }
        if (floatingLogoView != null) {
            windowManager.removeView(floatingLogoView);
        }
        if (espOverlayView != null) {
            windowManager.removeView(espOverlayView);
        }
        NativeBridge.stopEngine();
    }

    // 4. FLOATING LOGO VIEW ('N') — CLEAN & SIMPLE (NO 4-SIDED ROTATING ARCS)
    private static class FloatingLogoView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint statusDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public FloatingLogoView(Context context) {
            super(context);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(Config.COLOR_DARK_BG);

            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setColor(Config.COLOR_PRIMARY);
            ringPaint.setStrokeWidth(4.0f);

            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);

            shadowPaint.setColor(0xAA000000);
            shadowPaint.setTypeface(Typeface.DEFAULT_BOLD);
            shadowPaint.setTextAlign(Paint.Align.CENTER);

            statusDotPaint.setStyle(Paint.Style.FILL);
            statusDotPaint.setColor(Config.COLOR_ONLINE_GREEN);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float r = Math.min(cx, cy) - 4.0f;

            // 1. Base Dark Disc
            canvas.drawCircle(cx, cy, r, bgPaint);

            // 2. Crisp Solid Outline Ring (Simple, No 4-sided animations)
            canvas.drawCircle(cx, cy, r, ringPaint);

            // 3. Center "N" Letter with drop shadow
            float fontSize = r * 1.15f;
            textPaint.setTextSize(fontSize);
            shadowPaint.setTextSize(fontSize);

            float yPos = cy - ((textPaint.descent() + textPaint.ascent()) / 2);
            canvas.drawText("N", cx + 2.0f, yPos + 2.0f, shadowPaint);
            canvas.drawText("N", cx, yPos, textPaint);

            // 4. Status Dot
            if (NativeBridge.isAttached()) {
                canvas.drawCircle(cx + r * 0.65f, cy - r * 0.65f, 6.0f, statusDotPaint);
            }
        }
    }
}
