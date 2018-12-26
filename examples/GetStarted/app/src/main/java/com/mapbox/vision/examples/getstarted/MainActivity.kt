package com.mapbox.vision.examples.getstarted

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import com.mapbox.vision.VisionManager
import com.mapbox.vision.core.utils.SystemInfoUtils
import com.mapbox.vision.core.utils.snapdragon.SupportedSnapdragonBoards
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import com.mapbox.vision.view.VisualizationMode
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check does Vision SDK support the device
        if (!SupportedSnapdragonBoards.isBoardSupported(SystemInfoUtils.getSnpeSupportedBoard())) {
            val text =
                Html.fromHtml(
                    "The device is not supported, you need <b>Snapdragon-powered</b> device with " +
                            "<b>OpenCL</b> support, more details at <b>https://www.mapbox.com/android-docs/vision/overview/</b>"
                )
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        VisionManager.create()
        if (!allPermissionsGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(getRequiredPermissions(), PERMISSIONS_REQUEST_CODE)
            return
        } else {
            onPermissionsGranted()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear -> {
                vision_view.visualizationMode = VisualizationMode.CLEAR
                true
            }
            R.id.detection -> {
                vision_view.visualizationMode = VisualizationMode.DETECTION
                true
            }
            R.id.segmentation -> {
                vision_view.visualizationMode = VisualizationMode.SEGMENTATION
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (allPermissionsGranted() && requestCode == PERMISSIONS_REQUEST_CODE) {
            onPermissionsGranted()
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            VisionManager.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (allPermissionsGranted()) {
            VisionManager.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VisionManager.destroy()
    }

    private fun onPermissionsGranted() {
        VisionManager.setModelPerformanceConfig(
            ModelPerformanceConfig.Merged(
                performance = ModelPerformance.On(ModelPerformanceMode.FIXED, ModelPerformanceRate.HIGH)
            )
        )
    }

    private fun allPermissionsGranted(): Boolean =
        getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }


    private fun getRequiredPermissions(): Array<String> {
        return try {
            val info = packageManager?.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val ps = info!!.requestedPermissions
            if (ps != null && ps.isNotEmpty()) {
                ps
            } else {
                emptyArray()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            emptyArray()
        }
    }
}
