package eu.zlidak.bluetoothrestart

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "Alap engedélyek megadva!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Figyelem: Nem minden alap engedély lett megadva!", Toast.LENGTH_LONG).show()
            }
        }

    private val shizukuPermissionRequestListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku engedély megadva!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Shizuku engedély elutasítva. Az automatikus kapcsolgatás nem fog működni.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionRequestListener)

        askForPermissions()

        val btnCheckShizuku = findViewById<Button>(R.id.btnCheckShizuku)
        val btnStart = findViewById<Button>(R.id.btnStartService)
        val btnStop = findViewById<Button>(R.id.btnStopService)

        btnCheckShizuku.setOnClickListener {
            checkShizukuPermission()
        }

        btnStart.setOnClickListener {
            startServiceWithPermissionsCheck()
        }

        btnStop.setOnClickListener {
            val serviceIntent = Intent(this, BluetoothToggleService::class.java)
            stopService(serviceIntent)
        }
    }

    private fun checkShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "A Shizuku nem fut. Kérlek, indítsd el a Shizuku appból.", Toast.LENGTH_LONG).show()
            return
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
        } else {
            Toast.makeText(this, "A Shizuku már engedélyezve van.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }

    private fun askForPermissions() {
        val permissionsToRequest = getRequiredPermissions()
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun startServiceWithPermissionsCheck() {
        val permissions = getRequiredPermissions()
        val permissionsGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (permissionsGranted || permissions.isEmpty()) {
            val serviceIntent = Intent(this, BluetoothToggleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Szolgáltatás elindítva.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Indítás előtt add meg az alap engedélyeket!", Toast.LENGTH_LONG).show()
            askForPermissions()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionRequestListener)
        super.onDestroy()
    }
}