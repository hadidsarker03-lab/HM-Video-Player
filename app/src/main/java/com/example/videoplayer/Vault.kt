package com.example.videoplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.LruCache
import android.widget.ImageView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ভল্টের ফাইল এনক্রিপশন: AES-256-GCM, ৬৪ KB-এর খণ্ডে (chunk)। প্রতিটি খণ্ড আলাদাভাবে যাচাই (authenticated)
// হয়, তাই ফাইলের যেকোনো জায়গায় সরাসরি seek করা যায় (ভিডিও চালানোর সময় দরকার)।
//
// ফাইলের গঠন: [হেডার ৩২ বাইট: "HMV1" + ১৬ বাইট salt + শূন্য] [খণ্ড১ + ১৬ বাইট tag] [খণ্ড২ + tag] ...
// ফাইল-কী = HMAC-SHA256(মাস্টার কী, salt)। nonce = খণ্ডের ক্রমিক নম্বর। AAD = "শেষ খণ্ড কি না" (ছাঁটাই ধরার জন্য)।
object VaultCrypto {
    private val MAGIC = byteArrayOf('H'.code.toByte(), 'M'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
    const val HEADER = 32
    const val CHUNK = 64 * 1024
    private const val TAG = 16
    private const val SALT = 16
    private val rnd = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }

    private fun fileKey(master: ByteArray, salt: ByteArray): SecretKeySpec {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(master, "HmacSHA256"))
        mac.update("hmv1-file".toByteArray())
        return SecretKeySpec(mac.doFinal(salt), "AES")
    }

    private fun nonce(i: Long): ByteArray {
        val n = ByteArray(12)
        for (k in 0 until 8) n[11 - k] = (i shr (8 * k)).toByte()
        return n
    }

    private fun cipher(mode: Int, key: SecretKeySpec, idx: Long, last: Boolean): Cipher {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, key, GCMParameterSpec(128, nonce(idx)))
        c.updateAAD(byteArrayOf(if (last) 1 else 0))
        return c
    }

    private fun readFully(input: InputStream, buf: ByteArray, want: Int) {
        var got = 0
        while (got < want) {
            val n = input.read(buf, got, want - got)
            if (n < 0) throw EOFException("source ended early")
            got += n
        }
    }

    fun encrypt(master: ByteArray, input: InputStream, length: Long, out: OutputStream) {
        val salt = randomBytes(SALT)
        val header = ByteArray(HEADER)
        System.arraycopy(MAGIC, 0, header, 0, 4)
        System.arraycopy(salt, 0, header, 4, SALT)
        out.write(header)
        val key = fileKey(master, salt)
        val n = if (length == 0L) 0L else (length + CHUNK - 1) / CHUNK
        val buf = ByteArray(CHUNK)
        var i = 0L
        while (i < n) {
            val last = i == n - 1
            val want = if (last) (length - i * CHUNK).toInt() else CHUNK
            readFully(input, buf, want)
            out.write(cipher(Cipher.ENCRYPT_MODE, key, i, last).doFinal(buf, 0, want))
            i++
        }
    }

    fun encryptFile(master: ByteArray, src: File, dst: File) {
        src.inputStream().use { input ->
            dst.outputStream().buffered(256 * 1024).use { out ->
                encrypt(master, input, src.length(), out)
            }
        }
    }

    fun encryptBytes(master: ByteArray, data: ByteArray, dst: File) {
        dst.outputStream().buffered().use { out ->
            encrypt(master, ByteArrayInputStream(data), data.size.toLong(), out)
        }
    }

    fun isEncrypted(f: File): Boolean = try {
        f.inputStream().use { i ->
            val h = ByteArray(4)
            i.read(h) == 4 && h.contentEquals(MAGIC)
        }
    } catch (e: Exception) { false }

    // সরাসরি অ্যাক্সেস (random access) পাঠক
    class Reader(file: File, master: ByteArray) : Closeable {
        private val raf: RandomAccessFile
        private val key: SecretKeySpec
        private val encLen: Long = file.length()
        val plainLength: Long
        val chunks: Long

        init {
            if (encLen < HEADER) throw IOException("not a vault file")
            val h = ByteArray(HEADER)
            RandomAccessFile(file, "r").use { it.readFully(h) }
            if (!h.copyOfRange(0, 4).contentEquals(MAGIC)) throw IOException("not a vault file")
            key = fileKey(master, h.copyOfRange(4, 4 + SALT))
            val body = encLen - HEADER
            val full = (CHUNK + TAG).toLong()
            val nFull = body / full
            val rem = body % full
            if (rem != 0L && rem <= TAG) throw IOException("corrupt vault file")
            chunks = nFull + if (rem > 0) 1 else 0
            plainLength = nFull * CHUNK + if (rem > 0) rem - TAG else 0L
            raf = RandomAccessFile(file, "r")
        }

        // i-তম খণ্ড ডিক্রিপ্ট; ভুল কী বা ফাইল বদলানো হলে GeneralSecurityException
        fun chunk(i: Long): ByteArray {
            val full = (CHUNK + TAG).toLong()
            val start = HEADER + i * full
            val last = i == chunks - 1
            val len = if (last) (encLen - start).toInt() else full.toInt()
            val buf = ByteArray(len)
            raf.seek(start)
            raf.readFully(buf)
            return cipher(Cipher.DECRYPT_MODE, key, i, last).doFinal(buf)
        }

        override fun close() {
            try { raf.close() } catch (e: Exception) { }
        }
    }

    fun decryptToFile(master: ByteArray, src: File, dst: File) {
        try {
            Reader(src, master).use { r ->
                dst.outputStream().buffered(256 * 1024).use { out ->
                    var i = 0L
                    while (i < r.chunks) { out.write(r.chunk(i)); i++ }
                }
            }
        } catch (e: GeneralSecurityException) {
            throw IOException(e)
        }
    }

    fun decryptBytes(master: ByteArray, src: File): ByteArray? = try {
        Reader(src, master).use { r ->
            val bo = ByteArrayOutputStream()
            var i = 0L
            while (i < r.chunks) { bo.write(r.chunk(i)); i++ }
            bo.toByteArray()
        }
    } catch (e: Exception) { null }

    // এনক্রিপ্ট করা ফাইলের আসল আকার (যাচাইয়ের জন্য)
    fun plainSize(master: ByteArray, f: File): Long = Reader(f, master).use { it.plainLength }
}

