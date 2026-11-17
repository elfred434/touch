package com.elfred434.touch

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

private val ToolBg = Color(0xFF111319)
private val ToolSurface = Color(0xFF191C21)
private val ToolSurface2 = Color(0xFF21262D)
private val ToolMint = Color(0xFF00E293)
private val ToolText = Color(0xFFE1E2E9)
private val ToolMuted = Color(0xFF9BA6A0)

@Composable
fun ToolDestination(tool: String, onBack: () -> Unit) {
    when (tool) {
        TouchPrefs.NOTES -> NotesTool(onBack)
        TouchPrefs.TIMER -> FocusTimerTool(onBack)
        TouchPrefs.QUICK_WIDGETS, "widgets" -> QuickWidgetsTool(onBack)
        "contacts" -> QuickDialTool(onBack)
        "media" -> MediaTool(onBack)
        "app_picker" -> AppPickerTool(onBack)
        TouchPrefs.TASKER, "tasker" -> TaskerTool(onBack)
        else -> QuickWidgetsTool(onBack)
    }
}

@Composable
private fun ToolPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(ToolBg).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = ToolText) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ToolText)
                Text(subtitle, fontSize = 12.sp, color = ToolMuted)
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ToolCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(ToolSurface, RoundedCornerShape(18.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

private data class NoteItem(val id: String, val text: String, val done: Boolean)

private fun loadNotes(context: Context): List<NoteItem> {
    val raw = TouchPrefs.prefs(context).getString("notes_json", "[]") ?: "[]"
    return runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            NoteItem(x.optString("id", UUID.randomUUID().toString()), x.optString("text"), x.optBoolean("done"))
        }.filter { it.text.isNotBlank() }
    }.getOrDefault(emptyList())
}

private fun saveNotes(context: Context, notes: List<NoteItem>) {
    val a = JSONArray()
    notes.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
    TouchPrefs.prefs(context).edit().putString("notes_json", a.toString()).apply()
}

@Composable
private fun NotesTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val notes = remember { mutableStateListOf<NoteItem>().apply { addAll(loadNotes(context)) } }
    var draft by remember { mutableStateOf("") }
    ToolPage("Notes épinglées", "Pense-bêtes et checklists sur cet appareil", onBack) {
        ToolCard {
            Text("Ajouter un élément", color = ToolText, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex. Acheter du pain…") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ToolMint, cursorColor = ToolMint))
            Button(onClick = {
                if (draft.isNotBlank()) {
                    notes.add(0, NoteItem(UUID.randomUUID().toString(), draft.trim(), false))
                    saveNotes(context, notes); draft = ""
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolMint, contentColor = Color(0xFF003921))) { Text("Ajouter à la liste") }
        }
        if (notes.isEmpty()) ToolCard { Text("Ta liste est vide. Les notes restent enregistrées localement sur ce téléphone.", color = ToolMuted, fontSize = 13.sp) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
            items(notes, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().background(ToolSurface, RoundedCornerShape(15.dp)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.done, onCheckedChange = { checked ->
                        val index = notes.indexOfFirst { it.id == item.id }
                        if (index >= 0) { notes[index] = item.copy(done = checked); saveNotes(context, notes) }
                    }, colors = CheckboxDefaults.colors(checkedColor = ToolMint))
                    Text(item.text, Modifier.weight(1f), color = if (item.done) ToolMuted else ToolText, fontSize = 14.sp)
                    IconButton(onClick = { notes.removeAll { it.id == item.id }; saveNotes(context, notes) }) { Icon(Icons.Default.DeleteOutline, "Supprimer", tint = ToolMuted) }
                }
            }
        }
    }
}

