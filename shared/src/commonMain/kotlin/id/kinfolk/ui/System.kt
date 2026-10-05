package id.kinfolk.ui

import androidx.compose.runtime.Composable

/** Opens the system share sheet with [text]. */
@Composable
expect fun rememberShare(): (text: String) -> Unit

/** Light status bar icons while in composition, like the prototype's dark frame on `emergency`. */
@Composable
expect fun LightStatusBarIcons()

/** One string kept in this app's private storage across launches; writing null forgets it. */
class Kept(val read: () -> String?, val write: (String?) -> Unit)

@Composable
expect fun rememberKept(key: String): Kept

/**
 * A file the Member picked: its name without the extension (blank for a photo), "PDF", "JPG" or "PNG", its bytes, and
 * its page count (1 for a photo).
 */
class PickedFile(val name: String, val ext: String, val bytes: ByteArray, val pages: Int = 1)

/** Opens the system picker for a PDF, JPG or PNG, with the camera offered too; [onPicked] gets what was chosen. */
@Composable
expect fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit

/** Opens [bytes] in the system viewer for its type. */
@Composable
expect fun rememberFileViewer(): (name: String, ext: String, bytes: ByteArray) -> Unit

/** Opens the system print dialog for [pdf], named [name]; [onSent] runs once it went to a printer, not when cancelled. */
@Composable
expect fun rememberPrinter(): (name: String, pdf: ByteArray, onSent: () -> Unit) -> Unit

/**
 * The visit recorder: audio of an Appointment to files on the phone (app-private, so under Android's file-based
 * encryption), going on with the screen off. [start] asks for the microphone first, answers whether recording began,
 * and adds a part to what is saved for that Appointment ([discard] it first for a fresh recording); [level] is how
 * loud it is now, 0 to 1; [failed]: the microphone gave up mid-recording. [stop] ends it cleanly. [parts]: what is
 * saved, oldest first (AAC in ADTS, so a part cut off by a crash still plays); [stopped]: whether it ended with [stop].
 */
class Recorder(
    val start: (appointmentId: String, onStarted: (Boolean) -> Unit) -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val level: () -> Float,
    val failed: () -> Boolean,
    val stop: () -> Unit,
    val parts: (appointmentId: String) -> List<ByteArray>,
    val stopped: (appointmentId: String) -> Boolean,
    val discard: (appointmentId: String) -> Unit,
)

/** What is on the phone for one Appointment: the audio to upload, how long it is, whether it ended cleanly. */
class SavedAudio(val audio: ByteArray, val seconds: Int, val stopped: Boolean)

fun Recorder.saved(appointmentId: String): SavedAudio? = savedAudio(parts(appointmentId), stopped(appointmentId))

/** [parts] joined, each cut to its whole ADTS frames; the length counted from the frames. Null without a whole frame. */
fun savedAudio(parts: List<ByteArray>, stopped: Boolean): SavedAudio? {
    val rates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
    var samples = 0.0
    val audio = parts.fold(ByteArray(0)) { acc, b ->
        var i = 0
        while (i + 7 <= b.size && b[i].toInt() and 0xFF == 0xFF && b[i + 1].toInt() and 0xF0 == 0xF0) {
            val len = (b[i + 3].toInt() and 3 shl 11) or (b[i + 4].toInt() and 0xFF shl 3) or (b[i + 5].toInt() and 0xFF ushr 5)
            if (len < 7 || i + len > b.size) break
            samples += 1024.0 * ((b[i + 6].toInt() and 3) + 1) / rates.getOrElse(b[i + 2].toInt() ushr 2 and 0xF) { 16000 }
            i += len
        }
        acc + b.copyOf(i)
    }
    return if (audio.isEmpty()) null else SavedAudio(audio, samples.toInt(), stopped)
}

@Composable
expect fun rememberRecorder(): Recorder

/**
 * The store's monthly prices for the paywall, formatted for the store's country: [plan] alone, [addOn] (the plan with
 * the add-on less the plan, without the "+"), [plus] the plan with the add-on; [trial]: a free trial is offered.
 */
data class Offer(val plan: String, val addOn: String, val plus: String, val trial: Boolean)

/**
 * The store through RevenueCat (ADR 0006), logged in as the Care Circle. [offer]: null when the store can't say.
 * [buy]: true once bought, false when cancelled; throws when it fails. [manage]: the store's subscriptions page.
 */
class Store(val offer: suspend (circleId: String) -> Offer?, val buy: suspend (circleId: String, transcription: Boolean) -> Boolean, val manage: () -> Unit)

@Composable
expect fun rememberStore(): Store
