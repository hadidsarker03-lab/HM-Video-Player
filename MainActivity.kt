package com.example.videoplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyBox: View
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private lateinit var adapter: VideoAdapter

    private val executor = Executors.newSingleThreadExecutor()

    private val permission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadVideos() else showEmpty("ভিডিও দেখার জন্য স্টোরেজ পারমিশন প্রয়োজন")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        emptyBox = findViewById(R.id.emptyBox)
        emptyText = findViewById(R.id.emptyText)
        countText = findViewById(R.id.countText)

        adapter = VideoAdapter { video ->
            openPlayer(video.uri.toString(), video.title)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            permissionLauncher.launch(permission)
        }
        findViewById<Button>(R.id.btnSample).setOnClickListener {
            openPlayer(SAMPLE_URL, "Big Buck Bunny (স্যাম্পল)")
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) loadVideos() else showEmpty("ভিডিও দেখতে অনুমতি দিন")
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun openPlayer(uri: String, title: String) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, uri)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
        })
    }

    // ব্যাকগ্রাউন্ড থ্রেডে ভিডিও স্ক্যান (UI ফ্রিজ হবে না)
    private fun loadVideos() {
        progress.visibility = View.VISIBLE
        emptyBox.visibility = View.GONE
        executor.execute {
            val list = mutableListOf<Video>()
            val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
            )
            try {
                contentResolver.query(
                    collection, projection, null, null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        list += Video(
                            id = id,
                            uri = android.content.ContentUris.withAppendedId(collection, id),
                            title = c.getString(nameCol) ?: "Video",
                            duration = c.getLong(durCol),
                            size = c.getLong(sizeCol)
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            runOnUiThread {
                progress.visibility = View.GONE
                if (list.isEmpty()) {
                    showEmpty("ফোনে কোনো ভিডিও পাওয়া যায়নি")
                } else {
                    emptyBox.visibility = View.GONE
                    countText.text = "${list.size}টি ভিডিও"
                    adapter.submit(list)
                }
            }
        }
    }

    private fun showEmpty(message: String) {
        progress.visibility = View.GONE
        emptyText.text = message
        emptyBox.visibility = View.VISIBLE
        countText.text = ""
        adapter.submit(emptyList())
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    companion object {
        const val SAMPLE_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    }
}
