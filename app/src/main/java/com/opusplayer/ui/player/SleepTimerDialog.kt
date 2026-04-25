package com.opusplayer.ui.player

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.opusplayer.R
import com.opusplayer.service.MusicService

class SleepTimerDialog : DialogFragment() {

    private var musicService: MusicService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? MusicService.MusicBinder ?: return
            musicService = b.getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()

        // Bind service
        ctx.bindService(
            Intent(ctx, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        val numberPicker = NumberPicker(ctx).apply {
            minValue = 0
            maxValue = 120
            value = 0
            wrapSelectorWheel = false
        }

        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(60, 40, 60, 20)
            addView(numberPicker)
            addView(android.widget.TextView(ctx).apply {
                text = "minutes  (0 = off)"
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 0)
            })
        }

        return AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.sleep_timer))
            .setView(container)
            .setPositiveButton(getString(R.string.set_timer)) { _, _ ->
                val minutes = numberPicker.value
                musicService?.setSleepTimer(minutes)
                val msg = if (minutes == 0) "Sleep timer off"
                          else "Sleep timer set: $minutes min"
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .also { dlg ->
                dlg.setOnShowListener {
                    dlg.getButton(AlertDialog.BUTTON_POSITIVE)
                        ?.setTextColor(resources.getColor(R.color.accent_primary, null))
                    dlg.getButton(AlertDialog.BUTTON_NEGATIVE)
                        ?.setTextColor(resources.getColor(R.color.text_secondary, null))
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
    }
}
