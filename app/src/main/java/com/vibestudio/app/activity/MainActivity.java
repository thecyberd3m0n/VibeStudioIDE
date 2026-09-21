package com.vibestudio.app.activity;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.R;
import com.vibestudio.app.fragments.BrowserFragment;
import com.vibestudio.app.fragments.ChatFragment;
import com.vibestudio.app.fragments.LogViewerFragment;
import com.vibestudio.app.fragments.McpFragment;
import com.vibestudio.app.fragments.ModelsFragment;
import com.vibestudio.app.fragments.PermissionsFragment;
import com.vibestudio.app.fragments.TerminalFragment;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

public class MainActivity extends FragmentActivity {

    private static class MenuItem {
        String title;
        String icon;

        MenuItem(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private final MenuItem[] mMenuItems = new MenuItem[] {
        new MenuItem("Models", "🧠"),
        new MenuItem("MCP", "🔌"),
        new MenuItem("Terminal", "💻"),
        new MenuItem("Chat", "💬"),
        new MenuItem("Browser", "🌐"),
        new MenuItem("Permissions", "🔒"),
        new MenuItem("Logs", "📋")
    };

    private DrawerLayout mDrawerLayout;
    private View mDrawerContainer;
    private ListView mDrawerList;
    private TextView mToolbarTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LogViewerService.getInstance().i("MainActivity", "MainActivity created");

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerContainer = findViewById(R.id.left_drawer_container);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mToolbarTitle = (TextView) findViewById(R.id.toolbar_title);

        // Start global persistent TerminalSession directly from MainActivity
        TerminalSessionManager.getInstance().ensureSessionStarted(this.getApplicationContext());

        ArrayAdapter<MenuItem> adapter = new ArrayAdapter<MenuItem>(this, R.layout.drawer_list_item, mMenuItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.drawer_list_item, parent, false);
                }
                MenuItem item = getItem(position);
                TextView tvIcon = (TextView) convertView.findViewById(R.id.item_icon);
                TextView tvTitle = (TextView) convertView.findViewById(R.id.item_title);

                if (item != null) {
                    tvIcon.setText(item.icon);
                    tvTitle.setText(item.title);
                }
                return convertView;
            }
        };

        mDrawerList.setAdapter(adapter);

        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectItem(position);
            }
        });

        findViewById(R.id.btn_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDrawerLayout.isDrawerOpen(mDrawerContainer)) {
                    mDrawerLayout.closeDrawer(mDrawerContainer);
                } else {
                    mDrawerLayout.openDrawer(mDrawerContainer);
                }
            }
        });

        if (savedInstanceState == null) {
            selectItem(0);
        }
    }

    public void selectNavigationItem(int position) {
        selectItem(position);
    }

    private void selectItem(int position) {
        MenuItem item = mMenuItems[position];
        mToolbarTitle.setText(item.title);
        mDrawerList.setItemChecked(position, true);

        LogViewerService.getInstance().i("MainActivity", "Selected navigation view: " + item.title);

        Fragment fragment;
        switch (position) {
            case 0:
                fragment = new ModelsFragment();
                break;
            case 1:
                fragment = new McpFragment();
                break;
            case 2:
                fragment = new TerminalFragment();
                break;
            case 3:
                fragment = new ChatFragment();
                break;
            case 4:
                fragment = new BrowserFragment();
                break;
            case 5:
                fragment = new PermissionsFragment();
                break;
            case 6:
                fragment = new LogViewerFragment();
                break;
            default:
                fragment = new ModelsFragment();
                break;
        }

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit();

        mDrawerLayout.closeDrawer(mDrawerContainer);
    }
}
