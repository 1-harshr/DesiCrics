package com.harsh.desicrics.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.code_share
import com.harsh.desicrics.ui.system.rememberShareText
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A player's or team's 8-digit public code, in two groups of four so it reads out easily: "4821 7390". */
fun formatPublicCode(code: String): String = code.chunked(4).joinToString(" ")

/** The code on a card of its own, with a button to share [shareText] (WhatsApp and the like). */
@Composable
fun PublicCodeCard(code: String, label: String, shareText: String) {
    val share = rememberShareText()
    ListCard {
        ListRow(
            title = formatPublicCode(code),
            subtitle = label,
            leading = { IconTile(DesiIcons.Badge) },
            trailing = {
                IconButton({ share(shareText) }) {
                    Icon(painterResource(DesiIcons.Share), stringResource(Res.string.code_share), tint = DesiTheme.colors.brand, modifier = Modifier.size(DesiTheme.size.iconSmall))
                }
            },
            onClick = { share(shareText) },
        )
    }
}
