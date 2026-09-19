package org.julakali.chargeahead.shared.domain

data class Network(
    val key: String,
    val name: String,
    val operatorIds: Set<Long> = emptySet(),
    val nameKeywords: Set<String> = emptySet(),
    /** Position on the backend's current list, largest first; `null` once the network dropped off it. */
    val rank: Int? = null,
)
