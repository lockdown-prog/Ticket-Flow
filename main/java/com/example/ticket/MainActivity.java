package com.example.ticket;

import android.annotation.SuppressLint;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    FrameLayout frameLayout;
    DrawerLayout drawerLayout;
    NavigationView navigationView;
    MaterialToolbar toolbar;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Database
        DBHelper dbHelper = new DBHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Assign Views Values
        drawerLayout = findViewById(R.id.main);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        frameLayout = findViewById(R.id.frameLayout);

        // Initialize Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frameLayout, new CreateTicketFragment())
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();

        // Make the Navigation items Clickable
        navigationView.bringToFront();
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.createTicket) {
                closeDrawer();
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, new CreateTicketFragment())
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .commit();
            } else if (id == R.id.searchTicket) {
                closeDrawer();
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, new SearchTicketFragment())
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .commit();
            } else if (id == R.id.cloudBackup) {
                closeDrawer();
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, new SyncFragment())
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .commit();
                Toast.makeText(this, "Under Development", Toast.LENGTH_SHORT).show();
            } else {
                closeDrawer();
                Toast.makeText(MainActivity.this, "Unknown ID", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        setSupportActionBar(toolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.open_drawer, R.string.close_drawer);
        toggle.syncState();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    void closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
    }
}