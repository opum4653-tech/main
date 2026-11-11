package com.ninmods.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class ESPOverlayView extends View {

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxVisiblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint healthBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint healthFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fovPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fovAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Flat buffer to receive data from C++ JNI without garbage collection allocation
    // Maximum 64 players * 12 floats + 1 header = 769 floats
    private final float[] playerBuffer = new float[1024];
    private final RectF boxRect = new RectF();

    public ESPOverlayView(Context context) {
        super(context);
        initPaints();
    }

    private void initPaints() {
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(0xFF00E5FF); // Cyan
        boxPaint.setStrokeWidth(2.0f);

        boxVisiblePaint.setStyle(Paint.Style.STROKE);
        boxVisiblePaint.setColor(0xFF4CFF8C); // Green
        boxVisiblePaint.setStrokeWidth(2.0f);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(0xFFE10098); // Orchid Pink
        linePaint.setStrokeWidth(1.5f);

        healthBgPaint.setStyle(Paint.Style.FILL);
        healthBgPaint.setColor(0x80000000);

        healthFillPaint.setStyle(Paint.Style.FILL);
        healthFillPaint.setColor(0xFF00E676);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(26.0f);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(3.0f, 1.0f, 1.0f, 0xFF000000);

        fovPaint.setStyle(Paint.Style.STROKE);
        fovPaint.setColor(0x40FFFFFF);
        fovPaint.setStrokeWidth(1.2f);

        fovAccentPaint.setStyle(Paint.Style.STROKE);
        fovAccentPaint.setColor(0xCCE10098);
        fovAccentPaint.setStrokeWidth(1.8f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float screenW = getWidth();
        final float screenH = getHeight();
        if (screenW <= 0 || screenH <= 0) return;

        final float cx = screenW * 0.5f;
        final float cy = screenH * 0.5f;

        // 1. FOV CIRCLE (SCREEN CENTER)
        if (Config.aimEnable && Config.showFov) {
            float r = Config.fovRadius;
            canvas.drawCircle(cx, cy, r, fovPaint);
            canvas.drawCircle(cx, cy, r + 1.0f, fovAccentPaint);
        }

        // 2. QUERY LIVE FRAME PLAYERS FROM C++ ENGINE
        int count = NativeBridge.getPlayersData(playerBuffer);
        if (count <= 0) {
            postInvalidateOnAnimation();
            return;
        }

        final int STRIDE = 12;
        for (int i = 0; i < count; i++) {
            int base = 1 + i * STRIDE;
            float headX   = playerBuffer[base + 0];
            float headY   = playerBuffer[base + 1];
            float toeX    = playerBuffer[base + 2];
            float toeY    = playerBuffer[base + 3];
            float dist    = playerBuffer[base + 4];
            float hp      = playerBuffer[base + 5];
            float maxHp   = playerBuffer[base + 6];
            boolean isVis = (playerBuffer[base + 8] > 0.5f);
            boolean isBot = (playerBuffer[base + 9] > 0.5f);

            if (headY <= 0 || toeY <= 0) continue;

            float boxHeight = Math.abs(toeY - headY);
            if (boxHeight < 5.0f) continue;
            float boxWidth = boxHeight * 0.55f;
            float left   = headX - boxWidth * 0.5f;
            float right  = headX + boxWidth * 0.5f;
            float top    = headY;
            float bottom = toeY;

            boxRect.set(left, top, right, bottom);

            // DRAW ESP BOX
            if (Config.espBox) {
                canvas.drawRoundRect(boxRect, 4.0f, 4.0f, isVis ? boxVisiblePaint : boxPaint);
            }

            // DRAW TRACER LINE
            if (Config.espLine) {
                canvas.drawLine(cx, 0, headX, top, linePaint);
            }

            // DRAW HEALTH BAR
            if (Config.espHealth) {
                float barW = 4.0f;
                float barLeft = left - barW - 3.0f;
                float barRight = left - 3.0f;
                canvas.drawRect(barLeft, top, barRight, bottom, healthBgPaint);

                float hpPercent = Math.max(0.0f, Math.min(1.0f, hp / Math.max(1.0f, maxHp)));
                float hpTop = bottom - (boxHeight * hpPercent);

                if (hpPercent > 0.5f) {
                    healthFillPaint.setColor(0xFF00E676); // Green
                } else if (hpPercent > 0.2f) {
                    healthFillPaint.setColor(0xFFFFD600); // Yellow
                } else {
                    healthFillPaint.setColor(0xFFFF1744); // Red
                }
                canvas.drawRect(barLeft, hpTop, barRight, bottom, healthFillPaint);
            }

            // DRAW DISTANCE & BOT TAG
            if (Config.espDistance) {
                String distStr = String.format("%.0fm", dist);
                if (isBot) distStr += " [BOT]";
                float txtW = textPaint.measureText(distStr);
                canvas.drawText(distStr, headX - txtW * 0.5f, bottom + 26.0f, textPaint);
            }
        }

        // Keep loop at maximum smooth refresh rate
        postInvalidateOnAnimation();
    }
}
