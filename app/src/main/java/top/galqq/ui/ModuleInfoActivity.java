package top.galqq.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import top.galqq.R;

/**
 * 模块信息界面 - 从 LSPosed 管理器打开时显示
 * 显示模块基本信息、状态和快捷操作
 */
public class ModuleInfoActivity extends AppCompatTransferActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_module_info);
        
        // 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // 设置版本信息（与配置界面保持一致）
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText("版本 1.0.6 Alpha");
        
        // 设置按钮点击事件
        MaterialButton btnJoinGroup = findViewById(R.id.btnJoinGroup);
        btnJoinGroup.setOnClickListener(v -> {
            try {
                String url = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin= 859142525&card_type=group&source=qrcode";
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                // ignore
            }
        });
        
        MaterialButton btnGitHub = findViewById(R.id.btnGitHub);
        btnGitHub.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, 
                    Uri.parse("https://github.com/yiyihuohuo/GalQQ")));
            } catch (Exception e) {
                // ignore
            }
        });
        
        // 设置透明状态栏
        requestTranslucentStatusBar();
    }
    
    protected void requestTranslucentStatusBar() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        
        View decorView = window.getDecorView();
        int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        
        boolean isNight = (getResources().getConfiguration().uiMode 
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isNight) {
            option |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        
        decorView.setSystemUiVisibility(option);
    }
}
