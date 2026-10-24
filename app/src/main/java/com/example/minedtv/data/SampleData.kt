package com.example.minedtv.data

import androidx.compose.ui.graphics.Color

data class Clip(
    val id: Int,
    val title: String,
    val game: String,
    val user: String,
    val views: Int,
    val likes: Int,
    val comments: Int,
    val duration: String,
    val age: String,
    val colors: List<Color>
)

data class Game(val name: String, val clips: Int, val colors: List<Color>)

data class Notification(val user: String, val text: String, val time: String, val clip: Clip?)

data class Conversation(val user: String, val lastMessage: String, val time: String, val unread: Boolean)

data class Comment(val user: String, val text: String, val likes: Int, val age: String)

// Placeholder content until a real backend exists.
object SampleData {
    val palettes = listOf(
        listOf(Color(0xFFFF4655), Color(0xFF3B0A45)),
        listOf(Color(0xFF00C6FF), Color(0xFF6A00F4)),
        listOf(Color(0xFFFF8A00), Color(0xFF7A1F1F)),
        listOf(Color(0xFF2EC4B6), Color(0xFF0B3D4A)),
        listOf(Color(0xFF7BD389), Color(0xFF1E4D2B)),
        listOf(Color(0xFF4D7CFE), Color(0xFFFF6B9A)),
        listOf(Color(0xFFFFC53D), Color(0xFFB2361E)),
        listOf(Color(0xFF8E9AAF), Color(0xFF22223B))
    )

    val games = listOf(
        Game("Valorant", 12400, palettes[0]),
        Game("Fortnite", 31200, palettes[1]),
        Game("Apex Legends", 9800, palettes[2]),
        Game("CS2", 15100, palettes[3]),
        Game("Minecraft", 22700, palettes[4]),
        Game("Rocket League", 6400, palettes[5]),
        Game("Overwatch 2", 7300, palettes[6]),
        Game("Call of Duty", 18900, palettes[7])
    )

    private val titles = listOf(
        "1v5 clutch on Ascent", "No scope across the map", "Insane 360 flick",
        "Squad wipe with a pistol", "Last circle 20-bomb", "This bug is hilarious",
        "Perfect parry timing", "Ace in overtime", "Clutch revive under fire",
        "Speedrun skip that actually worked", "Triple kill off spawn", "When the teammate trolls",
        "Rocket jump into the win", "Sniper duel at 400m", "Building battle masterclass",
        "Boss down first try"
    )

    val users = listOf(
        "ShadowSniper", "NovaQueen", "xPixel", "Glitchy", "Rekt_Royale",
        "Kairo", "LunaFrags", "TurboTom", "ZeroLag", "Mistik"
    )

    private val ages = listOf("2m", "15m", "1h", "3h", "5h", "1d", "2d", "4d", "1w")

    val clips = titles.mapIndexed { i, title ->
        val game = games[i % games.size]
        Clip(
            id = i,
            title = title,
            game = game.name,
            user = users[(i * 3) % users.size],
            views = 1200 + (i * 73_919) % 980_000,
            likes = 80 + (i * 4_217) % 52_000,
            comments = 5 + (i * 389) % 2_400,
            duration = "0:${(12 + i * 7 % 47).toString().padStart(2, '0')}",
            age = ages[i % ages.size],
            colors = game.colors
        )
    }

    val currentUser = "ShadowSniper"
    val myClips = clips.filter { it.user == currentUser } + clips.take(7)

    val notifications = listOf(
        Notification("NovaQueen", "liked your clip", "2m", clips[0]),
        Notification("xPixel", "started following you", "10m", null),
        Notification("Glitchy", "commented: \"how did you hit that?? 🤯\"", "32m", clips[3]),
        Notification("Kairo", "shared your clip", "1h", clips[6]),
        Notification("LunaFrags", "liked your clip", "3h", clips[9]),
        Notification("TurboTom", "started following you", "5h", null),
        Notification("ZeroLag", "mentioned you in a comment", "1d", clips[12]),
        Notification("Mistik", "liked your clip", "2d", clips[0])
    )

    val conversations = listOf(
        Conversation("NovaQueen", "duo tonight?", "4m", true),
        Conversation("xPixel", "bro that clip is going viral", "1h", true),
        Conversation("Kairo", "gg", "3h", false),
        Conversation("LunaFrags", "sent you a clip", "1d", false),
        Conversation("TurboTom", "what settings do you use", "3d", false)
    )

    val comments = listOf(
        Comment("NovaQueen", "that flick was disgusting 🔥", 241, "1h"),
        Comment("xPixel", "aim assist on real life", 98, "2h"),
        Comment("Glitchy", "the enemy team must have rage quit", 64, "3h"),
        Comment("Kairo", "clip of the week for sure", 37, "5h"),
        Comment("ZeroLag", "how is your ping that low", 12, "1d")
    )
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f).replace(".0M", "M")
    n >= 1_000 -> String.format("%.1fK", n / 1_000f).replace(".0K", "K")
    else -> n.toString()
}
