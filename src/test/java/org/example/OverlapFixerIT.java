package org.example;

import org.example.analysis.MidiAnalyzer;
import org.example.analysis.NoteKey;
import org.example.fix.OverlapFixer;
import org.example.io.MidiLoader;
import org.example.model.FixResult;
import org.example.model.NoteEvent;
import org.junit.jupiter.api.Test;

import javax.sound.midi.Sequence;
import java.io.File;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests that operate on real MIDI files from the test-resources
 * {@code references/} directory.
 *
 * <p>These tests are run by the maven-failsafe-plugin during the
 * {@code verify} phase ({@code mvn verify}), separately from the fast
 * unit tests which run during the {@code test} phase ({@code mvn test}).
 *
 * <p>Each test writes its fixed output to {@code target/it-output/} so the
 * result can be inspected manually (e.g. opened in a DAW).
 */
class OverlapFixerIT {

    /**
     * Asserts that every note with a successor was trimmed to
     * {@code nextStart - start - 1}, and the final note is untouched at 5760 ticks.
     */
    private static void assertTrackFixed(Map<NoteKey, List<NoteEvent>> groups,
                                         int trackIndex, int channel, int pitch) {
        List<NoteEvent> notes = groups.get(new NoteKey(trackIndex, channel, pitch));
        assertNotNull(notes, "No notes for track=" + trackIndex + " ch=" + channel + " pitch=" + pitch);
        assertEquals(8, notes.size(), "Expected 8 notes for track=" + trackIndex + " pitch=" + pitch);

        List<NoteEvent> sorted = notes.stream()
                .sorted(Comparator.comparingLong(NoteEvent::getStartTick))
                .toList();

        long[] expectedStartTicks = {0, 480, 960, 1440, 5760, 6240, 6720, 7200};
        for (int i = 0; i < 8; i++) {
            assertEquals(expectedStartTicks[i], sorted.get(i).getStartTick(),
                    "Unexpected startTick at index " + i + " for track=" + trackIndex + " pitch=" + pitch);
        }

        // Notes 0–6: each must be trimmed to nextStart - start - 1
        for (int i = 0; i < 7; i++) {
            NoteEvent note        = sorted.get(i);
            NoteEvent next        = sorted.get(i + 1);
            long      expectedDur = next.getStartTick() - note.getStartTick() - 1;
            assertEquals(expectedDur, note.getDurationTicks(),
                    "Note at tick " + note.getStartTick() +
                            " (track=" + trackIndex + " pitch=" + pitch + ") should have dur=" + expectedDur);
            assertEquals(note.getStartTick() + expectedDur, note.getNoteOffEvent().getTick(),
                    "NOTE_OFF tick mismatch for note at tick " + note.getStartTick());
        }

        // Note 7: last note, no successor — untouched at original FIFO duration (5760)
        NoteEvent last = sorted.get(7);
        assertEquals(7200L, last.getStartTick());
        assertEquals(5760L, last.getDurationTicks(),
                "Last note (tick 7200) should be untouched at dur=5760");
    }

    // -----------------------------------------------------------------------

    /**
     * Diagnostic — prints every resolved note for track 1 of midi-fixer-test-2.mid
     * exactly as {@link MidiAnalyzer} produces them (FIFO pairing), for comparison
     * with what Cubase shows.  This test never fails.
     */
    @Test
    void diagnosticDumpTrack1_midiFile2() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("references/midi-fixer-test-2.mid");
        assertNotNull(resource);

        Sequence seq = MidiLoader.load(new File(resource.toURI()));
        int      ppq = seq.getResolution();

        MidiAnalyzer                  analyzer = new MidiAnalyzer(seq);
        Map<NoteKey, List<NoteEvent>> groups   = analyzer.analyze();

        NoteKey         key   = new NoteKey(1, 0, 60);
        List<NoteEvent> notes = groups.get(key);
        assertNotNull(notes, "No notes for track=1 ch=0 pitch=60");

        List<NoteEvent> sorted = notes.stream()
                .sorted(Comparator.comparingLong(NoteEvent::getStartTick))
                .toList();

        System.out.println();
        System.out.println("=== MidiAnalyzer view of track 1  (PPQ=" + ppq + ") ===");
        System.out.printf("  %-5s  %-10s  %-10s  %-10s  %-22s  %-18s  %s%n",
                "#", "startTick", "dur", "endTick", "noteOff event tick",
                "bar.beat (4/4)", "overlap? (endTick > nextStart)");
        System.out.println("  " + "-".repeat(110));

