package com.mynas.nastv.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * ️ 设置项数据模型
 * 用于SettingsActivity的各种设置选项
 * 支持多种设置项类型：开关、滑块、选择器、输入框、动作等
 */
public class SettingItem implements Parcelable {
    
    // 设置项类型枚举
    public enum Type {
        GROUP_HEADER,   // 分组标题
        SWITCH,         // 开关
        SEEK_BAR,       // 滑块
        SELECTOR,       // 选择器
        INPUT,          // 输入框
        ACTION          // 动作按钮
    }
    
    // 基本信息
    private String key;             // 设置项键值
    private String title;           // 标题
    private String description;     // 描述
    private Type type;              // 类型
    private boolean isEnabled;      // 是否启用
    
    // 开关类型 (SWITCH)
    private boolean switchValue;    // 开关状态
    
    // ️ 滑块类型 (SEEK_BAR)
    private int seekBarValue;       // 滑块当前值
    private int seekBarMin;         // 滑块最小值
    private int seekBarMax;         // 滑块最大值
    private String seekBarUnit;     // 滑块单位
    
    // 选择器类型 (SELECTOR)
    private String[] selectorOptions; // 选择器选项列表
    private int selectedIndex;      // 当前选中的索引
    private String selectedValue;   // 当前选中的值
    
    // 输入框类型 (INPUT)
    private String inputValue;      // 输入框内容
    private String inputHint;       // 输入框提示
    private String inputType;       // 输入类型 (text, number, email等)
    
    // UI相关
    private int iconResource;       // 图标资源ID
    private boolean showDivider;    // 是否显示分割线
    
    // 构造函数
    private SettingItem() {}
    
    // ️ 静态工厂方法
    
    /**
     * 创建分组标题
     */
    public static SettingItem createGroupHeader(String title) {
        SettingItem item = new SettingItem();
        item.type = Type.GROUP_HEADER;
        item.title = title;
        item.isEnabled = true;
        item.showDivider = false;
        return item;
    }
    
    /**
     * 创建开关设置项
     */
    public static SettingItem createSwitch(String key, String title, String description, boolean defaultValue) {
        SettingItem item = new SettingItem();
        item.type = Type.SWITCH;
        item.key = key;
        item.title = title;
        item.description = description;
        item.switchValue = defaultValue;
        item.isEnabled = true;
        item.showDivider = true;
        return item;
    }
    
    /**
     * ️ 创建滑块设置项
     */
    public static SettingItem createSeekBar(String key, String title, String description, 
                                          int currentValue, int minValue, int maxValue) {
        SettingItem item = new SettingItem();
        item.type = Type.SEEK_BAR;
        item.key = key;
        item.title = title;
        item.description = description;
        item.seekBarValue = currentValue;
        item.seekBarMin = minValue;
        item.seekBarMax = maxValue;
        item.isEnabled = true;
        item.showDivider = true;
        return item;
    }
    
    /**
     * ️ 创建带单位的滑块设置项
     */
    public static SettingItem createSeekBar(String key, String title, String description, 
                                          int currentValue, int minValue, int maxValue, String unit) {
        SettingItem item = createSeekBar(key, title, description, currentValue, minValue, maxValue);
        item.seekBarUnit = unit;
        return item;
    }
    
    /**
     * 创建选择器设置项
     */
    public static SettingItem createSelector(String key, String title, String description, 
                                           String currentValue, String[] options) {
        SettingItem item = new SettingItem();
        item.type = Type.SELECTOR;
        item.key = key;
        item.title = title;
        item.description = description;
        item.selectorOptions = options;
        item.selectedValue = currentValue;
        item.isEnabled = true;
        item.showDivider = true;
        
        // 查找当前值在选项中的索引
        if (options != null && currentValue != null) {
            for (int i = 0; i < options.length; i++) {
                if (currentValue.equals(options[i])) {
                    item.selectedIndex = i;
                    break;
                }
            }
        }
        
        return item;
    }
    
    /**
     * 创建输入框设置项
     */
    public static SettingItem createInput(String key, String title, String description, String currentValue) {
        SettingItem item = new SettingItem();
        item.type = Type.INPUT;
        item.key = key;
        item.title = title;
        item.description = description;
        item.inputValue = currentValue;
        item.inputType = "text";
        item.isEnabled = true;
        item.showDivider = true;
        return item;
    }
    
