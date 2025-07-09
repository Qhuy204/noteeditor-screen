package com.example.noteeditor.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
* A custom modifier to apply a "glassmorphism" effect to a composable.
*
* This effect includes a semi-transparent background, a subtle gradient to mimic light,
* and a light border to create a "glass edge" highlight.
*
* @param cornerRadius The corner radius for the shape.
* @param glassColor The base color of the glass effect.
* @param transparency The alpha level for the glass background.
* @param borderWidth The width of the border highlight.
*/
fun Modifier.glassmorphism(
cornerRadius: Dp,
glassColor: Color = Color.White,
transparency: Float = 0.1f,
borderWidth: Dp = 1.5.dp,
): Modifier {
// The main color for the glass background with the specified transparency
val transparentColor = glassColor.copy(alpha = transparency)

// A lighter, more transparent color for the "edge" of the glass to simulate a highlight
val edgeColor = glassColor.copy(alpha = 0.2f)

return this
    // Clip the content to the specified corner radius
    .clip(RoundedCornerShape(cornerRadius))
    // Apply a gradient background. A subtle gradient makes the glass look more realistic.
    .background(
        brush = Brush.linearGradient(
            colors = listOf(
                transparentColor.copy(alpha = transparency + 0.15f), // Slightly more opaque at the top
                transparentColor // The main transparent color at the bottom
            )
        )
    )
    // Add a border with its own gradient to create a shiny edge effect.
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = listOf(edgeColor, edgeColor.copy(alpha = 0.0f))
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
}
