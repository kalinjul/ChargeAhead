package org.julakali.chargeahead.android.phone

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The phone's destinations. */
internal sealed interface PhoneDestination : NavKey

@Serializable
internal data object Home : PhoneDestination

@Serializable
internal data object Garage : PhoneDestination

@Serializable
internal data object AddCar : PhoneDestination

@Serializable
internal data object VehicleEdit : PhoneDestination

@Serializable
internal data object Networks : PhoneDestination

@Serializable
internal data object CarData : PhoneDestination

@Serializable
internal data object Legal : PhoneDestination

@Serializable
internal data object Licenses : PhoneDestination
