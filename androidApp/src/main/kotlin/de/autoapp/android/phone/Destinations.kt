package de.autoapp.android.phone

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The phone's destinations. StopDetail carries an index into the current
 * plan's stops: the plan lives in TripViewModel, and an index survives
 * process death where a PlannedStop object would not.
 */
internal sealed interface PhoneDestination : NavKey

@Serializable
internal data object Home : PhoneDestination

@Serializable
internal data object Trip : PhoneDestination

@Serializable
internal data class StopDetail(val index: Int) : PhoneDestination

@Serializable
internal data object Garage : PhoneDestination

@Serializable
internal data object AddCar : PhoneDestination

@Serializable
internal data object VehicleEdit : PhoneDestination

@Serializable
internal data object Subscriptions : PhoneDestination

@Serializable
internal data object Networks : PhoneDestination

@Serializable
internal data object CarData : PhoneDestination
