package dev.steampad.client.window;

/** A screen-space rectangle (pixels): OS window bounds or monitor work-area bounds. */
public record WindowBounds(int x, int y, int width, int height) {}
