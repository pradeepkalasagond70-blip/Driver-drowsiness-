package com.driverdrowsiness.app

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

class CameraMonitor(private val context: Context, private val onMetrics: (DriverMetrics) -> Unit) {
    private val executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private val closedSamples = ArrayDeque<Boolean>()
    private var lastBlinkAt = 0L
    private var blinkCount = 0
    private var yawnCount = 0
    private var alertCount = 0
    private var mouthOpenSince = 0L
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .enableTracking()
                .build()
        )
    }

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
            try {
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    context as LifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA, analysis
                )
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() { provider?.unbindAll() }

    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull() ?: return@addOnSuccessListener
                val now = System.currentTimeMillis()
                val left = face.leftEyeOpenProbability
                val right = face.rightEyeOpenProbability
                val eyeOpen = left != null && right != null && (left.toDouble() + right.toDouble()) / 2.0 > 0.55
                closedSamples.addLast(!eyeOpen)
                while (closedSamples.size > 300) closedSamples.removeFirst()
                if (!eyeOpen && now - lastBlinkAt > 250) {
                    if (lastBlinkAt > 0 && now - lastBlinkAt < 1200) blinkCount++
                    lastBlinkAt = now
                }
                val perclos = if (closedSamples.isEmpty()) 0
                    else (closedSamples.count { it } * 100 / closedSamples.size)
                val mouth = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points.orEmpty()
                val lower = face.getContour(FaceContour.LOWER_LIP_TOP)?.points.orEmpty()
                val mar = if (mouth.size > 1 && lower.size > 1) {
                    val gap = hypot((mouth.first().x - lower.first().x).toDouble(),
                        (mouth.first().y - lower.first().y).toDouble())
                    val width = hypot((mouth.last().x - mouth.first().x).toDouble(),
                        (mouth.last().y - mouth.first().y).toDouble()).coerceAtLeast(1.0)
                    gap / width
                } else null
                if ((mar ?: 0.0) > 0.35) {
                    if (mouthOpenSince == 0L) mouthOpenSince = now
                    if (now - mouthOpenSince > 1600) { yawnCount++; mouthOpenSince = now + 5000 }
                } else mouthOpenSince = 0L

                val pitch = face.headEulerAngleX.toInt()
                val risk = (perclos * 0.65 + (if (abs(pitch) > 18) 15 else 0) +
                    (if ((mar ?: 0.0) > 0.35) 10 else 0)).toInt().coerceIn(0, 100)
                val label = when {
                    risk >= 65 -> "High"
                    risk >= 35 -> "Moderate"
                    else -> "Low"
                }
                if (risk >= 65) alertCount++
                onMetrics(DriverMetrics(
                    ear = ((left?.toDouble() ?: 0.0) + (right?.toDouble() ?: 0.0)) / 2.0,
                    mar = mar, perclosPercent = perclos,
                    blinksPerMinute = blinkCount, headPitch = pitch,
                    riskScore = risk, riskLabel = label,
                    status = if (risk >= 65) "Drowsiness warning" else "Monitoring",
                    yawnCount = yawnCount, alertCount = alertCount,
                    source = "Phone camera / ML Kit"
                ))
            }
            .addOnCompleteListener { proxy.close() }
    }
}
