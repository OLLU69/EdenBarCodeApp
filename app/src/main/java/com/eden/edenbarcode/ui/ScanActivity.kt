package com.eden.edenbarcode.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.PermissionChecker
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.widget.FrameLayout
import com.eden.edenbarcode.EdenApp
import com.eden.edenbarcode.R
import com.eden.edenbarcode.qr.IBarcodeRecognizer
import com.eden.edenbarcode.qr.ICameraWrapper
import kotlinx.android.synthetic.main.activity_scan.*
import javax.inject.Inject

private const val CHECK_PERMISSIONS_REQUEST_CODE = 100
private const val TAG: String = "ScanActivity"

class ScanActivity : AppCompatActivity() {

    @Inject
    lateinit var camera: ICameraWrapper
    @Inject
    lateinit var barcodeRecognizer: IBarcodeRecognizer
    private var previewCallback: (ByteArray, Point) -> Unit = { _, _ -> }
    private val handler = Handler()
    private val openCameraRunnable = {
        openCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        init()
    }

    override fun onResume() {
        super.onResume()
        activateCamera()
    }

    override fun onPause() {
        super.onPause()
        releaseCamera()
    }

    override fun onStop() {
        releaseCamera()
        barcodeRecognizer.release()
        super.onStop()
    }

    private fun init() {
        //проверить разрешение на камеру
        if (!checkPermissions()) {
            return
        }
        EdenApp.appComponent.inject(this)
        //инициализировать сканер
        barcodeRecognizer.prepare()
        previewCallback = { data, frameSize ->
            Log.e("", "start recognize barcode")
            camera.setPreviewCameraCallback(null)
            val barcodeRect = Rect(
                0,
                frameSize.y / 4,
                frameSize.x,
                frameSize.y * 3 / 4
            )
            barcodeRecognizer.recognizeBarCode(data, frameSize, barcodeRect) { barcode ->
                Log.e("TAG", "finish recognize")
                if (!TextUtils.isEmpty(barcode)) {
                    onBarcode(barcode)
                    handler.postDelayed(
                        {
                            camera.setPreviewCameraCallback(previewCallback)
                        }, 5000
                    )
                } else {
                    camera.setPreviewCameraCallback(previewCallback)
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val hasPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CHECK_PERMISSIONS_REQUEST_CODE)
        }
        return hasPermission
    }

    @Synchronized
    private fun activateCamera() {
        if (!checkPermissions()) return
        handler.removeCallbacks(openCameraRunnable)
        handler.postDelayed(openCameraRunnable, 50)
    }

    private fun openCamera() {
        if (!checkPermissions()) return
        try {
            val display = windowManager.defaultDisplay
            camera.setPreviewCameraCallback(previewCallback)
            val height = findViewById<FrameLayout>(android.R.id.content).height / 4
            top_scan_mask.layoutParams.height = height
            bottom_scan_mask.layoutParams.height = height
            camera.open(display, barcode_surface)
        } catch (e: Exception) {
            Log.e(TAG, "error opening camera", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CHECK_PERMISSIONS_REQUEST_CODE) {
            grantResults.forEach { grantResult ->
                if (grantResult != PermissionChecker.PERMISSION_GRANTED) return
            }
            init()
        }
    }

    private fun onBarcode(barcode: String?) {
        Log.e("onBarcode", "finish recognize $barcode")
        setResult(Activity.RESULT_OK, Intent().apply { putExtra("barcode", barcode) })
        finish()
    }

    @Synchronized
    private fun releaseCamera() {
        camera.close()
    }
}