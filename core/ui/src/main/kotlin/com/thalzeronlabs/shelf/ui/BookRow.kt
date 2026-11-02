package com.thalzeronlabs.shelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil3.compose.AsyncImage

/**
 * One book: cover, title and a subtitle (author and year), plus a trailing slot for an action or a status badge.
 * It takes plain values rather than a model class, so the design system does not depend on the data layer.
 *
 * For screen readers the row is one item that reads "title, [subtitleDescription], [stateDescription]" and
 * announces its action as "double tap to [onClickLabel]". [subtitleDescription] is the subtitle as it should be
 * spoken; it is set on the whole row, because a description on the subtitle alone would replace the title in
 * what the reader says. The cover is decorative (the title follows it), and whatever [trailing] shows should be
 * described by [stateDescription] and hidden from the reader.
 */
@Composable
fun BookRow(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    stateDescription: String? = null,
    subtitleDescription: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    // With a large system font size there is no room for the badge beside the text: it moves below it, and the
    // title and byline get the full width (and more lines) instead of breaking mid-word.
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .semantics {
                subtitleDescription?.let { contentDescription = "$title, $it" }
                stateDescription?.let { this.stateDescription = it }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 54.dp, height = 80.dp),
                )
            } else {
                Text(
                    title.take(1),
                    Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (largeFont) 4 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Room to wrap, so a large font size moves the year to the next line instead of cutting it off.
                maxLines = if (largeFont) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (largeFont) {
                Spacer(Modifier.height(8.dp))
                trailing()
            }
        }
        if (!largeFont) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** From this system font scale up (150 %), [BookRow] stacks its badge under the text. */
const val LARGE_FONT_SCALE = 1.5f

@Composable
fun Badge(text: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
