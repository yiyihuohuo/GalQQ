package top.galqq.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import top.galqq.R;
import top.galqq.config.ConfigManager;
import top.galqq.utils.HostInfo;

public class PromptManagerActivity extends AppCompatTransferActivity {

    private RecyclerView recyclerView;
    private PromptAdapter adapter;
    private List<ConfigManager.PromptItem> promptList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题管理器，检测QQ的夜间模式设置
        top.galqq.utils.ThemeManager.applyTheme(this);
        top.galqq.utils.ThemeManager.updateConfiguration(this);
        
        // 设置主题
        setTheme(top.galqq.utils.ThemeManager.getThemeResId(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt_manager);
        
        ConfigManager.init(this);
        
        // 设置工具栏
        setupToolbar();
        
        recyclerView = findViewById(R.id.prompt_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        loadPrompts();
        
        // 设置拖拽排序
        setupDragAndDrop();
        
        // 添加按钮
        findViewById(R.id.btn_add_prompt).setOnClickListener(v -> showAddPromptDialog());
    }
    
    /**
     * 设置工具栏
     */
    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("提示词配置");
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            
            // 设置帮助按钮点击监听器
            View btnHelp = findViewById(R.id.btn_help);
            if (btnHelp != null) {
                btnHelp.setOnClickListener(v -> showHelpDialog());
            }
        } else {
            // 回退：使用默认ActionBar
            setTitle("提示词配置");
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }
    
