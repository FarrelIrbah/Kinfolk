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
