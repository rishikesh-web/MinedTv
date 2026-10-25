package com.example.minedtv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// MinedIt logo mark: a brain ("Mined" -> "Mind"). 24x24 stroke path; the launcher icon
// in res/drawable/ic_launcher_foreground.xml uses the same path data.
internal const val BRAIN_PATH =
    // Left hemisphere
    "M12,4.5 C10.8,3.2 8.6,3.3 7.8,4.8 C6,4.6 4.6,6.2 5.1,7.9 C3.6,8.7 3.3,10.9 4.6,12 " +
        "C3.5,13.3 4,15.4 5.6,15.9 C5.6,17.7 7.4,19 9.1,18.4 C9.9,19.8 11.3,20 12,19.4 Z " +
        // Right hemisphere
        "M12,4.5 C13.2,3.2 15.4,3.3 16.2,4.8 C18,4.6 19.4,6.2 18.9,7.9 C20.4,8.7 20.7,10.9 19.4,12 " +
        "C20.5,13.3 20,15.4 18.4,15.9 C18.4,17.7 16.6,19 14.9,18.4 C14.1,19.8 12.7,20 12,19.4 " +
        // Folds
        "M8,8.2 C9,8 9.8,8.8 9.6,9.8 M6.8,12.2 C8,11.8 9.2,12.6 9,13.8 M9.5,15.8 C10.2,15.2 10.4,14.3 10,13.6 " +
        "M16,8.2 C15,8 14.2,8.8 14.4,9.8 M17.2,12.2 C16,11.8 14.8,12.6 15,13.8 M14.5,15.8 C13.8,15.2 13.6,14.3 14,13.6"

val BrainIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Brain",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(BRAIN_PATH),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).build()
}
