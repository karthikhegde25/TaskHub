// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.audio

import android.content.Context
import android.media.MediaPlayer
import com.kARThik.tasks.R

/** Sounds that can play when a task is ticked. Index 0 is "None"; the default is Hero 1. */
object TickSounds {
    data class Sound(val name: String, val res: Int)

    const val DEFAULT = 1

    val all: List<Sound> = listOf(
        Sound("None", 0),
        Sound("Hero 1", R.raw.hero_1),
        Sound("Hero 2", R.raw.hero_2),
        Sound("Bell", R.raw.bell),
        Sound("Chime", R.raw.chime),
        Sound("Ding", R.raw.ding),
        Sound("Pop", R.raw.pop),
        Sound("Sparkle", R.raw.sparkle),
        Sound("Success", R.raw.success),
        Sound("Harp", R.raw.harp),
        Sound("Marimba", R.raw.marimba),
        Sound("Coin", R.raw.coin),
        Sound("Droplet", R.raw.droplet),
    )

    fun play(ctx: Context, index: Int) {
        val sound = all.getOrNull(index) ?: return
        if (sound.res == 0) return
        try {
            val player = MediaPlayer.create(ctx.applicationContext, sound.res) ?: return
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ -> mp.release(); true }
            player.start()
        } catch (_: Exception) {
        }
    }
}
