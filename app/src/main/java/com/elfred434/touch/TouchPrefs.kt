package com.elfred434.touch

import android.content.Context
import android.content.SharedPreferences

object TouchPrefs {
    const val PREFS = "touch_preferences"
    const val ENABLED = "enabled"
    const val ZONE_WIDTH_DP = "zone_width_dp"
    const val ZONE_HEIGHT_DP = "zone_height_dp"
    const val ZONE_X_PERCENT = "zone_x_percent"
    const val ZONE_Y_DP = "zone_y_dp"
    const val HAPTICS = "haptics"
    const val LANDSCAPE = "landscape"

    const val TAP = "tap"
    const val DOUBLE_TAP = "double_tap"
    const val TRIPLE_TAP = "triple_tap"
    const val LONG_PRESS = "long_press"
    const val SWIPE_LEFT = "swipe_left"
    const val SWIPE_RIGHT = "swipe_right"

    const val TORCH = "torch"
    const val SCREENSHOT = "screenshot"
    const val CAMERA = "camera"
    const val OPEN_APP = "open_app"
    const val MEDIA_PLAY_PAUSE = "media_play_pause"
    const val MEDIA_NEXT = "media_next"
    const val MEDIA_PREVIOUS = "media_previous"
    const val RINGER = "ringer"
    const val DND = "dnd"
    const val RECENTS = "recents"
    const val HOME = "home"
    const val BACK = "back"
    const val NOTIFICATIONS = "notifications"
    const val QUICK_SETTINGS = "quick_settings"
    const val VOLUME_UP = "volume_up"
    const val VOLUME_DOWN = "volume_down"
    const val POWER = "power"
    const val NOTES = "notes"
    const val TIMER = "timer"

    data class Gesture(val key: String, val label: String, val symbol: String)
    val gestures = listOf(
        Gesture(TAP, "Appui simple", "●"),
        Gesture(DOUBLE_TAP, "Double appui", "●●"),
        Gesture(TRIPLE_TAP, "Triple appui", "●●●"),
        Gesture(LONG_PRESS, "Appui long", "⏱"),
        Gesture(SWIPE_LEFT, "Glissement gauche", "←"),
        Gesture(SWIPE_RIGHT, "Glissement droite", "→")
    )

    data class Action(val key: String, val label: String, val category: String, val detail: String)
    val actions = listOf(
        Action(TORCH, "Lampe torche", "Téléphone", "Active ou éteint le flash"),
        Action(SCREENSHOT, "Capture d’écran", "Téléphone", "Utilise l’action système Android"),
        Action(CAMERA, "Appareil photo", "Téléphone", "Ouvre l’application caméra"),
        Action(OPEN_APP, "Ouvrir une application", "Applications", "Ouvre l’application choisie"),
        Action(MEDIA_PLAY_PAUSE, "Lecture / pause", "Multimédia", "Contrôle le média actif"),
        Action(MEDIA_NEXT, "Piste suivante", "Multimédia", "Passe au média suivant"),
        Action(MEDIA_PREVIOUS, "Piste précédente", "Multimédia", "Revient au média précédent"),
        Action(RINGER, "Mode sonnerie", "Téléphone", "Alterner sonnerie, vibreur et silencieux"),
        Action(DND, "Ne pas déranger", "Téléphone", "Nécessite l’accès dédié Android"),
        Action(RECENTS, "Applications récentes", "Système", "Affiche les applications récentes"),
        Action(HOME, "Accueil Android", "Système", "Revient à l’écran d’accueil"),
        Action(BACK, "Retour", "Système", "Effectue l’action Retour"),
        Action(NOTIFICATIONS, "Notifications", "Système", "Ouvre le volet des notifications"),
        Action(QUICK_SETTINGS, "Réglages rapides", "Système", "Ouvre les réglages rapides"),
        Action(VOLUME_UP, "Volume +", "Système", "Augmente le volume multimédia"),
        Action(VOLUME_DOWN, "Volume −", "Système", "Baisse le volume multimédia"),
        Action(POWER, "Menu d’alimentation", "Système", "Ouvre le menu d’alimentation"),
        Action(NOTES, "Notes épinglées", "Outils", "Ouvre Touch sur les notes"),
        Action(TIMER, "Minuteur", "Outils", "Ouvre Touch sur le minuteur")
    )

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun defaultAction(gestureKey: String): String = when (gestureKey) {
        TAP -> TORCH
        DOUBLE_TAP -> SCREENSHOT
        TRIPLE_TAP -> MEDIA_PLAY_PAUSE
        LONG_PRESS -> CAMERA
        SWIPE_LEFT -> MEDIA_PREVIOUS
        SWIPE_RIGHT -> MEDIA_NEXT
        else -> ""
    }

    fun actionFor(context: Context, gestureKey: String): String =
        prefs(context).getString("action_$gestureKey", defaultAction(gestureKey)) ?: defaultAction(gestureKey)

    fun labelForAction(key: String): String = actions.firstOrNull { it.key == key }?.label ?: "Aucune action"
}
