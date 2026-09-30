package id.kinfolk.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun rememberShare(): (text: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) }
    }
}

@Composable
actual fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val bars = WindowCompat.getInsetsController((view.context as Activity).window, view)
        val was = bars.isAppearanceLightStatusBars
        bars.isAppearanceLightStatusBars = false
        onDispose { bars.isAppearanceLightStatusBars = was }
    }
}

@Composable
actual fun rememberKept(key: String): Kept {
    val prefs = LocalContext.current.getSharedPreferences("kinfolk", Context.MODE_PRIVATE)
    return remember(prefs) { Kept({ prefs.getString(key, null) }, { v -> prefs.edit().apply { if (v == null) remove(key) else putString(key, v) }.apply() }) }
}