    /**
     * 显示帮助对话框
     */
    private void showHelpDialog() {
        String helpContent = 
            "【优先级规则】\n" +
            "系统按以下顺序判断是否使用某个提示词：\n\n" +
            "1. 提示词是否禁用 - 禁用的提示词不会被使用\n" +
            "2. 用户白名单 - 指定QQ号强制使用此提示词\n" +
            "3. 群白名单 - 指定群号强制使用此提示词\n" +
            "4. 用户黑名单 - 指定QQ号禁止使用此提示词\n" +
            "5. 群黑名单 - 指定群号禁止使用此提示词\n" +
            "6. 默认顺序 - 按列表顺序选择第一个可用提示词\n\n" +
            "【功能说明】\n" +
            "• 拖拽排序：长按提示词可拖动调整优先级\n" +
            "• 点击切换：点击提示词可启用/禁用\n" +
            "• 黑白名单：支持用户级和群级的精细控制\n" +
            "• 提示词内容：支持分区编辑（内容/输入格式/输出格式）";
        new AlertDialog.Builder(this)
            .setTitle("提示词配置说明")
            .setMessage(helpContent)
            .setPositiveButton("知道了", null)
            .setNeutralButton("浏览提示词分享", (dialog, which) -> {
                try {
                    android.content.Intent intent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://blog.h-acker.cn/forum/3428.html")
                    );
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }
    
    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                // 允许上下拖拽
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                return makeMovementFlags(dragFlags, 0);
            }
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                
                // 交换列表中的位置（顺序决定优先级）
                Collections.swap(promptList, fromPosition, toPosition);
                adapter.notifyItemMoved(fromPosition, toPosition);
                
                // 刷新受影响范围内的所有项目序号显示
                int start = Math.min(fromPosition, toPosition);
                int end = Math.max(fromPosition, toPosition);
                adapter.notifyItemRangeChanged(start, end - start + 1);
                
                return true;
            }
            
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 不支持滑动删除
            }
            
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // 拖拽结束后保存新顺序
                ConfigManager.savePromptList(promptList);
            }
            
            @Override
            public boolean isLongPressDragEnabled() {
                return true; // 长按启用拖拽
            }
        };
        
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);
    }

    private void loadPrompts() {
        promptList = ConfigManager.getPromptList();
        adapter = new PromptAdapter();
        recyclerView.setAdapter(adapter);
    }

    // 内容分隔符
    private static final String SECTION_SEPARATOR = "\n\n---[INPUT_FORMAT]---\n\n";
    private static final String OUTPUT_SEPARATOR = "\n\n---[OUTPUT_FORMAT]---\n\n";
    
    /**
     * 将三个区域的内容合并为一个字符串
     */
    private String combineContentSections(String content, String inputFormat, String outputFormat) {
        StringBuilder sb = new StringBuilder();
        sb.append(content != null ? content : "");
        
        if (inputFormat != null && !inputFormat.isEmpty()) {
            sb.append(SECTION_SEPARATOR);
            sb.append(inputFormat);
        }
        
        if (outputFormat != null && !outputFormat.isEmpty()) {
            sb.append(OUTPUT_SEPARATOR);
            sb.append(outputFormat);
        }
        
        return sb.toString();
    }
    
    /**
     * 解析合并的内容字符串为三个区域
     * @return String[3] = {content, inputFormat, outputFormat}
     */
    private String[] parseContentSections(String combinedContent) {
        String[] result = new String[]{"", "", ""};
        if (combinedContent == null || combinedContent.isEmpty()) {
            return result;
        }
        
        // 先查找输出格式分隔符
        int outputIndex = combinedContent.indexOf(OUTPUT_SEPARATOR);
        String beforeOutput = combinedContent;
        if (outputIndex >= 0) {
            result[2] = combinedContent.substring(outputIndex + OUTPUT_SEPARATOR.length());
            beforeOutput = combinedContent.substring(0, outputIndex);
        }
        
        // 再查找输入格式分隔符
        int inputIndex = beforeOutput.indexOf(SECTION_SEPARATOR);
        if (inputIndex >= 0) {
            result[0] = beforeOutput.substring(0, inputIndex);
            result[1] = beforeOutput.substring(inputIndex + SECTION_SEPARATOR.length());
        } else {
            result[0] = beforeOutput;
        }
        
        return result;
    }
    
    /**
     * 设置可折叠区域的点击监听器
     */
    private void setupCollapsibleSection(View header, View content, android.widget.ImageView icon) {
        header.setOnClickListener(v -> {
            boolean isVisible = content.getVisibility() == View.VISIBLE;
            content.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            icon.setImageResource(isVisible ? R.drawable.ic_expand_more : R.drawable.ic_expand_less);
        });
    }
    
    /**
     * 显示全屏编辑对话框
     * @param title 对话框标题
     * @param currentContent 当前内容
     * @param targetEditText 目标EditText，编辑完成后将内容同步回去
     */
    private void showFullscreenEditDialog(String title, String currentContent, EditText targetEditText) {
        // 创建全屏编辑布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        // 创建大型编辑框
        EditText fullscreenEdit = new EditText(this);
        fullscreenEdit.setText(currentContent);
        fullscreenEdit.setHint("在此输入内容...");
        fullscreenEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        fullscreenEdit.setGravity(android.view.Gravity.TOP);
        fullscreenEdit.setMinLines(15);
        fullscreenEdit.setMaxLines(30);
        fullscreenEdit.setVerticalScrollBarEnabled(true);
        fullscreenEdit.setTextSize(14);
        fullscreenEdit.setLineSpacing(4, 1.2f);
        
        // 设置背景 - 根据夜间模式使用不同颜色
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        boolean isNightMode = top.galqq.utils.ThemeManager.isQQNightMode(this);
        bg.setColor(isNightMode ? 0xFF2D2D2D : 0xFFFAFAFA);
        bg.setCornerRadius(8);
        bg.setStroke(1, isNightMode ? 0xFF404040 : 0xFFE0E0E0);
        fullscreenEdit.setBackground(bg);
        fullscreenEdit.setTextColor(isNightMode ? 0xFFFFFFFF : 0xFF202124);
        fullscreenEdit.setHintTextColor(isNightMode ? 0xFF888888 : 0xFF999999);
        fullscreenEdit.setPadding(24, 24, 24, 24);
        
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fullscreenEdit.setLayoutParams(lp);
        
        layout.addView(fullscreenEdit);
        
        // 创建对话框
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("确定", (d, which) -> {
                // 将编辑内容同步回原EditText
                targetEditText.setText(fullscreenEdit.getText().toString());
            })
            .setNegativeButton("取消", null)
            .create();
        
        // 设置对话框为全屏宽度
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }
        });
        
        dialog.show();
        
        // 自动聚焦并将光标移到末尾
        fullscreenEdit.requestFocus();
        fullscreenEdit.setSelection(fullscreenEdit.getText().length());
    }
    
    /**
     * 设置放大编辑按钮的点击监听器
     */
    private void setupExpandEditButton(View button, String title, EditText targetEditText) {
        button.setOnClickListener(v -> {
            showFullscreenEditDialog(title, targetEditText.getText().toString(), targetEditText);
        });
    }

    private void showAddPromptDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_prompt, null);
        EditText nameEdit = dialogView.findViewById(R.id.edit_prompt_name);
        EditText contentEdit = dialogView.findViewById(R.id.edit_prompt_content);
        EditText inputFormatEdit = dialogView.findViewById(R.id.edit_input_format);
        EditText outputFormatEdit = dialogView.findViewById(R.id.edit_output_format);
        EditText whitelistEdit = dialogView.findViewById(R.id.edit_prompt_whitelist);
        EditText blacklistEdit = dialogView.findViewById(R.id.edit_prompt_blacklist);
        EditText groupWhitelistEdit = dialogView.findViewById(R.id.edit_prompt_group_whitelist);
        EditText groupBlacklistEdit = dialogView.findViewById(R.id.edit_prompt_group_blacklist);
        android.widget.Switch whitelistSwitch = dialogView.findViewById(R.id.switch_whitelist);
        android.widget.Switch blacklistSwitch = dialogView.findViewById(R.id.switch_blacklist);
        android.widget.Switch groupWhitelistSwitch = dialogView.findViewById(R.id.switch_group_whitelist);
        android.widget.Switch groupBlacklistSwitch = dialogView.findViewById(R.id.switch_group_blacklist);
        
        // 设置可折叠区域
        View contentHeader = dialogView.findViewById(R.id.section_content_header);
        View inputFormatHeader = dialogView.findViewById(R.id.section_input_format_header);
        View outputFormatHeader = dialogView.findViewById(R.id.section_output_format_header);
        android.widget.ImageView iconContent = dialogView.findViewById(R.id.icon_expand_content);
        android.widget.ImageView iconInputFormat = dialogView.findViewById(R.id.icon_expand_input_format);
        android.widget.ImageView iconOutputFormat = dialogView.findViewById(R.id.icon_expand_output_format);
        
        setupCollapsibleSection(contentHeader, contentEdit, iconContent);
        setupCollapsibleSection(inputFormatHeader, inputFormatEdit, iconInputFormat);
        setupCollapsibleSection(outputFormatHeader, outputFormatEdit, iconOutputFormat);
        
        // 设置放大编辑按钮
        View btnExpandContent = dialogView.findViewById(R.id.btn_expand_content);
        View btnExpandInputFormat = dialogView.findViewById(R.id.btn_expand_input_format);
        View btnExpandOutputFormat = dialogView.findViewById(R.id.btn_expand_output_format);
        setupExpandEditButton(btnExpandContent, "编辑提示词内容", contentEdit);
        setupExpandEditButton(btnExpandInputFormat, "编辑输入格式", inputFormatEdit);
        setupExpandEditButton(btnExpandOutputFormat, "编辑输出格式", outputFormatEdit);
        
        // 设置默认值
        inputFormatEdit.setText(ConfigManager.DEFAULT_INPUT_FORMAT);
        outputFormatEdit.setText(ConfigManager.DEFAULT_OUTPUT_FORMAT);
        
        // 设置开关监听器，控制输入框显示
        whitelistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            whitelistEdit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        blacklistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            blacklistEdit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        groupWhitelistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            groupWhitelistEdit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        groupBlacklistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            groupBlacklistEdit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        
        new AlertDialog.Builder(this)
            .setTitle("添加提示词")
            .setView(dialogView)
            .setPositiveButton("添加", (dialog, which) -> {
                String name = nameEdit.getText().toString().trim();
                String mainContent = contentEdit.getText().toString().trim();
                String inputFormat = inputFormatEdit.getText().toString().trim();
                String outputFormat = outputFormatEdit.getText().toString().trim();
                String whitelist = whitelistEdit.getText().toString().trim();
                String blacklist = blacklistEdit.getText().toString().trim();
                String groupWhitelist = groupWhitelistEdit.getText().toString().trim();
                String groupBlacklist = groupBlacklistEdit.getText().toString().trim();
                boolean whitelistEnabled = whitelistSwitch.isChecked();
                boolean blacklistEnabled = blacklistSwitch.isChecked();
                boolean groupWhitelistEnabled = groupWhitelistSwitch.isChecked();
                boolean groupBlacklistEnabled = groupBlacklistSwitch.isChecked();
                if (name.isEmpty()) {
                    Toast.makeText(this, "请输入提示词名称", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mainContent.isEmpty()) {
                    Toast.makeText(this, "请输入提示词内容", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 合并三个区域的内容
                String combinedContent = combineContentSections(mainContent, inputFormat, outputFormat);
                promptList.add(new ConfigManager.PromptItem(name, combinedContent, whitelist, blacklist, true, 
                    whitelistEnabled, blacklistEnabled, groupWhitelist, groupBlacklist, 
                    groupWhitelistEnabled, groupBlacklistEnabled));
                ConfigManager.savePromptList(promptList);
                adapter.notifyItemInserted(promptList.size() - 1);
                Toast.makeText(this, "添加成功", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }


    private void showEditPromptDialog(int position) {
        ConfigManager.PromptItem item = promptList.get(position);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_prompt, null);
        EditText nameEdit = dialogView.findViewById(R.id.edit_prompt_name);
        EditText contentEdit = dialogView.findViewById(R.id.edit_prompt_content);
        EditText inputFormatEdit = dialogView.findViewById(R.id.edit_input_format);
        EditText outputFormatEdit = dialogView.findViewById(R.id.edit_output_format);
        EditText whitelistEdit = dialogView.findViewById(R.id.edit_prompt_whitelist);
        EditText blacklistEdit = dialogView.findViewById(R.id.edit_prompt_blacklist);
        EditText groupWhitelistEdit = dialogView.findViewById(R.id.edit_prompt_group_whitelist);
        EditText groupBlacklistEdit = dialogView.findViewById(R.id.edit_prompt_group_blacklist);
        android.widget.Switch whitelistSwitch = dialogView.findViewById(R.id.switch_whitelist);
        android.widget.Switch blacklistSwitch = dialogView.findViewById(R.id.switch_blacklist);
        android.widget.Switch groupWhitelistSwitch = dialogView.findViewById(R.id.switch_group_whitelist);
        android.widget.Switch groupBlacklistSwitch = dialogView.findViewById(R.id.switch_group_blacklist);
        
        // 设置可折叠区域
        View contentHeader = dialogView.findViewById(R.id.section_content_header);
        View inputFormatHeader = dialogView.findViewById(R.id.section_input_format_header);
        View outputFormatHeader = dialogView.findViewById(R.id.section_output_format_header);
        android.widget.ImageView iconContent = dialogView.findViewById(R.id.icon_expand_content);
        android.widget.ImageView iconInputFormat = dialogView.findViewById(R.id.icon_expand_input_format);
        android.widget.ImageView iconOutputFormat = dialogView.findViewById(R.id.icon_expand_output_format);
        
        setupCollapsibleSection(contentHeader, contentEdit, iconContent);
        setupCollapsibleSection(inputFormatHeader, inputFormatEdit, iconInputFormat);
        setupCollapsibleSection(outputFormatHeader, outputFormatEdit, iconOutputFormat);
        
        // 设置放大编辑按钮
        View btnExpandContent = dialogView.findViewById(R.id.btn_expand_content);
        View btnExpandInputFormat = dialogView.findViewById(R.id.btn_expand_input_format);
        View btnExpandOutputFormat = dialogView.findViewById(R.id.btn_expand_output_format);
        setupExpandEditButton(btnExpandContent, "编辑提示词内容", contentEdit);
        setupExpandEditButton(btnExpandInputFormat, "编辑输入格式", inputFormatEdit);
        setupExpandEditButton(btnExpandOutputFormat, "编辑输出格式", outputFormatEdit);
        
        // 解析现有内容为三个区域
        String[] sections = parseContentSections(item.content);
        nameEdit.setText(item.name);
        contentEdit.setText(sections[0]);
        inputFormatEdit.setText(sections[1]);
        outputFormatEdit.setText(sections[2]);
        
        // 如果输入格式或输出格式有内容，展开对应区域
        if (!sections[1].isEmpty()) {
            inputFormatEdit.setVisibility(View.VISIBLE);
            iconInputFormat.setImageResource(R.drawable.ic_expand_less);
        }
        if (!sections[2].isEmpty()) {
            outputFormatEdit.setVisibility(View.VISIBLE);
            iconOutputFormat.setImageResource(R.drawable.ic_expand_less);
        }
        
        whitelistEdit.setText(item.whitelist);
        blacklistEdit.setText(item.blacklist);
        groupWhitelistEdit.setText(item.groupWhitelist);
        groupBlacklistEdit.setText(item.groupBlacklist);
        
        // 设置开关状态和输入框可见性
        whitelistSwitch.setChecked(item.whitelistEnabled);
        blacklistSwitch.setChecked(item.blacklistEnabled);
        groupWhitelistSwitch.setChecked(item.groupWhitelistEnabled);
        groupBlacklistSwitch.setChecked(item.groupBlacklistEnabled);
        whitelistEdit.setVisibility(item.whitelistEnabled ? View.VISIBLE : View.GONE);
        blacklistEdit.setVisibility(item.blacklistEnabled ? View.VISIBLE : View.GONE);
        groupWhitelistEdit.setVisibility(item.groupWhitelistEnabled ? View.VISIBLE : View.GONE);
        groupBlacklistEdit.setVisibility(item.groupBlacklistEnabled ? View.VISIBLE : View.GONE);
        
        // 设置开关监听器
        whitelistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            whitelistEdit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        blacklistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            blacklistEdit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        groupWhitelistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            groupWhitelistEdit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        groupBlacklistSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            groupBlacklistEdit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        
        new AlertDialog.Builder(this)
            .setTitle("编辑提示词")
            .setView(dialogView)
            .setPositiveButton("保存", (dialog, which) -> {
                String name = nameEdit.getText().toString().trim();
                String mainContent = contentEdit.getText().toString().trim();
                String inputFormat = inputFormatEdit.getText().toString().trim();
                String outputFormat = outputFormatEdit.getText().toString().trim();
                String whitelist = whitelistEdit.getText().toString().trim();
                String blacklist = blacklistEdit.getText().toString().trim();
                String groupWhitelist = groupWhitelistEdit.getText().toString().trim();
                String groupBlacklist = groupBlacklistEdit.getText().toString().trim();
                boolean whitelistEnabled = whitelistSwitch.isChecked();
                boolean blacklistEnabled = blacklistSwitch.isChecked();
                boolean groupWhitelistEnabled = groupWhitelistSwitch.isChecked();
                boolean groupBlacklistEnabled = groupBlacklistSwitch.isChecked();
                if (name.isEmpty() || mainContent.isEmpty()) {
                    Toast.makeText(this, "名称和内容不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 合并三个区域的内容
                String combinedContent = combineContentSections(mainContent, inputFormat, outputFormat);
                item.name = name;
                item.content = combinedContent;
                item.whitelist = whitelist;
                item.blacklist = blacklist;
                item.groupWhitelist = groupWhitelist;
                item.groupBlacklist = groupBlacklist;
                item.whitelistEnabled = whitelistEnabled;
                item.blacklistEnabled = blacklistEnabled;
                item.groupWhitelistEnabled = groupWhitelistEnabled;
                item.groupBlacklistEnabled = groupBlacklistEnabled;
                ConfigManager.savePromptList(promptList);
                adapter.notifyItemChanged(position);
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void deletePrompt(int position) {
        if (promptList.size() <= 1) {
            Toast.makeText(this, "至少保留一个提示词", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("删除提示词")
            .setMessage("确定要删除 \"" + promptList.get(position).name + "\" 吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                promptList.remove(position);
                ConfigManager.savePromptList(promptList);
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void togglePromptEnabled(int position) {
        ConfigManager.PromptItem item = promptList.get(position);
        item.enabled = !item.enabled;
        ConfigManager.savePromptList(promptList);
        adapter.notifyItemChanged(position);
        String status = item.enabled ? "已启用" : "已禁用";
        Toast.makeText(this, item.name + " " + status, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    class PromptAdapter extends RecyclerView.Adapter<PromptAdapter.ViewHolder> {
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_prompt, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ConfigManager.PromptItem item = promptList.get(position);
            // 显示优先级序号和名称，禁用时在名称后显示"(已禁用)"
            String nameDisplay = (position + 1) + ". " + item.name;
            if (!item.enabled) {
                nameDisplay += " (已禁用)";
            }
            holder.nameText.setText(nameDisplay);
            holder.contentText.setText(item.content.length() > 60 
                ? item.content.substring(0, 60) + "..." 
                : item.content);
            
            // 不再使用选中指示器和状态图标
            holder.selectedIndicator.setVisibility(View.GONE);
            holder.statusIndicator.setVisibility(View.GONE);
            
            // 禁用时显示半透明效果
            holder.itemView.setAlpha(item.enabled ? 1.0f : 0.5f);
            
            // 显示黑白名单预览（只有启用了对应功能才显示）
            StringBuilder listPreview = new StringBuilder();
            if (item.whitelistEnabled && item.whitelist != null && !item.whitelist.isEmpty()) {
                if (listPreview.length() > 0) listPreview.append(" | ");
                listPreview.append("用户白名单: ").append(item.whitelist);
            }
            if (item.groupWhitelistEnabled && item.groupWhitelist != null && !item.groupWhitelist.isEmpty()) {
                if (listPreview.length() > 0) listPreview.append(" | ");
                listPreview.append("群白名单: ").append(item.groupWhitelist);
            }
            if (item.blacklistEnabled && item.blacklist != null && !item.blacklist.isEmpty()) {
                if (listPreview.length() > 0) listPreview.append(" | ");
                listPreview.append("用户黑名单: ").append(item.blacklist);
            }
            if (item.groupBlacklistEnabled && item.groupBlacklist != null && !item.groupBlacklist.isEmpty()) {
                if (listPreview.length() > 0) listPreview.append(" | ");
                listPreview.append("群黑名单: ").append(item.groupBlacklist);
            }
            if (listPreview.length() > 0) {
                holder.listPreview.setText(listPreview.toString());
                holder.listPreview.setVisibility(View.VISIBLE);
            } else {
                holder.listPreview.setVisibility(View.GONE);
            }
            
            // 点击切换启用/禁用状态（使用 getAdapterPosition 获取当前实际位置）
            holder.itemView.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    togglePromptEnabled(currentPosition);
                }
            });
            // 长按由 ItemTouchHelper 处理拖拽，不设置 OnLongClickListener
            holder.btnEdit.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    showEditPromptDialog(currentPosition);
                }
            });
            holder.btnDelete.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    deletePrompt(currentPosition);
                }
            });
        }

        @Override
        public int getItemCount() {
            return promptList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, contentText, listPreview, statusIndicator;
            View selectedIndicator;
            TextView btnEdit, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.prompt_name);
                contentText = itemView.findViewById(R.id.prompt_content);
                listPreview = itemView.findViewById(R.id.list_preview);
                statusIndicator = itemView.findViewById(R.id.status_indicator);
                selectedIndicator = itemView.findViewById(R.id.selected_indicator);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
