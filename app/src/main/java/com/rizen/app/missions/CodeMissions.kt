package com.rizen.app.missions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.i18n.fmt
import com.rizen.app.core.model.Difficulty
import com.rizen.app.core.model.MissionSpec
import com.rizen.app.ui.components.SegmentBar
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.theme.LocalWake
import com.rizen.app.ui.theme.WakeShape
import kotlinx.coroutines.delay
import kotlin.random.Random

// ══════════════════════════════════════════════════════════════════════════════
// CODE GENERATION
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Strong-password-looking codes. `l`, `I`, `1`, `O` and `0` are deliberately kept in —
 * having to actually look at the characters is the point of the exercise.
 */
private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789#@%&*?+="

fun generateCode(length: Int): String =
    (1..length).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")

// ══════════════════════════════════════════════════════════════════════════════
// TYPE THE CODE
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Character-by-character live validation: each cell goes green the moment it matches and
 * red the moment it doesn't. No "wrong password" at the end — you can see yourself
 * failing in real time, which is far more annoying and therefore far more effective.
 */
@Composable
fun TypeCodeMission(
    length: Int,
    onPass: () -> Unit,
    onWrong: () -> Unit = {},
    allowRegenerate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = LocalWake.current
    val s = LocalStrings.current

    var target by remember { mutableStateOf(generateCode(length)) }
    var entry by remember { mutableStateOf("") }
    var passed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(target) { runCatching { focus.requestFocus() } }

    val mismatches = entry.filterIndexed { i, ch -> i < target.length && ch != target[i] }.length
    val complete = entry.length == target.length && mismatches == 0

    LaunchedEffect(complete) {
        if (complete && !passed) {
            passed = true
            delay(550)
            onPass()
        }
    }
    LaunchedEffect(mismatches) { if (mismatches > 0) onWrong() }

    Column(modifier.fillMaxSize()) {
        Text(s.typeCopyThis, style = MaterialTheme.typography.labelMedium, color = c.textFaint)
        Spacer(Modifier.height(8.dp))
        CodeStrip(target, null, c.textDim)

        Spacer(Modifier.height(22.dp))
        Text(s.typeYourTurn, style = MaterialTheme.typography.labelMedium, color = c.textFaint)
        Spacer(Modifier.height(8.dp))
        CodeStrip(entry.padEnd(target.length, ' '), target, c.text)

        Spacer(Modifier.height(14.dp))
        SegmentBar(
            progress = entry.length.toFloat() / target.length,
            segments = target.length.coerceAtMost(20),
            color = if (mismatches > 0) c.danger else c.accent,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                passed -> s.typeLocked
                mismatches > 0 -> s.typeMismatch.fmt(mismatches)
                entry.isEmpty() -> " "
                else -> s.typePerfect
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                passed -> c.accent
                mismatches > 0 -> c.danger
                else -> c.accent
            },
        )

        // Invisible field: the cells above are the real UI, this just eats keystrokes.
        BasicTextField(
            value = entry,
            onValueChange = { v -> if (!passed) entry = v.take(target.length).filter { it != '\n' } },
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .alpha(0.01f)
                .focusRequester(focus)
                .focusable(),
        )

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (allowRegenerate) {
                WPButton(s.typeNewCode, {
                    target = generateCode(length); entry = ""
                }, Modifier.weight(1f), ghost = true)
            }
            WPButton(s.reset, { entry = "" }, Modifier.weight(1f), ghost = true)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One cell per character, coloured against [against] when provided. */
@Composable
private fun CodeStrip(text: String, against: String?, neutral: Color) {
    val c = LocalWake.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        text.forEachIndexed { i, ch ->
            val state = when {
                against == null -> 0
                ch == ' ' -> 0
                i < against.length && ch == against[i] -> 1
                else -> 2
            }
            val bg by animateColorAsState(
                when (state) {
                    1 -> c.accent.copy(alpha = 0.18f)
                    2 -> c.danger.copy(alpha = 0.22f)
                    else -> c.surfaceHigh
                },
                label = "cellBg",
            )
            val fg = when (state) {
                1 -> c.accent
                2 -> c.danger
                else -> neutral
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .border(
                        BorderStroke(
                            1.dp,
                            when (state) {
                                1 -> c.accent.copy(alpha = 0.5f)
                                2 -> c.danger.copy(alpha = 0.5f)
                                else -> c.outlineSoft
                            },
                        ),
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (ch == ' ') "·" else ch.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = fg,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CRACK THE LOCK
// ══════════════════════════════════════════════════════════════════════════════

private data class LockSlot(val correct: Char, val options: List<Char>)

private fun buildLock(slots: Int): List<LockSlot> = (0 until slots).map {
    val correct = ALPHABET[Random.nextInt(ALPHABET.length)]
    val decoys = mutableSetOf<Char>()
    while (decoys.size < 3) {
        val ch = ALPHABET[Random.nextInt(ALPHABET.length)]
        if (ch != correct) decoys += ch
    }
    LockSlot(correct, (decoys + correct).shuffled())
}

/**
 * Four blank slots, four options each, guess until it opens.
 *
 * Nothing punishes a wrong answer beyond time — which is the punishment, because the
 * alarm comes back when the silence window expires. Low frustration, high pressure.
 */
@Composable
fun CrackLockMission(spec: MissionSpec, onPass: () -> Unit, onWrong: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current

    val slotCount = when (spec.difficulty) {
        Difficulty.EASY -> 3
        Difficulty.NORMAL -> spec.reps.coerceIn(4, 6)
        Difficulty.HARD -> spec.reps.coerceIn(5, 7)
        Difficulty.BRUTAL -> spec.reps.coerceIn(6, 8)
    }

    val lock = remember(slotCount) { buildLock(slotCount) }
    var solvedUpTo by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var wrongChar by remember { mutableStateOf<Char?>(null) }
    var passed by remember { mutableStateOf(false) }

    LaunchedEffect(wrongChar) { if (wrongChar != null) { delay(500); wrongChar = null } }
    LaunchedEffect(solvedUpTo) {
        if (solvedUpTo >= slotCount && !passed) {
            passed = true
            delay(650)
            onPass()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── the lock face ────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lock.forEachIndexed { i, slot ->
                val open = i < solvedUpTo
                val active = i == solvedUpTo && !passed
                val border by animateColorAsState(
                    when {
                        open -> c.accent
                        active -> c.warn
                        else -> c.outline
                    },
                    label = "slot",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(WakeShape.radiusSmall))
                        .background(if (open) c.accent.copy(alpha = 0.14f) else c.surfaceHigh)
                        .border(BorderStroke(1.5.dp, border), RoundedCornerShape(WakeShape.radiusSmall)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (open) slot.correct.toString() else if (active) "▮" else "·",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (open) c.accent else if (active) c.warn else c.textFaint,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (passed) s.guessLocked
            else s.guessPick.fmt(solvedUpTo + 1),
            style = MaterialTheme.typography.bodyMedium,
            color = if (passed) c.accent else c.textDim,
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val current = lock.getOrNull(solvedUpTo)
            if (current != null && !passed) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    current.options.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { ch ->
                                OptionKey(
                                    ch = ch,
                                    wrong = wrongChar == ch,
                                ) {
                                    attempts += 1
                                    if (ch == current.correct) solvedUpTo += 1
                                    else { wrongChar = ch; onWrong() }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            if (wrongChar != null) s.guessWrong else s.guessAttempts.fmt(attempts),
            style = MaterialTheme.typography.labelMedium,
            color = if (wrongChar != null) c.danger else c.textFaint,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OptionKey(ch: Char, wrong: Boolean, onClick: () -> Unit) {
    val c = LocalWake.current
    val shake by animateFloatAsState(if (wrong) 1f else 0f, label = "wrong")
    Box(
        Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(WakeShape.radius))
            .background(
                if (wrong) c.danger.copy(alpha = 0.18f * shake + 0.02f) else c.surfaceHigh
            )
            .border(
                BorderStroke(1.dp, if (wrong) c.danger else c.outline),
                RoundedCornerShape(WakeShape.radius),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ch.toString(),
            style = MaterialTheme.typography.displaySmall,
            color = if (wrong) c.danger else c.text,
        )
    }
}
