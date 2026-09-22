package org.julakali.chargeahead.shared.domain

/** The places found for [query]; [results] `null` means the search itself failed. */
data class DestinationSearch(
    val query: String,
    val results: List<Place>?,
    val searching: Boolean,
)
