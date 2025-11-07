package eu.zlidak.bluetoothrestart

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class BluetoothToggleService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var togglingJob: Job? = null

    companion object {
        const val TAG = "ShizukuReflectionTest"
        const val NOTIFICATION_CHANNEL_ID = "BluetoothToggleChannel"
        const val NOTIFICATION_ID = 1
        const val TOGGLE_INTERVAL_MS = 20 * 60 * 1000L // 20 minutes
        const val WAIT_AFTER_DISABLE_MS = 5 * 1000L // 5 sec
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate.")
        startToggling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { createNotificationChannel() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        return START_STICKY
    }

    private fun startToggling() {
        if (togglingJob?.isActive == true) return

        togglingJob = serviceScope.launch {
            Log.d(TAG, "Starting the REFLECTION-BASED toggle loop.")
            while (isActive) {
                delay(TOGGLE_INTERVAL_MS)
                Log.d(TAG, "------------------ CIKLUS INDUL ------------------")
                toggleBluetoothWithShizuku()
            }
        }
    }

    private suspend fun toggleBluetoothWithShizuku() {
        if (!isShizukuAvailableAndGranted()) {
            Log.e(TAG, "Shizuku nem elérhető vagy nincs engedélyezve.")
            return
        }

        try {
            Log.d(TAG, "Kikapcsolás: 'svc bluetooth disable'")
            val disableResult = runShellCommandWithShizuku(arrayOf("svc", "bluetooth", "disable"))
            Log.d(TAG, "Kikapcsolás Eredmény - STDOUT: ${disableResult.first}, STDERR: ${disableResult.second}")

            if (disableResult.second.isBlank()) {
                delay(WAIT_AFTER_DISABLE_MS)

                Log.d(TAG, "Bekapcsolás: 'svc bluetooth enable'")
                val enableResult = runShellCommandWithShizuku(arrayOf("svc", "bluetooth", "enable"))
                Log.d(TAG, "Bekapcsolás Eredmény - STDOUT: ${enableResult.first}, STDERR: ${enableResult.second}")
            } else {
                Log.e(TAG, "A kikapcsolás sikertelen volt, a bekapcsolási lépés kihagyva.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hiba a Shizuku művelet során", e)
        }
    }

    private suspend fun runShellCommandWithShizuku(command: Array<String>): Pair<String, String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true

            val remoteProcess = method.invoke(null, command, null, null) as ShizukuRemoteProcess

            val outputReader = async { remoteProcess.inputStream.bufferedReader().readText() }
            val errorReader = async { remoteProcess.errorStream.bufferedReader().readText() }

            val output = outputReader.await()
            val error = errorReader.await()

            remoteProcess.waitFor()
            remoteProcess.destroy()
            Pair(output.trim(), error.trim())
        } catch (exception: Exception) {
            Log.e(TAG, "Hiba a Reflection hívás közben", exception)
            Pair("", "Exception: ${exception.message}")
        }
    }

    private fun isShizukuAvailableAndGranted(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Szolgáltatás állapota", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Bluetooth Fix (Reflection)")
            .setContentText("A szolgáltatás a háttérben fut.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy.")
        togglingJob?.cancel()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}