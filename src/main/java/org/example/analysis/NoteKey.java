package org.example.analysis;

/**
 * Composite key identifying a unique (track, channel, pitch) group.
 *
 * @param trackIndex MIDI track index
 * @param channel    MIDI channel (0–15)
 * @param pitch      MIDI note number (0–127)
 */
public record NoteKey(int trackIndex, int channel, int pitch) {
}

