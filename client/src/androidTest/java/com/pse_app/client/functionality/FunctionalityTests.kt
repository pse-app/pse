package com.pse_app.client.functionality

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pse_app.client.MainActivity
import com.pse_app.client.model.data_layer.ACCESS_TOKEN_KEY
import com.pse_app.client.model.data_layer.REFRESH_TOKEN_KEY
import com.pse_app.client.persistence.Preferences
import com.pse_app.client.ui.view.CommonTestTags
import com.pse_app.client.ui.view.ConfirmationDialogTestTags
import com.pse_app.client.ui.view.CreateGroupViewTestTags
import com.pse_app.client.ui.view.ExpenseViewTestTags
import com.pse_app.client.ui.view.GroupSettingsViewTestTags
import com.pse_app.client.ui.view.GroupViewTestTags
import com.pse_app.client.ui.view.JoinDialogTestTags
import com.pse_app.client.ui.view.JoinGroupViewTestTags
import com.pse_app.client.ui.view.MainMenuViewTestTags
import com.pse_app.client.ui.view.PaymentViewTestTags
import com.pse_app.client.ui.view.ProfileViewTestTags
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * This class contains the system tests.
 * Each method tests a specific function of the App.
 */
@ExperimentalTestApi
@RunWith(AndroidJUnit4::class)
class FunctionalityTests {

