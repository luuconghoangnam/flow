package com.flowspeed.link.android.pages.onboarding.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowspeed.link.android.pages.onboarding.StartUpPageActions
import com.flowspeed.link.android.pages.onboarding.StartUpPageHeader
import com.flowspeed.link.android.pages.onboarding.StartUpPageTemplate
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.widget.ActionButton
import com.flowspeed.link.shared.ui.widget.PrimaryMainActionButton
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.shared.util.ui.LocalContentColor
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myShapes
import com.flowspeed.link.shared.util.ui.theme.mySpacings
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.link.shared.util.ui.widget.MyIcon
import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.resources.myStringResource


@Composable
fun PermissionsPage(
    component: PermissionComponent,
) {
    val currentPermission by component.currentPermission.collectAsState()
    StartUpPageTemplate(
        header = {
            StartUpPageHeader(
                title = Res.string.permissions.asStringSource(),
                onBackPressed = component::goToPreviousPermissionPage,
            )
        },
        content = {
            AnimatedContent(
                targetState = currentPermission,
            ) {
                val modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                when (it) {
                    is PermissionsPageSteps.AtPermission -> {
                        RenderPermissionContent(it.appPermission, modifier)
                    }

                    PermissionsPageSteps.Done -> {
                        RenderDonePermissionGranting(modifier)
                    }

                    PermissionsPageSteps.Initial -> {
                        RenderInitialPermissionGranting(modifier)
                    }
                }
            }
        },
        actions = {
            StartUpPageActions {
                AnimatedContent(
                    targetState = currentPermission,
                ) {
                    val modifier = Modifier
                        .fillMaxWidth()
                    when (it) {
                        is PermissionsPageSteps.AtPermission -> {
                            RenderPermissionActions(
                                component, it.appPermission, modifier,
                            )
                        }

                        PermissionsPageSteps.Done -> {
                            PrimaryMainActionButton(
                                text = myStringResource(Res.string.lets_go),
                                onClick = component::goToNextPermissionPage,
                                modifier = modifier,
                            )
                        }

                        PermissionsPageSteps.Initial -> {
                            PrimaryMainActionButton(
                                text = myStringResource(Res.string.next),
                                onClick = component::goToNextPermissionPage,
                                modifier = modifier,
                            )
                        }
                    }
                }
            }
        },

    )
}

@Composable
fun RenderInitialPermissionGranting(
    modifier: Modifier,
) {
    BasePermissionPageContent(
        title = Res.string.permissions_initial_title.asStringSource(),
        description = Res.string.permissions_initial_description.asStringSource(),
        icon = MyIcons.permission,
        modifier = modifier,
    )
}

@Composable
fun RenderDonePermissionGranting(
    modifier: Modifier,
) {
    BasePermissionPageContent(
        title = Res.string.permissions_done_title.asStringSource(),
        description = Res.string.permissions_done_description.asStringSource(),
        icon = MyIcons.check,
        modifier = modifier,
        iconColor = myColors.success,
    )
}


@Composable
fun RenderPermissionContent(
    appPermission: AppPermission,
    modifier: Modifier,
) {
    BasePermissionPageContent(
        title = appPermission.title,
        description = appPermission.description,
        modifier = modifier,
        icon = appPermission.icon,
    )
}

@Composable
fun RenderPermissionActions(
    permissionComponent: PermissionComponent,
    appPermission: AppPermission,
    modifier: Modifier,
) {
    var rejectedOnce by remember(appPermission) { mutableStateOf(false) }
    val permissionState = rememberAppPermissionState(appPermission) {
        if (!it) {
            rejectedOnce = true
        }
    }
    val userProbablyPressedOnDontAskAgain = !permissionState.requiresRational && rejectedOnce
    Column(modifier) {
        val permissionStatus = permissionState.permissionStatus
        val isGranted = permissionStatus is PermissionStatus.Granted
        AnimatedVisibility(isGranted) {
            Text(
                myStringResource(
                    Res.string.permission_granted,
                ),
                color = myColors.success,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(
                        start = mySpacings.mediumSpace,
                        bottom = mySpacings.mediumSpace,
                    )
            )
        }
        AnimatedVisibility(
            userProbablyPressedOnDontAskAgain && !isGranted && !appPermission.isOptional
        ) {
            Text(
                myStringResource(
                    Res.string.permission_not_granted,
                ),
                color = myColors.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(
                        start = mySpacings.mediumSpace,
                        bottom = mySpacings.mediumSpace,
                    )
            )
        }
        val activity = requireNotNull(LocalActivity.current) {
            "Activity is required to open app details"
        }
        if (permissionStatus is PermissionStatus.NotGranted) {
            PrimaryMainActionButton(
                text = myStringResource(
                    if (userProbablyPressedOnDontAskAgain) {
                        Res.string.open_settings
                    } else {
                        Res.string.give_permission
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (userProbablyPressedOnDontAskAgain) {
                        openApplicationDetailsInSettings(activity)
                    } else {
                        permissionState.launchRequest()
                    }
                },
            )
        } else {
            PrimaryMainActionButton(
                text = myStringResource(Res.string.next),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    permissionComponent.goToNextPermissionPage()
                },
            )
        }
        if (appPermission.isOptional && permissionStatus !is PermissionStatus.Granted) {
            Spacer(Modifier.height(mySpacings.mediumSpace))
            ActionButton(
                text = myStringResource(Res.string.skip),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    permissionComponent.goToNextPermissionPage()
                },
            )
        }
    }
}

fun openApplicationDetailsInSettings(activity: Activity) {
    activity.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
    )
}

@Composable
private fun BasePermissionPageContent(
    title: StringSource,
    description: StringSource,
    icon: IconSource,
    iconColor: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        val shape = RoundedCornerShape(24.dp)
        MyIcon(
            icon = icon,
            null,
            Modifier
                .weight(1f)
                .wrapContentHeight()
                .align(Alignment.CenterHorizontally)
                .clip(shape)
                .background(color = myColors.menuGradientBackground)
                .border(1.dp, myColors.menuBorderColor / 0.1f, shape)
                .padding(16.dp)
                .size(72.dp),
            tint = iconColor
        )
        Spacer(Modifier.height(mySpacings.largeSpace))
        Text(
            text = title.rememberString(),
            fontWeight = FontWeight.Bold,
            fontSize = myTextSizes.x2l,
        )
        Spacer(Modifier.height(mySpacings.largeSpace))
        Text(
            text = description.rememberString(),
            fontWeight = FontWeight.Normal,
            fontSize = myTextSizes.base,
        )
    }
}
