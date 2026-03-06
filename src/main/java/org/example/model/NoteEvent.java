package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Track;

/**
 * A resolved note event: the result of pairing a NOTE_ON with its corresponding
 * NOTE_OFF (or NOTE_ON velocity=0) in a single MIDI track.
 */
@Data
@AllArgsConstructor
public class NoteEvent {

    /** MIDI channel (0–15). */
    private final int channel;

    /** MIDI pitch (0–127). */
    private final int pitch;

    /** Absolute tick at which this note starts (NOTE_ON). */
    private long startTick;

    /**
     * Duration in ticks (distance to the NOTE_OFF event).
     * This is the value we may need to shorten when overlaps are detected.
     */
    private long durationTicks;

    /**
     * The Track that owns the NOTE_OFF event. Stored as a direct reference so
     * we can remove/re-add the event without worrying about index shifts.
     */
    private Track track;

    /**
     * Direct reference to the NOTE_OFF {@link MidiEvent}.
     * The fixer removes this from the track and re-adds it at the corrected tick.
     */
    private MidiEvent noteOffEvent;

    // -----------------------------------------------------------------------

    /** Convenience: the absolute tick at which this note ends (exclusive). */
    public long endTick() {
        return startTick + durationTicks;
    }
}

