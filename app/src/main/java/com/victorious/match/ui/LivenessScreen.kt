package com.victorious.match.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.victorious.match.backend.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private enum class Step { Consent, Camera, Uploading, Done }

@Composable
fun LivenessScreen() = SignedIn { _ ->
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Consent) }
    var agreed by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var check by remember { mutableStateOf<LivenessStart?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) { msg = "Camera permission is needed for the selfie check."; step = Step.Consent } }
    fun begin() { scope.launch {
        msg = null
        runCatching { LivenessApi.start(LivenessApi.provider().method) }.onSuccess { check = it; step = Step.Camera
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) perm.launch(Manifest.permission.CAMERA)
        }.onFailure { msg = LivenessLogic.friendlyError(it.message) } } }

    when (step) {
        Step.Consent -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonCard {
                H("Biometric consent")
                P("To verify you're a real person who matches your photos, we'll use your front camera for a short gesture challenge and take one selfie.")
                P("• Face detection runs on your phone (Google ML Kit). It measures head angle, smile and eye-open probability per frame; nothing leaves the phone except pass/fail per gesture.")
                P("• One selfie is uploaded privately and shown only to a MatchVictorious moderator, who compares it with your profile photos.")
                P("• " + LivenessLogic.RETENTION)
                P("• We never sell, lease, trade or share biometric data, and never use it for advertising. You can withdraw by deleting your account; any pending selfie is deleted with it.")
                P("• Verification is optional. Without it you keep using the app with an \"unverified\" badge.")
                Row { Checkbox(agreed, { agreed = it }); P("I am 18+ and I consent to the collection and use of my face geometry and selfie for identity verification as described above, including under Illinois BIPA and similar laws. Consent version $BIOMETRIC_CONSENT_VERSION.") }
                Button(enabled = agreed, onClick = { begin() }) { Text("I agree — start") }
                msg?.let { P(it, MaterialTheme.colorScheme.error) }
            }
        }
        Step.Camera -> check?.let { c -> Challenge(c, onFail = { msg = it; step = Step.Consent }) { result, jpeg ->
            step = Step.Uploading
            scope.launch { runCatching { LivenessApi.submit(c.id, result, jpeg) }
                .onSuccess { step = Step.Done }.onFailure { msg = LivenessLogic.friendlyError(it.message); step = Step.Consent } } } }
        Step.Uploading -> Box(Modifier.fillMaxSize().padding(16.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        Step.Done -> Column(Modifier.padding(16.dp)) { NeonCard { H("Submitted for review"); P("A moderator will compare your selfie with your photos. Your selfie is deleted as soon as they decide.", Cyan) } }
    }
}

@Composable
private fun Challenge(c: LivenessStart, onFail: (String) -> Unit, onDone: (kotlinx.serialization.json.JsonObject, ByteArray) -> Unit) {
    val ctx = LocalContext.current; val owner = LocalLifecycleOwner.current
    var idx by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf("Center your face") }
    val done = remember { mutableStateMapOf<String, Boolean>() }
    var finished by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val detector = remember { FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)       // smile / eyes-open probabilities only
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE).setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE).build()) }
    DisposableEffect(Unit) { onDispose { detector.close() } }
    var sawOpen by remember { mutableStateOf(false) }
    var lastFrame by remember { mutableStateOf<FaceFrame?>(null) }
    val deadline = remember { System.currentTimeMillis() + 60_000 }

    fun takeSelfie() {
        capture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes = runCatching { jpeg(image) }.getOrNull(); image.close()
                if (bytes == null) onFail("Couldn't capture selfie.") else onDone(LivenessLogic.result(done.toMap(), true), bytes)
            }
            override fun onError(e: ImageCaptureException) { onFail("Couldn't capture selfie: ${e.message}") }
        })
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val g = c.challenge.getOrNull(idx)
        H(if (g != null) "Step ${idx + 1}/${c.challenge.size}: ${LivenessLogic.label(g)}" else "Look straight at the camera")
        P(hint)
        AndroidView(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f), factory = { vctx ->
            val view = PreviewView(vctx)
            val future = ProcessCameraProvider.getInstance(vctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(vctx)) { proxy ->
                    val media = proxy.image
                    if (media == null || finished) { proxy.close(); return@setAnalyzer }
                    detector.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { faces ->
                        val face = faces.singleOrNull()
                        val fr = FaceFrame(faces.size, face?.headEulerAngleY ?: 0f, face?.smilingProbability, face?.leftEyeOpenProbability, face?.rightEyeOpenProbability)
                        lastFrame = fr
                        if (System.currentTimeMillis() > deadline) { finished = true; onFail("Time's up. Please try again."); return@addOnSuccessListener }
                        hint = when (faces.size) { 0 -> "No face found"; 1 -> "Good"; else -> "Only you should be in the frame" }
                        if (LivenessLogic.eyesOpen(fr)) sawOpen = true
                        val cur = c.challenge.getOrNull(idx)
                        if (cur != null && LivenessLogic.detects(cur, fr, sawOpen)) { done[cur] = true; idx++; sawOpen = false }
                        else if (cur == null && LivenessLogic.frontal(fr) && (fr.smileProb ?: 0f) < 0.9f && !finished) { finished = true; takeSelfie() }
                    }.addOnCompleteListener { proxy.close() }
                }
                runCatching { provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis, capture) }
                    .onFailure { onFail("Front camera unavailable: ${it.message}") }
            }, ContextCompat.getMainExecutor(vctx))
            view
        }, onRelease = { runCatching { ProcessCameraProvider.getInstance(it.context).get().unbindAll() } })
        P("Gestures are random each time so a photo or video replay can't pass. Nothing is recorded except the final selfie.", androidx.compose.ui.graphics.Color.Gray)
    }
}

/** Rotate upright and re-encode at ≤1280px, JPEG 85 (well under the 5 MB bucket limit). EXIF/location is not carried over. */
private fun jpeg(image: ImageProxy): ByteArray {
    var bmp: Bitmap = image.toBitmap()
    val m = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    val scale = 1280f / maxOf(bmp.width, bmp.height)
    if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    return ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it); it.toByteArray() }
}
