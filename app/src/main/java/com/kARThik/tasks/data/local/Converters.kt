// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.data.local

import androidx.room.TypeConverter

/** Room converters for simple lists. */
class Converters {
    @TypeConverter
    fun fromStringList(v: List<String>): String = v.joinToString("\u001F")

    @TypeConverter
    fun toStringList(s: String): List<String> =
        if (s.isEmpty()) emptyList() else s.split("\u001F")

    @TypeConverter
    fun fromIntList(v: List<Int>): String = v.joinToString(",")

    @TypeConverter
    fun toIntList(s: String): List<Int> =
        if (s.isEmpty()) emptyList() else s.split(",").mapNotNull { it.toIntOrNull() }
}
