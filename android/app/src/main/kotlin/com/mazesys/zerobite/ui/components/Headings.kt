package com.mazesys.zerobite.ui.components

import androidx.compose.ui.res.pluralStringResource
import com.mazesys.zerobite.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold

/** Small tracked label above titles and as section headings inside cards. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, tint: Color = ZeroBiteTheme.colors.textSecondary) {
    Text(text, modifier, color = tint, style = ZeroBiteTheme.type.caption.semibold(), letterSpacing = 0.6.sp)
}

/** Editorial screen title: orange overline, tightly tracked title, optional count tag, trailing buttons. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    count: Int? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    Row(modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (overline != null) Overline(overline, tint = colors.brandOrange)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    style = type.largeTitle.semibold(),
                    letterSpacing = (-1).sp,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                if (count != null) {
                    val countDescription = pluralStringResource(R.plurals.item_count_badge, count, count)
                    Text(
                        "$count",
                        style = type.caption.semibold(),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(colors.chipFill, RoundedCornerShape(Radius.control))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics { contentDescription = countDescription },
                    )
                }
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Row(
            Modifier.padding(top = if (overline == null) 0.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = trailing,
        )
    }
}

/** Heading between groups of cards: label on the left, optional detail on the right. */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tint: Color = ZeroBiteTheme.colors.textPrimary,
    icon: ImageVector? = null,
) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(title, style = ZeroBiteTheme.type.subheadline.semibold(), color = tint, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = ZeroBiteTheme.type.caption, color = ZeroBiteTheme.colors.textSecondary)
    }
}

/** Inline header on pushed screens and sheets: overline plus a title. */
@Composable
fun DetailTitle(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    overlineTint: Color = ZeroBiteTheme.colors.brandOrange,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (overline != null) Overline(overline, tint = overlineTint)
        Text(
            title,
            style = ZeroBiteTheme.type.title.semibold(),
            letterSpacing = (-0.8).sp,
            color = ZeroBiteTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Small static tag (ingredient names, recipe tags). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val colors = ZeroBiteTheme.colors
    Text(
        text,
        modifier
            .background(tint?.copy(alpha = 0.12f) ?: colors.chipFill, RoundedCornerShape(Radius.control))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = ZeroBiteTheme.type.caption.medium(),
        color = tint ?: colors.textSecondary,
    )
}
