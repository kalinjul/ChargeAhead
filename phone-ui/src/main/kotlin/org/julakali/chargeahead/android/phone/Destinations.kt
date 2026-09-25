package org.julakali.chargeahead.android.phone

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The phone's destinations. */
sealed interface PhoneDestination : NavKey

/** A destination that shows as a sheet over the map instead of a full-screen page. */
sealed interface PhoneSheet : PhoneDestination

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

@Serializable
data object ChargeNow : PhoneSheet

@Serializable
data object Routes : PhoneSheet

val DrawerTarget.destination: PhoneDestination
    get() = when (this) {
        DrawerTarget.VEHICLE -> Garage
        DrawerTarget.NETWORKS -> Networks
        DrawerTarget.LEGAL -> Legal
        DrawerTarget.LICENSES -> Licenses
        DrawerTarget.CAR_DATA -> CarData
    }
