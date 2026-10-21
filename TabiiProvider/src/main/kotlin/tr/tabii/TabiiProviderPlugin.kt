package tr.tabii

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TabiiProviderPlugin : BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(TabiiProvider())
        // TV8, ATV, Star TV ve DMAX canlı yayın kanalları.
        registerMainAPI(TrLiveProvider())
    }
}
