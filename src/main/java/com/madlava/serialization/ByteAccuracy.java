package com.madlava.serialization;

/** Explicit byte-measurement semantics. Unknown is never represented as zero. */
public enum ByteAccuracy {
    EXACT_RETURNED_BYTEBUFFER,
    EXACT_INPUT_BYTEBUFFER,
    UNAVAILABLE
}
