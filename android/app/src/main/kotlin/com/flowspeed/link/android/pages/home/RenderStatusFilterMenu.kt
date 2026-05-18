package com.flowspeed.link.android.pages.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.flowspeed.link.android.pages.home.sections.Categories
import com.flowspeed.link.android.pages.home.sections.queues.QueuesSection
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.ActionButton
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myShapes
import com.flowspeed.link.shared.util.ui.theme.mySpacings
import com.flowspeed.lib.util.compose.modifiers.hijackClick
import com.flowspeed.lib.util.compose.resources.myStringResource

@Composable
fun RenderStatusFilterMenu(
    component: HomeComponent,
    modifier: Modifier,
    enter: EnterTransition,
    exit: ExitTransition,
) {
    val isShowingStatusFilterMenu by component.isCategoryFilterShowing.collectAsState()
    AnimatedVisibility(
        modifier = modifier,
        visible = isShowingStatusFilterMenu,
        enter = enter,
        exit = exit,
    ) {
        BackHandler {
            component.setIsCategoryFilterShowing(false)
        }
        val shape = myShapes.defaultRounded
        Column(
            Modifier
                .clip(shape)
                .hijackClick()
                .background(myColors.surface, shape)
                .border(1.dp, myColors.onSurface / 0.2f, shape)
        ) {
            Column(
                Modifier
                    .weight(1f, false)
                    .verticalScroll(rememberScrollState())
            ) {
                Categories(component, Modifier)
                Spacer(
                    Modifier
                        .padding(4.dp)
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(myColors.onSurface / 0.1f)
                )
                QueuesSection(component, Modifier)
            }
            ActionButton(
                text = myStringResource(Res.string.ok),
                onClick = {
                    component.setIsSortMenuShowing(false)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        mySpacings.largeSpace
                    ),
            )
        }
    }
}
