package com.example.overlaytool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "overlay_channel";
    private static final int NOTIF_ID = 1;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private OverlayView overlayView;

    private int screenWidth, screenHeight, screenDensity;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        MediaProjectionManager mgr = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = mgr.getMediaProjection(resultCode, data);

        setupScreenMetrics();
        setupOverlay();
        setupImageReader();
        setupVirtualDisplay();

        running = true;
        handler.post(analysisLoop);

        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Screen Analysis",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Анализ активен")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
    }

    private void setupScreenMetrics() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);

        screenWidth = size.x;
        screenHeight = size.y;
        screenDensity = getResources().getDisplayMetrics().densityDpi;
    }

    private void setupOverlay() {
        overlayView = new OverlayView(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(overlayView, params);
    }

    private void setupImageReader() {
        imageReader = ImageReader.newInstance(
                screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                Bitmap bmp = imageToBitmap(image);
                if (bmp != null) {
                    DetectionEngine.process(bmp, overlayView);
                    bmp.recycle();
                }
            } catch (Exception ignored) {
            } finally {
                if (image != null) image.close();
            }
        }, handler);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();

        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        int bmpWidth = screenWidth + rowPadding / pixelStride;

        Bitmap bmp = Bitmap.createBitmap(bmpWidth, screenHeight, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);

        if (bmpWidth != screenWidth) {
            Bitmap cropped = Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight);
            bmp.recycle();
            return cropped;
        }
        return bmp;
    }

    private void setupVirtualDisplay() {
        Surface surface = imageReader.getSurface();

        virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, handler);
    }

    private final Runnable analysisLoop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            handler.postDelayed(this, 16); // ~60fps
        }
    };

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);

        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (projection != null) projection.stop();

        if (overlayView != null) {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            try { wm.removeView(overlayView); } catch (Exception ignored) {}
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
