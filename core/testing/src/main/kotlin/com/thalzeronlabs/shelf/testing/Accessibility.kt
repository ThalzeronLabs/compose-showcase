package com.thalzeronlabs.shelf.testing

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import com.google.android.apps.common.testing.accessibility.framework.uielement.ViewHierarchyElement
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/**
 * Runs Google's Accessibility Test Framework on everything on screen: labels for screen readers, 48 dp touch
 * targets, text contrast and the rest of its latest checks. Warnings fail the test too. Every window is checked,
 * so an open bottom sheet (which is its own window) is covered.
 *
 * Needs `@GraphicsMode(GraphicsMode.Mode.NATIVE)` on the test class: without real rendering the framework has no
 * bounds or colours to check and passes everything.
 */
fun ComposeTestRule.assertAccessible() {
    val options = RoborazziATFAccessibilityCheckOptions(
        checker = RoborazziATFAccessibilityChecker(
            preset = AccessibilityCheckPreset.LATEST,
            suppressions = materialDragHandleWidth,
        ),
        failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Warning,
    )
    val windows = onAllNodes(isRoot()).fetchSemanticsNodes().size
    repeat(windows) { onAllNodes(isRoot())[it].checkRoboAccessibility(roborazziATFAccessibilityCheckOptions = options) }
}

/**
 * The one known finding, suppressed on purpose: Material 3's bottom sheet drag handle is 32 dp wide (48 dp tall).
 * It belongs to the compose-bottom-sheets library, where it is to be fixed; drop this once that version is in.
 * Nothing else is suppressed: the same check on any other element still fails.
 */
private val materialDragHandleWidth: Matcher<AccessibilityViewCheckResult> =
    object : TypeSafeMatcher<AccessibilityViewCheckResult>() {
        override fun matchesSafely(result: AccessibilityViewCheckResult): Boolean {
            val check = result.accessibilityHierarchyCheckResult
            if (check.sourceCheckClass != TouchTargetSizeCheck::class.java) return false
            // The flagged node is the handle's touch area; the "Drag handle" description sits a level or two below it.
            return check.element?.let { isOrWraps(it, depth = 3) } == true
        }

        private fun isOrWraps(element: ViewHierarchyElement, depth: Int): Boolean =
            element.contentDescription?.toString().equals("drag handle", ignoreCase = true) ||
                (depth > 0 && (0 until element.childViewCount).any { isOrWraps(element.getChildView(it), depth - 1) })

        override fun describeTo(description: Description) {
            description.appendText("the Material 3 bottom sheet drag handle's touch target width")
        }
    }
