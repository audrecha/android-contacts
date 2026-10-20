package ru.yandex.practicum.contacts

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import ru.yandex.practicum.contacts.presentation.MainActivity

class MainActivitySmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS))
        .around(compose)

    @Test fun actualApplication_launchesAndOpensAllThreeMenus() {
        compose.onNodeWithText("Контакты").assertIsDisplayed()
        for (menu in listOf("Код страны", "Фильтр", "Сортировка")) {
            compose.onNodeWithContentDescription(menu).performClick()
            compose.onNode(isDialog()).assertExists()
            compose.onNodeWithContentDescription("Закрыть").performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
        }
    }
}