@Composable
private fun FocusTimerTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TouchPrefs.prefs(context) }
    var isStopwatch by remember { mutableStateOf(false) }
    var minutes by remember { mutableIntStateOf(25) }
    val savedTargetAt = remember { prefs.getLong("timer_target_at", 0L) }
    var targetAt by remember { mutableLongStateOf(savedTargetAt) }
    var running by remember { mutableStateOf(savedTargetAt > System.currentTimeMillis()) }
    var secondsLeft by remember { mutableIntStateOf(if (savedTargetAt > System.currentTimeMillis()) ((savedTargetAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0) else 25 * 60) }
    var elapsed by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(targetAt, running, isStopwatch) {
        while (running) {
            delay(1000)
            if (isStopwatch) elapsed += 1
            else {
                val left = ((targetAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                secondsLeft = left
                if (left == 0) { running = false; finished = true; prefs.edit().remove("timer_target_at").apply() }
            }
        }
    }
    ToolPage("Focus & chronomètre", "Minuteur local sans compte", onBack) {
        Row(Modifier.fillMaxWidth().background(ToolSurface, RoundedCornerShape(15.dp)).padding(4.dp)) {
            FilterChip(selected = !isStopwatch, onClick = { running = false; isStopwatch = false; secondsLeft = minutes * 60 }, label = { Text("Minuteur") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = isStopwatch, onClick = { running = false; isStopwatch = true }, label = { Text("Chronomètre") })
        }
        ToolCard {
            Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                Text(formatClock(if (isStopwatch) elapsed else secondsLeft), color = ToolMint, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
            if (!isStopwatch && !running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Durée", Modifier.weight(1f), color = ToolText)
                    Text("$minutes min", color = ToolMint, fontWeight = FontWeight.Bold)
                }
                Slider(value = minutes.toFloat(), onValueChange = { minutes = it.toInt().coerceIn(1, 90); secondsLeft = minutes * 60 }, valueRange = 1f..90f, colors = SliderDefaults.colors(thumbColor = ToolMint, activeTrackColor = ToolMint))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 25, 45).forEach { m -> AssistChip(onClick = { minutes = m; secondsLeft = m * 60 }, label = { Text("${m}m") }) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (running) running = false else {
                        if (!isStopwatch) { targetAt = System.currentTimeMillis() + secondsLeft * 1000L; prefs.edit().putLong("timer_target_at", targetAt).apply() }
                        running = true
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ToolMint, contentColor = Color(0xFF003921))) { Text(if (running) "Pause" else "Démarrer") }
                OutlinedButton(onClick = {
                    running = false; elapsed = 0; secondsLeft = minutes * 60; targetAt = 0L; prefs.edit().remove("timer_target_at").apply()
                }, modifier = Modifier.weight(1f)) { Text("Réinitialiser", color = ToolText) }
            }
        }
        if (finished) AlertDialog(onDismissRequest = { finished = false }, containerColor = ToolSurface,
            title = { Text("Temps écoulé", color = ToolText) },
            text = { Text("Ta session de concentration est terminée.", color = ToolMuted) },
            confirmButton = { TextButton(onClick = { finished = false }) { Text("OK") } })
    }
}

private fun formatClock(seconds: Int): String = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

private data class QuickContact(val id: String, val name: String, val number: String)

private fun loadContacts(context: Context): List<QuickContact> = runCatching {
    val array = JSONArray(TouchPrefs.prefs(context).getString("quick_contacts_json", "[]") ?: "[]")
    (0 until array.length()).map { i -> array.getJSONObject(i).let { QuickContact(it.optString("id"), it.optString("name"), it.optString("number")) } }
}.getOrDefault(emptyList())

private fun saveContacts(context: Context, contacts: List<QuickContact>) {
    val array = JSONArray()
    contacts.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("number", it.number)) }
    TouchPrefs.prefs(context).edit().putString("quick_contacts_json", array.toString()).apply()
}

@Composable
private fun QuickDialTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val contacts = remember { mutableStateListOf<QuickContact>().apply { addAll(loadContacts(context)) } }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    ToolPage("Appel rapide", "Contacts favoris enregistrés sur cet appareil", onBack) {
        ToolCard {
            Text("Ajouter un favori", color = ToolText, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nom") }, singleLine = true)
            OutlinedTextField(value = number, onValueChange = { number = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Numéro de téléphone") }, singleLine = true)
            Button(onClick = {
                if (name.isNotBlank() && number.isNotBlank()) {
                    contacts.add(QuickContact(UUID.randomUUID().toString(), name.trim(), number.trim()))
                    saveContacts(context, contacts); name = ""; number = ""
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolMint, contentColor = Color(0xFF003921))) { Text("Enregistrer le contact") }
        }
        if (contacts.isEmpty()) ToolCard { Text("Ajoute tes contacts favoris. Touch ouvrira le composeur Android, sans lancer l’appel automatiquement.", color = ToolMuted, fontSize = 12.sp) }
        contacts.forEach { contact ->
            ToolCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(contact.name, color = ToolText, fontWeight = FontWeight.SemiBold); Text(contact.number, color = ToolMuted, fontSize = 12.sp) }
                    IconButton(onClick = { contacts.removeAll { it.id == contact.id }; saveContacts(context, contacts) }) { Icon(Icons.Default.DeleteOutline, "Supprimer", tint = ToolMuted) }
                }
                Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.number, null))) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolMint, contentColor = Color(0xFF003921))) { Text("Composer le numéro") }
            }
        }
    }
}

