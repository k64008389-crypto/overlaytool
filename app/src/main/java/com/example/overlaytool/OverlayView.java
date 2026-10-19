package com.example.overlaytool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OverlayView extends SurfaceView implements SurfaceHolder.Callback {

    private final List<Detection> detections = new CopyOnWriteArrayList<>();
    
    // Кисти для рисования
    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Paint centerPaint = new Paint();
    private final Paint hudBgPaint = new Paint();
    private final Paint hudTextPaint = new Paint();
    private final Paint hudLinePaint = new Paint();
    
    private RenderThread renderThread;

    public static class Detection {
        public final Rect bounds;
        public final String label;

        public Detection(Rect bounds, String label) {
            this.bounds = bounds;
            this.label = label;
        }
    }

    public OverlayView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setZOrderOnTop(true);
        getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);

        // Настройка рамок вокруг врагов
        boxPaint.setColor(Color.parseColor("#FF3333")); // Ярко-красный
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3f);
        boxPaint.setAntiAlias(true);

        // Текст над врагами
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);

        // Линии наведения (Snaplines)
        linePaint.setColor(Color.parseColor("#00FFCC")); // Неоновый бирюзовый
        linePaint.setStrokeWidth(2f);
        linePaint.setAntiAlias(true);

        // Центральный круг (Триггер-подсказка)
        centerPaint.setColor(Color.parseColor("#00FF00")); // Зеленый
        centerPaint.setStyle(Paint.Style.STROKE);
        centerPaint.setStrokeWidth(2f);
        centerPaint.setAntiAlias(true);

        // Фон для HUD панели (полупрозрачный черный)
        hudBgPaint.setColor(Color.parseColor("#AA000000"));
        hudBgPaint.setStyle(Paint.Style.FILL);
        
        // Текст в HUD
        hudTextPaint.setColor(Color.parseColor("#00FFCC"));
        hudTextPaint.setTextSize(40f);
        hudTextPaint.setAntiAlias(true);
        hudTextPaint.setFakeBoldText(true);
        hudTextPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        // Линия-разделитель в HUD
        hudLinePaint.setColor(Color.parseColor("#00FFCC"));
        hudLinePaint.setStrokeWidth(3f);
    }

    public void updateDetections(List<Detection> newDetections) {
        detections.clear();
        detections.addAll(newDetections);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        renderThread = new RenderThread(holder);
        renderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (renderThread != null) renderThread.stopRender();
    }

    private class RenderThread extends Thread {
        private final SurfaceHolder holder;
        private volatile boolean active = true;

        RenderThread(SurfaceHolder holder) { this.holder = holder; }

        void stopRender() { active = false; }

        @Override
        public void run() {
            while (active) {
                Canvas canvas = null;
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        // Очищаем экран (прозрачный)
                        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);

                        int cx = canvas.getWidth() / 2;
                        int cy = canvas.getHeight() / 2;

                        // === РИСУЕМ ТРИГГЕР-ПОДСКАЗКУ ===
                        // Маленький круг в центре
                        canvas.drawCircle(cx, cy, 15, centerPaint);
                        // Перекрестие
                        canvas.drawLine(cx - 25, cy, cx - 5, cy, centerPaint);
                        canvas.drawLine(cx + 5, cy, cx + 25, cy, centerPaint);
                        canvas.drawLine(cx, cy - 25, cx, cy - 5, centerPaint);
                        canvas.drawLine(cx, cy + 5, cx, cy + 25, centerPaint);

                        // === РИСУЕМ ЦЕЛИ ===
                        for (Detection d : detections) {
                            // Рамка вокруг цели
                            canvas.drawRect(d.bounds, boxPaint);
                            
                            // Текст
                            canvas.drawText(d.label, d.bounds.left, d.bounds.top - 10, textPaint);
                            
                            // Линия наведения от центра экрана
                            canvas.drawLine(cx, cy, d.bounds.centerX(), d.bounds.centerY(), linePaint);
                        }

                        // === РИСУЕМ КРАСИВЫЙ HUD ===
                        drawHud(canvas, detections.size());
                    }
                } finally {
                    if (canvas != null) holder.unlockCanvasAndPost(canvas);
                }
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void drawHud(Canvas canvas, int targetCount) {
        // Координаты панели (левый верхний угол)
        int hudX = 40;
        int hudY = 100;
        int hudWidth = 300;
        int hudHeight = 140;
        int cornerRadius = 20;

        // Рисуем фон панели (закругленный прямоугольник)
        RectF rectF = new RectF(hudX, hudY, hudX + hudWidth, hudY + hudHeight);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, hudBgPaint);

        // Декоративная полоска слева
        canvas.drawLine(hudX, hudY + 20, hudX, hudY + hudHeight - 20, hudLinePaint);

        // Пишем текст
        String title = "OVERLAY";
        String status = "STATUS: ACTIVE";
        String targets = "TARGETS: " + targetCount;

        canvas.drawText(title, hudX + 30, hudY + 50, hudTextPaint);
        canvas.drawText(status, hudX + 30, hudY + 90, hudTextPaint);
        
        // Если есть цели, красим текст в красный
        if (targetCount > 0) {
            hudTextPaint.setColor(Color.parseColor("#FF3333"));
            canvas.drawText(targets, hudX + 30, hudY + 130, hudTextPaint);
            hudTextPaint.setColor(Color.parseColor("#00FFCC")); // Возвращаем цвет
        } else {
            canvas.drawText(targets, hudX + 30, hudY + 130, hudTextPaint);
        }
    }
}
