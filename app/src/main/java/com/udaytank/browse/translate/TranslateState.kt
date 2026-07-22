package com.udaytank.browse.translate

/** UI state of full-page translation for the active tab (v6.1). */
sealed interface TranslateState {
    /** Not translating; no bar shown. */
    data object Idle : TranslateState

    /** Text collected; detecting the source language. */
    data object Detecting : TranslateState

    /** Downloading the on-device model for [language] (one time). */
    data class Downloading(val language: String) : TranslateState

    /** Model ready; translating the collected nodes. */
    data object Translating : TranslateState

    /** Page is showing the translation into [targetName]; offers Show original / change target. */
    data class Shown(val source: String, val target: String, val targetName: String) : TranslateState

    /** The page is already in the target language; nothing to do. */
    data class AlreadyTarget(val languageName: String) : TranslateState

    /** Something went wrong (no model, unsupported language, empty page). */
    data class Error(val message: String) : TranslateState
}
