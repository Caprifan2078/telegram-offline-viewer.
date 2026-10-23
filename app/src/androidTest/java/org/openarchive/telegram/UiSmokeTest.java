package org.openarchive.telegram;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

@RunWith(AndroidJUnit4.class)
public class UiSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    @Test public void opensHomeAndDrawer(){
        onView(withText("Мои переписки")).check(matches(isDisplayed()));
        onView(withText("☰")).perform(ViewActions.click());
        onView(withText("▧  Фон переписки")).perform(ViewActions.scrollTo()).check(matches(isDisplayed()));
    }
    @Test public void opensWallpaperAndSettings(){
        onView(withText("☰")).perform(ViewActions.click());
        onView(withText("▧  Фон переписки")).perform(ViewActions.scrollTo(),ViewActions.click());
        onView(withText("Фон переписки")).check(matches(isDisplayed()));
    }
}