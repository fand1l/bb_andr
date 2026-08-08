package com.blockpuzzle.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Every colour the playfield uses. Kept in one place so the whole board stays on one palette. */
object GameColors {

    /** Backdrop gradient, top to bottom. */
    val backgroundTop = Color(0xFF23276B)
    val backgroundBottom = Color(0xFF0C0E2A)

    /** The plate the 8x8 grid sits on. */
    val boardPlate = Color(0xFF1A1D4B)
    val boardPlateEdge = Color(0xFF2B3070)

    /** An unoccupied grid cell. */
    val emptyCell = Color(0xFF2A2F67)

    val panel = Color(0xFF232867)
    val panelEdge = Color(0xFF343A86)

    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFA9AEE8)
    val accent = Color(0xFFFFC93C)

    val lineHighlight = Color(0xFFFFFFFF)

    /**
     * Block palette. Index 0 is a placeholder so a colour id maps straight to
     * `BLOCKS[colorId]` without off-by-one juggling.
     */
    val BLOCKS: List<Color> = listOf(
        Color(0xFF2A2F67), // 0 - never drawn as a block
        Color(0xFF43C6FF), // 1 cyan
        Color(0xFF3B7BFF), // 2 blue
        Color(0xFF7A5CFF), // 3 violet
        Color(0xFFFF5FAE), // 4 pink
        Color(0xFFFF5A5A), // 5 red
        Color(0xFFFF9F43), // 6 orange
        Color(0xFFFFD93D), // 7 yellow
        Color(0xFF3CDC7C), // 8 green
    )

    fun block(colorId: Int): Color = BLOCKS.getOrElse(colorId) { BLOCKS[1] }

    val backgroundBrush: Brush
        get() = Brush.verticalGradient(listOf(backgroundTop, backgroundBottom))
}

/** Mixes toward black. */
fun Color.shade(amount: Float): Color = lerp(this, Color.Black, amount)

/** Mixes toward white. */
fun Color.tint(amount: Float): Color = lerp(this, Color.White, amount)

private val GameColorScheme = darkColorScheme(
    primary = GameColors.BLOCKS[1],
    onPrimary = Color(0xFF06214A),
    secondary = GameColors.accent,
    background = GameColors.backgroundBottom,
    onBackground = GameColors.textPrimary,
    surface = GameColors.panel,
    onSurface = GameColors.textPrimary,
    surfaceVariant = GameColors.boardPlate,
    onSurfaceVariant = GameColors.textSecondary,
)

@Composable
fun BlockPuzzleTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The game is intentionally always dark: the block palette is tuned for this backdrop.
    MaterialTheme(colorScheme = GameColorScheme, content = content)
}
