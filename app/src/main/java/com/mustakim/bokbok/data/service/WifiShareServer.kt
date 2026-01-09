package com.mustakim.bokbok.data.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Environment
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address

class WifiShareServer(
    private val context: Context,
    private val isPasswordRequired: Boolean,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val securityManager = SecurityManager()
    private val htmlGenerator = HtmlGenerator(context)
    
    // Point to the actual recordings directory: Movies/BokBok
    private val recordingsDir: File by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "BokBok")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        // 1. Favicon (always allow)
        if (uri == "/favicon.ico") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        }

        // 2. Security Check (if enabled)
        if (isPasswordRequired) {
            val authorized = securityManager.validateLogin(session)
            if (!authorized) {
                return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlGenerator.getLoginPage())
            }
        }

        // 3. Serve Content
        return try {
            if (uri == "/") {
                serveIndexPage()
            } else {
                serveFile(uri)
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, htmlGenerator.getErrorPage(500, e.message ?: "Unknown Error"))
        }
    }

    private fun serveIndexPage(): Response {
        val files = recordingsDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".mp4") || it.name.endsWith(".mkv")) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
            
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlGenerator.getIndexPage(files))
    }

    private fun serveFile(uri: String): Response {
        // Remove leading slash
        val fileName = uri.removePrefix("/")
        if (fileName.isEmpty() || fileName.contains("..") || fileName.contains("/")) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access Denied")
        }

        val file = File(recordingsDir, fileName)

        // Security: Ensure file is actually in our directory
        if (!file.exists() || !file.isFile || file.parentFile?.absolutePath != recordingsDir.absolutePath) {
             return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, htmlGenerator.getErrorPage(404, "File Not Found"))
        }

        return try {
            val fis = FileInputStream(file)
            val mimeType = when {
                fileName.endsWith(".mp4") -> "video/mp4"
                fileName.endsWith(".mkv") -> "video/x-matroska"
                else -> "application/octet-stream"
            }
            newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, htmlGenerator.getErrorPage(500, "Error Reading File"))
        }
    }
    
    fun getPin(): String? = if (isPasswordRequired) securityManager.getPassword() else null

    fun getIpAddress(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
        
        linkProperties?.linkAddresses?.forEach { linkAddress ->
            val address = linkAddress.address
            if (address is Inet4Address && !address.isLoopbackAddress) {
                return address.hostAddress ?: ""
            }
        }
        return "Unknown IP"
    }
}
