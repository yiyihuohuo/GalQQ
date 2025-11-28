package top.galqq.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import top.galqq.R;
import top.galqq.utils.AiLogManager;

import top.galqq.utils.HostInfo;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AI日志查看Activity
 */
public class AiLogViewerActivity extends AppCompatTransferActivity {
    
    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;
    private Button btnExport;
    private Button btnClear;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 设置主题（与SettingsUiFragmentHostActivity保持一致）
        if (HostInfo.isInHostProcess()) {
            setTheme(R.style.Theme_GalQQ_DayNight);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_log_viewer);
        
        // 设置标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.ai_log_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        logRecyclerView = findViewById(R.id.log_recycler_view);
        btnExport = findViewById(R.id.btn_export);
        btnClear = findViewById(R.id.btn_clear);
        View fastScroller = findViewById(R.id.fast_scroller);
        
        // 设置RecyclerView
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogAdapter();
        logRecyclerView.setAdapter(logAdapter);
        
        // 设置快速滚动条
        setupFastScroller(fastScroller);
        
        // 导出按钮
        btnExport.setOnClickListener(v -> exportLogs());
        
        // 清除按钮
        btnClear.setOnClickListener(v -> {
            AiLogManager.clearLogs(this);
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show();
            loadLogs();
        });
        
        // 初始加载日志
        loadLogs();
    }
    
    private void setupFastScroller(View fastScroller) {
        fastScroller.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == android.view.MotionEvent.ACTION_DOWN || 
                action == android.view.MotionEvent.ACTION_MOVE) {
                
                // 获取触摸点相对于RecyclerView的Y坐标
                int[] recyclerLocation = new int[2];
                logRecyclerView.getLocationOnScreen(recyclerLocation);
                
                int[] scrollerLocation = new int[2];
                v.getLocationOnScreen(scrollerLocation);
                
                float touchY = event.getRawY() - recyclerLocation[1];
                float recyclerHeight = logRecyclerView.getHeight();
                
                // 计算滚动位置百分比
                float percentage = touchY / recyclerHeight;
                percentage = Math.max(0f, Math.min(1f, percentage));
                
                // 计算目标位置
                int itemCount = logAdapter.getItemCount();
                int targetPosition = (int) (percentage * itemCount);
                targetPosition = Math.max(0, Math.min(itemCount - 1, targetPosition));
                
                // 滚动到目标位置
                logRecyclerView.scrollToPosition(targetPosition);
                
                // 更新scroller位置
                float scrollerY = percentage * (recyclerHeight - v.getHeight());
                v.setY(scrollerY);
                
                return true;
            }
            return false;
        });
        
        // 监听RecyclerView滚动，同步更新scroller位置
        logRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateScrollerPosition(fastScroller);
            }
        });
    }
    
    private void updateScrollerPosition(View fastScroller) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) logRecyclerView.getLayoutManager();
        if (layoutManager == null) return;
        
        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        int itemCount = logAdapter.getItemCount();
        
        if (itemCount == 0) return;
        
        float percentage = (float) firstVisiblePosition / itemCount;
        float recyclerHeight = logRecyclerView.getHeight();
        float scrollerY = percentage * (recyclerHeight - fastScroller.getHeight());
        
        fastScroller.setY(scrollerY);
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    private void loadLogs() {
        String logs = AiLogManager.getLogs(this);
        // 按行分割日志
        String[] lines = logs.split("\n");
        List<String> logLines = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                logLines.add(line);
            }
        }
        logAdapter.setLogLines(logLines);
        
        // 滚动到底部显示最新日志
        if (logLines.size() > 0) {
            logRecyclerView.scrollToPosition(logLines.size() - 1);
        }
    }
    
    private void exportLogs() {
        try {
            String logs = AiLogManager.getLogs(this);
            
            // 创建文件名（带时间戳）
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "galqq_ai_logs_" + timestamp + ".txt";
            
            // 保存到外部存储的Download目录
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File logFile = new File(downloadsDir, filename);
            
            FileWriter writer = new FileWriter(logFile);
            writer.write(logs);
            writer.close();
            
            // 分享文件
            Uri fileUri = FileProvider.getUriForFile(this, 
                getApplicationContext().getPackageName() + ".fileprovider", logFile);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GalQQ AI Logs");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_logs)));
            
            Toast.makeText(this, "日志已保存到: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * RecyclerView适配器，用于显示日志行
     */
    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {
        
        private List<String> logLines = new ArrayList<>();
        
        public void setLogLines(List<String> lines) {
            this.logLines = lines;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            textView.setTextColor(0xFF333333);
            textView.setTextSize(12);
            textView.setTypeface(android.graphics.Typeface.MONOSPACE);
            textView.setTextIsSelectable(true);
            textView.setPadding(0, 4, 0, 4);
            return new LogViewHolder(textView);
        }
        
        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            holder.textView.setText(logLines.get(position));
        }
        
        @Override
        public int getItemCount() {
            return logLines.size();
        }
        
        static class LogViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            
            LogViewHolder(TextView itemView) {
                super(itemView);
                this.textView = itemView;
            }
        }
    }
}
