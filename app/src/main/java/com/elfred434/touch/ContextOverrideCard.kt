package com.elfred434.touch

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun ContextOverrideCard(prefs: android.content.SharedPreferences) {
    var enabled by remember { mutableStateOf(prefs.getBoolean(TouchPrefs.CONTEXT_OVERRIDE_ENABLED, false)) }
    var action by remember { mutableStateOf(prefs.getString(TouchPrefs.CONTEXT_DOUBLE_TAP_ACTION, TouchPrefs.MEDIA_NEXT) ?: TouchPrefs.MEDIA_NEXT) }
    var chooseAction by remember { mutableStateOf(false) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Action contextuelle", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text("Si la musique joue, remplacer l’action du double appui", color = TextMuted, fontSize = 11.sp)
            }
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                prefs.edit().putBoolean(TouchPrefs.CONTEXT_OVERRIDE_ENABLED, it).apply()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Mint))
        }
        if (enabled) TextButton(onClick = { chooseAction = true }) {
            Text("Pendant la musique : ${TouchPrefs.labelForAction(action)}  ·  Modifier", color = Mint, fontSize = 12.sp)
        }
    }
    if (chooseAction) AlertDialog(onDismissRequest = { chooseAction = false }, containerColor = Surface1,
        title = { Text("Action quand la musique joue") },
        text = {
            Column {
                listOf(TouchPrefs.MEDIA_NEXT, TouchPrefs.MEDIA_PREVIOUS, TouchPrefs.MEDIA_PLAY_PAUSE).forEach { key ->
                    TextButton(onClick = {
                        action = key
                        prefs.edit().putString(TouchPrefs.CONTEXT_DOUBLE_TAP_ACTION, key).apply()
                        chooseAction = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(TouchPrefs.labelForAction(key), color = TextPrimary) }
                }
            }
        }, confirmButton = { TextButton(onClick = { chooseAction = false }) { Text("Fermer") } })
}
