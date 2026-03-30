package com.mustakim.bokbok.data.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context,
    private val serviceType: String,
    private val onPortFound: (java.net.InetAddress?, Int) -> Unit
) {

    private var registered = false
    private var running = false
    private var foundValidPort = false
    private val listener = DiscoveryListener(this)
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val REDISCOVERY_INTERVAL_MS = 4000L
    private val rediscoveryRunnable = Runnable { doRediscovery() }

    private var lastFoundServiceName: String? = null

    fun start() {
        if (running) return
        running = true
        foundValidPort = false
        lastFoundServiceName = null
        if (!registered) {
            Log.d(TAG, "Starting mDNS discovery for $serviceType")
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }
        scheduleRediscovery()
    }

    fun stop() {
        if (!running) return
        running = false
        foundValidPort = false
        handler.removeCallbacksAndMessages(null)
        if (registered) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
            }
        }
    }

    private fun scheduleRediscovery() {
        handler.removeCallbacks(rediscoveryRunnable)
        if (running && !foundValidPort) {
            handler.postDelayed(rediscoveryRunnable, REDISCOVERY_INTERVAL_MS)
        }
    }

    private fun doRediscovery() {
        if (!running) return
        
        Log.d(TAG, "Rediscovery cycle: refreshing NSD discovery...")
        if (registered) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Stop during rediscovery failed", e)
            }
            // Wait for onDiscoveryStopped to fire, then restart
            handler.postDelayed({
                if (running && !foundValidPort && !registered) {
                    try {
                        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                    } catch (e: Exception) {
                        Log.e(TAG, "Restart during rediscovery failed", e)
                    }
                }
            }, 300L)
        } else {
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.e(TAG, "Fresh start during rediscovery failed", e)
            }
        }
        scheduleRediscovery()
    }

    private fun onDiscoveryStart() {
        registered = true
    }

    private fun onDiscoveryStop() {
        registered = false
    }

    private fun onServiceFound(info: NsdServiceInfo) {
        Log.d(TAG, "onServiceFound: ${info.serviceName}")
        nsdManager.resolveService(info, ResolveListener(this))
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        Log.d(TAG, "onServiceLost: ${info.serviceName}")
        if (info.serviceName == lastFoundServiceName) {
            lastFoundServiceName = null
            foundValidPort = false
            onPortFound(null, -1)
        }
    }

    private fun onServiceResolved(resolvedService: NsdServiceInfo) {
        val host = resolvedService.host
        val port = resolvedService.port
        Log.d(TAG, "onServiceResolved: ${resolvedService.serviceName} at ${host?.hostAddress}:$port")

        if (!running) return

        // Check if the host is local (we are looking for adbd on the same device)
        val isLocal = isHostLocal(host)
        val isLoopback = resolvedService.host?.isLoopbackAddress == true
        val hostAddress = host?.hostAddress
        // Check port on the ACTUAL resolved host, not loopback
        val portActive = isPortActive(hostAddress ?: "127.0.0.1", port)
        Log.d(TAG, "isLocal=$isLocal, isLoopback=$isLoopback, portActive=$portActive")

        if ((isLocal || isLoopback) && portActive) {
            Log.d(TAG, "✓ Valid pairing service confirmed: ${resolvedService.host}:$port")
            foundValidPort = true
            lastFoundServiceName = resolvedService.serviceName
            handler.removeCallbacks(rediscoveryRunnable)
            onPortFound(resolvedService.host, port)
        } else {
            Log.d(TAG, "Service not valid yet (isLocal=$isLocal, portActive=$portActive). Will rediscover if dialog opened/changed.")
            scheduleRediscovery()
        }
    }

    private fun isHostLocal(host: java.net.InetAddress?): Boolean {
        if (host == null) return false
        if (host.isLoopbackAddress) return true
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence().any { ni ->
                ni.inetAddresses.asSequence().any { it.hostAddress == host.hostAddress }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isPortActive(host: String, port: Int): Boolean {
        // Try connect to loopback first (often more reliable for self-connections)
        if (tryConnect("127.0.0.1", port)) return true
        // Then try the provided host address
        if (host != "127.0.0.1" && tryConnect(host, port)) return true
        return false
    }

    private fun tryConnect(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 800)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    internal class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.d(TAG, "Discovery started: $serviceType")
            adbMdns.onDiscoveryStart()
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "onStartDiscoveryFailed: $errorCode")
            adbMdns.running = false
            adbMdns.registered = false
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "Discovery stopped: $serviceType")
            adbMdns.onDiscoveryStop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "onStopDiscoveryFailed: $errorCode")
            adbMdns.registered = false
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceLost(serviceInfo)
        }
    }

    internal class ResolveListener(private val adbMdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "onResolveFailed: $errorCode for ${serviceInfo.serviceName}")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(serviceInfo)
        }
    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TAG = "AdbMdns"
    }
}
