package com.example.overlaytool;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_CAPTURE = 1002;

    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        Button startBtn = new Button(this);
        startBtn.setText("Запустить");
        startBtn.setOnClickListener(v -> requestOverlay());
        setContentView(startBtn);
    }

    private void requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
        } else {
            requestCapture();
        }
    }

    private void requestCapture() {
        startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) requestCapture();
            else Toast.makeText(this, "Нужно разрешение оверлея", Toast.LENGTH_SHORT).show();
        }

        if (requestCode == REQ_CAPTURE && resultCode == Activity.RESULT_OK) {
            Intent svc = new Intent(this, ScreenCaptureService.class);
            svc.putExtra("resultCode", resultCode);
            svc.putExtra("data", data);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(svc);
            else
                startService(svc);
        }
    }
}
