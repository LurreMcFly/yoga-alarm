package com.lurremcfly.yogaalarm.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.lurremcfly.yogaalarm.model.PoseFrame
import java.util.concurrent.Executors

class PoseCameraController(
    context: Context,
    onResult: (PoseFrame) -> Unit,
    onError: (String) -> Unit,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val mainExecutor = ContextCompat.getMainExecutor(applicationContext)
    private val reportResult = onResult
    private val reportError = onError

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var landmarker: PoseLandmarkerClient? = null
    @Volatile private var closed = false

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(applicationContext)
        providerFuture.addListener(
            {
                if (closed) return@addListener

                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(640, 480),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                                    ),
                                )
                                .build(),
                        )
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    cameraProvider = provider
                    imageAnalysis = analysis
                    this.preview = preview
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis,
                    )
                    // Preview starts independently; loading the model must not block the UI.
                    analysisExecutor.execute {
                        if (closed) return@execute
                        try {
                            val client = PoseLandmarkerClient(
                                applicationContext,
                                onResult = { result -> mainExecutor.execute { if (!closed) reportResult(result) } },
                                onError = { message -> mainExecutor.execute { if (!closed) reportError(message) } },
                            )
                            landmarker = client
                            mainExecutor.execute {
                                if (!closed) analysis.setAnalyzer(analysisExecutor, client::detect)
                            }
                        } catch (error: Exception) {
                            mainExecutor.execute {
                                if (!closed) reportError(error.message ?: "Unable to prepare pose detection")
                            }
                        }
                    }
                } catch (error: Exception) {
                    reportError(error.message ?: "Unable to start the front camera")
                }
            },
            mainExecutor,
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        imageAnalysis?.clearAnalyzer()
        imageAnalysis?.let { cameraProvider?.unbind(it) }
        preview?.let { cameraProvider?.unbind(it) }
        // Queued after any in-progress initialization or inference on the same worker.
        analysisExecutor.execute { landmarker?.close(); landmarker = null }
        analysisExecutor.shutdown()
    }
}
