package com.mapbox.vision.examples;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mapbox.vision.mobile.core.utils.SystemInfoUtils;
import com.mapbox.vision.mobile.core.utils.snapdragon.SupportedSnapdragonBoards;
import com.mapbox.vision.utils.VisionLogger;

public abstract class BaseActivity extends AppCompatActivity {

    private static final String PERMISSION_FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE";
    private static final int PERMISSIONS_REQUEST_CODE = 123;

    protected abstract void onPermissionsGranted();

    protected abstract void initViews();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String board = SystemInfoUtils.INSTANCE.getSnpeSupportedBoard();
        if (!SupportedSnapdragonBoards.isBoardSupported(board)) {
            Spanned text =
                    Html.fromHtml("The device is not supported, you need <b>Snapdragon-powered</b> device with <b>OpenCL</b> support, more details at <b>https://www.mapbox.com/android-docs/vision/overview/</b>");
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            VisionLogger.Companion.e(
                    "NotSupportedBoard",
                    "Current board is " + board + ", Supported Boards: [ " + getSupportedBoardNames() + " ]; System Info: [ " + SystemInfoUtils.INSTANCE.obtainSystemInfo() + " ]"
            );
            finish();
            return;
        }

        initViews();

        if (!allPermissionsGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
        } else {
            onPermissionsGranted();
        }
    }

    private String getSupportedBoardNames() {
        StringBuilder sb = new StringBuilder();
        for (SupportedSnapdragonBoards board : SupportedSnapdragonBoards.values()) {
            sb.append(board.name());
        }

        return sb.toString();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (allPermissionsGranted() && requestCode == PERMISSIONS_REQUEST_CODE) {
            onPermissionsGranted();
        }
    }
}