        for (int i = 0; i < sorted.size(); i++) {
            NoteEvent note             = sorted.get(i);
            NoteEvent next             = i < sorted.size() - 1 ? sorted.get(i + 1) : null;
            long      noteOffEventTick = note.getNoteOffEvent().getTick();
            long      beatsTotal       = note.getStartTick() / ppq;
            long      bar              = beatsTotal / 4 + 1;
            long      beat             = beatsTotal % 4 + 1;
            long      subTick          = note.getStartTick() % ppq;

            String flag = "";
            if (next != null) {
                if (note.endTick() > next.getStartTick()) flag = "⚠ OVERLAP  endTick=" + note.endTick() + " > nextStart=" + next.getStartTick();
                else if (note.endTick() == next.getStartTick()) flag = "· butted   endTick=" + note.endTick() + " == nextStart";
                else flag = "  gap      endTick=" + note.endTick() + " < nextStart=" + next.getStartTick();
            }

            System.out.printf("  %-5d  %-10d  %-10d  %-10d  %-22d  %d.%d.1.%-8d  %s%n",
                    i, note.getStartTick(), note.getDurationTicks(), note.endTick(),
                    noteOffEventTick, bar, beat, subTick, flag);
        }
        System.out.println();
    }

    // -----------------------------------------------------------------------

    /**
     * Resolves {@code target/it-output/} relative to the Maven project root
     * (via the {@code basedir} system property set by Surefire/Failsafe),
     * creates the directory if needed, and returns the output {@link File}.
     */
    private static File itOutputFile(String filename) throws Exception {
        String basedir = System.getProperty("basedir", System.getProperty("user.dir"));
        File   outDir  = new File(basedir, "target/it-output");
        outDir.mkdirs();
        return new File(outDir, filename);
    }

    // -----------------------------------------------------------------------

    /**
     * Reference file {@code midi-fixer-test-1.mid}: 4 notes, track 1, ch 0, pitch 60.
     *
     * <p>With FIFO pairing all 4 notes resolve to the same duration (5760 ticks).
     * The first 3 overlap the next note-on and are trimmed to 479 ticks each.
     * The last note (tick 1440) has no successor and stays at 5760 ticks.
     *
     * <p>Expected: 2 tracks, 4 notes scanned, 3 overlaps fixed.
     */
    @Test
    void realMidiFile1() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("references/midi-fixer-test-1.mid");
        assertNotNull(resource, "Reference MIDI file not found on classpath");

        Sequence seq = MidiLoader.load(new File(resource.toURI()));

        MidiAnalyzer                  analyzer = new MidiAnalyzer(seq);
        Map<NoteKey, List<NoteEvent>> groups   = analyzer.analyze();
        OverlapFixer                  fixer    = new OverlapFixer(groups, false);
        FixResult                     result   = fixer.fix(seq);

        // ---- write fixed output for manual inspection ---------------------
        File output = itOutputFile("midi-fixer-test-1-fixed.mid");
        MidiLoader.save(seq, output);
        System.out.println("[IT] Fixed output written to: " + output.getAbsolutePath());

        // ---- high-level assertions ----------------------------------------
        assertEquals(2, result.getTracks(), "Expected 2 tracks");
        assertEquals(4, result.getNotesScanned(), "Expected 4 notes");
        assertEquals(3, result.getOverlapsFixed(), "Expected 3 overlaps fixed");

        // ---- per-note assertions -------------------------------------------
        List<NoteEvent> sorted = groups.get(new NoteKey(1, 0, 60)).stream()
                .sorted(Comparator.comparingLong(NoteEvent::getStartTick))
                .toList();
        assertEquals(4, sorted.size());

        // First 3 notes trimmed to 479 (nextStart - start - 1 = 480 - 1 = 479)
        for (int i = 0; i < 3; i++) {
            NoteEvent note = sorted.get(i);
            assertEquals(479L, note.getDurationTicks(),
                    "Note at tick " + note.getStartTick() + " should be trimmed to 479");
            assertEquals(note.getStartTick() + 479L, note.getNoteOffEvent().getTick(),
                    "NOTE_OFF tick mismatch for note at tick " + note.getStartTick());
        }

        // Last note has no successor — untouched at its original FIFO duration (5760)
        NoteEvent last = sorted.get(3);
        assertEquals(1440L, last.getStartTick());
        assertEquals(5760L, last.getDurationTicks(), "Last note should be untouched at dur=5760");
    }

    // -----------------------------------------------------------------------

    /**
     * Reference file {@code midi-fixer-test-2.mid}: 2 melodic tracks, each with
     * 8 notes on the same pitch.
     *
     * <p>With FIFO pairing all 8 notes on each track resolve to the same
     * duration (5760 ticks).  The first 7 notes on each track overlap the
     * next note-on and are trimmed to {@code nextStart - start - 1}.
     * The last note (tick 7200) has no successor and stays at 5760 ticks.
     *
     * <p>Expected: 3 tracks, 16 notes scanned, 14 overlaps fixed (7 per melodic track).
     */
    @Test
    void realMidiFile2() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("references/midi-fixer-test-2.mid");
        assertNotNull(resource, "Reference MIDI file not found on classpath");

        Sequence seq = MidiLoader.load(new File(resource.toURI()));

        MidiAnalyzer                  analyzer = new MidiAnalyzer(seq);
        Map<NoteKey, List<NoteEvent>> groups   = analyzer.analyze();
        OverlapFixer                  fixer    = new OverlapFixer(groups, false);
        FixResult                     result   = fixer.fix(seq);

        // ---- write fixed output for manual inspection ---------------------
        File output = itOutputFile("midi-fixer-test-2-fixed.mid");
        MidiLoader.save(seq, output);
        System.out.println("[IT] Fixed output written to: " + output.getAbsolutePath());

        // ---- high-level assertions ----------------------------------------
        assertEquals(3, result.getTracks(), "Expected 3 tracks (incl. meta track 0)");
        assertEquals(16, result.getNotesScanned(), "Expected 16 notes total");
        assertEquals(14, result.getOverlapsFixed(), "Expected 14 overlaps fixed (7 per melodic track)");

        // ---- per-track assertions ------------------------------------------
        assertTrackFixed(groups, 1, 0, 60);
        assertTrackFixed(groups, 2, 0, 64);
    }
}
