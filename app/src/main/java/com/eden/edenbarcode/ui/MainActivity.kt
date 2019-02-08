package com.eden.edenbarcode.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.PermissionChecker
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import com.eden.edenbarcode.R
import kotlinx.android.synthetic.main.main_activity.*

private const val CHECK_PERMISSIONS_REQUEST_CODE = 100

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setIcon(R.mipmap.ic_launcher)
            title = null
        }
        savedInstanceState ?: let {
            if (!checkPermissions()) return
            showProducts()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.scan_menu, menu)
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CHECK_PERMISSIONS_REQUEST_CODE) {
            grantResults.forEach { grantResult ->
                if (grantResult != PermissionChecker.PERMISSION_GRANTED) return
            }
            showProducts()
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

    private fun showProducts() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.container,
                MainFragment.newInstance()
            )
            .commitNow()
    }
}