    @get:Rule(order = 0)
    val notifyServerAboutTest = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            URL("http://localhost:5001/start?test=" + URLEncoder.encode(description.methodName, StandardCharsets.UTF_8.name())).openStream().close()
            try {
                base.evaluate()
            } finally {
                URL("http://localhost:5001/stop?test=" + URLEncoder.encode(description.methodName, StandardCharsets.UTF_8.name())).openStream().close()
            }
        }
    } }

    @get:Rule(order = 1)
    val loginUser1Rule = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            URL("http://localhost:5001/restart").openStream().close() // Restart the application server
            loginUser1()
            base.evaluate()
        }
    } }

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<MainActivity>()

    private val expiredAccessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJjb20ucHNlX2FwcC5zZXJ2ZXIiLCJpc3MiOiJjb20ucHNlX2FwcC5zZXJ2ZXIiLCJzdWIiOiI2ZWYxMmMzYy0xNTg3LTQ0MDQtOGZmZC1mNTMyN2M5OTVjMTEiLCJleHAiOjE3MzkzNTk5NjR9.1RYd5uaouCrEaLnJS9BRDE2b6IlbTlHMDmPo8p-Yeqo"

    private fun loginUser1() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            Preferences(appContext).set(ACCESS_TOKEN_KEY, expiredAccessToken)
            Preferences(appContext).set(
                REFRESH_TOKEN_KEY,
                "eyJ1c2VySWQiOiJ1c2VyMSIsInNlY3JldCI6InNlY3JldDEifQ==" // RefreshToken(UserId("user1"), "secret1")
            )
        }
    }

    private fun loginUser2() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            Preferences(appContext).set(ACCESS_TOKEN_KEY, expiredAccessToken)
            Preferences(appContext).set(
                REFRESH_TOKEN_KEY,
                "eyJ1c2VySWQiOiJ1c2VyMiIsInNlY3JldCI6InNlY3JldDIifQ==" // RefreshToken(UserId("user2"), "secret2")
            )
        }
    }

    @Test
    fun transactionCanBeViewed() = runTest {
        rule.waitFor(hasText("Test Group")).performClick()
        rule.waitForAny(hasText("Some Expense")).assertExists()
    }

    @Test
    fun logout() = runTest {
        rule.waitFor(hasTestTag(MainMenuViewTestTags.ProfileButton.testTag)).performClick()
        rule.waitFor(hasTestTag(ProfileViewTestTags.LogoutButton.testTag)).performClick()
        rule.waitFor(hasTestTag(ConfirmationDialogTestTags.ConfirmButton.testTag)).performClick()
        // check that the user is not logged in anymore
        rule.waitForAny(hasText("Google", substring = true)).assertExists()
    }

    @Test
    fun deactivateAccount() = runTest {
        rule.waitFor(hasTestTag(MainMenuViewTestTags.ProfileButton.testTag)).performClick()
        rule.waitFor(hasTestTag(ProfileViewTestTags.DeactivateAccountButton.testTag)).performClick()
        rule.waitFor(hasTestTag(ConfirmationDialogTestTags.ConfirmButton.testTag)).performClick()
        rule.waitForAny(hasText("Google", substring = true))
        loginUser2()
        restartMainActivity()
        rule.waitFor(hasText("Test Group")).performClick()
        // assert that user1's display name is now their user id
        rule.waitForAny(hasText("user1")).assertExists()
    }

    private fun restartMainActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(
            context, MainActivity::class.java
        ).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    @Test
    fun createGroup() = runTest {
        val groupName = "Newly created Group"
        rule.waitFor(hasTestTag(MainMenuViewTestTags.CreateGroupButton.testTag)).performClick()
        rule.waitFor(hasTestTag(CreateGroupViewTestTags.GroupNameTextField.testTag))
            .performTextInput(groupName)
        rule.waitFor(hasTestTag(CreateGroupViewTestTags.CreateGroupConfirmButton.testTag))
            .performClick()
        rule.waitFor(hasTestTag(CommonTestTags.BackButton.testTag)).performClick()
        rule.waitForAny(hasText(groupName)).assertExists()
    }

    @Test
    fun viewInviteLink() = runTest {
        rule.waitFor(hasText("Test Group")).performClick()
        rule.waitFor(hasTestTag(GroupViewTestTags.SettingsButton.testTag)).performClick()
        rule.waitForAny(hasText("http://localhost:5000/v1/join/invite_token_for_test_group"))
            .assertExists()
    }

    @Test
    fun regenerateInviteLink() = runTest {
        rule.waitFor(hasText("Test Group")).performClick()
        rule.waitFor(hasTestTag(GroupViewTestTags.SettingsButton.testTag)).performClick()
        rule.waitForAny(hasText("http://localhost:5000/v1/join/invite_token_for_test_group"))
            .assertExists()
        rule.waitFor(hasTestTag(GroupSettingsViewTestTags.RegenerateInviteLinkButton.testTag))
            .performClick()
        rule.waitForNone(hasText("http://localhost:5000/v1/join/invite_token_for_test_group"))
            .assertDoesNotExist()
    }

    @Test
    fun joinGroup() = runTest {
        rule.waitFor(hasTestTag(MainMenuViewTestTags.UseGroupInviteButton.testTag)).performClick()
        rule.waitFor(hasTestTag(JoinDialogTestTags.InviteLinkTextField.testTag))
            .performTextInput("http://localhost:5000/v1/join/invite_token_for_another_group")
        rule.waitFor(hasTestTag(JoinDialogTestTags.OkButton.testTag)).performClick()
        rule.waitFor(hasTestTag(JoinGroupViewTestTags.AcceptButton.testTag)).performClick()
        rule.waitFor(hasTestTag(CommonTestTags.BackButton.testTag)).performClick()
        rule.waitForAny(hasText("Another Group")).assertExists()
    }

    @Test
    fun changeGroupDisplayName() = runTest {
        val groupName = "Edited Group Name"
        rule.waitFor(hasText("Test Group")).performClick()
        rule.waitFor(hasTestTag(GroupViewTestTags.SettingsButton.testTag)).performClick()
        val textField =
            rule.waitFor(hasTestTag(GroupSettingsViewTestTags.GroupNameTextField.testTag))
        textField.performTextClearance()
        textField.performTextInput(groupName)
        rule.waitFor(hasTestTag(GroupSettingsViewTestTags.GroupNameSubmitButton.testTag))
            .performClick()
        rule.waitFor(hasTestTag(CommonTestTags.BackButton.testTag)).performClick()
        rule.waitFor(hasTestTag(CommonTestTags.BackButton.testTag)).performClick()
        rule.waitForAny(hasText(groupName)).assertExists()
    }

    @Test
    fun changeUserDisplayName() = runTest {
        val userName = "Edited User Name"
        rule.waitFor(hasTestTag(MainMenuViewTestTags.ProfileButton.testTag)).performClick()
        rule.waitFor(hasTestTag(ProfileViewTestTags.EditDisplayNameButton.testTag)).performClick()
        val textField = rule.waitFor(hasTestTag(ProfileViewTestTags.DisplayNameTextField.testTag))
        textField.performTextClearance()
        textField.performTextInput(userName)
        rule.waitFor(hasTestTag(ProfileViewTestTags.SubmitDisplayNameButton.testTag)).performClick()
        rule.waitFor(hasTestTag(CommonTestTags.BackButton.testTag)).performClick()
        rule.waitFor(hasTestTag(MainMenuViewTestTags.ProfileButton.testTag)).performClick()
        rule.waitForAny(hasText(userName)).assertExists()
    }

    @Test
    fun leaveGroup() = runTest {
        rule.waitFor(hasText("Test Group")).performClick()
        rule.waitFor(hasTestTag(GroupViewTestTags.SettingsButton.testTag)).performClick()
        rule.waitFor(hasTestTag(GroupSettingsViewTestTags.LeaveGroupButton.testTag)).performClick()
        rule.waitFor(hasTestTag(ConfirmationDialogTestTags.ConfirmButton.testTag)).performClick()
        rule.waitForNone(hasText("Test Group")).assertDoesNotExist()
    }

    @Test
    fun kick() = runTest {
        rule.waitFor(hasText("Test Group")).performClick()
        rule.waitFor(hasTestTag(GroupViewTestTags.SettingsButton.testTag)).performClick()
        rule.waitFor(hasText("User4"))
            .onParent()
            .onChildren()
            .filter(hasTestTag(GroupSettingsViewTestTags.KickButton.testTag)).onFirst()
            .performClick()
        rule.waitFor(hasTestTag(ConfirmationDialogTestTags.ConfirmButton.testTag)).performClick()
        rule.waitFor(hasTestTag(CommonTestTags.BackButton.testTag)).performClick()
        rule.waitFor(hasTestTag(GroupViewTestTags.SettingsButton.testTag)).performClick()
        rule.waitForNone(hasText("User4")).assertDoesNotExist()
    }

    @Test
    fun createExpense() = runTest {
        val expenseName = "Another Expense"
        rule.waitFor(hasText("Test Group")).performClick()
        rule.waitFor(hasTestTag(GroupViewTestTags.CreateTransactionButton.testTag)).performClick()
        rule.waitFor(hasTestTag(ExpenseViewTestTags.NameTextField.testTag))
            .performTextInput(expenseName)
        rule.waitFor(hasTestTag(ExpenseViewTestTags.SplitEvenlyCheckbox.testTag)).performClick()
        rule.waitFor(hasTestTag(ExpenseViewTestTags.TotalAmountTextField.testTag))
            .performTextInput("51")
        rule.waitFor(hasTestTag(ExpenseViewTestTags.SubmitButton.testTag)).assertIsEnabled()
            .performClick()
        rule.waitFor(hasText("Some Expense")).assertExists()
        rule.waitFor(hasText(expenseName)).assertExists()
    }

    @Test
    fun createPayment() = runTest {
        val paymentComment = "Some Payment"
        rule.waitFor(hasText("Test Group")).performClick()
        rule.waitFor(hasTestTag(GroupViewTestTags.CreateTransactionButton.testTag)).performClick()
        rule.waitFor(hasTestTag(ExpenseViewTestTags.PaymentTab.testTag)).performClick()
        rule.waitFor(hasTestTag(PaymentViewTestTags.CommentTextField.testTag))
            .performTextInput(paymentComment)
        rule.waitForAny(hasTestTag(PaymentViewTestTags.AmountTextField.testTag))
            .performTextInput("51")
        rule.waitFor(hasTestTag(PaymentViewTestTags.SubmitButton.testTag)).assertIsEnabled()
            .performClick()
        rule.waitFor(hasText("Some Expense")).assertExists()
        rule.waitFor(hasText(paymentComment, substring = true)).assertExists()
    }

    private fun ComposeTestRule.waitFor(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitForIdle()
        waitUntilExactlyOneExists(matcher, timeoutMillis = 5000)
        waitForIdle()
        return onNode(matcher)
    }

    private fun ComposeTestRule.waitForNone(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitForIdle()
        waitUntilDoesNotExist(matcher, timeoutMillis = 5000)
        waitForIdle()
        return onNode(matcher)
    }

    private fun ComposeTestRule.waitForAny(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitForIdle()
        waitUntilAtLeastOneExists(matcher, timeoutMillis = 5000)
        waitForIdle()
        return onAllNodes(matcher).onFirst()
    }
}
