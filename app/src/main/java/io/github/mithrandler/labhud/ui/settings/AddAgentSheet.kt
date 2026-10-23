package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.mithrandler.labhud.data.AgentCode
import io.github.mithrandler.labhud.ui.Heading
import io.github.mithrandler.labhud.ui.Lab

/**
 * "Add agent": a one-time pairing code (it lives [AgentCode.minutes] minutes), the line that
 * installs the agent with it, and that line as a QR code for a screen to scan. A new code on
 * request; the list behind the sheet refreshes when the agent has dialled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAgentSheet(code: AgentCode, onNewCode: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember(code.code) { mutableStateOf("") }
    var left by remember(code.code) { mutableLongStateOf(code.minutes * 60L) }
    LaunchedEffect(code.code) {
        while (left > 0) { kotlinx.coroutines.delay(1000); left-- }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Lab.card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = LabScale.space4, end = LabScale.space4, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(LabScale.space3)) {
            Text("ADD AN AGENT", style = Heading)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Code", fontSize = LabScale.textSm, color = Lab.muted)
                    Text(code.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                }
                Text(if (left > 0) countdown(left) else "expired", fontSize = LabScale.textMd, color = if (left > 0) Lab.sub else Lab.red)
            }
            if (code.install.isNotEmpty()) {
                Text("Run this on the host (it carries the code):", fontSize = LabScale.textSm, color = Lab.sub)
                Line(code.install)
                Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                    OutlinedButton(onClick = { copy(context, "LabHUD agent install line", code.install); copied = "line" }) { Text("Copy line") }
                    OutlinedButton(onClick = { copy(context, "LabHUD agent code", code.code); copied = "code" }) { Text("Copy code") }
                }
                if (code.docker.isNotEmpty()) {
                    Text("Or as a container:", fontSize = LabScale.textSm, color = Lab.sub)
                    Line(code.docker)
                    OutlinedButton(onClick = { copy(context, "LabHUD agent container", code.docker); copied = "docker" }) { Text("Copy container line") }
                }
                Qr(code.install, Modifier.align(Alignment.CenterHorizontally))
            } else {
                Text("This LabHUD is too old to give the line: use the code with the installer.", fontSize = LabScale.textSm, color = Lab.muted)
            }
            if (copied.isNotEmpty()) Text("Copied.", fontSize = LabScale.textSm, color = Lab.green)
            OutlinedButton(onClick = onNewCode, modifier = Modifier.fillMaxWidth()) { Text("Make a new code") }
        }
    }
}

@Composable
private fun Line(text: String) = SelectionContainer {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = LabScale.textXs,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Lab.block).padding(LabScale.space2))
}

/** [text] as a QR code in black on white (white border, so a camera finds it in dark mode too). */
@Composable
private fun Qr(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2))
        }.getOrNull()
    } ?: return
    Canvas(modifier.size(220.dp).background(Color.White)) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** 754 s as "12:34". */
internal fun countdown(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun copy(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}