@Composable
private fun MediaTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    fun sendKey(code: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, code, 0))
    }
    ToolPage("Contrôles multimédias", "Commandes pour la session audio active", onBack) {
        ToolCard {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("♫", color = ToolMint, fontSize = 54.sp) }
            Text("La piste en cours est contrôlée par Android. Les informations de pochette dépendent du lecteur utilisé.", color = ToolMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sendKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { Icon(Icons.Default.SkipPrevious, "Précédent", tint = ToolText) }
                IconButton(onClick = { sendKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, modifier = Modifier.size(58.dp).background(ToolMint, CircleShape)) { Icon(Icons.Default.PlayArrow, "Lecture ou pause", tint = Color(0xFF003921)) }
                IconButton(onClick = { sendKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) }) { Icon(Icons.Default.SkipNext, "Suivant", tint = ToolText) }
            }
        }
        ToolCard {
            Text("Volume multimédia", color = ToolText, fontWeight = FontWeight.SemiBold)
            Slider(value = volume, onValueChange = { volume = it; audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..max.toFloat(), colors = SliderDefaults.colors(thumbColor = ToolMint, activeTrackColor = ToolMint))
            Text("${volume.toInt()} / $max", color = ToolMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuickWidgetsTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var brightness by remember { mutableFloatStateOf(runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat() }.getOrDefault(128f)) }
    ToolPage("Panneau rapide", "Média, luminosité et volume", onBack) {
        ToolCard {
            Text("Luminosité", color = ToolText, fontWeight = FontWeight.SemiBold)
            Slider(value = brightness, onValueChange = { value ->
                brightness = value
                if (Settings.System.canWrite(context)) Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.toInt())
            }, valueRange = 20f..255f, colors = SliderDefaults.colors(thumbColor = ToolMint, activeTrackColor = ToolMint))
            if (!Settings.System.canWrite(context)) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Autoriser le réglage de luminosité", color = ToolMint) }
        }
        ToolCard {
            Text("Volume multimédia", color = ToolText, fontWeight = FontWeight.SemiBold)
            Slider(value = volume, onValueChange = { volume = it; audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVolume.toFloat(), colors = SliderDefaults.colors(thumbColor = ToolMint, activeTrackColor = ToolMint))
        }
        ToolCard {
            Text("Accès à tes outils", color = ToolText, fontWeight = FontWeight.SemiBold)
            ToolLaunchRow("♫", "Contrôles multimédias") { context.startActivity(Intent(context, MainActivity::class.java).putExtra("open_tool", "media").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ToolLaunchRow("◷", "Minuteur de concentration") { context.startActivity(Intent(context, MainActivity::class.java).putExtra("open_tool", TouchPrefs.TIMER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ToolLaunchRow("☷", "Notes et checklist") { context.startActivity(Intent(context, MainActivity::class.java).putExtra("open_tool", TouchPrefs.NOTES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
private fun ToolLaunchRow(icon: String, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).background(ToolSurface2, RoundedCornerShape(12.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = ToolMint, fontSize = 18.sp); Spacer(Modifier.width(12.dp)); Text(label, color = ToolText, fontSize = 13.sp)
    }
}

private data class AppChoice(val label: String, val packageName: String)

@Composable
private fun AppPickerTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        context.packageManager.getInstalledApplications(0).mapNotNull { info ->
            val intent = context.packageManager.getLaunchIntentForPackage(info.packageName) ?: return@mapNotNull null
            AppChoice(info.loadLabel(context.packageManager).toString(), info.packageName)
        }.filter { it.packageName != context.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
    var query by remember { mutableStateOf("") }
    ToolPage("Choisir une application", "Définit le raccourci « Ouvrir une application »", onBack) {
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Rechercher") }, singleLine = true)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(apps.filter { it.label.contains(query, true) }) { app ->
                Row(Modifier.fillMaxWidth().background(ToolSurface, RoundedCornerShape(13.dp)).clickable {
                    TouchPrefs.prefs(context).edit().putString(TouchPrefs.APP_PACKAGE, app.packageName).putString("app_label", app.label).apply(); onBack()
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, Modifier.weight(1f), color = ToolText, fontWeight = FontWeight.Medium)
                    Text("Choisir", color = ToolMint, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TaskerTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TouchPrefs.prefs(context) }
    var name by remember { mutableStateOf(prefs.getString("tasker_task_name", "") ?: "") }
    ToolPage("Automatisation Tasker", "Déclenche une tâche Tasker par son nom", onBack) {
        ToolCard {
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nom exact de la tâche") }, singleLine = true)
            Button(onClick = { prefs.edit().putString("tasker_task_name", name.trim()).apply() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolMint, contentColor = Color(0xFF003921))) { Text("Enregistrer") }
            Text("Dans Tasker, autorise la réception des intents externes. La disponibilité dépend de la version et des réglages de Tasker.", color = ToolMuted, fontSize = 12.sp)
        }
        ToolCard {
            Button(onClick = { TouchAccessibilityService.currentAction(context, TouchPrefs.TASKER) }, modifier = Modifier.fillMaxWidth()) { Text("Tester le déclenchement") }
        }
    }
}
