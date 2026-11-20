package com.truefind.app.capture

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.truefind.app.R

class FactCheckTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.tile_subtitle)
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = ScreenCaptureActivity.createIntent(
            context = this,
            mode = CaptureMode.SINGLE,
            delayMs = 360L // Wait for Quick Settings shade collapse animation
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                301,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
