package com.dozenesstudio.chunkoid.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConversionSettings(
    val inputUri: Uri,
    val inputWorldName: String,
    val targetFormat: String,
    val sourcePlatform: String,
    val maxMemory: Int = 4096,
    val blockMappings: String? = null,
    val worldSettings: String? = null,
    val pruningSettings: String? = null,
    val converterSettings: String? = null,
    val dimensionMappings: String? = null,
    val keepOriginalNbt: Boolean = false
) : Parcelable
