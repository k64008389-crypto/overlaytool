package com.example.overlaytool;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

public class DetectionEngine {

    // === НАСТРОЙКИ ПОД COUNTER FLAME ===
    // Цвет врага (примерный красный контур/ник). Поменяй если нужно.
    private static final int TARGET_COLOR = 0xFFFF0000;
    private static final int COLOR_TOLERANCE = 60;
    private static final int MIN_PIXEL_COUNT = 50;
    // Размер шага сканирования (больше = быстрее, но менее точно)
    private static final int STEP = 6;
    // ====================================

    public static void process(Bitmap frame, OverlayView overlay) {
        int w = frame.getWidth();
        int h = frame.getHeight();

        int[] pixels = new int[w * h];
        frame.getPixels(pixels, 0, w, 0, 0, w, h);

        List<Rect> boxes = new ArrayList<>();
        int gridW = w / STEP;
        int gridH = h / STEP;
        boolean[] visited = new boolean[gridW * gridH];

        for (int y = 0; y < h; y += STEP) {
            for (int x = 0; x < w; x += STEP) {
                int idx = (y / STEP) * gridW + (x / STEP);
                if (visited[idx]) continue;

                int px = pixels[y * w + x];
                if (!matches(px)) continue;

                Rect box = floodRegion(pixels, w, h, x, y, gridW, visited);
                if (box != null) boxes.add(box);
            }
        }

        List<OverlayView.Detection> detections = new ArrayList<>();
        for (Rect b : boxes) {
            detections.add(new OverlayView.Detection(b, "target"));
        }

        overlay.updateDetections(detections);
    }

    private static boolean matches(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int tr = (TARGET_COLOR >> 16) & 0xFF;
        int tg = (TARGET_COLOR >> 8) & 0xFF;
        int tb = TARGET_COLOR & 0xFF;

        return Math.abs(r - tr) < COLOR_TOLERANCE
                && Math.abs(g - tg) < COLOR_TOLERANCE
                && Math.abs(b - tb) < COLOR_TOLERANCE;
    }

    private static Rect floodRegion(int[] pixels, int w, int h,
                                     int startX, int startY, int gridW,
                                     boolean[] visited) {
        int left = startX, right = startX, top = startY, bottom = startY;
        int count = 0;
        int searchRadius = STEP * 20;

        for (int y = Math.max(0, startY - searchRadius);
             y < Math.min(h, startY + searchRadius); y += STEP) {
            for (int x = Math.max(0, startX - searchRadius);
                 x < Math.min(w, startX + searchRadius); x += STEP) {

                int idx = (y / STEP) * gridW + (x / STEP);
                if (visited[idx]) continue;

                if (matches(pixels[y * w + x])) {
                    visited[idx] = true;
                    count++;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        if (count < MIN_PIXEL_COUNT) return null;
        return new Rect(left, top, right, bottom);
    }
}
