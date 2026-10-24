package com.example.minedtv.data

/** The user's own longer recordings (their personal clip store), searchable by moments. */
object MyLibrary {
    val clips: List<Clip> = listOf(
        Triple("Fortnite ranked session", "Fortnite", "2:14"),
        Triple("Valorant comp - Ascent", "Valorant", "2:40"),
        Triple("Fortnite zero build duos", "Fortnite", "1:48"),
        Triple("Apex - Kings Canyon trios", "Apex Legends", "2:05"),
        Triple("CS2 Mirage premier", "CS2", "2:50"),
        Triple("Fortnite late game", "Fortnite", "1:57"),
        Triple("Valorant deathmatch warmup", "Valorant", "1:32"),
        Triple("Minecraft hardcore day 12", "Minecraft", "2:22"),
        Triple("Rocket League 2v2 ranked", "Rocket League", "1:40"),
        Triple("Warzone hot drop", "Call of Duty", "2:31")
    ).mapIndexed { i, (title, game, duration) ->
        Clip(
            id = 100 + i,
            title = title,
            game = game,
            user = SampleData.currentUser,
            views = 0,
            likes = 0,
            comments = 0,
            duration = duration,
            age = listOf("today", "yesterday", "2d", "3d", "5d")[i % 5],
            colors = SampleData.games.first { it.name == game }.colors
        )
    }
}
