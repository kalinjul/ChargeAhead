package de.autoapp.shared.domain

/**
 * What the charger markers on the phone map say.
 *
 * [FREE_CHARGERS] is declared but not yet servable: no connected source
 * delivers live occupancy. The setting exists so the intent is visible in the
 * UI — the option stays disabled there until a live source lands, because a
 * made-up "3 frei" on a map would be worse than none.
 */
enum class MapLabelStyle { PRICE, FREE_CHARGERS }
