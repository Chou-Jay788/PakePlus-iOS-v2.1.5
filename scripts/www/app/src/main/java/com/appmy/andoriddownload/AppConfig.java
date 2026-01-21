package com.appmy.andoriddownload;

public class AppConfig {
    
    // 登录账号密码配置
    public static final String USERNAME = "admin";
    public static final String PASSWORD = "123456";
    
    // 默认主页，可通过安卓killer修改
    public static final String DEFAULT_HOME_URL = "http://118.24.11.142:8167";
    
    // SharedPreferences 配置
    public static final String PREF_NAME = "app_settings";
    public static final String KEY_HOME_URL = "home_url";
    public static final String KEY_LOGGED_IN = "logged_in";
    
    // 隐藏设置触发配置
    public static final int CLICK_THRESHOLD = 10;
    public static final long CLICK_INTERVAL = 500; // 毫秒
}
