package tr.trlive

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TrLiveProviderPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(TrLiveProvider())
    }
}
