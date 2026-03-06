package org.example.model;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable summary of what the {@link org.example.fix.OverlapFixer} did.
 */
@Value
@Builder
public class FixResult {

    /** Number of tracks in the sequence. */
    int tracks;

    /** Total number of fully-resolved note events scanned across all tracks. */
    int notesScanned;

    /** Number of note durations that were shortened to eliminate overlaps. */
    int overlapsFixed;
}

