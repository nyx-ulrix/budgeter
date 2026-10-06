package com.nyxulrix.budgeter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nyxulrix.budgeter.R
import kotlin.math.floor

/** Nostelika palette. */
object Px {
    val orange = Color(0xFFF4512A)
    val orangeBright = Color(0xFFFF4B16)
    val cream = Color(0xFFE7D6AD)
    val creamLight = Color(0xFFF1E4C6)
    val navy = Color(0xFF10172F)
    val brown = Color(0xFF2B1D12)
    val blue = Color(0xFF2459A6)
    val green = Color(0xFF4A9A78)
    val red = Color(0xFFC93721)
    val white = Color(0xFFFFF4DC)
    val yellow = Color(0xFFF2C230)   // dipping into savings
    val muted = Color(0xFF2B1D12).copy(alpha = 0.55f)
}

private val displayFont = FontFamily(Font(R.font.press_start))
private val pixelFont = FontFamily(Font(R.font.silkscreen), Font(R.font.silkscreen_bold, FontWeight.Bold))
private val bodyFont = FontFamily(Font(R.font.vt323))

/** Type roles from the brief. VT323 renders small, so body sizes run larger than usual. */
object Type {
    val hero = TextStyle(fontFamily = displayFont, fontSize = 20.sp, lineHeight = 26.sp, color = Px.brown)
    val number = TextStyle(fontFamily = displayFont, fontSize = 16.sp, lineHeight = 22.sp, color = Px.brown)
    val title = TextStyle(fontFamily = pixelFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp, lineHeight = 16.sp)
    val label = TextStyle(fontFamily = pixelFont, fontSize = 11.sp, letterSpacing = 0.5.sp, lineHeight = 14.sp, color = Px.brown)
    val button = TextStyle(fontFamily = pixelFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp, lineHeight = 14.sp)
    val body = TextStyle(fontFamily = bodyFont, fontSize = 21.sp, lineHeight = 22.sp, color = Px.brown)
    val small = TextStyle(fontFamily = bodyFont, fontSize = 18.sp, lineHeight = 19.sp, color = Px.brown)
}

@Composable
fun BudgeterTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Px.orange, onPrimary = Px.creamLight, secondary = Px.blue, background = Px.cream,
        surface = Px.creamLight, onSurface = Px.brown, onBackground = Px.brown, error = Px.red,
    ),
    typography = MaterialTheme.typography.copy(bodyLarge = Type.body, bodyMedium = Type.body, labelLarge = Type.button),
    content = content,
)

/** 9×9 pixel glyphs drawn as hard-edged squares ('#' = filled). */
object Glyphs {
    val home = listOf(
        "....#....", "...###...", "..#####..", ".#######.", "#########",
        ".#.....#.", ".#.##..#.", ".#.##..#.", ".#######.",
    )
    val list = listOf(
        "#########", "#.......#", "#.#.###.#", "#.......#", "#.#.###.#",
        "#.......#", "#.#.###.#", "#.......#", "#########",
    )
    val coin = listOf(
        "..#####..", ".#.....#.", "#..###..#", "#..#....#", "#..###..#",
        "#....#..#", "#..###..#", ".#.....#.", "..#####..",
    )
    val people = listOf(
        ".##...##.", "####.####", "####.####", ".##...##.", ".........",
        "####.####", "#########", "#########", "#########",
    )
    val person = listOf(
        "...###...", "..#####..", "..#####..", "..#####..", "...###...",
        ".........", ".#######.", "#########", "#########",
    )
    val plus = listOf(
        "...###...", "...###...", "...###...", "#########", "#########",
        "#########", "...###...", "...###...", "...###...",
    )
    val image = listOf(
        "#########", "#.......#", "#.##....#", "#.##....#", "#.....#.#",
        "#....###.", "#...#####", "#.#######", "#########",
    )
    val camera = listOf(
        ".........", "..###....", "#########", "#...#...#", "#..###..#",
        "#..###..#", "#...#...#", "#########", ".........",
    )
    val bag = listOf(
        "...###...", "..#...#..", "#########", "#.......#", "#.#####.#",
        "#.......#", "#.......#", "#.......#", "#########",
    )
    val plane = listOf(
        "....#....", "...###...", "...###...", "#########", "#########",
        "...###...", "...###...", "..#####..", ".........",
    )
    val sync = listOf(
        "..#####..", ".#.....#.", "#.......#", "#....####", "#.....##.",
        ".##.....#", "####....#", ".#.....#.", "..#####..",
    )
}

@Composable
fun Glyph(rows: List<String>, color: Color = Px.brown, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val px = floor(this.size.width / rows.size)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c == '#') drawRect(color, Offset(x * px, y * px), Size(px, px)) }
        }
    }
}
