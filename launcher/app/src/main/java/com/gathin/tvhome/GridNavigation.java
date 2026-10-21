package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /** Whether the highlight's target is no longer where its tile now is (a layout moved the grid under it). */
    static boolean glassMoved(float targetX, float targetY, float x, float y, float tolerance) {
        return Math.abs(targetX - x) > tolerance || Math.abs(targetY - y) > tolerance;
    }
}
