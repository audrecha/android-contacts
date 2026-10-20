package ru.yandex.practicum.contacts

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.yandex.practicum.contacts.data.models.*
import ru.yandex.practicum.contacts.domain.repository.ContactsRepository
import ru.yandex.practicum.contacts.domain.usecase.GetContactsUseCase
import ru.yandex.practicum.contacts.presentation.main.ContactsScreen
import ru.yandex.practicum.contacts.presentation.main.ContactsViewModel
import ru.yandex.practicum.contacts.presentation.ui.theme.ContactsTheme
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
class ContactsChecklistTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var model: ContactsViewModel
    private val countries = CountryCode.COMMON_CODES.take(3)
    private val contacts = listOf(
        Contact(1, "Alice", "Zulu", "+12025550101", listOf(MessagingApp.TELEGRAM)),
        Contact(2, "Bob", "Alpha", "+442079460001", listOf(MessagingApp.TELEGRAM)),
        Contact(3, "Carol", "Beta", "+4930123456", listOf(MessagingApp.SIGNAL)),
        Contact(4, "Diana", "Gamma", "+33123456789", listOf(MessagingApp.TELEGRAM))
    )

    private fun start() {
        model = ContactsViewModel(GetContactsUseCase(object : ContactsRepository {
            override fun getContacts() = flowOf(contacts)
        }))
        val granted = object : PermissionState {
            override val permission = Manifest.permission.READ_CONTACTS
            override val status = PermissionStatus.Granted
            override fun launchPermissionRequest() = Unit
        }
        compose.setContent { ContactsTheme { ContactsScreen(granted, model) } }
        compose.waitUntil(10_000) { model.contacts.size == contacts.size }
        compose.onNodeWithText("Alice Zulu").assertIsDisplayed()
    }

    private fun open(menu: String) {
        compose.onNodeWithContentDescription(menu).performClick()
        compose.onNode(isDialog()).assertExists()
    }

    private fun selectRow(menu: String, label: String) {
        open(menu)
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    private fun close() {
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "checklist")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun countriesAndMessengers_keepMultipleSelectionAcrossReopening() {
        start()
        countries.forEach { selectRow("Код страны", it.code) }
        compose.runOnIdle { assertEquals(countries.toSet(), model.selectedCountryCodes) }
        open("Код страны")
        repeat(3) { compose.onAllNodes(isToggleable())[it].assertIsOn() }
        screenshot("countries-selected")
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.runOnIdle { assertEquals(setOf(countries[0], countries[2]), model.selectedCountryCodes) }
        open("Код страны")
        compose.onAllNodes(isToggleable())[1].assertIsOff().performClick()
        compose.runOnIdle { assertEquals(countries.toSet(), model.selectedCountryCodes) }

        MessagingApp.entries.take(3).forEach { selectRow("Фильтр", it.name) }
        open("Фильтр")
        repeat(3) { compose.onAllNodes(isToggleable())[it].assertIsOn() }
        screenshot("messengers-selected")
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.runOnIdle { assertEquals(setOf(MessagingApp.TELEGRAM, MessagingApp.VIBER), model.selectedMessagingApps) }
        open("Фильтр")
        compose.onAllNodes(isToggleable())[1].assertIsOff().performClick()
        compose.runOnIdle { assertEquals(MessagingApp.entries.take(3).toSet(), model.selectedMessagingApps) }

        repeat(6) { selectRow("Фильтр", MessagingApp.TELEGRAM.name) }
        compose.runOnIdle { assertEquals(MessagingApp.entries.take(3).toSet(), model.selectedMessagingApps) }
    }

    @Test fun combinedFilters_sortingAndDismissal_preserveState() {
        start()
        countries.forEach { selectRow("Код страны", it.code) }
        selectRow("Фильтр", "TELEGRAM")
        compose.onNodeWithText("Alice Zulu").assertIsDisplayed()
        compose.onNodeWithText("Bob Alpha").assertIsDisplayed()
        compose.onNodeWithText("Carol Beta").assertDoesNotExist()
        compose.onNodeWithText("Diana Gamma").assertDoesNotExist()
        for (order in SortOrder.entries) {
            selectRow("Сортировка", order.displayName)
            val expected = if (order == SortOrder.NAME_ASC || order == SortOrder.LAST_NAME_DESC)
                listOf(1L, 2L) else listOf(2L, 1L)
            compose.runOnIdle {
                assertEquals(expected, model.getFilteredAndSortedContacts().map { it.id })
                assertEquals(countries.toSet(), model.selectedCountryCodes)
                assertEquals(setOf(MessagingApp.TELEGRAM), model.selectedMessagingApps)
            }
            val first = compose.onNodeWithText(if (expected.first() == 1L) "Alice Zulu" else "Bob Alpha").fetchSemanticsNode().boundsInRoot.top
            val second = compose.onNodeWithText(if (expected.last() == 1L) "Alice Zulu" else "Bob Alpha").fetchSemanticsNode().boundsInRoot.top
            org.junit.Assert.assertTrue(first < second)
        }
        screenshot("combined-filters-sorted")
        for ((menu, title) in listOf("Код страны" to "Фильтр по коду страны", "Фильтр" to "Фильтр по приложению", "Сортировка" to "Отсортировать по")) {
            open(menu)
            close()
            open(menu)
            compose.onNode(isDialog()).performTouchInput { click(Offset(5f, 5f)) }
            compose.onNode(isDialog()).assertDoesNotExist()
            open(menu)
            compose.onNodeWithText(title).performTouchInput {
                swipe(center, Offset(center.x, center.y + 2000f), 300)
            }
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(countries.toSet(), model.selectedCountryCodes)
                assertEquals(setOf(MessagingApp.TELEGRAM), model.selectedMessagingApps)
            }
        }
    }
}
