package com.ufuq.dream

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Explicit, user-selected document only. No broad storage permission, network, or retained URI grant. */
internal object LocalVideoImport {
    const val MAX_BYTES = 128L * 1024 * 1024
    fun copyBounded(input: InputStream, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                check(!Thread.currentThread().isInterrupted) { "Canceled" }
                val n = input.read(buffer)
                if (n < 0) break
                count += n
                require(count <= MAX_BYTES) { "Video exceeds 128 MiB" }
                digest.update(buffer, 0, n); output.write(buffer, 0, n)
            }
        }
        require(count >= 1024) { "Empty video" }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Runs on an Activity-owned worker. Only catalog publication makes a file visible to playback. */
    @Synchronized fun import(context: Context, uri: Uri): String {
        require(uri.scheme == "content") { "Choose a document" }
        val directory = File(context.filesDir, "personal-motion").apply { mkdirs() }
        require(android.os.StatFs(directory.absolutePath).availableBytes > MAX_BYTES + 32L * 1024 * 1024) { "Not enough free space" }
        val catalog = File(directory, "catalog.json")
        val entries = if (catalog.isFile) {
            require(catalog.length() in 1..32768)
            JSONObject(catalog.readText()).getJSONArray("clips")
        } else JSONArray()
        require(entries.length() < 12) { "Local library is full" }
        val temporary = File.createTempFile("import-", ".tmp", directory)
        var video: File? = null; var poster: File? = null; var published = false
        try {
            val hash = context.contentResolver.openInputStream(uri)?.use { copyBounded(it, temporary) }
                ?: error("Cannot open document")
            val stem = "local_${hash.take(24)}"
            val name = "$stem.mp4"
            for (i in 0 until entries.length()) if (entries.getJSONObject(i).getString("fileName") == name) return "موجود في مكتبتك"
            validate(temporary)
            require(MotionSupport.decoder() != null) { "Hardware playback unavailable" }
            poster = File(directory, "$stem.jpg")
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(temporary.absolutePath)
                val frame = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360)
                    ?: error("Cannot read poster")
                try { poster.outputStream().use { check(frame.compress(Bitmap.CompressFormat.JPEG, 85, it)) } }
                finally { frame.recycle() }
            } finally { retriever.release() }
            check(!Thread.currentThread().isInterrupted) { "Canceled" }
            video = File(directory, name)
            check(!video.exists() && temporary.renameTo(video))
            entries.put(JSONObject().put("fileName", name).put("category", -1)
                .put("posterId", "alula_1").put("title", "فيديو محلي ${entries.length() + 1}"))
            val json = JSONObject().put("usage", "personal-only").put("clips", entries)
            val atomic = AtomicFile(catalog)
            val stream = atomic.startWrite()
            try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
            catch (error: Exception) { atomic.failWrite(stream); throw error }
            published = true
            PersonalMotionStore.reset(); MediaLibrary.initialize(context)
            return "أُضيف الفيديو؛ اختر تلقائي ثم الخلفيات المتحركة واحفظ إعداداتك"
        } finally {
            temporary.delete()
            if (!published) { video?.delete(); poster?.delete() }
        }
    }

    private fun validate(file: File) {
        val header = ByteArray(12)
        file.inputStream().use { require(it.read(header) == header.size) }
        require(String(header, 4, 4, Charsets.US_ASCII) == "ftyp") { "Use an MP4 container" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            require(extractor.trackCount == 1) { "Use a silent, video-only MP4" }
            val format = extractor.getTrackFormat(0)
            require(format.getString(MediaFormat.KEY_MIME) == "video/avc") { "Use H.264 video" }
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            require(MotionProfile.accepts(width, height, format.getLong(MediaFormat.KEY_DURATION)))
            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) require(format.getInteger(MediaFormat.KEY_FRAME_RATE) in 1..30)
            extractor.selectTrack(0)
            var frames = 0; var previous = -1L
            while (extractor.sampleTime >= 0) {
                check(!Thread.currentThread().isInterrupted) { "Canceled" }
                val time = extractor.sampleTime
                // Permit timestamp rounding at 29.97/30 fps, reject high-frame-rate streams.
                if (previous >= 0 && time > previous) require(time - previous >= 32_000)
                previous = time
                require(++frames <= 5500)
                if (!extractor.advance()) break
            }
            require(frames > 0)
        } finally { extractor.release() }
    }
}
