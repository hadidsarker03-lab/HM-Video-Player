package com.example.videoplayer

import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// ডিলিট/রিনেমের জন্য সিস্টেমের অনুমতি-ডায়ালগ চালানোর সাধারণ ভিত্তি
abstract class MediaActivity : AppCompatActivity() {
    private var afterGrant: (() -> Unit)? = null
    private var afterCancel: (() -> Unit)? = null

    private val grantLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            val action = afterGrant
            val cancel = afterCancel
            afterGrant = null
            afterCancel = null
            if (r.resultCode == RESULT_OK) action?.invoke() else cancel?.invoke()
        }

    fun requestGrant(sender: IntentSender, onCancel: (() -> Unit)? = null, then: () -> Unit) {
        afterGrant = then
        afterCancel = onCancel
        try {
            grantLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (e: Exception) {
            afterGrant = null
            afterCancel = null
        }
    }
}
