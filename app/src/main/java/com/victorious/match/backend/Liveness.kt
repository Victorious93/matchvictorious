package com.victorious.match.backend

import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/** Bump when the consent text changes; stored with every check (BIPA written-release record). */
const val BIOMETRIC_CONSENT_VERSION = "bio-2026-10-20"

@Serializable data class LivenessStart(val id: String, val challenge: List<String>)
@Serializable data class PendingLiveness(@SerialName("check_id") val checkId: String, @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String, val challenge: List<String> = emptyList(), val result: JsonObject = JsonObject(emptyMap()),
    @SerialName("selfie_path") val selfiePath: String? = null, @SerialName("created_at") val createdAt: String? = null, val photos: List<String> = emptyList())
@Serializable data class VendorConfig(val vendor: Boolean = false, val region: String? = null)
@Serializable data class VendorResult(val passed: Boolean = false, val confidence: Double? = null, val status: String? = null)

/** Per-frame face measurements from ML Kit (probabilities may be null when the classifier is unsure). No embeddings/landmark templates. */
data class FaceFrame(val faces: Int, val headYaw: Float, val smileProb: Float?, val leftEyeOpen: Float?, val rightEyeOpen: Float?)

/** Pure, unit-tested challenge logic. */
object LivenessLogic {
    val gestures = listOf("turn_left", "turn_right", "smile", "blink")
    fun label(g: String) = when (g) { "turn_left" -> "Turn your head left"; "turn_right" -> "Turn your head right"; "smile" -> "Smile"; "blink" -> "Blink"; else -> g }
    const val YAW = 25f
    /** Front camera image is mirrored: ML Kit's positive Euler Y means the face turned toward the image's left = user's right. */
    fun detects(g: String, f: FaceFrame, sawEyesOpen: Boolean): Boolean {
        if (f.faces != 1) return false
        return when (g) {
            "turn_left" -> f.headYaw < -YAW
            "turn_right" -> f.headYaw > YAW
            "smile" -> (f.smileProb ?: 0f) > 0.8f
            "blink" -> sawEyesOpen && (f.leftEyeOpen ?: 1f) < 0.2f && (f.rightEyeOpen ?: 1f) < 0.2f
            else -> false
        }
    }
    fun eyesOpen(f: FaceFrame) = f.faces == 1 && (f.leftEyeOpen ?: 0f) > 0.7f && (f.rightEyeOpen ?: 0f) > 0.7f
    /** Selfie must be a neutral, frontal, single face. */
    fun frontal(f: FaceFrame) = f.faces == 1 && kotlin.math.abs(f.headYaw) < 12f
    /** Only gesture booleans + single_face are ever sent (server also whitelists). */
    fun result(done: Map<String, Boolean>, singleFace: Boolean): JsonObject = buildJsonObject {
        done.filterKeys { it in gestures }.forEach { (k, v) -> put(k, v) }; put("single_face", singleFace) }
    fun summarize(r: JsonObject) = r.entries.joinToString { (k, v) -> "${label(k).takeIf { k in gestures } ?: k}: ${if (v.jsonPrimitive.booleanOrNull == true) "✓" else "✗"}" }.ifEmpty { "none" }
    fun selfiePath(user: String) = "$user/${UUID.randomUUID()}.jpg"
    fun friendlyError(msg: String?): String = when {
        msg == null -> "Something went wrong"
        "consent" in msg -> "Please accept the biometric consent first."
        "too many attempts" in msg -> "Too many attempts today. Try again tomorrow."
        "already verified" in msg -> "You're already verified (or your account can't be verified)."
        "expired" in msg -> "That check expired. Please start again."
        else -> msg.lineSequence().first().take(160)
    }
    const val RETENTION = "We never create or store a face template. On-device checks keep one selfie only until a moderator decides (deleted immediately after), and in any case no longer than 30 days. Vendor checks keep only pass/fail and a confidence score."
}

/** A way to prove "a live person who matches these photos". */
interface LivenessProvider {
    val method: String
    suspend fun available(): Boolean
}

object OnDeviceLiveness : LivenessProvider {
    override val method = "on_device"
    override suspend fun available() = true
}

/** Amazon Rekognition Face Liveness via the `liveness` Edge Function; reports unavailable until AWS secrets are set. */
object VendorLiveness : LivenessProvider {
    override val method = "vendor"
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun available(): Boolean = runCatching {
        json.decodeFromString<VendorConfig>(Backend.client.functions.invoke("liveness", buildJsonObject { put("action", "config") }).bodyAsText()).vendor
    }.getOrDefault(false)
    suspend fun startSession(checkId: String): JsonObject =
        json.parseToJsonElement(Backend.client.functions.invoke("liveness", buildJsonObject { put("action", "start"); put("check_id", checkId) }).bodyAsText()).jsonObject
    suspend fun result(checkId: String): VendorResult =
        json.decodeFromString(Backend.client.functions.invoke("liveness", buildJsonObject { put("action", "result"); put("check_id", checkId) }).bodyAsText())
}

object LivenessApi {
    private val c get() = Backend.client
    /** Vendor first when configured AND this build ships the vendor capture UI (Amplify FaceLivenessDetector); else on-device. */
    const val VENDOR_CAPTURE_UI = false
    suspend fun provider(): LivenessProvider = if (VENDOR_CAPTURE_UI && VendorLiveness.available()) VendorLiveness else OnDeviceLiveness
    suspend fun start(method: String): LivenessStart = c.postgrest.rpc("start_liveness",
        buildJsonObject { put("p_method", method); put("p_consent_version", BIOMETRIC_CONSENT_VERSION) }).decodeList<LivenessStart>().first()
    suspend fun submit(checkId: String, result: JsonObject, jpeg: ByteArray) {
        val me = Backend.me() ?: error("not signed in")
        val path = LivenessLogic.selfiePath(me)
        c.storage.from("selfies").upload(path, jpeg)
        runCatching { c.postgrest.rpc("submit_liveness", buildJsonObject { put("p_check", checkId); put("p_result", result); put("p_selfie_path", path) }) }
            .onFailure { runCatching { c.storage.from("selfies").delete(path) }; throw it }
    }
    suspend fun pending(): List<PendingLiveness> = c.postgrest.rpc("mod_pending_liveness").decodeList()
    suspend fun signedSelfieUrl(path: String): String? = runCatching { c.storage.from("selfies").createSignedUrl(path, 5.minutes) }.getOrNull()
    /** Decision, then delete the selfie object immediately. */
    suspend fun review(checkId: String, ok: Boolean, reason: String?) {
        val path = c.postgrest.rpc("mod_review_liveness", buildJsonObject { put("p_check", checkId); put("p_ok", ok); put("p_reason", reason) }).decodeAs<String?>()
        if (path != null) c.storage.from("selfies").delete(path)
    }
    /** Retention sweep: delete selfie objects no longer tied to an open check. */
    suspend fun purgeExpired(): Int {
        val paths = c.postgrest.rpc("mod_selfies_to_purge").decodeList<JsonObject>().mapNotNull { it["path"]?.jsonPrimitive?.contentOrNull }
        if (paths.isNotEmpty()) c.storage.from("selfies").delete(paths)
        return paths.size
    }
}
