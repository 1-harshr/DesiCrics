package com.harsh.desicrics.designsystem.theme

/** Every theme a player can pick, in picker order. Add a new [DesiThemeSpec] here to ship it. */
object DesiThemes {
    val default: DesiThemeSpec = ClassicTheme

    val all: List<DesiThemeSpec> = listOf(
        ClassicTheme,
        FloodlightsTheme,
        GullyTheme,
        RedBallTheme,
    )

    /** The theme saved as [id], or [default] when nothing is saved or that theme no longer ships. */
    fun byId(id: String?): DesiThemeSpec = all.firstOrNull { it.id == id } ?: default
}
