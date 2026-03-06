package org.example;

import lombok.extern.slf4j.Slf4j;
import org.example.analysis.MidiAnalyzer;
import org.example.fix.OverlapFixer;
import org.example.io.MidiLoader;
import org.example.model.FixResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.sound.midi.Sequence;
import java.io.File;
import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "midi-fixer",
        mixinStandardHelpOptions = true,
        version = "midi-fixer 1.0",
        description = "Detects and fixes overlapping notes in MIDI files.%n" +
                      "A note overlaps itself when its duration extends past the start of its next occurrence on the same channel.",
        footer = "%nExamples:%n" +
                 "  midi-fixer input.mid                   # fix in-place (atomic overwrite)%n" +
                 "  midi-fixer input.mid -o output.mid     # write to a new file%n" +
                 "  midi-fixer input.mid --dry-run         # report fixes without writing%n"
)
public class MidiFixerApp implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<input-file>", description = "MIDI file to process.")
    private File inputFile;

    @Option(names = {"-o", "--output"}, paramLabel = "<output-file>",
            description = "Output file path. If omitted, the input file is overwritten in-place.")
    private File outputFile;

    @Option(names = {"-n", "--dry-run"},
            description = "Analyse and report overlaps without writing any output.")
    private boolean dryRun;

    @Option(names = {"-v", "--verbose"},
            description = "Print details of every fix applied.")
    private boolean verbose;

    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MidiFixerApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // --- Validate input -------------------------------------------------
        if (!inputFile.exists()) {
            System.err.println("ERROR: Input file not found: " + inputFile.getAbsolutePath());
            return 2;
        }
        if (!inputFile.isFile()) {
            System.err.println("ERROR: Input path is not a file: " + inputFile.getAbsolutePath());
            return 2;
        }

        File destination = (outputFile != null) ? outputFile : inputFile;

        try {
            // --- Load -------------------------------------------------------
            log.info("Loading: {}", inputFile.getAbsolutePath());
            Sequence sequence = MidiLoader.load(inputFile);

            // --- Analyse & Fix ----------------------------------------------
            MidiAnalyzer analyzer = new MidiAnalyzer(sequence);
            OverlapFixer fixer = new OverlapFixer(analyzer.analyze(), verbose);
            FixResult result = fixer.fix(sequence);

            // --- Report -----------------------------------------------------
            printSummary(result, destination);

            if (result.getOverlapsFixed() == 0) {
                log.info("No overlapping notes found — file is clean.");
            }

            // --- Write ------------------------------------------------------
            if (!dryRun) {
                MidiLoader.save(sequence, destination);
                log.info("Written: {}", destination.getAbsolutePath());
            } else {
                log.info("Dry-run mode — no file written.");
            }

            return 0;

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            log.debug("Stack trace:", e);
            return 1;
        }
    }

    // -----------------------------------------------------------------------

    private void printSummary(FixResult result, File destination) {
        System.out.printf("%n=== midi-fixer summary ===%n");
        System.out.printf("  Input file    : %s%n", inputFile.getAbsolutePath());
        System.out.printf("  Output file   : %s%s%n",
                destination.getAbsolutePath(), dryRun ? " (dry-run, not written)" : "");
        System.out.printf("  Tracks        : %d%n", result.getTracks());
        System.out.printf("  Notes scanned : %d%n", result.getNotesScanned());
        System.out.printf("  Overlaps fixed: %d%n", result.getOverlapsFixed());
        System.out.println();
    }
}

