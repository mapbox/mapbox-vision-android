package com.mapbox.vision.examples;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import com.mapbox.vision.mobile.core.utils.SystemInfoUtils;
import com.mapbox.vision.utils.VisionLogger;

public abstract class BaseActivity extends AppCompatActivity {

    private static final String PERMISSION_FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE";
    private static final int PERMISSIONS_REQUEST_CODE = 123;

    protected abstract void onPermissionsGranted();

    protected abstract void initViews();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SystemInfoUtils.INSTANCE.isVisionSupported()) {
            final TextView textView = new TextView(this);
            final int padding = (int) dpToPx(20f);
            textView.setPadding(padding, padding, padding, padding);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            textView.setClickable(true);
            textView.setText(
                    HtmlCompat.fromHtml(
                            getString(R.string.vision_not_supported_message),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
            );
            new AlertDialog.Builder(this)
                    .setTitle(R.string.vision_not_supported_title)
                    .setView(textView)
                    .setCancelable(false)
                    .show();

            VisionLogger.Companion.e(
                    "BoardNotSupported",
                    "System Info: [" + SystemInfoUtils.INSTANCE.obtainSystemInfo() + "]"
            );
        }

        initViews();

        setTitle(getString(R.string.app_name) + " " + this.getClass().getSimpleName());

        if (!allPermissionsGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
        } else {
            onPermissionsGranted();
        }
    }

    protected boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // PERMISSION_FOREGROUND_SERVICE was added for targetSdkVersion >= 28, it is normal and always granted, but should be added to the Manifest file
                // on devices with Android < P(9) checkSelfPermission(PERMISSION_FOREGROUND_SERVICE) can return PERMISSION_DENIED, but in fact it is GRANTED, so skip it
                // https://developer.android.com/guide/components/services#Foreground
                if (permission.equals(PERMISSION_FOREGROUND_SERVICE)) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private String[] getRequiredPermissions() {
        String[] permissions;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] requestedPermissions = info.requestedPermissions;
            if (requestedPermissions != null && requestedPermissions.length > 0) {
                permissions = requestedPermissions;
            } else {
                permissions = new String[]{};
            }
        } catch (PackageManager.NameNotFoundException e) {
            permissions = new String[]{};
        }

        return permissions;
    }

    private float dpToPx(final float dp) {
        return dp * getApplicationContext().getResources().getDisplayMetrics().density;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (allPermissionsGranted() && requestCode == PERMISSIONS_REQUEST_CODE) {
            onPermissionsGranted();
        }
    }
}