    /**
     * 创建指定类型的输入框设置项
     */
    public static SettingItem createInput(String key, String title, String description, 
                                        String currentValue, String inputType, String hint) {
        SettingItem item = createInput(key, title, description, currentValue);
        item.inputType = inputType;
        item.inputHint = hint;
        return item;
    }
    
    /**
     * 创建动作按钮设置项
     */
    public static SettingItem createAction(String key, String title, String description) {
        SettingItem item = new SettingItem();
        item.type = Type.ACTION;
        item.key = key;
        item.title = title;
        item.description = description;
        item.isEnabled = true;
        item.showDivider = true;
        return item;
    }
    
    /**
     * 创建带图标的动作按钮设置项
     */
    public static SettingItem createAction(String key, String title, String description, int iconResource) {
        SettingItem item = createAction(key, title, description);
        item.iconResource = iconResource;
        return item;
    }
    
    // Parcelable实现
    protected SettingItem(Parcel in) {
        key = in.readString();
        title = in.readString();
        description = in.readString();
        type = Type.valueOf(in.readString());
        isEnabled = in.readByte() != 0;
        switchValue = in.readByte() != 0;
        seekBarValue = in.readInt();
        seekBarMin = in.readInt();
        seekBarMax = in.readInt();
        seekBarUnit = in.readString();
        selectorOptions = in.createStringArray();
        selectedIndex = in.readInt();
        selectedValue = in.readString();
        inputValue = in.readString();
        inputHint = in.readString();
        inputType = in.readString();
        iconResource = in.readInt();
        showDivider = in.readByte() != 0;
    }
    
    public static final Creator<SettingItem> CREATOR = new Creator<SettingItem>() {
        @Override
        public SettingItem createFromParcel(Parcel in) {
            return new SettingItem(in);
        }
        
        @Override
        public SettingItem[] newArray(int size) {
            return new SettingItem[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(key);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(type.name());
        dest.writeByte((byte) (isEnabled ? 1 : 0));
        dest.writeByte((byte) (switchValue ? 1 : 0));
        dest.writeInt(seekBarValue);
        dest.writeInt(seekBarMin);
        dest.writeInt(seekBarMax);
        dest.writeString(seekBarUnit);
        dest.writeStringArray(selectorOptions);
        dest.writeInt(selectedIndex);
        dest.writeString(selectedValue);
        dest.writeString(inputValue);
        dest.writeString(inputHint);
        dest.writeString(inputType);
        dest.writeInt(iconResource);
        dest.writeByte((byte) (showDivider ? 1 : 0));
    }
    
    // Getter和Setter方法
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    
    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }
    
    // 开关相关
    public boolean isSwitchValue() { return switchValue; }
    public void setSwitchValue(boolean switchValue) { this.switchValue = switchValue; }
    
    // 滑块相关
    public int getSeekBarValue() { return seekBarValue; }
    public void setSeekBarValue(int seekBarValue) { this.seekBarValue = seekBarValue; }
    
    public int getSeekBarMin() { return seekBarMin; }
    public void setSeekBarMin(int seekBarMin) { this.seekBarMin = seekBarMin; }
    
    public int getSeekBarMax() { return seekBarMax; }
    public void setSeekBarMax(int seekBarMax) { this.seekBarMax = seekBarMax; }
    
    public String getSeekBarUnit() { return seekBarUnit; }
    public void setSeekBarUnit(String seekBarUnit) { this.seekBarUnit = seekBarUnit; }
    
    // 选择器相关
    public String[] getSelectorOptions() { return selectorOptions; }
    public void setSelectorOptions(String[] selectorOptions) { this.selectorOptions = selectorOptions; }
    
    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int selectedIndex) { 
        this.selectedIndex = selectedIndex;
        // 同步更新选中值
        if (selectorOptions != null && selectedIndex >= 0 && selectedIndex < selectorOptions.length) {
            this.selectedValue = selectorOptions[selectedIndex];
        }
    }
    
    public String getSelectedValue() { return selectedValue; }
    public void setSelectedValue(String selectedValue) { 
        this.selectedValue = selectedValue;
        // 同步更新选中索引
        if (selectorOptions != null && selectedValue != null) {
            for (int i = 0; i < selectorOptions.length; i++) {
                if (selectedValue.equals(selectorOptions[i])) {
                    this.selectedIndex = i;
                    break;
                }
            }
        }
    }
    
