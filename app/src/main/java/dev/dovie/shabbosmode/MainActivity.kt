package dev.dovie.shabbosmode

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) postPreviewNotification()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                PREVIEW_CHANNEL,
                "Preview",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        setContent {
            MaterialTheme {
                ShabbosModeHome(onPreviewNotification = ::requestPreviewNotification)
            }
        }
    }

    private fun requestPreviewNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            postPreviewNotification()
        }
    }

    private fun postPreviewNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(this, PREVIEW_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Shabbos Mode preview")
            .setContentText("Notifications work on this device.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(PREVIEW_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val PREVIEW_CHANNEL = "preview"
        const val PREVIEW_NOTIFICATION_ID = 1
    }
}

@Composable
private fun ShabbosModeHome(onPreviewNotification: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(PaddingValues(24.dp)),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Shabbos Mode",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "A home for Shabbos preparation, alarms, and useful information.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(32.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Device preview", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Check that this native app can notify your phone.")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onPreviewNotification) {
                            Text("Send test notification")
                        }
                    }
                }
            }
        }
    }
}
