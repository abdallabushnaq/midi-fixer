package org.example.io;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Thin wrapper around {@link MidiSystem} for loading and saving MIDI sequences.
 *
 * <p>Saving uses an atomic write-to-temp-then-move strategy so that a failure
 * mid-write never corrupts the original file.
 */
public final class MidiLoader {

    private MidiLoader() {
        // utility class
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    /**
     * Reads a MIDI file and returns its {@link Sequence}.
     *
     * @param file the MIDI file to read
     * @return the parsed sequence
     * @throws Exception if the file cannot be read or is not a valid MIDI file
     */
    public static Sequence load(File file) throws Exception {
        return MidiSystem.getSequence(file);
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    /**
     * Writes a {@link Sequence} to {@code destination}.
     *
     * <p>The write is atomic: data is first written to a sibling temp file,
     * then atomically moved over {@code destination}. This protects the original
     * file if the JVM dies or the disk runs out of space mid-write.
     *
     * @param sequence    the sequence to write
     * @param destination target file (created or overwritten)
     * @throws IOException if the write or move fails
     * @throws Exception   if MidiSystem cannot determine the MIDI file type
     */
    public static void save(Sequence sequence, File destination) throws Exception {
        // Determine supported MIDI file types (0, 1, or 2); prefer the same
        // type as the input if possible, otherwise fall back to type 1.
        int[] types = MidiSystem.getMidiFileTypes(sequence);
        if (types == null || types.length == 0) {
            throw new IOException("No MIDI file type supported for this sequence.");
        }

        // Prefer type 1 (multi-track), then whatever is available
        int fileType = types[0];
        for (int t : types) {
            if (t == 1) {
                fileType = 1;
                break;
            }
        }

        // Write to a temp file in the same directory for an atomic replace
        File tempFile = File.createTempFile(
                destination.getName() + ".tmp-", ".mid",
                destination.getAbsoluteFile().getParentFile());

        try {
            MidiSystem.write(sequence, fileType, tempFile);
            Files.move(tempFile.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // Clean up temp file if the move failed
            tempFile.delete();
            throw e;
        }
    }
}

