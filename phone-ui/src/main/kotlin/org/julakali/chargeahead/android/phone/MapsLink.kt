package org.julakali.chargeahead.android.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a `MapsHandoff` link; `false` when no app takes it. */
fun Context.openMapsLink(link: String): Boolean =
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    }