    // 输入框相关
    public String getInputValue() { return inputValue; }
    public void setInputValue(String inputValue) { this.inputValue = inputValue; }
    
    public String getInputHint() { return inputHint; }
    public void setInputHint(String inputHint) { this.inputHint = inputHint; }
    
    public String getInputType() { return inputType; }
    public void setInputType(String inputType) { this.inputType = inputType; }
    
    // UI相关
    public int getIconResource() { return iconResource; }
    public void setIconResource(int iconResource) { this.iconResource = iconResource; }
    
    public boolean isShowDivider() { return showDivider; }
    public void setShowDivider(boolean showDivider) { this.showDivider = showDivider; }
    
    // 辅助方法
    
    /**
     * ️ 获取滑块显示文本
     */
    public String getSeekBarDisplayText() {
        if (seekBarUnit != null && !seekBarUnit.isEmpty()) {
            return seekBarValue + seekBarUnit;
        } else {
            return String.valueOf(seekBarValue);
        }
    }
    
    /**
     * 获取选择器显示文本
     */
    public String getSelectorDisplayText() {
        if (selectedValue != null && !selectedValue.isEmpty()) {
            return selectedValue;
        } else if (selectorOptions != null && selectedIndex >= 0 && selectedIndex < selectorOptions.length) {
            return selectorOptions[selectedIndex];
        } else {
            return "";
        }
    }
    
    /**
     * 获取输入框显示文本
     */
    public String getInputDisplayText() {
        if (inputValue != null && !inputValue.isEmpty()) {
            return inputValue;
        } else if (inputHint != null && !inputHint.isEmpty()) {
            return inputHint;
        } else {
            return "";
        }
    }
    
    /**
     * 是否为分组标题
     */
    public boolean isGroupHeader() {
        return type == Type.GROUP_HEADER;
    }
    
    /**
     * 是否为开关类型
     */
    public boolean isSwitch() {
        return type == Type.SWITCH;
    }
    
    /**
     * ️ 是否为滑块类型
     */
    public boolean isSeekBar() {
        return type == Type.SEEK_BAR;
    }
    
    /**
     * 是否为选择器类型
     */
    public boolean isSelector() {
        return type == Type.SELECTOR;
    }
    
    /**
     * 是否为输入框类型
     */
    public boolean isInput() {
        return type == Type.INPUT;
    }
    
    /**
     * 是否为动作按钮类型
     */
    public boolean isAction() {
        return type == Type.ACTION;
    }
    
    /**
     * 是否有图标
     */
    public boolean hasIcon() {
        return iconResource > 0;
    }
    
    /**
     * 获取滑块进度百分比
     */
    public int getSeekBarProgressPercentage() {
        if (seekBarMax <= seekBarMin) {
            return 0;
        }
        return (int) (((float) (seekBarValue - seekBarMin) / (seekBarMax - seekBarMin)) * 100);
    }
    
    /**
     * 复制设置项
     */
    public SettingItem copy() {
        SettingItem copy = new SettingItem();
        copy.key = this.key;
        copy.title = this.title;
        copy.description = this.description;
        copy.type = this.type;
        copy.isEnabled = this.isEnabled;
        copy.switchValue = this.switchValue;
        copy.seekBarValue = this.seekBarValue;
        copy.seekBarMin = this.seekBarMin;
        copy.seekBarMax = this.seekBarMax;
        copy.seekBarUnit = this.seekBarUnit;
        copy.selectorOptions = this.selectorOptions;
        copy.selectedIndex = this.selectedIndex;
        copy.selectedValue = this.selectedValue;
        copy.inputValue = this.inputValue;
        copy.inputHint = this.inputHint;
        copy.inputType = this.inputType;
        copy.iconResource = this.iconResource;
        copy.showDivider = this.showDivider;
        return copy;
    }
    
    @Override
    public String toString() {
        return "SettingItem{" +
                "key='" + key + '\'' +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", isEnabled=" + isEnabled +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SettingItem that = (SettingItem) obj;
        return key != null ? key.equals(that.key) : that.key == null;
    }
    
    @Override
    public int hashCode() {
        return key != null ? key.hashCode() : 0;
    }
}
