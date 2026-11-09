package com.spaceclipboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spaceclipboard.app.R
import com.spaceclipboard.app.domain.model.ReceiverPhase
import com.spaceclipboard.app.ui.screens.DevicesScreen
import com.spaceclipboard.app.ui.screens.HistoryScreen
import com.spaceclipboard.app.ui.screens.HomeScreen
import com.spaceclipboard.app.ui.screens.SettingsScreen
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.DeepSpace
import com.spaceclipboard.app.ui.theme.FocusRing
import com.spaceclipboard.app.ui.theme.Outline
import com.spaceclipboard.app.ui.theme.Panel
import com.spaceclipboard.app.ui.theme.PanelFocused
import com.spaceclipboard.app.ui.theme.PanelRaised
import com.spaceclipboard.app.ui.theme.Success
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

private data class Destination(
    val route: String,
    val label: String,
    val icon: Painter,
)

@Composable
fun SpaceApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: "receiver"
    val snackbar = remember { SnackbarHostState() }
    val receiverState by viewModel.receiverState.collectAsStateWithLifecycle()

    val destinations = listOf(
        Destination("receiver", "Receptor", painterResource(R.drawable.ic_clipboard)),
        Destination("history", "Histórico", painterResource(R.drawable.ic_history)),
        Destination("devices", "Dispositivos", painterResource(R.drawable.ic_devices)),
        Destination("settings", "Ajustes", painterResource(R.drawable.ic_settings)),
    )
    val railDestinations = destinations + Destination(
        "send",
        "Enviar",
        painterResource(R.drawable.ic_send),
    )

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { snackbar.showSnackbar(it) }
    }

    fun navigate(target: String) {
        if (target == route) return
        navController.navigate(target) {
            popUpTo("receiver") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .drawDeepSpaceGlow(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val expanded = maxWidth >= 840.dp
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        destinations = railDestinations,
                        selectedRoute = route,
                        onNavigate = ::navigate,
                        receiverState = receiverState,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 34.dp, vertical = 24.dp),
                    ) {
                        AppNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            onNavigate = ::navigate,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        AppNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            onNavigate = ::navigate,
                        )
                    }
                    BottomNavigation(
                        destinations = destinations,
                        selectedRoute = route,
                        onNavigate = ::navigate,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
        ) { data ->
            androidx.compose.material3.Surface(
                color = PanelRaised,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
            ) {
                Text(
                    text = data.visuals.message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = "receiver",
        modifier = Modifier.fillMaxSize(),
    ) {
        composable("receiver") {
            HomeScreen(
                viewModel = viewModel,
                onOpenHistory = { onNavigate("history") },
                onOpenSend = { onNavigate("send") },
            )
        }
        composable("send") {
            com.spaceclipboard.app.ui.screens.ManualSendScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable("history") {
            HistoryScreen(viewModel = viewModel)
        }
        composable("devices") {
            DevicesScreen(viewModel = viewModel)
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onOpenDevices = { onNavigate("devices") },
            )
        }
    }
}

@Composable
private fun NavigationRail(
    destinations: List<Destination>,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    receiverState: com.spaceclipboard.app.domain.model.ReceiverUiState,
) {
    Column(
        modifier = Modifier
            .width(232.dp)
            .fillMaxHeight()
            .background(DeepSpace.copy(alpha = 0.72f))
            .border(width = 1.dp, color = Outline.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 22.dp),
    ) {
        Brand()
        Spacer(Modifier.height(34.dp))
        Text(
            text = "NAVEGAÇÃO",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            destinations.forEach { destination ->
                RailItem(
                    destination = destination,
                    selected = selectedRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        ReceiverMiniStatus(receiverState)
    }
}

@Composable
private fun Brand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Cyan.copy(alpha = 0.12f))
                .border(1.dp, Cyan.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_clipboard),
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(23.dp),
            )
        }
        Column {
            Text(
                text = "SPACE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
            Text(
                text = "CLIPBOARD",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RailItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background = when {
        focused -> PanelFocused
        selected -> PanelRaised
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(
                if (focused) Modifier.border(2.dp, FocusRing, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Cyan else Color.Transparent),
        )
        Icon(
            painter = destination.icon,
            contentDescription = null,
            tint = if (selected || focused) Cyan else TextMuted,
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = destination.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected || focused) TextPrimary else TextSecondary,
        )
    }
}

@Composable
private fun ReceiverMiniStatus(receiverState: com.spaceclipboard.app.domain.model.ReceiverUiState) {
    val active = receiverState.phase == ReceiverPhase.ACTIVE
    val color = if (active) Success else TextMuted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel.copy(alpha = 0.7f))
            .border(1.dp, Outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
            Text(
                text = if (active) "Receptor ativo" else "Receptor inativo",
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
        Text(
            text = receiverState.network.ipAddress ?: "Sem endereço local",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}

@Composable
private fun BottomNavigation(
    destinations: List<Destination>,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSpace.copy(alpha = 0.96f))
            .border(width = 1.dp, color = Outline.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        destinations.forEach { destination ->
            val selected = selectedRoute == destination.route
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            focused -> PanelFocused
                            selected -> PanelRaised
                            else -> Color.Transparent
                        },
                    )
                    .then(
                        if (focused) Modifier.border(2.dp, FocusRing, RoundedCornerShape(10.dp))
                        else Modifier,
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onNavigate(destination.route) },
                    )
                    .semantics { this.selected = selected },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = destination.icon,
                    contentDescription = null,
                    tint = if (selected) Cyan else TextMuted,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) TextPrimary else TextMuted,
                )
            }
        }
    }
}

private fun Modifier.drawDeepSpaceGlow(): Modifier = this.then(
    Modifier.background(
        Brush.radialGradient(
            colors = listOf(Cyan.copy(alpha = 0.055f), Color.Transparent),
            radius = 1200f,
        ),
    ),
)
