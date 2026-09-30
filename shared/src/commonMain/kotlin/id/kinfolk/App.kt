package id.kinfolk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import id.kinfolk.ui.Kf
import id.kinfolk.ui.KinfolkTheme
import id.kinfolk.ui.serifStyle

@Composable
@Preview
fun App() {
    KinfolkTheme {
        Column(Modifier.fillMaxSize().background(Kf.Paper).safeContentPadding()) {
            Text("Kinfolk", style = serifStyle(30f, 1.1f))
        }
    }
}
