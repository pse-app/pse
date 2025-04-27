@file:KoverIgnore("UI code")
package com.pse_app.client

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pse_app.client.annotations.KoverIgnore
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.ui.view.CreateGroup
import com.pse_app.client.ui.view.CreateGroupView
import com.pse_app.client.ui.view.ExpenseView
import com.pse_app.client.ui.view.GroupSettings
import com.pse_app.client.ui.view.GroupSettingsView
import com.pse_app.client.ui.view.GroupView
import com.pse_app.client.ui.view.JoinGroup
import com.pse_app.client.ui.view.JoinGroupView
import com.pse_app.client.ui.view.Login
import com.pse_app.client.ui.view.LoginView
import com.pse_app.client.ui.view.MainMenu
import com.pse_app.client.ui.view.MainMenuView
import com.pse_app.client.ui.view.PaymentView
import com.pse_app.client.ui.view.Profile
import com.pse_app.client.ui.view.ProfileView
import com.pse_app.client.ui.view.SettleUpGroupSelection
import com.pse_app.client.ui.view.SettleUpGroupSelectionView
import com.pse_app.client.ui.view.SettleUpUserSelection
import com.pse_app.client.ui.view.SettleUpUserSelectionView
import com.pse_app.client.ui.view_model.BaseViewModel
import com.pse_app.client.ui.view_model.create_group.CreateGroupViewModel
import com.pse_app.client.ui.view_model.expense.ExpenseViewModel
import com.pse_app.client.ui.view_model.group.GroupViewModel
import com.pse_app.client.ui.view_model.group_settings.GroupSettingsViewModel
import com.pse_app.client.ui.view_model.join_group.JoinGroupViewModel
import com.pse_app.client.ui.view_model.login.LoginViewModel
import com.pse_app.client.ui.view_model.main_menu.MainMenuViewModel
import com.pse_app.client.ui.view_model.payment.PaymentViewModel
import com.pse_app.client.ui.view_model.profile.ProfileViewModel
import com.pse_app.client.ui.view_model.settle_up_group_selection.SettleUpGroupSelectionViewModel
import com.pse_app.client.ui.view_model.settle_up_user_selection.SettleUpUserSelectionViewModel

/**
 * The contents of the main activity.
 */
@Composable
fun MainActivityContent(
    model: ModelFacade, // application scope
    navController: NavHostController, // `remember` scope
) = NavHost(navController, Login) {
    composable<Login> { backStackEntry ->
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = { LoginViewModel(model) },
        ) {
            LoginView(it)
        }
    }
    composable<JoinGroup> { backStackEntry ->
        val route = backStackEntry.toRoute<JoinGroup>()
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = {
                JoinGroupViewModel(
                    inviteToken = route.inviteToken,
                    facade = model,
                )
            },
        ) {
            JoinGroupView(it)
        }
    }
    composable<Profile> { backStackEntry ->
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = { ProfileViewModel(model) },
        ) {
            ProfileView(it)
        }
    }
    composable<MainMenu> { backStackEntry ->
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = { MainMenuViewModel(model) },
        ) {
            MainMenuView(it)
        }
    }
    composable<CreateGroup> { backStackEntry ->
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = { CreateGroupViewModel(model) },
        ) {
            CreateGroupView(it)
        }
    }
    composable<GroupView> { backStackEntry ->
        val group = backStackEntry.toRoute<GroupView>().groupId
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = {
                GroupViewModel(
                    model = model,
                    activeGroupId = group,
                )
            },
        ) {
            GroupView(it)
        }
    }
    composable<GroupSettings> { backStackEntry ->
        val groupSettings = backStackEntry.toRoute<GroupSettings>()
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = {
                GroupSettingsViewModel(
                    model = model,
                    activeGroupId = groupSettings.groupId,
                )
            },
        ) {
            GroupSettingsView(it)
        }
    }
    composable<ExpenseView> { backStackEntry ->
        val group = backStackEntry.toRoute<ExpenseView>().groupId
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = {
                ExpenseViewModel(
                    model = model,
                    activeGroupId = group,
                )
            },
        ) {
            ExpenseView(it)
        }
    }
    composable<PaymentView> { backStackEntry ->
        val group = backStackEntry.toRoute<PaymentView>().groupId

        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = {
                PaymentViewModel(
                    model = model,
                    activeGroupId = group,
                )
            },
        ) {
            PaymentView(it)
        }
    }
    composable<SettleUpUserSelection> { backStackEntry ->
        AttachViewModel(
            navController = navController,
            vmStoreOwner = backStackEntry,
            vmFactory = { SettleUpUserSelectionViewModel(model) },
        ) {
            SettleUpUserSelectionView(it)
        }
    }
    composable<SettleUpGroupSelection> { backStackEntry ->
        val settleUpGroupSelection = backStackEntry.toRoute<SettleUpGroupSelection>()
        AttachViewModel(
            navController = navController,
            vmFactory = {
                SettleUpGroupSelectionViewModel(
                    model = model,
                    selectedUserId = settleUpGroupSelection.selectedUserId,
                )
            },
            vmStoreOwner = backStackEntry,
        ) {
            SettleUpGroupSelectionView(it)
        }
    }
}

@Composable
private inline fun <reified VM: BaseViewModel> AttachViewModel(
    navController: NavController,
    vmStoreOwner: ViewModelStoreOwner,
    crossinline vmFactory: () -> VM,
    view: @Composable (VM) -> Unit,
) {
    val viewModel = viewModel(vmStoreOwner) { vmFactory() }
    val context = LocalContext.current

    // Whether the onEntry handler has been run
    var hasEntered by rememberSaveable { mutableStateOf(false) }

    // The onEntry handler, runs when the viewmodel is attached to a view
    // for the first time since navigating to it.
    LaunchedEffect(Unit) {
        if (!hasEntered) {
            hasEntered = true
            Log.d("AttachViewModel", "Entering ${viewModel.loggingTag}")
            viewModel.onEntry()
        }
    }

    LaunchedEffect(navController, context) {
        Log.d(
            "Navigation",
            "Attaching NavController ${navController.loggingTag} to ${viewModel.loggingTag} using context ${context.loggingTag}"
        )
        viewModel.navigations.collect {
            it.handle(navController, context)
            hasEntered = false
        }
    }

    // Handle system back presses with the viewModel's back handler.
    BackHandler(onBack = viewModel::goBack)

    view(viewModel)
}
