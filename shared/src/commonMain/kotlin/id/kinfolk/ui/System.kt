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
