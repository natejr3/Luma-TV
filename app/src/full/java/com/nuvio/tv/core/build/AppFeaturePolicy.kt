package com.nuvio.tv.core.build

import com.nuvio.tv.BuildConfig

object AppFeaturePolicy {
    val pluginsEnabled: Boolean = true
    val addonsEnabled: Boolean = true
    // This fork is distributed from one stable APK link. Do not query the upstream Nuvio
    // release feed or show misleading "old/new version" popups after connecting omega.
    val inAppUpdatesEnabled: Boolean = false
    val inAppTrailerPlaybackEnabled: Boolean = true
    val externalTrailerPlaybackEnabled: Boolean = true
    val supportNuvioEnabled: Boolean = true
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    val imdbRatingLogoEnabled: Boolean = true
    val p2pEnabled: Boolean = true
    val debugBackendSwitcherEnabled: Boolean = BuildConfig.IS_DEBUG_BUILD
}
