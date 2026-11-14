    fun refreshOutlook() {
        viewModelScope.launch {
            isOutlookLoading.value = true
            outlookStatusMessage.value = null
            val currentSettings = settings.value
            val fredKey = currentSettings.fredApiKey.trim()
            val xoomarImportance = currentSettings.calendarImportance.trim().ifBlank { "high" }

            if (fredKey.isBlank()) {
                val xoomarRes = repository.fetchXoomarCalendar(xoomarImportance)
                xoomarRes.onSuccess { fetched ->
                    if (fetched.isNotEmpty()) repository.insertAllReleases(fetched)
                }.onFailure { err ->
                    outlookStatusMessage.value = "XOOMAR fetch failed: ${err.message}"
                }
                if (newsReleases.value.isEmpty()) {
                    outlookStatusMessage.value = "Add a FRED API key or refresh the XOOMAR calendar in Settings."
                }
                isOutlookLoading.value = false
                return@launch
            }

            val fredRes = repository.fetchFredMacroIndicators(fredKey)
            fredRes.onSuccess { indicators ->
                macroIndicators.value = indicators
            }.onFailure { err ->
                outlookStatusMessage.value = "FRED fetch failed: ${err.message}"
            }

            val xoomarRes = repository.fetchXoomarCalendar(xoomarImportance)
            xoomarRes.onSuccess { fetched ->
                if (fetched.isNotEmpty()) repository.insertAllReleases(fetched)
            }.onFailure { err ->
                outlookStatusMessage.value = "XOOMAR fetch failed: ${err.message}"
            }

            val event = selectedNewsEventType.value
            val allEventReleases = newsReleases.value.filter { it.eventType == event }
            val upcoming = allEventReleases.firstOrNull { it.isUpcoming } ?: allEventReleases.firstOrNull()
            val historical = allEventReleases.filter { !it.isUpcoming }
            val indicators = macroIndicators.value

            if (upcoming != null || indicators.isNotEmpty()) {
                val claimsInd = indicators.firstOrNull { it.id == "claims" }
                val claimsTrend = if (claimsInd != null && claimsInd.previousValue != 0.0) {
                    ((claimsInd.currentValue - claimsInd.previousValue) / claimsInd.previousValue) * 100.0
                } else 0.0
                val unrateInd = indicators.firstOrNull { it.id == "unrate" }
                val unrateChange = if (unrateInd != null) unrateInd.currentValue - unrateInd.previousValue else 0.0
                val wageInd = indicators.firstOrNull { it.id == "wages" }
                val wageGrowth = if (wageInd != null && wageInd.previousValue != 0.0) {
                    ((wageInd.currentValue - wageInd.previousValue) / wageInd.previousValue) * 100.0
                } else 0.0

                val newPred = com.example.domain.usecase.GoldOutlookModel.computePrediction(
                    eventType = event,
                    releaseDate = upcoming?.dateString ?: "Pending Calendar",
                    releaseTimestamp = upcoming?.timestamp ?: System.currentTimeMillis(),
                    forecast = upcoming?.forecast ?: 0.0,
                    previous = upcoming?.previous ?: 0.0,
                    claimsTrend = claimsTrend,
                    unemploymentChange = unrateChange,
                    wageGrowthYoY = wageGrowth,
                    historicalReleases = historical
                )
                repository.savePrediction(newPred)
            } else if (outlookStatusMessage.value == null) {
                outlookStatusMessage.value = "No live releases or indicators returned yet."
            }

            isOutlookLoading.value = false
        }
    }