// কী ব্যবস্থাপনা:
//  • মাস্টার কী (৩২ বাইট, র‍্যান্ডম) দিয়ে সব ফাইল এনক্রিপ্ট হয়।
//  • PIN থেকে PBKDF2 দিয়ে বের করা কী মাস্টার কী-কে মুড়ে (wrap) লক ফোল্ডারের ".vault.key"-তে রাখা হয়
//    (অ্যাপ আনইনস্টল করলেও থাকে, তাই আবার ইনস্টলে PIN দিয়ে ফাইল ফেরত পাওয়া যায়)।
//  • একই মাস্টার কী ফোনের Android Keystore-এ মুড়ে রাখা হয়, যাতে ফিঙ্গারপ্রিন্টে খোলা ও প্লেব্যাক চলে।
//    এই কপি ফোন ছেড়ে বের হয় না।
object VaultKeys {
    private const val ALIAS = "hm_vault_ks"
    private const val PREF_KS = "vault_mk_ks"
    private const val KEYFILE = ".vault.key"
    private const val ITER = 200_000
    private const val AAD = "hm-vault-key"

    @Volatile
    private var session: ByteArray? = null

    fun setSession(master: ByteArray) { session = master }

    fun master(c: Context): ByteArray? {
        session?.let { return it }
        val m = fromKeystore(c)
        if (m != null) session = m
        return m
    }

