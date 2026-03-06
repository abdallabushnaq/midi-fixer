package org.example;

import org.example.analysis.MidiAnalyzer;
import org.example.analysis.NoteKey;
import org.example.fix.OverlapFixer;
import org.example.model.FixResult;
import org.example.model.NoteEvent;
import org.junit.jupiter.api.Test;

import javax.sound.midi.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OverlapFixerTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a minimal single-track sequence with one channel and one pitch,
     * using the given pairs of (noteOnTick, noteOffTick).
     */
    private static Sequence buildSequence(int channel, int pitch,
                                          long... tickPairs) throws InvalidMidiDataException {
        Sequence seq   = new Sequence(Sequence.PPQ, 480);
        Track    track = seq.createTrack();

        for (int i = 0; i < tickPairs.length; i += 2) {
            long onTick  = tickPairs[i];
            long offTick = tickPairs[i + 1];

            ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, channel, pitch, 100);
            track.add(new MidiEvent(noteOn, onTick));

            ShortMessage noteOff = new ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0);
            track.add(new MidiEvent(noteOff, offTick));
        }

        return seq;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void differentPitches_noInterference() throws Exception {
        // Pitch 60 and pitch 64 can overlap each other freely — only same-pitch matters
        Sequence seq   = new Sequence(Sequence.PPQ, 480);
        Track    track = seq.createTrack();

        // Pitch 60: 0–1000
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1000));
        // Pitch 64: 480–960 (overlaps with pitch 60 in time, but different pitch)
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 64, 100), 480));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 64, 0), 960));

        MidiAnalyzer analyzer = new MidiAnalyzer(seq);
        OverlapFixer fixer    = new OverlapFixer(analyzer.analyze(), false);
        FixResult    result   = fixer.fix(seq);

        assertEquals(0, result.getOverlapsFixed(), "Different pitches should never be fixed");
    }

    @Test
    void exactAdjacent_nothingFixed() throws Exception {
        // Note 1 ends exactly where Note 2 starts — no overlap
        Sequence seq = buildSequence(0, 60, 0, 480, 480, 960);

        MidiAnalyzer analyzer = new MidiAnalyzer(seq);
        OverlapFixer fixer    = new OverlapFixer(analyzer.analyze(), false);
        FixResult    result   = fixer.fix(seq);

        assertEquals(0, result.getOverlapsFixed(), "Exact adjacency should not be fixed");
    }

    @Test
    void multipleOverlaps_allFixed() throws Exception {
        // Build a sequence where three notes genuinely overlap after FIFO pairing.
        // Raw events: ON@0, ON@400, ON@800, OFF@960, OFF@1200, OFF@2000
        // FIFO pairing (oldest open note first) gives:
        //   note A: start=0    dur=960   (OFF@960  - ON@0)    → endTick=960  > 400 ✓
        //   note B: start=400  dur=800   (OFF@1200 - ON@400)  → endTick=1200 > 800 ✓
        //   note C: start=800  dur=1200  (OFF@2000 - ON@800)  → no successor
        // Sorted: [A(0,960), B(400,800), C(800,1200)]
        //   A.endTick=960  > B.startTick=400 → fix A
        //   B.endTick=1200 > C.startTick=800 → fix B
        Sequence seq   = new Sequence(Sequence.PPQ, 480);
        Track    track = seq.createTrack();

        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 400));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 800));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 960));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1200));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 2000));

        MidiAnalyzer analyzer = new MidiAnalyzer(seq);
        OverlapFixer fixer    = new OverlapFixer(analyzer.analyze(), false);
        FixResult    result   = fixer.fix(seq);

        assertEquals(2, result.getOverlapsFixed(), "Both overlapping notes should be fixed");
        assertEquals(3, result.getNotesScanned());
    }

    @Test
    void noOverlap_nothingFixed() throws Exception {
        // Note 1: ticks 0–480, Note 2: ticks 960–1440 — gap between them
        Sequence seq = buildSequence(0, 60, 0, 480, 960, 1440);

        MidiAnalyzer analyzer = new MidiAnalyzer(seq);
        OverlapFixer fixer    = new OverlapFixer(analyzer.analyze(), false);
        FixResult    result   = fixer.fix(seq);

        assertEquals(0, result.getOverlapsFixed(), "Expected no fixes");
        assertEquals(2, result.getNotesScanned());
    }

    @Test
    void overlap_durationShortened() throws Exception {
        // Note 1: ticks 0–1000 (long), Note 2 starts at tick 480 — overlap!
        Sequence seq = buildSequence(0, 60, 0, 1000, 480, 960);

        MidiAnalyzer                  analyzer = new MidiAnalyzer(seq);
        Map<NoteKey, List<NoteEvent>> groups   = analyzer.analyze();
        OverlapFixer                  fixer    = new OverlapFixer(groups, false);
        FixResult                     result   = fixer.fix(seq);

        assertEquals(1, result.getOverlapsFixed(), "Expected exactly one fix");

        // Verify the model was updated: note 1 should now end at 479 (480 - 1)
        NoteKey         key   = new NoteKey(0, 0, 60);
        List<NoteEvent> notes = groups.get(key);
        assertNotNull(notes);
        NoteEvent first = notes.stream()
                .min(java.util.Comparator.comparingLong(NoteEvent::getStartTick))
                .orElseThrow();
        assertEquals(479L, first.getDurationTicks(),
                "Duration should be shortened to next.startTick - current.startTick - 1 = 479");
    }

    @Test
    void velocity0NoteOn_treatedAsNoteOff() throws Exception {
        // Use NOTE_ON velocity=0 as the note-off signal (valid MIDI)
        Sequence seq   = new Sequence(Sequence.PPQ, 480);
        Track    track = seq.createTrack();

        // NOTE_ON at 0
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        // NOTE_ON velocity=0 at 1000 (acts as NOTE_OFF)
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 0), 1000));
        // Second NOTE_ON at 480 — would overlap if the first note weren't closed
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 480));
        // NOTE_OFF at 960
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 960));

        MidiAnalyzer                  analyzer = new MidiAnalyzer(seq);
        Map<NoteKey, List<NoteEvent>> groups   = analyzer.analyze();
        OverlapFixer                  fixer    = new OverlapFixer(groups, false);
        FixResult                     result   = fixer.fix(seq);

        assertEquals(1, result.getOverlapsFixed());
    }
}
