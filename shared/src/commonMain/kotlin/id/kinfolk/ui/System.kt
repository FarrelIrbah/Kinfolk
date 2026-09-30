package id.kinfolk.ui

import androidx.compose.runtime.Composable

/** Opens the system share sheet with [text]. */
@Composable
expect fun rememberShare(): (text: String) -> Unit

/** Light status bar icons while in composition, like the prototype's dark frame on `emergency`. */
@Composable
expect fun LightStatusBarIcons()
