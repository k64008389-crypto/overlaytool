package com.example.overlaytool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OverlayView extends SurfaceView implements SurfaceHolder.Callback {

    private final List<Detection> detections = new CopyOnWriteArrayList<>();
    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Paint centerPaint = new Paint();
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

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);

        textPaint.setColor(Color.YELLOW);
        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(true);

        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(2f);

        centerPaint.setColor(Color.GREEN);
        centerPaint.setStyle(Paint.Style.STROKE);
        centerPaint.setStrokeWidth(6f);
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
                        canvas.drawColor(Color.TRANSPARENT,
                                android.graphics.PorterDuff.Mode.CLEAR);

                        int cx = canvas.getWidth() / 2;
                        int cy = canvas.getHeight() / 2;

                        // Триггер-подсказка в центре
                        canvas.drawCircle(cx, cy, 40, centerPaint);

                        for (Detection d : detections) {
                            canvas.drawRect(d.bounds, boxPaint);
                            canvas.drawText(d.label,
                                    d.bounds.left,
                                    d.bounds.top - 8, textPaint);

                            // Линия наведения от центра к цели
                            canvas.drawLine(cx, cy,
                                    d.bounds.centerX(), d.bounds.centerY(),
                                    linePaint);
                        }
                    }
                } finally {
                    if (canvas != null) holder.unlockCanvasAndPost(canvas);
                }
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }
        }
    }
}
