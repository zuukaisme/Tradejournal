package com.example.domain.model

data class EconomicCalendarEvent(
    val id: String,
    val title: String,
    val currency: String,
    val timestamp: Long,
    val impact: String,
    val actual: String?,
    val forecast: String?,
    val previous: String?
)
