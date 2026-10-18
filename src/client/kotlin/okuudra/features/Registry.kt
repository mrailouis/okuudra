package okuudra.features

import okuudra.config.Features
import okuudra.features.impl.gui.Interface
import okuudra.features.impl.kuudra.general.KuudraQol
import okuudra.features.impl.kuudra.general.KuudraSplits
import okuudra.features.impl.kuudra.general.KuudraTimers
import okuudra.features.impl.kuudra.general.ShopHelper
import okuudra.features.impl.kuudra.phaseone.CrateOverlay
import okuudra.features.impl.kuudra.phaseone.NoPreAlert
import okuudra.features.impl.kuudra.phaseone.PileWaypoints
import okuudra.features.impl.kuudra.phaseone.PearlWaypoints
import okuudra.features.impl.kuudra.phaseone.SupplyHighlighter
import okuudra.features.impl.kuudra.phaseone.prio.Priority
import okuudra.features.impl.kuudra.phasethree.EatenTimer
import okuudra.features.impl.kuudra.phasethree.StunWaypoints
import okuudra.features.impl.kuudra.phasefour.KuudraDirection
import okuudra.features.impl.kuudra.phasetwo.BuildProgress
import okuudra.features.impl.kuudra.phasetwo.BuildWaypoints
import okuudra.features.impl.kuudra.phasetwo.FreshMessages

object Registry {
    fun init() {
        Features.register(
            KuudraQol, KuudraSplits, KuudraTimers, ShopHelper,
            CrateOverlay, NoPreAlert, PearlWaypoints, PileWaypoints, SupplyHighlighter, Priority,
            BuildProgress, BuildWaypoints, FreshMessages, EatenTimer, StunWaypoints, KuudraDirection, Interface,
        )
        Features.all.forEach { it.init() }
    }
}
