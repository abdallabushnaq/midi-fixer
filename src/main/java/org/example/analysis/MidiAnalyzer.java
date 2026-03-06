package org.example.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.model.NoteEvent;

import javax.sound.midi.*;
import java.util.*;

/**
 * Scans all tracks in a {@link Sequence} and produces fully-resolved
 * {@link NoteEvent} objects by pairing every NOTE_ON with its corresponding
 * NOTE_OFF (or NOTE_ON with velocity 0, which the MIDI spec treats as NOTE_OFF).
 *
 * <p><b>Pairing strategy (FIFO):</b> When multiple NOTE_ONs for the same pitch
 * arrive before their NOTE_OFFs, the <em>oldest</em> still-open NOTE_ON is
 * closed by the next NOTE_OFF.  This matches the behaviour of DAWs such as
 * Cubase and reflects musical intent: the first note you pressed is the first
 * one that ends.
 *
 * <p>After pairing, notes are grouped by {@code (trackIndex, channel, pitch)}
 * and sorted by start tick so that {@link org.example.fix.OverlapFixer} can
 * detect any remaining overlaps and trim them.
 */
@Slf4j
@RequiredArgsConstructor
public class MidiAnalyzer {

    private final Sequence sequence;

    // -----------------------------------------------------------------------

    private static void addToResult(Map<NoteKey, List<NoteEvent>> result, int trackIdx, NoteEvent event) {
        NoteKey key = new NoteKey(trackIdx, event.getChannel(), event.getPitch());
        result.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
    }

    // -----------------------------------------------------------------------

    /**
     * Analyses the sequence and returns all resolved note events, grouped by
     * {@code (trackIndex, channel, pitch)}.
     *
     * @return map keyed by {@link NoteKey}; each value is sorted by start tick
     */
    public Map<NoteKey, List<NoteEvent>> analyze() {
        Map<NoteKey, List<NoteEvent>> result = new LinkedHashMap<>();

        Track[] tracks = sequence.getTracks();
        log.debug("Analysing {} track(s).", tracks.length);

        for (int trackIdx = 0; trackIdx < tracks.length; trackIdx++) {
            analyzeTrack(trackIdx, tracks[trackIdx], result);
        }

        // Sort each group by start tick
        result.values().forEach(list -> list.sort(Comparator.comparingLong(NoteEvent::getStartTick)));

        return result;
    }

    private void analyzeTrack(int trackIdx, Track track,
                              Map<NoteKey, List<NoteEvent>> result) {
        // pending[channel][pitch] → queue of PendingNotes (FIFO: oldest first)
        @SuppressWarnings("unchecked")
        Deque<PendingNote>[][] pending = new ArrayDeque[16][128];
        for (int c = 0; c < 16; c++) {
            for (int p = 0; p < 128; p++) {
                pending[c][p] = new ArrayDeque<>();
            }
        }

        // Snapshot so that any synthetic events we add don't affect iteration
        List<MidiEvent> snapshot = new ArrayList<>(track.size());
        for (int i = 0; i < track.size(); i++) {
            snapshot.add(track.get(i));
        }

        for (MidiEvent midiEvent : snapshot) {
            MidiMessage msg = midiEvent.getMessage();
            if (!(msg instanceof ShortMessage sm)) {
                continue;
            }

            int  command  = sm.getCommand();
            int  channel  = sm.getChannel();
            int  pitch    = sm.getData1();
            int  velocity = sm.getData2();
            long tick     = midiEvent.getTick();

            boolean isNoteOn = (command == ShortMessage.NOTE_ON) && (velocity > 0);
            boolean isNoteOff = (command == ShortMessage.NOTE_OFF) ||
                    (command == ShortMessage.NOTE_ON && velocity == 0);

            if (isNoteOn) {
                pending[channel][pitch].addLast(new PendingNote(channel, pitch, tick, track));

            } else if (isNoteOff) {
                PendingNote open = pending[channel][pitch].pollFirst();   // FIFO: oldest open note first
                if (open == null) {
                    log.debug("Track {} ch={} pitch={}: NOTE_OFF at tick {} with no matching NOTE_ON — skipped.",
                            trackIdx, channel, pitch, tick);
                    continue;
                }

                long duration = Math.max(1L, tick - open.startTick());
                NoteEvent resolved = new NoteEvent(channel, pitch,
                        open.startTick(), duration, track, midiEvent);
                addToResult(result, trackIdx, resolved);
            }
        }

        // Unclosed NOTE_ONs at end-of-track
        for (int c = 0; c < 16; c++) {
            for (int p = 0; p < 128; p++) {
                for (PendingNote unclosed : pending[c][p]) {
                    log.debug("Track {} ch={} pitch={}: unclosed NOTE_ON at tick {} — ignored.",
                            trackIdx, unclosed.channel(), unclosed.pitch(), unclosed.startTick());
                }
            }
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Partial state of an unresolved NOTE_ON while we wait for its NOTE_OFF.
     */
    private record PendingNote(int channel, int pitch, long startTick, Track track) {
    }
}
