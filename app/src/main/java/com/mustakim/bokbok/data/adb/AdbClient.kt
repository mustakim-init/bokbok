package com.mustakim.bokbok.data.adb

import android.util.Log
import com.mustakim.bokbok.data.adb.AdbProtocol.ADB_AUTH_RSAPUBLICKEY
import com.mustakim.bokbok.data.adb.AdbProtocol.ADB_AUTH_SIGNATURE
import com.mustakim.bokbok.data.adb.AdbProtocol.ADB_AUTH_TOKEN
import com.mustakim.bokbok.data.adb.AdbProtocol.A_AUTH
import com.mustakim.bokbok.data.adb.AdbProtocol.A_CLSE
import com.mustakim.bokbok.data.adb.AdbProtocol.A_CNXN
import com.mustakim.bokbok.data.adb.AdbProtocol.A_MAXDATA
import com.mustakim.bokbok.data.adb.AdbProtocol.A_OKAY
import com.mustakim.bokbok.data.adb.AdbProtocol.A_OPEN
import com.mustakim.bokbok.data.adb.AdbProtocol.A_STLS
import com.mustakim.bokbok.data.adb.AdbProtocol.A_STLS_VERSION
import com.mustakim.bokbok.data.adb.AdbProtocol.A_VERSION
import com.mustakim.bokbok.data.adb.AdbProtocol.A_WRTE
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbClient"

class AdbClient(private val host: String, private val port: Int, private val key: AdbKey) : Closeable {

    private lateinit var socket: Socket
    private lateinit var plainInputStream: DataInputStream
    private lateinit var plainOutputStream: DataOutputStream

    private var useTls = false

    private lateinit var tlsSocket: SSLSocket
    private lateinit var tlsInputStream: DataInputStream
    private lateinit var tlsOutputStream: DataOutputStream

    private val inputStream get() = if (useTls) tlsInputStream else plainInputStream
    private val outputStream get() = if (useTls) tlsOutputStream else plainOutputStream

    private var nextLocalId = 1

    fun connect() {
        socket = Socket()
        val address = InetSocketAddress(host, port)
        socket.connect(address, 5000)

        socket.tcpNoDelay = true
        plainInputStream = DataInputStream(socket.getInputStream())
        plainOutputStream = DataOutputStream(socket.getOutputStream())

        write(A_CNXN, A_VERSION, A_MAXDATA, "host::")

        var message = read()
        if (message.command == A_STLS) {
            write(A_STLS, A_STLS_VERSION, 0)

            val sslContext = key.sslContext
            tlsSocket = sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            tlsSocket.startHandshake()
            Log.d(TAG, "TLS Handshake succeeded.")

            tlsInputStream = DataInputStream(tlsSocket.inputStream)
            tlsOutputStream = DataOutputStream(tlsSocket.outputStream)
            useTls = true

            message = read()
        } else if (message.command == A_AUTH) {
            if (message.arg0 != ADB_AUTH_TOKEN) error("Expected ADB_AUTH_TOKEN")
            write(A_AUTH, ADB_AUTH_SIGNATURE, 0, key.sign(message.data))

            message = read()
            if (message.command != A_CNXN) {
                write(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, key.adbPublicKey)
                message = read()
            }
        }

        if (message.command != A_CNXN) error("Failed to establish A_CNXN")
    }

    fun command(cmd: String, listener: ((ByteArray) -> Unit)? = null) {
        val localId = nextLocalId++
        Log.d(TAG, "Executing command ($localId): $cmd")
        write(A_OPEN, localId, 0, "$cmd\u0000")

        var message: AdbMessage
        while (true) {
            message = read()
            Log.v(TAG, "Recv ($localId): ${AdbProtocol.cmdToStr(message.command)} arg0=${message.arg0} arg1=${message.arg1} data=${message.data_length}")
            
            // In A_OKAY, A_WRTE, A_CLSE: arg1 is the local_id we sent
            if (message.arg1 != localId) {
                Log.w(TAG, "Ignoring message for different localId: expected $localId, got ${message.arg1}")
                continue
            }

            when (message.command) {
                A_OKAY -> break // Stream opened
                A_CLSE -> {
                    Log.d(TAG, "Command failed or closed immediately by remote")
                    return
                }
                else -> {
                    Log.w(TAG, "Unexpected message while waiting for A_OKAY: ${AdbProtocol.cmdToStr(message.command)}")
                }
            }
        }

        val remoteId = message.arg0
        while (true) {
            message = read()
            Log.v(TAG, "Recv ($localId): ${AdbProtocol.cmdToStr(message.command)} arg0=${message.arg0} arg1=${message.arg1} data=${message.data_length}")

            if (message.arg1 != localId) {
                Log.w(TAG, "Ignoring message for different localId in data loop: expected $localId, got ${message.arg1}")
                continue
            }

            when (message.command) {
                A_WRTE -> {
                    if (message.data?.isNotEmpty() == true) {
                        listener?.invoke(message.data)
                    }
                    write(A_OKAY, localId, remoteId)
                }
                A_CLSE -> {
                    write(A_CLSE, localId, remoteId)
                    return
                }
                else -> {
                    Log.w(TAG, "Unexpected message in data loop: ${AdbProtocol.cmdToStr(message.command)}")
                }
            }
        }
    }

    private fun write(command: Int, arg0: Int, arg1: Int, data: ByteArray? = null) = write(AdbMessage(command, arg0, arg1, data))

    private fun write(command: Int, arg0: Int, arg1: Int, data: String) = write(AdbMessage(command, arg0, arg1, data.toByteArray()))

    private fun write(message: AdbMessage) {
        outputStream.write(message.toByteArray())
        outputStream.flush()
    }

    private fun read(): AdbMessage {
        val header = ByteArray(AdbMessage.HEADER_LENGTH)
        inputStream.readFully(header)
        
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int
        
        val data = if (dataLength > 0) {
            val d = ByteArray(dataLength)
            inputStream.readFully(d)
            d
        } else null
        
        val message = AdbMessage(command, arg0, arg1, dataLength, checksum, magic, data)
        message.validateOrThrow()
        return message
    }

    override fun close() {
        runCatching { plainInputStream.close() }
        runCatching { plainOutputStream.close() }
        runCatching { socket.close() }
        if (useTls) {
            runCatching { tlsInputStream.close() }
            runCatching { tlsOutputStream.close() }
            runCatching { tlsSocket.close() }
        }
    }
}
