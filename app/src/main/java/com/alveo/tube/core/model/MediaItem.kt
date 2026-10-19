package com.alveo.tube.core.model

import java.io.Serializable

data class MediaItem(
    val id: String,
    val title: String,
    val channelTitle: String,
    val durationText: String,
    val thumbnailUrl: String
) : Serializable
