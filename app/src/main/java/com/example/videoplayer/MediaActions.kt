package com.example.videoplayer

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
        act: MediaActivity, anchor: View, v: Video, queue: List<Video>,
        playlistName: String?, onSelect: () -> Unit = {}, onChanged: () -> Unit
    ) {
        val fav = Store.isFav(act, v.isAudio, v.id)
        val items = ArrayList<Pair<Int, Int>>()
        items.add(1 to R.string.menu_play)
        items.add(2 to if (fav) R.string.menu_unfav else R.string.menu_fav)
        items.add(3 to R.string.menu_add_playlist)
        if (playlistName != null) items.add(6 to R.string.menu_remove_playlist)
        items.add(4 to R.string.menu_share)
        items.add(5 to R.string.menu_details)
        items.add(7 to R.string.menu_rename)
        items.add(8 to R.string.menu_select)
        items.add(10 to R.string.menu_hide)
        items.add(9 to R.string.btn_delete)
        UiKit.borderedMenu(act, anchor, items) { id ->
            when (id) {
                1 -> play(act, queue, v)
                2 -> {
                    Store.toggleFav(act, v.isAudio, v.id)
                    onChanged()
                }
                3 -> addToPlaylist(act, v)
                4 -> share(act, v)
                5 -> details(act, v)
                7 -> rename(act, v) { onChanged() }
                8 -> onSelect()
                9 -> delete(act, listOf(v)) { onChanged() }
                10 -> PrivacyOps.hide(act, listOf(v)) { onChanged() }
                6 -> {
                    if (playlistName != null) {
                        Store.removeFromPlaylist(act, v.isAudio, playlistName, v.id)
                        Toast.makeText(act, R.string.removed, Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                }
            }
        }
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

    fun share(act: AppCompatActivity, v: Video) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (v.isAudio) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, v.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        act.startActivity(Intent.createChooser(send, act.getString(R.string.share_via)))
    }

    fun details(act: AppCompatActivity, v: Video) {
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

    // ---------- ডিলিট (একটি বা একাধিক) ----------
    fun delete(act: MediaActivity, items: List<Video>, onDone: () -> Unit) {
        if (items.isEmpty()) return
        val msg = if (items.size == 1) act.getString(R.string.delete_file_q, items[0].title)
        else act.getString(R.string.delete_files_q, items.size)
        MaterialAlertDialogBuilder(act)
            .setMessage(msg)
            .setPositiveButton(R.string.btn_delete) { _, _ -> runDelete(act, items, onDone) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun runDelete(act: MediaActivity, items: List<Video>, onDone: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pi = MediaStore.createDeleteRequest(act.contentResolver, items.map { it.uri })
                act.requestGrant(pi.intentSender) { finishChange(act, items.size, R.string.deleted, onDone) }
            } catch (e: Exception) {
                Toast.makeText(act, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }
        deleteLegacy(act, items, 0, 0, onDone)
    }

    // Android 10 ও আগের জন্য: একটি একটি করে মোছা; দরকারে সিস্টেম অনুমতি চায়
    fun deleteLegacy(
        act: MediaActivity, items: List<Video>, from: Int, doneSoFar: Int, onDone: () -> Unit,
        msg: Int = R.string.deleted
    ) {
        var i = from
        var ok = doneSoFar
        while (i < items.size) {
            try {
                if (act.contentResolver.delete(items[i].uri, null, null) > 0) ok++
                i++
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    val next = i
                    val soFar = ok
                    act.requestGrant(e.userAction.actionIntent.intentSender) {
                        deleteLegacy(act, items, next, soFar, onDone, msg)
                    }
                    return
                }
                i++
            } catch (e: Exception) {
                i++
            }
        }
        finishChange(act, ok, msg, onDone)
    }

    fun finishChange(act: MediaActivity, count: Int, msg: Int, onDone: () -> Unit) {
        Thread {
            Library.reload(act.applicationContext)
            act.runOnUiThread {
                if (count > 0 && msg != 0) Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
                onDone()
            }
        }.start()
    }

    // ---------- রিনেম ----------
    fun rename(act: MediaActivity, v: Video, onDone: () -> Unit) {
        val input = EditText(act)
        input.setText(v.title)
        input.setSingleLine()
        input.selectAll()
        val box = FrameLayout(act)
        val m = UiKit.dp(act, 22)
        box.setPadding(m, UiKit.dp(act, 8), m, 0)
        box.addView(input)
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.menu_rename)
            .setView(box)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val name = input.text.toString().trim().replace('/', '_')
                if (name.isNotEmpty() && name != v.title) runRename(act, v, name, onDone)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun displayExt(act: MediaActivity, uri: Uri): String {
        return try {
            act.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getString(0) ?: ""
                        if (n.contains('.')) "." + n.substringAfterLast('.') else ""
                    } else ""
                } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun runRename(act: MediaActivity, v: Video, name: String, onDone: () -> Unit) {
        val full = name + displayExt(act, v.uri)
        val doUpdate = {
            try {
                val cv = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, full) }
                val n = act.contentResolver.update(v.uri, cv, null, null)
                finishChange(act, n, R.string.renamed, onDone)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    act.requestGrant(e.userAction.actionIntent.intentSender) { runRename(act, v, name, onDone) }
                } else Toast.makeText(act, R.string.action_failed, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(act, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pi = MediaStore.createWriteRequest(act.contentResolver, listOf(v.uri))
                act.requestGrant(pi.intentSender) { doUpdate() }
            } catch (e: Exception) {
                Toast.makeText(act, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
        } else doUpdate()
    }

    // ---------- একাধিকের জন্য প্রিয়/প্লেলিস্ট/শেয়ার ----------
    fun favoriteMany(act: MediaActivity, items: List<Video>) {
        if (items.isEmpty()) return
        val allFav = items.all { Store.isFav(act, it.isAudio, it.id) }
        for (v in items) {
            val fav = Store.isFav(act, v.isAudio, v.id)
            if (allFav == fav) Store.toggleFav(act, v.isAudio, v.id)
        }
        Toast.makeText(act, if (allFav) R.string.removed else R.string.added_to_playlist, Toast.LENGTH_SHORT).show()
    }

    fun addManyToPlaylist(act: MediaActivity, items: List<Video>, onDone: () -> Unit) {
        if (items.isEmpty()) return
        val audio = items[0].isAudio
        val list = items.filter { it.isAudio == audio }
        val addAll = { name: String ->
            list.forEach { Store.addToPlaylist(act, audio, name, it.id) }
            Toast.makeText(act, R.string.added_to_playlist, Toast.LENGTH_SHORT).show()
            onDone()
        }
        val names = Store.playlists(act, audio).keys.toList()
        if (names.isEmpty()) {
            promptNewPlaylist(act, audio) { addAll(it) }
            return
        }
        val labels = (names + act.getString(R.string.new_playlist)).toTypedArray()
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.menu_add_playlist)
            .setItems(labels) { _, which ->
                if (which < names.size) addAll(names[which])
                else promptNewPlaylist(act, audio) { addAll(it) }
            }
            .show()
    }

    fun shareMany(act: MediaActivity, items: List<Video>) {
        if (items.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        act.startActivity(Intent.createChooser(send, act.getString(R.string.share_via)))
    }
}
