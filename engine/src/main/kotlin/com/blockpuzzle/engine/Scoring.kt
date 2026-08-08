package com.blockpuzzle.engine

/**
 * Score model.
 *
 * Two independent rewards, matching how the genre plays:
 *  - dropping a piece pays [perCell] per occupied cell, so the score always creeps up;
 *  - clearing pays a *triangular* bonus in the number of lines wiped at once, so a
 *    four-line clear is worth far more than four one-line clears;
 *  - clearing on consecutive drops raises the combo, which scales the clear bonus.
 *
 * @param perCell points per cell of a dropped piece.
 * @param lineBase points for the first line of a clear.
 * @param comboStepPercent extra percent added to the clear bonus per combo level above 1.
 * @param maxComboLevel combo levels above this stop increasing the multiplier.
 * @param allClearBonus paid when a drop leaves the board completely empty.
 */
data class ScoreRules(
    val perCell: Int = 1,
    val lineBase: Int = 10,
    val comboStepPercent: Int = 50,
    val maxComboLevel: Int = 10,
    val allClearBonus: Int = 300,
) {

    /** Points for dropping a piece of [cellCount] cells. */
    fun placementScore(cellCount: Int): Int = cellCount * perCell

    /**
     * Points for wiping [lines] lines at combo level [combo].
     *
     * [combo] is 1 for the first clear of a streak. `lines == 0` scores nothing regardless
     * of the combo, so a dry drop can never be worth points it did not earn.
     */
    fun clearScore(lines: Int, combo: Int): Int {
        if (lines <= 0) return 0
        val base = lineBase * lines * (lines + 1) / 2
        val level = combo.coerceIn(1, maxComboLevel)
        val multiplierPercent = 100 + comboStepPercent * (level - 1)
        return base * multiplierPercent / 100
    }
}
