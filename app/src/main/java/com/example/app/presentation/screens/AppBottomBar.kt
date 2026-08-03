package com.example.app.presentation.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.app.R
import com.example.app.presentation.theme.AppColors

enum class BottomNavTab {
    HOME,
    RULES,
    ADD_RULE,
    PROFILE,
}

private val FabSize = 44.dp
private val BarHeight = 56.dp
private val CradleMargin = 6.dp
private val IconSize = 24.dp
private val InactiveIcon = Color(0xFF9CA3AF)

/** 상단 중앙에 FAB가 반쯤 들어가도록 반원 노치가 파인 하단바 모양 */
private class NotchedBottomBarShape(
    private val fabRadiusPx: Float,
    private val cradleMarginPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cradleRadius = fabRadiusPx + cradleMarginPx
        val centerX = size.width / 2f
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(centerX - cradleRadius, 0f)
            arcTo(
                rect = Rect(
                    left = centerX - cradleRadius,
                    top = -cradleRadius,
                    right = centerX + cradleRadius,
                    bottom = cradleRadius,
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false,
            )
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun AppBottomBar(
    selected: BottomNavTab,
    onHome: () -> Unit,
    onRules: () -> Unit,
    onAddRule: () -> Unit,
    onProfile: () -> Unit,
    onChat: () -> Unit,
    modifier: ComposeModifier = ComposeModifier,
) {
    val density = LocalDensity.current
    val fabRadiusPx = with(density) { (FabSize / 2).toPx() }
    val cradleMarginPx = with(density) { CradleMargin.toPx() }
    val totalHeight = BarHeight + FabSize / 2

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        Box(
            modifier = ComposeModifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BarHeight)
                .shadow(10.dp, NotchedBottomBarShape(fabRadiusPx, cradleMarginPx))
                .clip(NotchedBottomBarShape(fabRadiusPx, cradleMarginPx))
                .background(AppColors.Nav)
        ) {
            Row(
                modifier = ComposeModifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavIconTab(
                    iconRes = R.drawable.ic_nav_home,
                    selected = selected == BottomNavTab.HOME,
                    onClick = onHome,
                    contentDescription = "홈",
                    modifier = ComposeModifier.weight(1f),
                )
                NavIconTab(
                    iconRes = R.drawable.ic_nav_calendar,
                    selected = selected == BottomNavTab.RULES,
                    onClick = onRules,
                    contentDescription = "규칙",
                    modifier = ComposeModifier.weight(1f),
                )
                Spacer(modifier = ComposeModifier.width(FabSize + CradleMargin * 2))
                NavIconTab(
                    iconRes = R.drawable.ic_nav_document,
                    selected = selected == BottomNavTab.ADD_RULE,
                    onClick = onAddRule,
                    contentDescription = "등록",
                    modifier = ComposeModifier.weight(1f),
                )
                NavIconTab(
                    iconRes = R.drawable.ic_nav_profile,
                    selected = selected == BottomNavTab.PROFILE,
                    onClick = onProfile,
                    contentDescription = "내정보",
                    modifier = ComposeModifier.weight(1f),
                )
            }
        }

        FloatingActionButton(
            onClick = onChat,
            containerColor = AppColors.Primary,
            contentColor = AppColors.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 10.dp,
            ),
            modifier = ComposeModifier
                .align(Alignment.TopCenter)
                .size(FabSize)
                .shadow(10.dp, CircleShape, spotColor = Color(0x595F33E1)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_add),
                contentDescription = "채팅",
                tint = Color.Unspecified,
                modifier = ComposeModifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun NavIconTab(
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: ComposeModifier = ComposeModifier,
) {
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (selected) AppColors.Primary else InactiveIcon,
            modifier = ComposeModifier
                .size(IconSize)
                .then(
                    if (selected) {
                        ComposeModifier.shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            spotColor = Color(0x595F33E1),
                            ambientColor = Color(0x335F33E1),
                        )
                    } else {
                        ComposeModifier
                    }
                ),
        )
    }
}
