package com.elfred434.touch

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val Bg = Color(0xFF111319)
internal val Surface1 = Color(0xFF191C21)
internal val Surface2 = Color(0xFF21262D)
internal val Mint = Color(0xFF00E293)
internal val TextPrimary = Color(0xFFE1E2E9)
internal val TextMuted = Color(0xFF9BA6A0)

class MainActivity : ComponentActivity() {
    private val openedTool = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedTool.value = intent?.getStringExtra("open_tool")
        setContent { TouchTheme { TouchRoot(openedTool.value) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedTool.value = intent.getStringExtra("open_tool")
    }

    override fun onResume() {
        super.onResume()
        TouchAccessibilityService.refreshZone()
    }
}

@Composable
private fun TouchTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Mint, onPrimary = Color(0xFF003921), background = Bg, onBackground = TextPrimary,
        surface = Surface1, onSurface = TextPrimary, surfaceVariant = Surface2, onSurfaceVariant = TextMuted,
        secondary = Color(0xFF4EDEA3), error = Color(0xFFFFB4AB)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun TouchRoot(initialTool: String? = null) {
    val context = LocalContext.current
    val prefs = remember { TouchPrefs.prefs(context) }
    var tab by remember { mutableStateOf(if (initialTool != null) 2 else 0) }
    var toolPage by remember { mutableStateOf(initialTool) }
    LaunchedEffect(initialTool) {
        if (initialTool != null) { toolPage = initialTool; tab = 2 }
    }
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var updateChecked by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!updateChecked) {
            updateChecked = true
            val found = withContext(Dispatchers.IO) { runCatching { UpdateManager.check() }.getOrNull() }
            if (found != null) { update = found; showUpdate = true }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Bg, contentColor = TextMuted) {
                val labels = listOf("Accueil", "Gestes", "Actions", "Calibrage")
                val icons = listOf(Icons.Default.Home, Icons.Default.TouchApp, Icons.Default.GridView, Icons.Default.CenterFocusStrong)
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { if (tab == index && index == 2) toolPage = null; tab = index; if (index != 2) toolPage = null }, icon = { Icon(icons[index], null) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint,
                            indicatorColor = Color(0xFF1C362D), unselectedIconColor = TextMuted, unselectedTextColor = TextMuted))
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(prefs, { tab = 1 }, { showNotice = true }, { tool -> toolPage = tool; tab = 2 })
                1 -> GesturesScreen(context, prefs)
                2 -> if (toolPage != null) ToolDestination(toolPage!!) { toolPage = null } else ActionsScreen(context) { tool -> toolPage = tool }
                else -> CalibrationScreen(context, prefs)
            }
        }
    }
    if (showUpdate && update != null) {
        val found = update!!
        AlertDialog(onDismissRequest = { showUpdate = false }, containerColor = Surface1,
            title = { Text("Mise à jour disponible") },
            text = { Text("Touch ${found.versionName} est disponible. Le lien APK s’ouvrira dans ton navigateur ; l’installation se fera ensuite par le processus normal d’Android.", color = TextMuted) },
            confirmButton = { TextButton(onClick = { showUpdate = false; UpdateManager.openDownloadPage(context, found) }) { Text("Ouvrir le téléchargement") } },
            dismissButton = { TextButton(onClick = { showUpdate = false }) { Text("Plus tard") } })
    }
    if (showNotice) AlertDialog(onDismissRequest = { showNotice = false }, containerColor = Surface1,
        title = { Text("Confidentialité") },
        text = { Text("Les gestes sont détectés par le service d’accessibilité Android ; Touch ne lit pas le contenu des fenêtres. Notes et contacts favoris restent sur cet appareil. Internet sert à vérifier les versions publiées dans le dépôt GitHub public.", color = TextMuted) },
        confirmButton = { TextButton(onClick = { showNotice = false }) { Text("Compris") } })
}

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(13.dp), content = { item { Column(content = content) } })
}

@Composable
private fun Header(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = TextMuted)
        }
        if (action != null) action()
    }
}

@Composable
internal fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (trailing != null) TextButton(onClick = { onTrailing?.invoke() }, contentPadding = PaddingValues(0.dp)) { Text(trailing, color = Mint, fontSize = 12.sp) }
    }
}

