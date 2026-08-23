package com.rizen.app.missions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.Difficulty
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.ui.components.Caret
import com.rizen.app.ui.components.SegmentBar
import com.rizen.app.ui.theme.LocalWake
import com.rizen.app.ui.theme.WakeShape
import kotlinx.coroutines.delay
import kotlin.random.Random

// ══════════════════════════════════════════════════════════════════════════════
// MATH
// ══════════════════════════════════════════════════════════════════════════════

private data class MathQuestion(val text: String, val answer: Int)

private fun generateMath(difficulty: Difficulty): MathQuestion {
    fun r(a: Int, b: Int) = Random.nextInt(a, b + 1)
    return when (difficulty) {
        Difficulty.EASY -> {
            val a = r(4, 19); val b = r(3, 19)
            MathQuestion("$a + $b", a + b)
        }
        Difficulty.NORMAL -> {
            val a = r(12, 49); val b = r(3, 12)
            if (Random.nextBoolean()) MathQuestion("$a × $b", a * b)
            else MathQuestion("$a − ${b * 2}", a - b * 2)
        }
        Difficulty.HARD -> {
            val a = r(13, 39); val b = r(6, 19); val cc = r(2, 9)
            MathQuestion("$a × $b − $cc", a * b - cc)
        }
        Difficulty.BRUTAL -> {
            val a = r(21, 79); val b = r(11, 29); val cc = r(3, 12)
            MathQuestion("($a + $b) × $cc", (a + b) * cc)
        }
    }
}

@Composable
fun MathMission(spec: MissionSpec, onPass: () -> Unit, onFail: (String) -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current

    val total = spec.reps.coerceAtLeast(1)
    var solved by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(generateMath(spec.difficulty)) }
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }

    LaunchedEffect(wrong) {
        if (wrong) { delay(750); wrong = false; entry = "" }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            s.mathQuestionN.fmt(solved + 1, total),
            style = MaterialTheme.typography.labelMedium,
            color = c.textFaint,
        )
        Spacer(Modifier.height(6.dp))
        SegmentBar(solved.toFloat() / total, segments = total.coerceAtMost(10))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${question.text} =",
                    style = MaterialTheme.typography.displaySmall,
                    color = c.textDim,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.ifEmpty { "_" },
                        style = MaterialTheme.typography.displayMedium,
                        color = when {
                            right -> c.accent
                            wrong -> c.danger
                            else -> c.text
                        },
                    )
                    if (!right && !wrong) {
                        Spacer(Modifier.width(4.dp))
                        Caret()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        right -> s.mathRight
                        wrong -> s.mathWrong
                        else -> s.mathSolve
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        right -> c.accent
                        wrong -> c.danger
                        else -> c.textFaint
                    },
                )
            }
        }

        Keypad(
            onDigit = { d -> if (entry.length < 7 && !right) entry += d },
            onDelete = { entry = entry.dropLast(1) },
            onSubmit = {
                val v = entry.toIntOrNull()
                if (v != null && v == question.answer) {
                    right = true
                    solved += 1
                } else {
                    wrong = true
                    onFail("math_wrong")
                }
            },
            onNegate = { entry = if (entry.startsWith("-")) entry.drop(1) else "-$entry" },
        )
        Spacer(Modifier.height(8.dp))
    }

    LaunchedEffect(right) {
        if (right) {
            delay(500)
            if (solved >= total) onPass()
            else {
                question = generateMath(spec.difficulty)
                entry = ""
                right = false
            }
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onNegate: () -> Unit,
) {
    val rows = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf("±", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Key(key, Modifier.weight(1f)) {
                        when (key) {
                            "⌫" -> onDelete()
                            "±" -> onNegate()
                            else -> onDigit(key)
                        }
                    }
                }
            }
        }
        Key("ENTER", Modifier.fillMaxWidth(), accent = true, onClick = onSubmit)
    }
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalWake.current
    Box(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(WakeShape.radiusSmall))
            .background(if (accent) c.accentSoft else c.surfaceHigh)
            .border(
                BorderStroke(1.dp, if (accent) c.accent.copy(alpha = 0.6f) else c.outline),
                RoundedCornerShape(WakeShape.radiusSmall),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (accent) MaterialTheme.typography.labelLarge
            else MaterialTheme.typography.titleLarge,
            color = if (accent) c.accent else c.text,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// MEMORY
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MemoryMission(spec: MissionSpec, onPass: () -> Unit, onFail: (String) -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current

    val rounds = spec.reps.coerceAtLeast(1)
    val baseLength = when (spec.difficulty) {
        Difficulty.EASY -> 3
        Difficulty.NORMAL -> 4
        Difficulty.HARD -> 5
        Difficulty.BRUTAL -> 6
    }

    var round by remember { mutableIntStateOf(0) }
    var sequence by remember { mutableStateOf(List(baseLength) { Random.nextInt(9) }) }
    var showIndex by remember { mutableIntStateOf(-1) }
    var watching by remember { mutableStateOf(true) }
    var entered by remember { mutableStateOf(listOf<Int>()) }
    var errorCell by remember { mutableStateOf<Int?>(null) }

    fun newRound(r: Int) {
        sequence = List(baseLength + r) { Random.nextInt(9) }
        entered = emptyList()
        watching = true
    }

    LaunchedEffect(round, watching) {
        if (!watching) return@LaunchedEffect
        delay(500)
        sequence.forEachIndexed { i, cell ->
            showIndex = cell
            delay(520)
            showIndex = -1
            delay(180)
            @Suppress("UNUSED_EXPRESSION") i
        }
        watching = false
    }

    LaunchedEffect(errorCell) {
        if (errorCell != null) {
            delay(700)
            errorCell = null
            entered = emptyList()
            watching = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            s.memoryRound.fmt(round + 1, rounds),
            style = MaterialTheme.typography.labelMedium,
            color = c.textFaint,
        )
        Spacer(Modifier.height(6.dp))
        SegmentBar(round.toFloat() / rounds, segments = rounds.coerceAtMost(10))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) { col ->
                            val id = row * 3 + col
                            MemoryCell(
                                id = id,
                                lit = showIndex == id,
                                error = errorCell == id,
                                accepted = entered.lastOrNull() == id && !watching,
                                enabled = !watching,
                            ) {
                                if (watching) return@MemoryCell
                                val pos = entered.size
                                if (sequence[pos] == id) {
                                    entered = entered + id
                                    if (entered.size == sequence.size) {
                                        if (round + 1 >= rounds) onPass()
                                        else { round += 1; newRound(round + 1) }
                                    }
                                } else {
                                    errorCell = id
                                    onFail("memory_wrong")
                                }
                            }
                        }
                    }
                }
            }
        }

        MissionStatusLine(
            when {
                errorCell != null -> s.memoryWrong
                watching -> s.memoryWatch
                else -> s.memoryRepeat
            },
            good = !watching && errorCell == null,
            bad = errorCell != null,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MemoryCell(
    id: Int,
    lit: Boolean,
    error: Boolean,
    accepted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalWake.current
    val bg by animateColorAsState(
        when {
            error -> c.danger.copy(alpha = 0.5f)
            lit -> c.accent
            accepted -> c.accentSoft
            else -> c.surfaceHigh
        },
        label = "cell",
    )
    Box(
        Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(WakeShape.radius))
            .background(bg)
            .border(
                BorderStroke(1.dp, if (lit) c.accent else c.outline),
                RoundedCornerShape(WakeShape.radius),
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${id + 1}",
            style = MaterialTheme.typography.titleLarge,
            color = if (lit) c.bg else c.textFaint,
            textAlign = TextAlign.Center,
        )
    }
}


