package com.driverdrowsiness.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted.value = granted }
    private val permissionGranted = mutableStateOf(false)
    private lateinit var monitor: CameraMonitor
    private lateinit var backend: MetricsSocket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        monitor = CameraMonitor(this) { metrics ->
            liveMetrics.postValue(metrics)
            backend.send(metrics)
        }
        backend = MetricsSocket()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF38D9A9),
                background = Color(0xFF0B1220),
                surface = Color(0xFF151F30)
            )) {
                DriverApp(
                    metrics = liveMetrics.value ?: DriverMetrics(),
                    cameraGranted = permissionGranted.value,
                    onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onStartCamera = { monitor.start() },
                    onStopCamera = { monitor.stop() },
                    onConnect = { url -> backend.connect(url) },
                    onDisconnect = { backend.disconnect() },
                    backendState = backend.state
                )
            }
        }
    }

    override fun onDestroy() {
        monitor.stop()
        backend.disconnect()
        super.onDestroy()
    }

    companion object {
        val liveMetrics = mutableStateOf(DriverMetrics())
    }
}

@Composable
fun DriverApp(
    metrics: DriverMetrics,
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    onStartCamera: () -> Unit,
    onStopCamera: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    backendState: String
) {
    var tab by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("ws://192.168.1.10:8000/ws/metrics") }
    Scaffold(
        containerColor = Color(0xFF0B1220),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF111B2B)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Overview") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.CameraAlt, null) }, label = { Text("Camera") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Cloud, null) }, label = { Text("Connect") })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("DRIVER GUARD", color = Color(0xFF38D9A9), fontSize = 12.sp)
                Text("Drowsiness Monitor", color = Color.White, fontSize = 27.sp)
                Text("Live driver safety analytics", color = Color.LightGray)
            }
            if (tab == 0) {
                item {
                    MetricCard("Drowsiness risk", "${metrics.riskScore}%",
                        metrics.riskLabel, Color(0xFF38D9A9))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("EAR", metrics.ear?.let { "%.3f".format(it) } ?: "--",
                            "Eye aspect ratio", Color(0xFF67B7FF), Modifier.weight(1f))
                        MetricCard("MAR", metrics.mar?.let { "%.3f".format(it) } ?: "--",
                            "Mouth ratio", Color(0xFFFFC66D), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("PERCLOS", "${metrics.perclosPercent}%",
                            "Rolling eye-closure", Color(0xFFFF8B8B), Modifier.weight(1f))
                        MetricCard("Head pose", "${metrics.headPitch}°",
                            "Pitch deviation", Color(0xFFB69CFF), Modifier.weight(1f))
                    }
                }
                item {
                    MetricCard("Blink rate", "${metrics.blinksPerMinute}/min",
                        "Detected blinks per minute", Color(0xFF67B7FF))
                }
                item {
                    Text("Current status: ${metrics.status}", color = Color.White)
                    Text("Yawns: ${metrics.yawnCount}  •  Alerts: ${metrics.alertCount}",
                        color = Color.LightGray)
                    Text("Source: ${metrics.source}", color = Color.Gray, fontSize = 12.sp)
                }
                item {
                    Text("Safety note: this app is a prototype aid, not a certified driver-safety device. Never interact with the phone while driving.",
                        color = Color(0xFFFFC66D), fontSize = 12.sp)
                }
            } else if (tab == 1) {
                item { Text("On-device camera monitoring", color = Color.White, fontSize = 20.sp) }
                item {
                    Text("Uses the front camera and ML Kit face contours/classification. Keep the phone securely mounted and do not operate it while driving.",
                        color = Color.LightGray)
                }
                item {
                    Button(
                        onClick = {
                            if (!cameraGranted) onRequestCamera()
                            else { running = !running; if (running) onStartCamera() else onStopCamera() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (running) "Stop camera analysis" else "Start camera analysis") }
                }
                item { Text("Camera permission: ${if (cameraGranted) "Granted" else "Not granted"}", color = Color.White) }
                item { Text("Detection status: ${metrics.status}", color = Color.White) }
            } else {
                item { Text("Connect existing Python AI system", color = Color.White, fontSize = 20.sp) }
                item { Text("Enter the WebSocket endpoint exposed by your Python service. The app sends local camera metrics to this endpoint and can also receive metrics from the Python system.", color = Color.LightGray) }
                item {
                    OutlinedTextField(value = url, onValueChange = { url = it },
                        label = { Text("WebSocket URL") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onConnect(url) }, modifier = Modifier.weight(1f)) { Text("Connect") }
                        OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text("Disconnect") }
                    }
                }
                item { Text("Connection: $backendState", color = Color(0xFF38D9A9)) }
                item { Text("Expected JSON format is documented in README.md.", color = Color.LightGray) }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String, value: String, detail: String, accent: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF151F30)),
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color.LightGray, fontSize = 12.sp)
            Text(value, color = accent, fontSize = 27.sp)
            Text(detail, color = Color.Gray, fontSize = 11.sp)
        }
    }
}
