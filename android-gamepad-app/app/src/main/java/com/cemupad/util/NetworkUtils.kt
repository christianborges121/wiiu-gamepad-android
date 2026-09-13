package com.cemupad.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Network utilities for finding the local IP address on Wi-Fi or USB tethering interfaces.
 */
object NetworkUtils {
    fun getLocalIpAddress(): String {
        return getLocalIpAddresses().firstOrNull() ?: "127.0.0.1"
    }

    /** All local IPv4 addresses (Wi-Fi, tethering, etc.), excluding loopback. */
    fun getLocalIpAddresses(): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { result.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }
}
