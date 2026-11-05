package io.github.abhishekkaisar.glassos

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

/**
 * A minimal ADB client for the TV's own ADB daemon over the network (127.0.0.1:5555), so GlassOS can grant itself
 * the permissions only ADB can grant. It speaks the ADB wire protocol directly (CNXN, AUTH, OPEN, WRTE, OKAY,
 * CLSE) and signs with its own 2048-bit RSA key, kept in private storage. The first connection makes the TV ask
 * "Allow USB debugging?"; with "Always allow" ticked, later connections need no prompt.
 *
 * Written here rather than taken from a library so it runs on API 22 to 25 (Fire OS 5 and 6), which have no
 * java.util.Base64.
 */
class AdbClient private constructor(private val socket: Socket) : AutoCloseable {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output: OutputStream = socket.getOutputStream()
    private var nextId = 1

    private class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private fun send(cmd: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        val b = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0
        for (x in data) sum += x.toInt() and 0xFF
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size).putInt(sum).putInt(cmd.inv()).put(data)
        output.write(b.array())
        output.flush()
    }

    private fun read(): Msg {
        val h = ByteArray(24)
        input.readFully(h)
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = b.int; val a0 = b.int; val a1 = b.int; val len = b.int
        if (len < 0 || len > 1 shl 20) throw java.io.IOException("bad adb message")
        val data = ByteArray(len)
        input.readFully(data)
        return Msg(cmd, a0, a1, data)
    }

    /** Runs a shell command and returns its output (stdout and stderr). */
    fun shell(command: String): String {
        val local = nextId++
        send(OPEN, local, 0, "shell:$command\u0000".toByteArray())
        val out = ByteArrayOutputStream()
        var remote = 0
        while (true) {
            val m = read()
            if (m.arg1 != local) continue
            when (m.cmd) {
                OKAY -> remote = m.arg0
                WRTE -> { out.write(m.data); send(OKAY, local, m.arg0) }
                CLSE -> { if (remote != 0) send(CLSE, local, remote); break }
            }
        }
        return out.toString("UTF-8")
    }

    override fun close() { try { socket.close() } catch (e: Throwable) { } }

    /**
     * adbd accepted the TCP connection, then closed it during the handshake ([stage] says when). [quiet]: it did not
     * close but stopped answering before any prompt, which Fire OS builds that block apps also do.
     */
    class Closed(val stage: String, cause: Throwable, val quiet: Boolean = false) :
        java.io.IOException("adbd closed the connection $stage", cause)

    companion object {
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257
        private const val VERSION = 0x01000000
        private const val MAX_DATA = 4096
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3

        /** SHA-1 DigestInfo prefix: ADB signs its 20-byte token as if it were a SHA-1 digest. */
        private val SHA1_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )

        /**
         * Connects and authenticates. [onPrompt] is called when the TV shows its "Allow debugging?" prompt (our
         * key is not trusted yet); the call then waits up to [promptTimeoutMs] for the user to accept.
         */
        fun connect(ctx: Context, host: String = "127.0.0.1", port: Int = 5555, onPrompt: () -> Unit = {}, promptTimeoutMs: Int = 90_000): AdbClient {
            val (priv, pub) = keys(ctx)
            // One retry when adbd hangs up mid-handshake, in case it was still dropping an earlier connection. Not
            // after silence: that would only wait as long again.
            return try {
                connectOnce(priv, pub, host, port, onPrompt, promptTimeoutMs)
            } catch (e: Closed) {
                if (e.quiet) throw e
                Thread.sleep(1500)
                connectOnce(priv, pub, host, port, onPrompt, promptTimeoutMs)
            }
        }

        private fun connectOnce(priv: PrivateKey, pub: ByteArray, host: String, port: Int, onPrompt: () -> Unit, promptTimeoutMs: Int): AdbClient {
            val s = Socket()
            // A refusal comes at once; silence on the TV's own port is adbd holding back, not debugging being off.
            try { s.connect(InetSocketAddress(host, port), 3000) } catch (e: java.net.SocketTimeoutException) {
                try { s.close() } catch (x: Throwable) { }
                WLog.i("adb: no answer to the connection: $e")
                throw Closed("with no answer to the connection", e, quiet = true)
            }
            s.soTimeout = 10_000
            s.tcpNoDelay = true
            val c = AdbClient(s)
            var stage = "before auth"
            try {
                c.send(CNXN, VERSION, MAX_DATA, "host::GlassOS\u0000".toByteArray())
                var sentSignature = false
                while (true) {
                    val m = c.read()
                    when (m.cmd) {
                        CNXN -> { s.soTimeout = 20_000; return c }
                        AUTH -> if (m.arg0 == AUTH_TOKEN) {
                            if (!sentSignature) {
                                sentSignature = true
                                c.send(AUTH, AUTH_SIGNATURE, 0, sign(priv, m.data))
                                stage = "after the signature"
                            } else {
                                // Our key is unknown to the TV: send it, which shows the prompt.
                                c.send(AUTH, AUTH_RSAPUBLICKEY, 0, pub)
                                stage = "after the key"
                                s.soTimeout = promptTimeoutMs
                                onPrompt()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                c.close()
                // adbd hanging up mid-handshake: Fire OS since 7.6.6.9 and 8.1.0.3 (and late Fire OS 6) does this to
                // every connection from an app on the TV itself.
                if (e is java.io.EOFException || (e is java.net.SocketException && e !is java.net.ConnectException)) {
                    WLog.i("adb: closed $stage: $e")
                    throw Closed(stage, e)
                }
                // Only a timeout after our key is the person not answering the prompt; before it, adbd went quiet.
                if (e is java.net.SocketTimeoutException && stage != "after the key") {
                    WLog.i("adb: no answer $stage: $e")
                    throw Closed("$stage, with no answer", e, quiet = true)
                }
                throw e
            }
        }

        private fun sign(key: PrivateKey, token: ByteArray): ByteArray {
            // PKCS#1 v1.5 type 1 padding, built by hand so every provider gives the same result.
            val k = 256
            val t = SHA1_PREFIX + token
            val block = ByteArray(k)
            block[0] = 0; block[1] = 1
            for (i in 2 until k - t.size - 1) block[i] = 0xFF.toByte()
            block[k - t.size - 1] = 0
            System.arraycopy(t, 0, block, k - t.size, t.size)
            val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher.doFinal(block)
        }

        /** Loads the RSA key pair, or makes one on first use. Returns the private key and the public key in ADB's format. */
        private fun keys(ctx: Context): Pair<PrivateKey, ByteArray> {
            val dir = File(ctx.filesDir, "adb").apply { mkdirs() }
            val privFile = File(dir, "adbkey.pk8")
            val pubFile = File(dir, "adbkey.pub")
            if (privFile.exists() && pubFile.exists()) {
                try {
                    val priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
                    return priv to pubFile.readBytes()
                } catch (e: Throwable) { }
            }
            val g = KeyPairGenerator.getInstance("RSA")
            g.initialize(2048)
            val kp = g.generateKeyPair()
            val pub = adbPublicKey(kp.public as RSAPublicKey)
            privFile.writeBytes(kp.private.encoded)
            pubFile.writeBytes(pub)
            return kp.private to pub
        }

        /** Android's RSAPublicKey struct (modulus words, n0inv, n, rr, exponent), base64, then " name\0". */
        private fun adbPublicKey(key: RSAPublicKey): ByteArray {
            val words = 64
            val n = key.modulus
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = n.mod(r32).modInverse(r32).negate().mod(r32).toLong().toInt()
            val rr = BigInteger.ONE.shiftLeft(4096).mod(n)
            val b = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(words).putInt(n0inv)
            putWords(b, n, words)
            putWords(b, rr, words)
            b.putInt(key.publicExponent.toInt())
            val text = android.util.Base64.encodeToString(b.array(), android.util.Base64.NO_WRAP) + " GlassOS@tv\u0000"
            return text.toByteArray()
        }

        private fun putWords(b: ByteBuffer, v: BigInteger, words: Int) {
            val mask = BigInteger.valueOf(0xFFFFFFFFL)
            var x = v
            repeat(words) {
                b.putInt(x.and(mask).toLong().toInt())
                x = x.shiftRight(32)
            }
        }
    }
}
