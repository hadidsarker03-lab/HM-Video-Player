package com.example.videoplayer

import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// চালানো, মেনু (প্রিয়, প্লেলিস্ট, শেয়ার, বিস্তারিত)
object MediaActions {

    fun play(act: AppCompatActivity, queue: List<Video>, video: Video) {
        val index = queue.indexOfFirst { it.id == video.id && it.isAudio == video.isAudio }
            .coerceAtLeast(0)
        Store.addHistory(act, video.isAudio, video.id)
        // বড় তালিকায় Intent-এ শুধু আশেপাশের অংশ পাঠানো হয়
        val from = (index - 500).coerceAtLeast(0)
        val to = (index + 500).coerceAtMost(queue.size)
        val part = queue.subList(from, to)
        act.startActivity(Intent(act, PlayerActivity::class.java).apply {
            putStringArrayListExtra(
                PlayerActivity.EXTRA_URIS, ArrayList(part.map { it.uri.toString() })
            )
            putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, ArrayList(part.map { it.title }))
            putExtra(PlayerActivity.EXTRA_INDEX, index - from)
            putExtra(PlayerActivity.EXTRA_FLAGS, BooleanArray(part.size) { part[it].isAudio })
            putExtra(PlayerActivity.EXTRA_AUDIO, video.isAudio)
        })
    }

    fun showMenu(
        act: AppCompatActivity, anchor: View, v: Video, queue: List<Video>,
        playlistName: String?, onChanged: () -> Unit
    ) {
        val pm = PopupMenu(act, anchor)
        val fav = Store.isFav(act, v.isAudio, v.id)
        pm.menu.add(0, 1, 0, R.string.menu_play)
        pm.menu.add(0, 2, 1, if (fav) R.string.menu_unfav else R.string.menu_fav)
        pm.menu.add(0, 3, 2, R.string.menu_add_playlist)
        if (playlistName != null) pm.menu.add(0, 6, 3, R.string.menu_remove_playlist)
        pm.menu.add(0, 4, 4, R.string.menu_share)
        pm.menu.add(0, 5, 5, R.string.menu_details)
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> play(act, queue, v)
                2 -> {
                    Store.toggleFav(act, v.isAudio, v.id)
                    onChanged()
                }
                3 -> addToPlaylist(act, v)
                4 -> share(act, v)
                5 -> details(act, v)
                6 -> {
                    if (playlistName != null) {
                        Store.removeFromPlaylist(act, v.isAudio, playlistName, v.id)
                        Toast.makeText(act, R.string.removed, Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                }
            }
            true
        }
        pm.show()
    }

    fun addToPlaylist(act: AppCompatActivity, v: Video) {
        val names = Store.playlists(act, v.isAudio).keys.toList()
        val addAndToast = { name: String ->
            Store.addToPlaylist(act, v.isAudio, name, v.id)
            Toast.makeText(act, R.string.added_to_playlist, Toast.LENGTH_SHORT).show()
        }
        if (names.isEmpty()) {
            promptNewPlaylist(act, v.isAudio) { addAndToast(it) }
            return
        }
        val items = (names + act.getString(R.string.new_playlist)).toTypedArray()
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.menu_add_playlist)
            .setItems(items) { _, which ->
                if (which < names.size) addAndToast(names[which])
                else promptNewPlaylist(act, v.isAudio) { addAndToast(it) }
            }
            .show()
    }

    fun promptNewPlaylist(act: AppCompatActivity, audio: Boolean, onCreated: (String) -> Unit) {
        val input = EditText(act)
        input.hint = act.getString(R.string.playlist_name)
        input.setSingleLine()
        val box = FrameLayout(act)
        val m = UiKit.dp(act, 22)
        box.setPadding(m, UiKit.dp(act, 8), m, 0)
        box.addView(input)
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.new_playlist)
            .setView(box)
            .setPositiveButton(R.string.btn_create) { _, _ ->
                val name = input.text.toString().trim()
                if (Store.createPlaylist(act, audio, name)) onCreated(name)
                else Toast.makeText(act, R.string.playlist_exists, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun share(act: AppCompatActivity, v: Video) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (v.isAudio) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, v.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        act.startActivity(Intent.createChooser(send, act.getString(R.string.share_via)))
    }

    private fun details(act: AppCompatActivity, v: Video) {
        val sb = StringBuilder()
        sb.append(act.getString(R.string.d_name)).append(": ").append(v.title).append('\n')
        sb.append(act.getString(R.string.d_size)).append(": ").append(v.size.toSizeString()).append('\n')
        sb.append(act.getString(R.string.d_duration)).append(": ").append(v.duration.toTimeString())
        if (!v.isAudio && v.resolution().isNotEmpty()) {
            sb.append('\n').append(act.getString(R.string.d_resolution)).append(": ").append(v.resolution())
        }
        if (v.folder.isNotEmpty()) {
            sb.append('\n').append(act.getString(R.string.d_folder)).append(": ").append(v.folder)
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.menu_details)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }
}
