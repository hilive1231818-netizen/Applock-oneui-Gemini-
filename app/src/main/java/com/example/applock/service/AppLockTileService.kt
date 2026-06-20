package com.example.applock.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.applock.AuthenticationActivity

class AppLockTileService : TileService() {
    companion object {
        var isPaused = false
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        
        if (isPaused) {
            isPaused = false
            updateTile()
        } else {
            val intent = Intent(this, AuthenticationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("action", "action_toggle_pause")
            }
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        if (isPaused) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "AppLock: Paused"
            tile.subtitle = "Tap to Resume"
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "AppLock: Active"
            tile.subtitle = "Tap to Pause"
        }
        tile.updateTile()
    }
}
