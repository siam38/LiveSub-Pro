package com.charles.livecaptionn.service

/**
 * Pure functions extracted from [CaptionForegroundService] for testability.
 *
 * These transforms drive the core captioning pipeline: recording incoming
 * transcript lines, pairing them with translations, and rendering the
 * scrolling overlay body.
 */

/** Maximum number of transcript lines kept in the scroll-back buffer. */
internal const val MAX_TRANSCRIPT_LINES = 30

/**
 * Record a new STT result into the transcript line list.
 *
 * Open/closed state is driven solely by [isFinal] (the recognizer's own
 * segment boundary), never by whether a translation has landed yet — a
 * translation landing must not cause the next partial of the same
 * utterance to be appended as a duplicate line.
 *
 * @param lines        Current transcript lines (scroll-back buffer).
 * @param originalText The latest recognized text from STT.
 * @param isFinal      Whether this result is Vosk's finalized segment.
 * @param newLineId    Id to assign if this text starts/becomes a new version.
 * @return The updated line list, trimmed to [MAX_TRANSCRIPT_LINES].
 */
internal fun recordTranscriptResult(
    lines: List<CaptionRuntimeLine>,
    originalText: String,
    isFinal: Boolean,
    newLineId: Long
): List<CaptionRuntimeLine> {
    if (originalText.isBlank()) return lines
    val last = lines.lastOrNull()
    val lastIsOpen = last != null && !last.isFinal

    if (lastIsOpen) {
        if (last!!.originalText == originalText && last.isFinal == isFinal) return lines
        val updatedLine = if (last.originalText == originalText) {
            last.copy(isFinal = isFinal)
        } else {
            CaptionRuntimeLine(id = newLineId, originalText = originalText, isFinal = isFinal)
        }
        return (lines.dropLast(1) + updatedLine).takeLast(MAX_TRANSCRIPT_LINES)
    }

    return (lines + CaptionRuntimeLine(id = newLineId, originalText = originalText, isFinal = isFinal))
        .takeLast(MAX_TRANSCRIPT_LINES)
}

/**
 * Pair a translated text with its matching transcript line by id. If the id
 * is no longer present (the line's text has since evolved past this
 * translation's snapshot), the result is considered stale and discarded
 * (prevents orphan/stale translations from causing double captions).
 *
 * @param lines          Current transcript lines.
 * @param lineId         Id of the line this translation was requested for.
 * @param translatedText The translation result to attach.
 * @return Updated lines with the translation filled in (or unchanged if no match).
 */
internal fun updateTranscriptTranslation(
    lines: List<CaptionRuntimeLine>,
    lineId: Long,
    translatedText: String
): List<CaptionRuntimeLine> {
    val existingIndex = lines.indexOfFirst { it.id == lineId }
    if (existingIndex < 0) return lines
    return lines.mapIndexed { index, line ->
        if (index == existingIndex) line.copy(translatedText = translatedText) else line
    }
}

/** The transcript line whose translation is currently on screen, if any. */
internal fun overlayCueLine(runtime: CaptionRuntimeState): CaptionRuntimeLine? =
    runtime.transcriptLines.lastOrNull { it.translatedText.isNotBlank() }

/**
 * Build the subtitle cue for the overlay. Real subtitles show ONE completed
 * cue at a time and hold it steady: while the next sentence is still being
 * spoken (its line has no translation yet), the previous completed cue stays
 * on screen instead of flickering through fragment translations. Only when a
 * new line's full translation lands does the cue swap, in one step.
 */
internal fun overlayTranscriptText(runtime: CaptionRuntimeState): String {
    val translated = overlayCueLine(runtime)?.translatedText
        ?: runtime.translatedText.takeIf { runtime.transcriptLines.isEmpty() }
        ?: ""
    // Show the current cue in full — never roll or cut it.
    return translated.trim().replace(Regex("\\s+"), " ")
}

/**
 * The original text paired with the on-screen cue (bilingual mode). This is
 * the completed sentence for the displayed translation — not the live
 * partial of the sentence currently being spoken.
 */
internal fun overlayOriginalText(runtime: CaptionRuntimeState): String =
    overlayCueLine(runtime)?.originalText?.trim()?.replace(Regex("\\s+"), " ") ?: ""
