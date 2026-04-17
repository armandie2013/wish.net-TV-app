package com.example.wishnet_tv_app.data.model

data class PlayResponse(
    val ok: Boolean,
    val strategy: String? = null,
    val fallbackUsed: Boolean = false,
    val user: PlayUser? = null,
    val location: PlayLocation? = null,
    val node: PlayNode? = null,
    val channel: PlayChannel? = null,
    val playback: PlaybackInfo? = null,
    val message: String? = null
)

data class PlayUser(
    val id: String,
    val nombre: String,
    val localidad: String? = null
)

data class PlayLocation(
    val id: String,
    val nombre: String,
    val codigo: String? = null
)

data class PlayNode(
    val id: String,
    val nombre: String,
    val tipo: String? = null,
    val urlBase: String? = null
)

data class PlayChannel(
    val id: String,
    val name: String,
    val logo: String? = null,
    val category: String? = null,
    val tvgId: String? = null,
    val numero: Int? = null,
    val orden: Int? = null,
    val visibleName: String? = null,
    val sourceName: String? = null
)

data class PlaybackInfo(
    val mode: String? = null,
    val streamUrl: String? = null,
    val directSourceUrl: String? = null
)