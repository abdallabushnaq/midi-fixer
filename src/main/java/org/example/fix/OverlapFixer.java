package org.example.fix;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.analysis.NoteKey;
import org.example.model.FixResult;
import org.example.model.NoteEvent;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import java.util.List;
import java.util.Map;

/**
 * Detects and fixes overlapping notes within a {@link Sequence}.
 *
 * <h2>Definition of "overlap"</h2>
 * A note at tick T with duration D "overlaps itself" when the same pitch on
 * the same channel starts again at tick T2, and {@code T + D > T2}.
 * In that case we shorten the first note so that its NOTE_OFF fires exactly
 * one tick before the second NOTE_ON: {@code D_fixed = T2 - T - 1}.
 * A minimum duration of 1 tick is always preserved.
 */
@Slf4j
@RequiredArgsConstructor
public class OverlapFixer {

    private final Map<NoteKey, List<NoteEvent>> noteGroups;
    private final boolean                       verbose;

    // -----------------------------------------------------------------------

    /**
     * Moves the NOTE_OFF event for {@code note} to
     * {@code note.startTick + newDuration} within its track.
     * <p>
     * {@link javax.sound.midi.Track} keeps events sorted by tick, so we must
     * remove the old event and add a new one at the corrected tick.
     */
    private void applyFix(NoteEvent note, long newDuration) {
        MidiEvent oldNoteOff = note.getNoteOffEvent();
        long      newTick    = note.getStartTick() + newDuration;

        note.getTrack().remove(oldNoteOff);
        MidiEvent corrected = new MidiEvent(oldNoteOff.getMessage(), newTick);
        note.getTrack().add(corrected);

        // Keep the model in sync
        note.setNoteOffEvent(corrected);
        note.setDurationTicks(newDuration);
    }

    // -----------------------------------------------------------------------

    /**
     * Scans every (track, channel, pitch) group for overlaps, moves the
     * NOTE_OFF events inside the sequence to fix them, and returns a summary.
     *
     * @param sequence the in-memory sequence (used only to count tracks for the summary)
     * @return a {@link FixResult} describing what was found and changed
     */
    public FixResult fix(Sequence sequence) {
        int totalNotes = 0;
        int totalFixed = 0;

        for (Map.Entry<NoteKey, List<NoteEvent>> entry : noteGroups.entrySet()) {
            NoteKey         key   = entry.getKey();
            List<NoteEvent> notes = entry.getValue();   // already sorted by startTick

            totalNotes += notes.size();

            for (int i = 0; i < notes.size() - 1; i++) {
                NoteEvent current = notes.get(i);
                NoteEvent next    = notes.get(i + 1);

                if (current.endTick() > next.getStartTick()) {
                    long newDuration = Math.max(1L, next.getStartTick() - current.getStartTick() - 1);

                    log.info("Fix: track={} ch={} pitch={} startTick={} duration: {} → {} (next note at tick {})",
                            key.trackIndex(), key.channel(), key.pitch(),
                            current.getStartTick(),
                            current.getDurationTicks(), newDuration,
                            next.getStartTick());

                    if (verbose) {
                        System.out.printf(
                                "  [FIX] track=%d ch=%d pitch=%3d  "
                                        + "startTick=%d  duration: %d → %d  (next note at tick %d)%n",
                                key.trackIndex(), key.channel(), key.pitch(),
                                current.getStartTick(),
                                current.getDurationTicks(), newDuration,
                                next.getStartTick());
                    }

                    applyFix(current, newDuration);
                    totalFixed++;
                }
            }
        }

        return FixResult.builder()
                .tracks(sequence.getTracks().length)
                .notesScanned(totalNotes)
                .overlapsFixed(totalFixed)
                .build();
    }
}
