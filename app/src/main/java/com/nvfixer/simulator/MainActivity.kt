package com.nvfixer.simulator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NvApp(vm) }
    }
}

// ---------- palette ----------
data class Pal(
    val bg: Color, val card: Color, val line: Color, val txt: Color, val mut: Color,
    val acc: Color, val ok: Color, val bad: Color, val warn: Color, val con: Color, val btn: Color
)

val DarkPal = Pal(
    Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF30363D), Color(0xFFC9D1D9), Color(0xFF8B949E),
    Color(0xFF58A6FF), Color(0xFF3FB950), Color(0xFFF85149), Color(0xFFD29922), Color(0xFF010409), Color(0xFF21262D)
)
val LightPal = Pal(
    Color(0xFFF4F6F8), Color(0xFFFFFFFF), Color(0xFFD0D7DE), Color(0xFF1F2328), Color(0xFF59636E),
    Color(0xFF0969DA), Color(0xFF1A7F37), Color(0xFFCF222E), Color(0xFF9A6700), Color(0xFFF6F8FA), Color(0xFFEAEEF2)
)
val LocalPal = staticCompositionLocalOf { DarkPal }
private val GreenBtn = Color(0xFF238636)
private val Mono = FontFamily.Monospace

private fun Tone.color(p: Pal) = when (this) {
    Tone.BAD -> p.bad
    Tone.OK -> p.ok
    Tone.WARN -> p.warn
}

private fun LogKind.color(p: Pal) = when (this) {
    LogKind.N -> p.mut
    LogKind.I -> p.acc
    LogKind.S -> p.ok
    LogKind.W -> p.warn
    LogKind.E -> p.bad
}

// ---------- root ----------
@Composable
fun NvApp(vm: MainViewModel) {
    val dark = vm.darkOverride ?: isSystemInDarkTheme()
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark)
        darkColorScheme(primary = p.acc, background = p.bg, surface = p.card, onSurface = p.txt, onBackground = p.txt)
    else
        lightColorScheme(primary = p.acc, background = p.bg, surface = p.card, onSurface = p.txt, onBackground = p.txt)

    CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(colorScheme = scheme) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.bg)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                NvCard(vm, dark)
            }
        }
    }
}