@Composable
private fun HomeScreen(prefs: android.content.SharedPreferences, onGestures: () -> Unit, onPrivacy: () -> Unit, onOpenTool: (String) -> Unit) {
    var enabled by remember { mutableStateOf(prefs.getBoolean(TouchPrefs.ENABLED, true)) }
    val context = LocalContext.current
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    PageColumn {
        Header("Touch", "Raccourcis par gestes")
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(if (enabled && isAccessibilityEnabled(context)) Mint else Color(0xFFF0A63C), CircleShape))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isAccessibilityEnabled(context) && enabled) "Service d’arrière-plan actif" else "Configuration requise", fontWeight = FontWeight.SemiBold)
                    Text(if (isAccessibilityEnabled(context) && enabled) "Gestes prêts à fonctionner" else "Active le service d’accessibilité Android", fontSize = 12.sp, color = TextMuted)
                }
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it; prefs.edit().putBoolean(TouchPrefs.ENABLED, it).apply(); TouchAccessibilityService.refreshZone()
                }, colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Mint))
            }
            if (!isAccessibilityEnabled(context)) Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF003921)), modifier = Modifier.fillMaxWidth()) { Text("Ouvrir les autorisations") }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                OutlinedButton(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text("Autoriser le flash pour les gestes") }
            }
        }
        CardBox {
            SectionTitle("Simulateur de poinçon", "Rayon réglable")
            Box(Modifier.fillMaxWidth().height(185.dp).background(Color(0xFF0B0E13), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(68.dp).background(Color(0xFF10251E), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(44.dp).background(Color(0xFF0A1210), CircleShape), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(25.dp).background(Color(0xFF183A2D), CircleShape), contentAlignment = Alignment.Center) { Box(Modifier.size(11.dp).background(Mint, CircleShape)) }
                        }
                    }
                    Text("Zone tactile configurée", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Appuie sur la zone pour déclencher ton geste", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
        SectionTitle("Raccourcis favoris", "Personnaliser", onGestures)
        val favorite = listOf(TouchPrefs.TAP, TouchPrefs.DOUBLE_TAP, TouchPrefs.LONG_PRESS, TouchPrefs.SWIPE_RIGHT)
        favorite.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val gesture = TouchPrefs.gestures.first { it.key == key }
                    val action = TouchPrefs.labelForAction(TouchPrefs.actionFor(context, key))
                    CardBox(Modifier.weight(1f)) {
                        Text(gesture.symbol, color = Mint, fontSize = 18.sp)
                        Text(action, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(gesture.label, color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        CardBox {
            SectionTitle("Boîte d’outils rapides")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ToolTile(Modifier.weight(1f), "◷", "Minuteur", "Focus") { onOpenTool(TouchPrefs.TIMER) }
                ToolTile(Modifier.weight(1f), "♫", "Média", "Lecture") { onOpenTool("media") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ToolTile(Modifier.weight(1f), "☷", "Notes", "Épinglées") { onOpenTool(TouchPrefs.NOTES) }
                ToolTile(Modifier.weight(1f), "☎", "Contacts", "Favoris") { onOpenTool("contacts") }
            }
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = Mint)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text("Confidentialité", fontWeight = FontWeight.SemiBold); Text("Le service ne lit pas le contenu des écrans.", color = TextMuted, fontSize = 11.sp) }
                TextButton(onClick = onPrivacy) { Text("Détails") }
            }
        }
    }
}