    // ---- Android Keystore ----
    private fun ksKey(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        (ks.getKey(ALIAS, null) as? SecretKey) ?: run {
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            g.init(
                KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            g.generateKey()
        }
    } catch (e: Exception) { null }

    fun storeKeystore(c: Context, master: ByteArray): Boolean = try {
        val k = ksKey() ?: throw IOException("no keystore")
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.ENCRYPT_MODE, k)
        val ct = ci.doFinal(master)
        val out = ci.iv + ct
        AppSettings.prefs(c).edit().putString(PREF_KS, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
        true
    } catch (e: Exception) { false }

    private fun fromKeystore(c: Context): ByteArray? = try {
        val s = AppSettings.prefs(c).getString(PREF_KS, null)
        val k = if (s == null) null else ksKey()
        if (s == null || k == null) null else {
            val all = Base64.decode(s, Base64.NO_WRAP)
            val ci = Cipher.getInstance("AES/GCM/NoPadding")
            ci.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, all, 0, 12))
            ci.doFinal(all, 12, all.size - 12)
        }
    } catch (e: Exception) { null }

    // ---- PIN দিয়ে মোড়া কী ফাইল ----
    private fun file() = File(PrivacyStore.rootPath(), KEYFILE)

    fun hasKeyFile(c: Context): Boolean = PrivacyStore.hasAccess(c) && file().exists()

    private fun readKeyFile(): JSONObject? = try {
        val f = file()
        if (f.exists()) JSONObject(f.readText()) else null
    } catch (e: Exception) { null }

    fun keyFileNumeric(): Boolean = readKeyFile()?.optBoolean("num", true) ?: true

    private fun pickKdf(): String = try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        "PBKDF2WithHmacSHA256"
    } catch (e: Exception) { "PBKDF2WithHmacSHA1" }

    private fun derive(pw: String, salt: ByteArray, iter: Int, kdf: String): ByteArray {
        val spec = PBEKeySpec(pw.toCharArray(), salt, iter, 256)
        try {
            return SecretKeyFactory.getInstance(kdf).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun writeKeyFile(c: Context, master: ByteArray, pw: String, numeric: Boolean): Boolean = try {
        PrivacyStore.dir(c) // ফোল্ডার তৈরি নিশ্চিত
        val kdf = pickKdf()
        val salt = VaultCrypto.randomBytes(16)
        val kek = derive(pw, salt, ITER, kdf)
        val iv = VaultCrypto.randomBytes(12)
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
        ci.updateAAD(AAD.toByteArray())
        val ct = ci.doFinal(master)
        val o = JSONObject().put("v", 1).put("kdf", kdf).put("iter", ITER).put("num", numeric)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
        val f = file()
        val tmp = File(f.parentFile, "$KEYFILE.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) { f.writeText(o.toString()); tmp.delete() }
        true
    } catch (e: Exception) { false }

    // সঠিক PIN হলে মাস্টার কী, ভুল হলে null (GCM tag মেলে না)
    fun unwrap(pw: String): ByteArray? = try {
        val o = readKeyFile() ?: throw IOException("no key file")
        val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP)
        val ct = Base64.decode(o.getString("ct"), Base64.NO_WRAP)
        val kek = derive(pw, salt, o.getInt("iter"), o.getString("kdf"))
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
        ci.updateAAD(AAD.toByteArray())
        ci.doFinal(ct)
    } catch (e: Exception) { null }
}

// ভল্টের এনক্রিপ্ট করা ফাইল প্লেয়ারে সরাসরি চালানোর জন্য: লক ফোল্ডারের ফাইল হলে ডিক্রিপ্ট করে পড়ে,
// অন্য সব ফাইল/লিংক সাধারণ DataSource-এ যায়।
@UnstableApi
class VaultDataSource(private val ctx: Context, private val upstream: DataSource) : DataSource {
    private var reader: VaultCrypto.Reader? = null
    private var uri: Uri? = null
    private var usingUpstream = false
    private var pos = 0L
    private var remaining = 0L
    private var cacheIdx = -1L
    private var cache: ByteArray? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val u = dataSpec.uri
        uri = u
        val path = if (u.scheme == "file") u.path else null
        if (path != null && PrivacyStore.isVaultPath(path) && VaultCrypto.isEncrypted(File(path))) {
            val master = VaultKeys.master(ctx) ?: throw IOException("Vault is locked")
            val r = VaultCrypto.Reader(File(path), master)
            if (dataSpec.position > r.plainLength) {
                r.close()
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            reader = r
            pos = dataSpec.position
            val left = r.plainLength - pos
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) left else minOf(dataSpec.length, left)
            return remaining
        }
        usingUpstream = true
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (usingUpstream) return upstream.read(buffer, offset, length)
        val r = reader ?: throw IOException("closed")
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val idx = pos / VaultCrypto.CHUNK
        if (idx != cacheIdx) {
            cache = try {
                r.chunk(idx)
            } catch (e: GeneralSecurityException) {
                throw IOException(e)
            }
            cacheIdx = idx
        }
        val c = cache!!
        val inChunk = (pos % VaultCrypto.CHUNK).toInt()
        val n = minOf(length.toLong(), remaining, (c.size - inChunk).toLong()).toInt()
        System.arraycopy(c, inChunk, buffer, offset, n)
        pos += n
        remaining -= n
        return n
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (usingUpstream) upstream.responseHeaders else emptyMap()

    override fun close() {
        try {
            if (usingUpstream) upstream.close()
        } finally {
            reader?.close()
            reader = null
            cache = null
            cacheIdx = -1
            usingUpstream = false
            uri = null
        }
    }
}

@UnstableApi
class VaultDataSourceFactory(context: Context) : DataSource.Factory {
    private val app = context.applicationContext
    private val base = DefaultDataSource.Factory(app)
    override fun createDataSource(): DataSource = VaultDataSource(app, base.createDataSource())
}

// লক করা ভিডিওর থাম্বনেইল: লক করার সময় একটি ছোট ছবি তৈরি করে এনক্রিপ্ট করে রাখা হয় (ফাইল নাম + "t")।
object VaultThumbs {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val exec = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    // মূল (এখনও এনক্রিপ্ট না হওয়া) ভিডিও থেকে JPEG বাইট
    fun make(path: String): ByteArray? = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0)
            if (frame == null) null else {
                val w = 480
                val scaled = if (frame.width > w)
                    Bitmap.createScaledBitmap(frame, w, (frame.height * w.toFloat() / frame.width).toInt().coerceAtLeast(1), true)
                else frame
                val bo = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, bo)
                bo.toByteArray()
            }
        } finally {
            r.release()
        }
    } catch (e: Exception) { null }

    fun load(iv: ImageView, uri: Uri, onMissing: () -> Unit) {
        Glide.with(iv).clear(iv)
        val path = uri.path
        if (path == null) { onMissing(); return }
        iv.setTag(R.id.vault_thumb, path)
        val hit = cache.get(path)
        if (hit != null) { iv.setImageBitmap(hit); return }
        iv.setImageDrawable(null)
        val app = iv.context.applicationContext
        exec.execute {
            var bmp: Bitmap? = null
            try {
                val master = VaultKeys.master(app)
                val f = File(path + "t")
                if (master != null && f.exists()) {
                    val bytes = if (VaultCrypto.isEncrypted(f)) VaultCrypto.decryptBytes(master, f) else f.readBytes()
                    if (bytes != null) bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (e: Exception) { }
            val result = bmp
            if (result != null) cache.put(path, result)
            main.post {
                if (iv.getTag(R.id.vault_thumb) == path) {
                    if (result != null) iv.setImageBitmap(result) else onMissing()
                }
            }
        }
    }
}
