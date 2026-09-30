package id.kinfolk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
