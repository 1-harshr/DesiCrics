package com.harsh.desicrics.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.component.Avatar
import org.koin.compose.koinInject

/**
 * An avatar with the player's photo or the team's logo when there is one, initials otherwise (and while
 * it loads). [url] is a path on the DesiCrics server ("/images/…") or a full URL such as a Google photo.
 */
@Composable
fun PhotoAvatar(name: String, url: String?, modifier: Modifier = Modifier, size: Int = 40) {
    val config = koinInject<ApiConfig>()
    val resolved = url?.let { if (it.startsWith("/")) config.baseUrl.trimEnd('/') + it else it }
    Avatar(
        name = name,
        size = size,
        modifier = modifier,
        image = resolved?.let { link -> { AsyncImage(model = link, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } },
    )
}
