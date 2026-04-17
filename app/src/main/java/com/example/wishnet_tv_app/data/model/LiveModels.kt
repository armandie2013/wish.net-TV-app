package com.example.wishnet_tv_app.data.model

data class LiveResponse(
    val ok: Boolean,
    val mode: String? = null,
    val user: LiveUser? = null,
    val plan: LivePlan? = null,
    val summary: LiveSummary? = null,
    val grid: List<LiveChannelItem> = emptyList(),
    val message: String? = null
)

data class LiveUser(
    val id: String,
    val nombre: String,
    val email: String? = null,
    val rol: String? = null,
    val localidad: String? = null
)

data class LivePlan(
    val id: String,
    val nombre: String,
    val estado: String
)

data class LiveSummary(
    val totalChannels: Int = 0
)

data class LiveChannelItem(
    val numero: Int,
    val orden: Int,
    val id: String,
    val name: String,
    val logo: String? = null,
    val category: String? = null,
    val sourceName: String? = null,
    val enabled: Boolean = true
)