@Composable
private fun ToolTile(modifier: Modifier, icon: String, title: String, sub: String, onClick: () -> Unit) {
    Column(modifier.background(Surface2, RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(icon, color = Mint, fontSize = 18.sp); Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(sub, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun GesturesScreen(context: Context, prefs: android.content.SharedPreferences) {
    var selectedGesture by remember { mutableStateOf<TouchPrefs.Gesture?>(null) }
    val actionsByCategory = TouchPrefs.actions.groupBy { it.category }
    PageColumn {
        Header("Gestes", "Chaque geste déclenche une action choisie")
        ContextOverrideCard(prefs)
        TouchPrefs.gestures.forEach { gesture ->
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Color(0xFF10251E), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(gesture.symbol, color = Mint, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(gesture.label, fontWeight = FontWeight.SemiBold)
                        Text(TouchPrefs.labelForAction(TouchPrefs.actionFor(context, gesture.key)), fontSize = 12.sp, color = TextMuted)
                    }
                    TextButton(onClick = { selectedGesture = gesture }) { Text("Modifier", color = Mint) }
                }
            }
        }
        Text("Les gestes peuvent varier selon l’appareil. Les commandes système nécessitent que le service Touch soit activé.", color = TextMuted, fontSize = 12.sp)
    }
    selectedGesture?.let { gesture ->
        var currentCategory by remember { mutableStateOf("Téléphone") }
        AlertDialog(onDismissRequest = { selectedGesture = null }, containerColor = Surface1,
            title = { Text("Action — ${gesture.label}") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        actionsByCategory.keys.forEach { category -> TextButton(onClick = { currentCategory = category }) { Text(category, fontSize = 10.sp, color = if (currentCategory == category) Mint else TextMuted) } }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(actionsByCategory[currentCategory].orEmpty().size) { index ->
                            val action = actionsByCategory[currentCategory]!![index]
                            TextButton(onClick = {
                                prefs.edit().putString("action_${gesture.key}", action.key).apply()
                                selectedGesture = null
                                TouchAccessibilityService.refreshZone()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) { Text(action.label, color = TextPrimary); Text(action.detail, color = TextMuted, fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selectedGesture = null }) { Text("Fermer") } })
    }
}

@Composable
private fun ActionsScreen(context: Context, onOpenTool: (String) -> Unit) {
    val grouped = TouchPrefs.actions.groupBy { it.category }
    var pendingTorch by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingTorch) TouchAccessibilityService.currentAction(context, TouchPrefs.TORCH)
        pendingTorch = false
    }
    PageColumn {
        Header("Actions", "Raccourcis disponibles")
        grouped.forEach { (category, actions) ->
            SectionTitle(category)
            actions.forEach { action ->
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconFor(action.key), null, tint = Mint)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(action.label, fontWeight = FontWeight.SemiBold); Text(action.detail, color = TextMuted, fontSize = 11.sp) }
                        TextButton(onClick = {
                            when (action.key) {
                                TouchPrefs.NOTES, TouchPrefs.TIMER, TouchPrefs.QUICK_WIDGETS -> onOpenTool(action.key)
                                TouchPrefs.OPEN_APP -> onOpenTool("app_picker")
                                TouchPrefs.TASKER -> onOpenTool("tasker")
                                TouchPrefs.TORCH -> if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    pendingTorch = true
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                } else TouchAccessibilityService.currentAction(context, action.key)
                                else -> TouchAccessibilityService.currentAction(context, action.key)
                            }
                        }) { Text(if (action.key == TouchPrefs.OPEN_APP) "Choisir" else if (action.key in listOf(TouchPrefs.NOTES, TouchPrefs.TIMER, TouchPrefs.QUICK_WIDGETS, TouchPrefs.TASKER)) "Ouvrir" else "Tester", color = Mint, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

private fun iconFor(key: String): ImageVector = when (key) {
    TouchPrefs.TORCH -> Icons.Default.FlashOn
    TouchPrefs.SCREENSHOT -> Icons.Default.ScreenshotMonitor
    TouchPrefs.CAMERA -> Icons.Default.CameraAlt
    TouchPrefs.MEDIA_NEXT, TouchPrefs.MEDIA_PREVIOUS, TouchPrefs.MEDIA_PLAY_PAUSE -> Icons.Default.MusicNote
    TouchPrefs.NOTES -> Icons.Default.StickyNote2
    TouchPrefs.TIMER -> Icons.Default.Timer
    else -> Icons.Default.Bolt
}

@Composable
private fun CalibrationScreen(context: Context, prefs: android.content.SharedPreferences) {
    var x by remember { mutableFloatStateOf(prefs.getFloat(TouchPrefs.ZONE_X_PERCENT, 0.5f)) }
    var y by remember { mutableFloatStateOf(prefs.getInt(TouchPrefs.ZONE_Y_DP, 0).toFloat()) }
    var width by remember { mutableFloatStateOf(prefs.getInt(TouchPrefs.ZONE_WIDTH_DP, 82).toFloat()) }
    var height by remember { mutableFloatStateOf(prefs.getInt(TouchPrefs.ZONE_HEIGHT_DP, 50).toFloat()) }
    var landscape by remember { mutableStateOf(prefs.getBoolean(TouchPrefs.LANDSCAPE, false)) }
    PageColumn {
        Header("Calibrage", "Ajuste la zone tactile à ton téléphone")
        CardBox {
            Box(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF0B0E13), RoundedCornerShape(18.dp)), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.padding(top = 12.dp).width((width / 2).dp).height((height / 2).dp).background(Mint.copy(alpha = .20f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(16.dp).background(Mint, CircleShape))
                }
                Text("Aperçu approximatif de la découpe", Modifier.align(Alignment.BottomCenter).padding(10.dp), color = TextMuted, fontSize = 11.sp)
            }
            SliderSetting("Position horizontale", "${(x * 100).toInt()} %", x, 0f..1f) { x = it; saveZone(prefs, x, y, width, height) }
            SliderSetting("Décalage vertical", "${y.toInt()} dp", y, 0f..80f) { y = it; saveZone(prefs, x, y, width, height) }
            SliderSetting("Largeur", "${width.toInt()} dp", width, 48f..160f) { width = it; saveZone(prefs, x, y, width, height) }
            SliderSetting("Hauteur", "${height.toInt()} dp", height, 32f..96f) { height = it; saveZone(prefs, x, y, width, height) }
            Button(onClick = { TouchAccessibilityService.refreshZone() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF003921))) { Text("Appliquer la zone") }
        }
        CardBox {
            Text("Retour haptique", fontWeight = FontWeight.SemiBold)
            Text("Choisis la sensation au déclenchement d’un geste.", fontSize = 12.sp, color = TextMuted)
            var profile by remember { mutableStateOf(prefs.getString(TouchPrefs.HAPTIC_PROFILE, "standard") ?: "standard") }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("off" to "Désactivé", "soft" to "Doux", "standard" to "Standard", "mechanical" to "Mécanique", "deep" to "Profond").forEach { (key, label) ->
                    FilterChip(selected = profile == key, onClick = { profile = key; prefs.edit().putString(TouchPrefs.HAPTIC_PROFILE, key).apply() }, label = { Text(label, fontSize = 10.sp) })
                }
            }
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Autoriser en mode paysage", fontWeight = FontWeight.SemiBold)
                    Text("Désactivé par défaut pour éviter les gestes accidentels en jeu ou vidéo.", fontSize = 11.sp, color = TextMuted)
                }
                Switch(checked = landscape, onCheckedChange = { landscape = it; prefs.edit().putBoolean(TouchPrefs.LANDSCAPE, it).apply(); TouchAccessibilityService.refreshZone() }, colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Mint))
            }
        }
        CardBox {
            Text("Garder Touch actif", fontWeight = FontWeight.SemiBold)
            Text("Certains fabricants suspendent les services en arrière-plan. Si les gestes s’arrêtent, autorise Touch dans les réglages de batterie.", fontSize = 12.sp, color = TextMuted)
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir les réglages de batterie") }
        }
        CardBox {
            Text("L’emplacement réel dépend des marges système et de la découpe de l’écran. Ajuste la zone, puis teste les gestes depuis une autre application.", fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun SliderSetting(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column {
        Row { Text(label, Modifier.weight(1f), fontSize = 13.sp); Text(value, color = Mint, fontSize = 12.sp) }
        Slider(value = current, onValueChange = onValue, valueRange = range, colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
    }
}

private fun saveZone(prefs: android.content.SharedPreferences, x: Float, y: Float, width: Float, height: Float) {
    prefs.edit().putFloat(TouchPrefs.ZONE_X_PERCENT, x).putInt(TouchPrefs.ZONE_Y_DP, y.toInt())
        .putInt(TouchPrefs.ZONE_WIDTH_DP, width.toInt()).putInt(TouchPrefs.ZONE_HEIGHT_DP, height.toInt()).apply()
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, TouchAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
