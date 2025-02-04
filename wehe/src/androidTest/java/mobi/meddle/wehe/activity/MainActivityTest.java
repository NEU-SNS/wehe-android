package mobi.meddle.wehe.activity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.Manifest;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import mobi.meddle.wehe.R;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityTest {

    private UiDevice device;

    private boolean skipSetup = false;

    @Rule
    public TestRule skipRule = (base, description) -> new Statement() {
        @Override
        public void evaluate() throws Throwable {
            skipSetup = description.getAnnotation(SkipSetup.class) != null;
            base.evaluate();
        }
    };

    @Rule
    public GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION);

    @Before
    public void setup() throws UiObjectNotFoundException {
        if(!skipSetup) {
            ActivityScenario.launch(MainActivity.class);

            // Click "Accept" on the first AlertDialog
            onView(withText("Accept")).perform(click());

            // Click "OK" on the second AlertDialog
            onView(withText("OK")).perform(click());

            device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

            // Handle system location permission dialog
            UiObject allowWhileUsingApp = device.findObject(new UiSelector().text("While using the app"));
            if (allowWhileUsingApp.exists()) {
                allowWhileUsingApp.click();
            }
        }
    }

    @Test
    public void testNavigationDrawerOpens() throws UiObjectNotFoundException {

        // Open the drawer
        onView(withContentDescription(R.string.nav_app_bar_open_drawer_description)).perform(click());

        // Verify that navigation view is displayed
        onView(withId(R.id.nav_view)).check(matches(isDisplayed()));
    }

    @Test
    public void testNavigationToAboutFragment() {
        // Open the drawer
        onView(withContentDescription(R.string.nav_app_bar_open_drawer_description)).perform(click());

        // Click on About menu item
        onView(withId(R.id.nav_about)).perform(click());

        // Verify that About fragment is displayed
        onView(withText("Why Wehe")).check(matches(isDisplayed()));
    }

    @Test
    public void testNavigationToResultsFragment() {
        // Open the drawer
        onView(withContentDescription(R.string.nav_app_bar_open_drawer_description)).perform(click());

        // Click on Results menu item
        onView(withId(R.id.nav_results)).perform(click());

        // Verify that Results fragment is displayed
        onView(withText("Previous Results")).check(matches(isDisplayed()));
    }

    @Test
    @SkipSetup
    public void testConsentDialogAppears() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("userAgreedNov2018Consent", false)
                .apply();

        // Relaunch activity
        ActivityScenario.launch(MainActivity.class);

        // Verify that the consent dialog is shown
        onView(withText(R.string.consent_form_title)).check(matches(isDisplayed()));

        // Click Accept
        onView(withText(R.string.accept)).perform(click());
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@interface SkipSetup {}
