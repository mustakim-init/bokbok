package com.mustakim.bokbok.data.service

import fi.iki.elonen.NanoHTTPD
import java.util.Collections
import java.util.Random

class SecurityManager {

    companion object {
        const val PASSWORD_LENGTH = 4
    }

    private var password = ""
    private val authorizedIps = Collections.synchronizedList(ArrayList<String>())

    init {
        generateNewPassword()
    }

    fun generateNewPassword() {
        val r = StringBuilder()
        val random = Random()
        repeat(PASSWORD_LENGTH) {
            r.append(random.nextInt(10))
        }
        password = r.toString()
        // In a real session-based system we'd rotate tokens, but IP allowlist is simple and effective for local LAN
        authorizedIps.clear()
    }

    fun getPassword(): String = password

    fun getFormattedPassword(): String = password.toCharArray().joinToString(" ")

    fun hasAccess(session: NanoHTTPD.IHTTPSession): Boolean {
        val remoteIp = session.remoteIpAddress
        // NanoHTTPD sometimes wraps IPv6 or adds extra info, simple contains check is usually enough for LAN
        return authorizedIps.any { it == remoteIp }
    }

    fun validateLogin(session: NanoHTTPD.IHTTPSession): Boolean {
        val remoteIp = session.remoteIpAddress
        
        // If already authorized, return true
        if (hasAccess(session)) return true

        // Check for POST password
        if (session.method == NanoHTTPD.Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parms
                if (params["p"] == password) {
                    authorizedIps.add(remoteIp)
                    return true
                }
            } catch (e: Exception) {
                // Parse error
            }
        }
        return false
    }
}
