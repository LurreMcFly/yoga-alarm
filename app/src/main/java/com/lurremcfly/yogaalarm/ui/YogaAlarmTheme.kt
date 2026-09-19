package com.lurremcfly.yogaalarm.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.model.YogaPose

internal val Ink = Color(0xFF142018)
internal val MutedInk = Color(0xFF647069)
internal val AlarmLime = Color(0xFFBDEC68)
internal val Forest = Color(0xFF1E3A28)
internal val SoftSurface = Color(0xFFF8FBF7)
internal val Hairline = Color(0xFFDDE4DE)

@Composable
internal fun YogaAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF274D34),
            onPrimary = Color.White,
            secondary = Color(0xFFBDEC68),
            surface = Color(0xFFF8FBF7),
            onSurface = Color(0xFF142018),
        ),
        content = content,
    )
}

@Composable
internal fun PoseGlyph(pose: YogaPose, modifier: Modifier = Modifier) {
    val illustration = when (pose) {
        YogaPose.MOUNTAIN -> R.drawable.pose_mountain
        YogaPose.WARRIOR_TWO -> R.drawable.pose_warrior_two
        YogaPose.TREE -> R.drawable.pose_tree
        YogaPose.CHAIR -> R.drawable.pose_chair
        YogaPose.FORWARD_FOLD -> R.drawable.pose_forward_fold
        YogaPose.TRIANGLE -> R.drawable.pose_triangle
        YogaPose.GODDESS -> R.drawable.pose_goddess
        YogaPose.WIDE_LEG_FOLD -> R.drawable.pose_wide_legged_fold
    }
    Image(
        painter = painterResource(illustration),
        contentDescription = "${pose.displayName} pose illustration",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun yogaSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Forest,
    checkedTrackColor = AlarmLime,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFFB9C1BB),
    uncheckedBorderColor = Color.Transparent,
)
