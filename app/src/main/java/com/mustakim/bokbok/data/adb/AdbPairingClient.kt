package com.mustakim.bokbok.data.adb

import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.PrivateKey
import java.security.cert.Certificate

private const val TAG = "AdbPairingClient"

/**
 * Handles the one-time ADB pairing handshake (SPAKE2) using libadb-android.
 */
class AdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairingCode: String,
    private val adbKey: AdbKey
) : AbsAdbConnectionManager() {

    init {
        setApi(Build.VERSION.SDK_INT)
    }

    override fun getPrivateKey(): PrivateKey = adbKey.privateKey

    override fun getCertificate(): Certificate = adbKey.certificate

    override fun getDeviceName(): String = "BokBok"

    /**
     * Executes the pairing handshake.
     * @return true if pairing was successful.
     */
    suspend fun pair(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting pairing handshake with $host:$port using code $pairingCode")
            pair(host, port, pairingCode)
            Log.d(TAG, "Pairing handshake successful!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pairing handshake failed", e)
            false
        }
    }
}
