package com.poodicraft.bookquest.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the device can reach the internet at all.
 *
 * Every cloud failure in this app used to surface as the same flat "that did
 * not work", which is useless when the real answer is that the phone is on the
 * school wifi with no route out. Knowing the difference lets the app say so,
 * and lets it retry the backup by itself the moment the connection returns
 * rather than waiting to be asked.
 */
class Connectivity private constructor(appContext: Context) {

    private val manager = appContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = true
        }

        override fun onLost(network: Network) {
            // Losing one network does not mean losing them all — a phone
            // dropping wifi may still have mobile data — so re-check rather
            // than assuming this was the last one.
            _online.value = currentlyOnline()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            _online.value = capabilities
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            manager?.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            // Without the callback the flow keeps its startup value, which is
            // still better than pretending to be offline.
        }
    }

    /** Re-reads the state now, for the moments a callback may have been missed. */
    fun refresh() {
        _online.value = currentlyOnline()
    }

    private fun currentlyOnline(): Boolean = try {
        val active = manager?.activeNetwork
        val capabilities = active?.let { manager?.getNetworkCapabilities(it) }
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        // Assume there is a connection: a false "you are offline" banner on a
        // working phone is worse than no banner at all.
        true
    }

    companion object {
        @Volatile
        private var instance: Connectivity? = null

        fun get(context: Context): Connectivity = instance ?: synchronized(this) {
            instance ?: Connectivity(context.applicationContext).also { instance = it }
        }
    }
}