@Composable
private fun NvCard(vm: MainViewModel, dark: Boolean) {
    val p = LocalPal.current
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(12.dp)

    Column(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.line, shape)
            .padding(20.dp)
    ) {
        // header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NV-FIXER PRO v3.0", color = p.acc, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "SIMULATOR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GreenBtn)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = p.line)

        // status panel
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(p.bg)
                .border(1.dp, p.line, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusRow("USB Link:", vm.usb, vm.usbTone)
            StatusRow("Baseband:", vm.baseband, vm.bbTone)
            StatusRow("IMEI 1:", vm.shown1, vm.imeiTone)
            StatusRow("IMEI 2:", vm.shown2, vm.imeiTone)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Network:", color = p.txt, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(vm.network, color = vm.netTone.color(p), fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    SignalBars(vm.signal)
                }
            }
        }

        Label("Real device info (read-only)")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(p.bg)
                .border(1.dp, p.line, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            vm.info.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$k:", color = p.txt, fontSize = 13.sp)
                    Text(
                        v, color = p.acc, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }
            }
            SmallBtn("🔄 Refresh info", Modifier.fillMaxWidth()) { vm.refreshInfo() }
        }

        Label("Device Profile")
        Picker(PROFILES.map { it.name }, vm.profileIdx) { vm.onProfile(it) }

        Label("Target Chipset Mode")
        Picker(CHIPS.map { it.label }, CHIPS.indexOfFirst { it.key == vm.chip }.coerceAtLeast(0)) {
            vm.chip = CHIPS[it].key
        }

        ImeiField("IMEI 1 (15 digits)", vm.imei1, "e.g. 358240051111110", vm::onImei1, vm::randomImei1)
        ImeiField("IMEI 2 (optional, 15 digits)", vm.imei2, "e.g. 358240051111128", vm::onImei2, vm::randomImei2)

        Spacer(Modifier.height(10.dp))
        OptionRow("Backup NVRAM first", vm.optBackup) { vm.optBackup = it }
        OptionRow("Sound", vm.optSound) { vm.optSound = it }
        OptionRow("Chaos mode (random errors)", vm.optChaos) { vm.optChaos = it }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = vm::start,
            enabled = !vm.running,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenBtn, contentColor = Color.White)
        ) { Text("⚡ Write IMEI & Fix Baseband", fontWeight = FontWeight.Bold, fontSize = 13.sp) }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallBtn("🔌 Connect", Modifier.weight(1f)) { vm.connect() }
            SmallBtn("📋 Copy log", Modifier.weight(1f)) {
                clipboard.setText(AnnotatedString(vm.logText()))
                vm.addLog("[SYS] Log copied.", LogKind.S)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallBtn("📤 Share log", Modifier.weight(1f)) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "NV-FIXER PRO log")
                    putExtra(Intent.EXTRA_TEXT, vm.logText())
                }
                try {
                    ctx.startActivity(Intent.createChooser(send, "Share log"))
                } catch (e: Exception) {
                    vm.addLog("[SYS] No app available to share the log.", LogKind.W)
                }
            }
            SmallBtn("🌓 Theme", Modifier.weight(1f)) { vm.toggleTheme(dark) }
        }
        Spacer(Modifier.height(8.dp))
        SmallBtn("Reset", Modifier.fillMaxWidth()) { vm.reset() }

        // progress
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { vm.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = p.acc,
            trackColor = p.bg
        )

        // console
        val listState = rememberLazyListState()
        LaunchedEffect(vm.log.size) {
            if (vm.log.isNotEmpty()) listState.animateScrollToItem(vm.log.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(p.con)
                .border(1.dp, p.line, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            items(vm.log) { l ->
                Text(l.text, color = l.kind.color(p), fontSize = 12.sp, fontFamily = Mono, lineHeight = 18.sp)
            }
        }

        // history
        Text("Repair history", color = p.mut, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 90.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (vm.history.isEmpty()) {
                Text("No repairs yet.", color = p.txt, fontSize = 12.sp, fontFamily = Mono)
            } else {
                vm.history.reversed().forEach {
                    Text(it, color = p.txt, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }

        Text(
            "IMEI writing and baseband repair are simulated only. USB scan, device info and log sharing are real and read-only.",
            color = p.mut, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}

// ---------- small components ----------
@Composable
private fun Label(t: String) {
    Text(t, color = LocalPal.current.mut, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun StatusRow(label: String, value: String, tone: Tone) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = p.txt, fontSize = 13.sp)
        Text(value, color = tone.color(p), fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SignalBars(level: Int) {
    val p = LocalPal.current
    val heights = listOf(4, 7, 10, 14)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < level) p.ok else p.line)
            )
        }
    }
}

@Composable
private fun Picker(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, p.line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = p.bg, contentColor = p.txt)
        ) {
            Text(options[selected], modifier = Modifier.weight(1f), fontSize = 14.sp, textAlign = TextAlign.Start)
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); open = false })
            }
        }
    }
}

@Composable
private fun ImeiField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onRandom: () -> Unit
) {
    val p = LocalPal.current
    Label(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = p.txt),
            placeholder = { Text(placeholder, fontSize = 12.sp, color = p.mut) },
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.acc,
                unfocusedBorderColor = p.line,
                focusedContainerColor = p.bg,
                unfocusedContainerColor = p.bg,
                cursorColor = p.acc
            )
        )
        Spacer(Modifier.width(6.dp))
        OutlinedButton(
            onClick = onRandom,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, p.line),
            contentPadding = PaddingValues(horizontal = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = p.btn, contentColor = p.txt)
        ) { Text("🎲 Random", fontSize = 12.sp) }
    }
    val h = imeiHint(value)
    Text(
        h?.first ?: "",
        color = h?.second?.color(p) ?: p.mut,
        fontSize = 11.sp,
        fontFamily = Mono,
        modifier = Modifier
            .padding(top = 4.dp)
            .heightIn(min = 14.dp)
    )
}

@Composable
private fun OptionRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = p.acc, uncheckedColor = p.mut)
        )
        Text(text, color = p.mut, fontSize = 12.sp)
    }
}

@Composable
private fun SmallBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, p.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = p.btn, contentColor = p.txt)
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
