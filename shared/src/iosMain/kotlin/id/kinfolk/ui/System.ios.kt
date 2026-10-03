package id.kinfolk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShare(): (text: String) -> Unit = remember {
    { text ->
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(UIActivityViewController(listOf(text), null), animated = true, completion = null)
    }
}

// ponytail: iOS keeps its status bar style; the dark `emergency` screen needs a UIViewController override when iOS ships.
@Composable
actual fun LightStatusBarIcons() {}

@Composable
actual fun rememberKept(key: String): Kept = remember {
    val defaults = NSUserDefaults.standardUserDefaults
    Kept({ defaults.stringForKey(key) }, { v -> if (v == null) defaults.removeObjectForKey(key) else defaults.setObject(v, key) })
}

// ponytail: iOS picks and opens nothing yet; UIDocumentPickerViewController and QLPreviewController when iOS ships.
@Composable
actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit = remember { {} }

@Composable
actual fun rememberFileViewer(): (name: String, ext: String, bytes: ByteArray) -> Unit = remember { { _, _, _ -> } }

// ponytail: iOS prints nothing yet; UIPrintInteractionController with the PDF's NSData when iOS ships.
@Composable
actual fun rememberPrinter(): (name: String, pdf: ByteArray, onSent: () -> Unit) -> Unit = remember { { _, _, _ -> } }
