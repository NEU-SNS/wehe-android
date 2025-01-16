package mobi.meddle.wehe;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import mobi.meddle.wehe.activity.MainActivity;
import mobi.meddle.wehe.fragment.ResultsFragment;
import mobi.meddle.wehe.fragment.AboutFragment;
import mobi.meddle.wehe.constant.Consts;

@RunWith(AndroidJUnit4.class)
public class NavigationDrawerTest {

    private MainActivity mainActivity;

    @Before
    public void setUp() {
        // Initialize MainActivity and FragmentManager mock
        Context context = ApplicationProvider.getApplicationContext();
        mainActivity = new MainActivity();
        FragmentManager mockFragmentManager = mock(FragmentManager.class);
    }

    @Test
    public void testNavRunSelection() {
        simulateDrawerSelection(R.id.nav_run, "Differentiation Tests");

        // Verify the correct fragment is displayed
        verifyFragmentDisplayed(Consts.TAG_DIFFERENTIATION_TESTS);

        // Verify the correct title
        assertEquals("Differentiation Tests", mainActivity.getTitle().toString());
    }

    @Test
    public void testNavRunPortSelection() {
        simulateDrawerSelection(R.id.nav_run_port, "Port Tests");

        // Verify the correct fragment is displayed
        verifyFragmentDisplayed(Consts.TAG_PORT_TESTS);

        // Verify the correct title
        assertEquals("Port Tests", mainActivity.getTitle().toString());
    }

    @Test
    public void testNavResultsSelection() {
        simulateDrawerSelection(R.id.nav_results, "Results");

        // Verify the correct fragment is displayed
        verifyFragmentDisplayed(ResultsFragment.TAG);

        // Verify the correct title
        assertEquals("Results", mainActivity.getTitle().toString());
    }

    @Test
    public void testNavAboutSelection() {
        simulateDrawerSelection(R.id.nav_about, "About");

        // Verify the correct fragment is displayed
        verifyFragmentDisplayed(AboutFragment.TAG);

        // Verify the correct title
        assertEquals("About", mainActivity.getTitle().toString());
    }

    // Utility methods
    private void simulateDrawerSelection(int menuItemId, String expectedTitle) {
        // Mock MenuItem
        MenuItem mockMenuItem = mock(MenuItem.class);
        when(mockMenuItem.getItemId()).thenReturn(menuItemId);
        when(mockMenuItem.getTitle()).thenReturn(expectedTitle);

//        // Simulate onNavigationItemSelected
//        mainActivity.getNavigationView().getMenu().performIdentifierAction(menuItemId, 0);
    }

    private void verifyFragmentDisplayed(@NonNull String tag) {
        Fragment fragment = mainActivity.getFragmentManager().findFragmentByTag(tag);
        assertNotNull("Fragment should not be null", fragment);
    }
}
