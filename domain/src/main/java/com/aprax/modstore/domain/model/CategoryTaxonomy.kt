package com.aprax.modstore.domain.model

/**
 * The app's category taxonomy.
 *
 * This is the brief's fixed list. Each entry maps to the slug the source sites actually
 * use, which was read from their live category listings, so a category tap queries each
 * site with a term it really understands instead of a guessed translation.
 *
 * Sources that publish content the brief's taxonomy does not cover (for example adult
 * categories) are simply not mapped, so that content is never surfaced.
 */
object CategoryTaxonomy {

    val GAMES_HEADER = "Games"
    val APPS_HEADER = "Apps"

    val ACTION = Category(
        key = "action",
        title = "Action Games",
        isGame = true,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "action",
            SourceId.HAPPYMOD to "action",
        ),
        description = "Fast, combat-driven games.",
    )

    val ADVENTURE = Category(
        key = "adventure",
        title = "Adventure Games",
        isGame = true,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "adventure",
            SourceId.HAPPYMOD to "adventure",
        ),
        description = "Exploration and story-led games.",
    )

    val RACING = Category(
        key = "racing",
        title = "Racing Games",
        isGame = true,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "racing",
            SourceId.HAPPYMOD to "racing",
        ),
        description = "Racing and driving games.",
    )

    val SIMULATION = Category(
        key = "simulation",
        title = "Simulation Games",
        isGame = true,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "simulation",
            SourceId.HAPPYMOD to "simulation",
        ),
        description = "Life, city and vehicle simulators.",
    )

    val STRATEGY = Category(
        key = "strategy",
        title = "Strategy Games",
        isGame = true,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "strategy",
            SourceId.HAPPYMOD to "strategy",
        ),
        description = "Tactics, base-building and turn-based games.",
    )

    /**
     * "Offline Games" is a cross-cutting convenience category. Only Modyolo publishes
     * offline-specific landing pages, so it is queried there and omitted elsewhere rather
     * than being turned into a fake keyword search.
     */
    val OFFLINE_GAMES = Category(
        key = "offline-games",
        title = "Offline Games",
        isGame = true,
        sourceSlugs = mapOf(SourceId.MODYOLO to "offline"),
        description = "Games that play without a connection.",
    )

    val ONLINE_GAMES = Category(
        key = "online-games",
        title = "Online Games",
        isGame = true,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "online",
            SourceId.HAPPYMOD to "new",
        ),
        description = "Games built around online play.",
    )

    val TOOLS = Category(
        key = "tools",
        title = "Tools",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "tools",
            SourceId.HAPPYMOD to "tools",
        ),
        description = "Utilities and system tools.",
    )

    val PRODUCTIVITY = Category(
        key = "productivity",
        title = "Productivity",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "productivity",
            SourceId.HAPPYMOD to "productivity",
        ),
        description = "Notes, office and workflow apps.",
    )

    val PHOTOGRAPHY = Category(
        key = "photography",
        title = "Photography",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "photography",
            SourceId.HAPPYMOD to "photography",
        ),
        description = "Camera and photo tools.",
    )

    val VIDEO_EDITING = Category(
        key = "video-editing",
        title = "Video Editing",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "video-players-editors",
            SourceId.HAPPYMOD to "video-players-editors",
        ),
        description = "Video players and editors.",
    )

    val MUSIC = Category(
        key = "music",
        title = "Music",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "music-audio",
            SourceId.HAPPYMOD to "music-audio",
        ),
        description = "Players, audio tools and streaming.",
    )

    val SOCIAL_MEDIA = Category(
        key = "social-media",
        title = "Social Media",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "social",
            SourceId.HAPPYMOD to "social",
        ),
        description = "Social networking and messaging.",
    )

    val ENTERTAINMENT = Category(
        key = "entertainment",
        title = "Entertainment",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "entertainment",
            SourceId.HAPPYMOD to "entertainment",
        ),
        description = "Streaming, TV and media.",
    )

    val EDUCATION = Category(
        key = "education",
        title = "Education",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "education",
            SourceId.HAPPYMOD to "educational",
        ),
        description = "Learning and study apps.",
    )

    val PERSONALIZATION = Category(
        key = "personalization",
        title = "Personalization",
        isGame = false,
        sourceSlugs = mapOf(
            SourceId.MODYOLO to "personalization",
            SourceId.HAPPYMOD to "personalization",
        ),
        description = "Themes, wallpapers and launchers.",
    )

    val games: List<Category> = listOf(
        ACTION, ADVENTURE, RACING, SIMULATION, STRATEGY, OFFLINE_GAMES, ONLINE_GAMES,
    )

    val apps: List<Category> = listOf(
        TOOLS, PRODUCTIVITY, PHOTOGRAPHY, VIDEO_EDITING, MUSIC, SOCIAL_MEDIA,
        ENTERTAINMENT, EDUCATION, PERSONALIZATION,
    )

    val all: List<Category> = games + apps

    fun byKey(key: String): Category? = all.firstOrNull { it.key.equals(key, ignoreCase = true) }

    /** Shortcut strip shown on the home screen. */
    val homeShortcuts: List<Category> = listOf(
        ACTION, RACING, SIMULATION, STRATEGY, TOOLS, PHOTOGRAPHY, MUSIC, PERSONALIZATION,
    )
}
