package mobi.meddle.wehe;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerMatchers.isClosed;
import static androidx.test.espresso.contrib.DrawerMatchers.isOpen;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.activity.MainActivity;
import mobi.meddle.wehe.fragment.AboutFragment;
import mobi.meddle.wehe.fragment.DashboardFragment;
import mobi.meddle.wehe.fragment.FunctionalityFragment;
import mobi.meddle.wehe.fragment.ResultsFragment;
import mobi.meddle.wehe.fragment.SettingsFragment;
import mobi.meddle.wehe.fragment.SelectionFragment;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class NavigationDrawerTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() {
        // Launch the activity before each test
        activityRule.getScenario().onActivity(activity -> {
            // Ensure activity is initialized
            activity.getSupportFragmentManager().executePendingTransactions();
        });

        // Handle consent dialog if it appears
        try {
            onView(withId(android.R.id.button1)) // "Accept" button in consent dialog
                    .perform(click());
        } catch (Exception e) {
            // Consent dialog might not appear if user already accepted
        }
    }

    @Test
    public void testDrawerOpenClose() {
        // Verify drawer is closed at startup
        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));

        // Open drawer
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.drawer_layout)).check(matches(isOpen()));

        // Close drawer
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.close());
        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
    }

    @Test
    public void testNavigationToResults() {
        // Open drawer and click on Results
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.nav_results)).perform(click());

        // Verify Results fragment is displayed
        scenario.onActivity(activity -> {
            ResultsFragment fragment = (ResultsFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag(ResultsFragment.TAG);
            assert fragment != null;
            assert fragment.isVisible();
        });

        // Verify drawer is closed after selection
        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
    }

    @Test
    public void testNavigationToAbout() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.nav_about)).perform(click());

        scenario.onActivity(activity -> {
            AboutFragment fragment = (AboutFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag(AboutFragment.TAG);
            assert fragment != null;
            assert fragment.isVisible();
        });

        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
    }

    @Test
    public void testNavigationToFunctionality() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.nav_functionality)).perform(click());

        scenario.onActivity(activity -> {
            FunctionalityFragment fragment = (FunctionalityFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag(FunctionalityFragment.TAG);
            assert fragment != null;
            assert fragment.isVisible();
        });

        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
    }

    @Test
    public void testNavigationToDashboard() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.nav_dashboard)).perform(click());

        scenario.onActivity(activity -> {
            DashboardFragment fragment = (DashboardFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag(DashboardFragment.TAG);
            assert fragment != null;
            assert fragment.isVisible();
        });

        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
    }

    @Test
    public void testNavigationToSettings() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.nav_settings)).perform(click());

        scenario.onActivity(activity -> {
            SettingsFragment fragment = (SettingsFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag(SettingsFragment.TAG);
            assert fragment != null;
            assert fragment.isVisible();
        });

        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
    }

    @Test
    public void testNavigationToRunTest() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.nav_run)).perform(click());

        // Verify the SelectionFragment is displayed with correct tag
        scenario.onActivity(activity -> {
            SelectionFragment fragment = (SelectionFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("differentiation_tests");
            assert fragment != null;
            assert fragment.isVisible();

            // Verify the arguments
            assert !fragment.getArguments().getBoolean("runPortTest");
            assert fragment.getArguments().getString("TAG").equals("differentiation_tests");
        });

        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
    }

    @Test
    public void testNavigationToPortTest() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withId(R.id.nav_run_port)).perform(click());

        scenario.onActivity(activity -> {
            SelectionFragment fragment = (SelectionFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("port_tests");
            assert fragment != null;
            assert fragment.isVisible();

            // Verify the arguments
            assert fragment.getArguments().getBoolean("runPortTest");
            assert fragment.getArguments().getString("TAG").equals("port_tests");
        });

        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
    }
}