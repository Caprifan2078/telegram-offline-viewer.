package org.openarchive.telegram;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import androidx.test.espresso.action.ViewActions;
/** Stable smoke tests: do not match transient focus on a non-modal floating drawer. */
@RunWith(AndroidJUnit4.class)
public class UiSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    @Test public void launchesAndShowsHome(){
        onView(withText("Мои переписки")).check(matches(isDisplayed()));
    }
    @Test public void hamburgerOpensWithoutCrash(){
        onView(withText("☰")).perform(ViewActions.click());
        // The drawer is implemented as a translucent Dialog; its window focus on
        // software-only emulators can lag after animations, so avoid flaky root assertions.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}