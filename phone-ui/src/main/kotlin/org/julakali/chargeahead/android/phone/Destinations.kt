package org.julakali.chargeahead.android.phone

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The phone's destinations. */
sealed interface PhoneDestination : NavKey

@Serializable
data object Home : PhoneDestination

@Serializable
data object Garage : PhoneDestination

@Serializable
data object AddCar : PhoneDestination

@Serializable
data object VehicleEdit : PhoneDestination

@Serializable
data object Networks : PhoneDestination

@Serializable
data object CarData : PhoneDestination

@Serializable
data object Legal : PhoneDestination

@Serializable
data object Licenses : PhoneDestination
