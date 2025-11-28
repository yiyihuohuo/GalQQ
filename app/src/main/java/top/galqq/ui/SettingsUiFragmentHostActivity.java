package top.galqq.ui;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.widget.Toolbar;
import top.galqq.R;
import top.galqq.utils.HostInfo;

public class SettingsUiFragmentHostActivity extends AppCompatTransferActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (HostInfo.isInHostProcess()) {
            setTheme(R.style.Theme_GalQQ_DayNight);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_host);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.app_name);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        requestTranslucentStatusBar();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new GalSettingsFragment())
                .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重新应用状态栏设置，防止被系统或其他因素重置
        requestTranslucentStatusBar();
    }

    protected void requestTranslucentStatusBar() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        
        View decorView = window.getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        
        // 检测当前是否为深色模式
        int uiMode = getResources().getConfiguration().uiMode;
        boolean isNightMode = (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isNightMode) {
                // 浅色模式：背景为白色，需要深色文字
                option |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                // 深色模式：背景为深色，需要浅色文字（默认），清除LIGHT_STATUS_BAR标志
                option &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        
        decorView.setSystemUiVisibility(option);
    }
}
