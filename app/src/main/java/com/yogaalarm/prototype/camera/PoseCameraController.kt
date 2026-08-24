package com.yogaalarm.prototype.camera

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
import com.yogaalarm.prototype.model.PoseFrame
import java.util.concurrent.Executors

class PoseCameraController(
    context: Context,
    onResult: (PoseFrame) -> Unit,
    onError: (String) -> Unit,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val landmarker = PoseLandmarkerClient(applicationContext, onResult, onError)
    private val reportError = onError

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var closed = false

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
                        .also { useCase ->
                            useCase.setAnalyzer(analysisExecutor, landmarker::detect)
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis,
                    )
                    cameraProvider = provider
                    imageAnalysis = analysis
                } catch (error: Exception) {
                    reportError(error.message ?: "Unable to start the front camera")
                }
            },
            ContextCompat.getMainExecutor(applicationContext),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        analysisExecutor.execute(landmarker::close)
        analysisExecutor.shutdown()
    }
}
