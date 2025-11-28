package top.galqq.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import top.galqq.R;
import top.galqq.utils.AiRateLimitedQueue;
import top.galqq.utils.HostInfo;
import java.util.List;
import java.util.Locale;

public class AiMonitorActivity extends AppCompatTransferActivity {

    private TextView tvQueueSize;
    private TextView tvQps;
    private TextView tvThreads;
    private TextView tvActiveRequests;
    private Handler handler;
    private Runnable refreshRunnable;
    private boolean isResumed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (HostInfo.isInHostProcess()) {
            setTheme(R.style.Theme_GalQQ_DayNight);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_monitor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("AI 监控面板");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvQueueSize = findViewById(R.id.tv_queue_size);
        tvQps = findViewById(R.id.tv_qps);
        tvThreads = findViewById(R.id.tv_threads);
        tvActiveRequests = findViewById(R.id.tv_active_requests);
        handler = new Handler(Looper.getMainLooper());

        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isResumed) {
                    updateStats();
                    handler.postDelayed(this, 1000); // Refresh every 1s
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        handler.removeCallbacks(refreshRunnable);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateStats() {
        AiRateLimitedQueue queue = AiRateLimitedQueue.getInstance(this);
        
        // Update Stats Cards
        tvQueueSize.setText(String.valueOf(queue.getQueueSize()));
        tvQps.setText(String.format(Locale.getDefault(), "%.2f", queue.getCurrentQPS()));
        
        // Parse thread info: "Active: X, Pool: Y..."
        String threadInfo = queue.getThreadPoolInfo();
        String activeThreads = "0";
        String poolSize = "0";
        try {
            if (threadInfo.contains("Active:")) {
                String[] parts = threadInfo.split(",");
                for (String part : parts) {
                    if (part.trim().startsWith("Active:")) {
                        activeThreads = part.split(":")[1].trim();
                    } else if (part.trim().startsWith("Pool:")) {
                        poolSize = part.split(":")[1].trim();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        tvThreads.setText(activeThreads + "/" + poolSize);

        // Update Active Requests List
        List<String> requests = queue.getActiveRequests();
        if (requests.isEmpty()) {
            tvActiveRequests.setText("暂无活动请求");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String req : requests) {
                // Translate Priority
                String displayReq = req.replace("HIGH", "高优")
                                     .replace("NORMAL", "普通");
                sb.append("• ").append(displayReq).append("\n\n");
            }
            tvActiveRequests.setText(sb.toString());
        }
    }
}
