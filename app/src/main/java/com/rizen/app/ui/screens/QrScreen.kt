package com.rizen.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizen.app.core.i18n.LocalStrings
import com.rizen.app.core.util.QrGen
import com.rizen.app.ui.AppViewModel
import com.rizen.app.ui.components.SectionLabel
import com.rizen.app.ui.components.WPButton
import com.rizen.app.ui.components.WPCard
import com.rizen.app.ui.theme.LocalWake

/**
 * Generates the printable code for the "get out of bed and go scan it" mission.
 * Regenerating invalidates the old printout on purpose — otherwise you could keep a
 * spare copy on the nightstand, which defeats the entire idea.
 */
@Composable
fun QrScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = LocalWake.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val st by vm.settings.collectAsStateWithLifecycle()

    LaunchedEffect(st.qrPayload) {
        if (st.qrPayload.isBlank()) vm.regenerateQr()
    }

    val bitmap = remember(st.qrPayload) {
        if (st.qrPayload.isBlank()) null
        else runCatching { QrGen.bitmap(st.qrPayload, 720) }.getOrNull()
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.qrGenTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                "[ ${s.back} ]",
                style = MaterialTheme.typography.labelSmall,
                color = c.textFaint,
                modifier = Modifier.clickable { onBack() }.padding(8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(s.qrGenBody, style = MaterialTheme.typography.bodyMedium, color = c.textDim)
        Spacer(Modifier.height(18.dp))

        WPCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("payload")
            Text(
                st.qrPayload.ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = c.textFaint,
            )
        }

        Spacer(Modifier.height(16.dp))
        WPButton(
            s.qrGenShare,
            {
                runCatching {
                    val uri = QrGen.share(context, st.qrPayload)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            s.qrGenShare,
                        )
                    )
                }
            },
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        WPButton(s.qrGenNew, { vm.regenerateQr() }, Modifier.fillMaxWidth(), ghost = true)
        Spacer(Modifier.height(8.dp))
        Text(
            s.qrGenWarning,
            style = MaterialTheme.typography.bodySmall,
            color = c.warn,
        )
        Spacer(Modifier.height(40.dp))
    }
}
