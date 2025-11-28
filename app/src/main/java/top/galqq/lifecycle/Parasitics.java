package top.galqq.lifecycle;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import top.galqq.R;
import top.galqq.hook.GalqqHook;

public class Parasitics {

    private static final String TAG = "GalQQ.Parasitics";
    private static boolean __stub_hooked = false;
    private static String sModulePath = null;

    public static void setModulePath(String path) {
        sModulePath = path;
    }

    public static void injectModuleResources(Resources res) {
        if (res == null || sModulePath == null) {
            return;
        }
        try {
            res.getString(R.string.res_inject_success);
            return;
        } catch (Resources.NotFoundException ignored) {
        }

        if (Build.VERSION.SDK_INT >= 30) {
            injectResourcesAboveApi30(res, sModulePath);
        } else {
            injectResourcesBelowApi30(res, sModulePath);
        }
    }

    @RequiresApi(30)
    private static void injectResourcesAboveApi30(@NonNull Resources res, @NonNull String path) {
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            ResourcesProvider provider = ResourcesProvider.loadFromApk(pfd);
            ResourcesLoader loader = new ResourcesLoader();
            loader.addProvider(provider);
            res.addLoaders(loader);
        } catch (IOException e) {
            XposedBridge.log(TAG + ": Failed to inject resources (API 30+): " + e.getMessage());
        }
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    @SuppressLint("PrivateApi")
    private static void injectResourcesBelowApi30(@NonNull Resources res, @NonNull String path) {
        try {
            AssetManager assets = res.getAssets();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            addAssetPath.invoke(assets, path);
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to inject resources (< API 30): " + e.getMessage());
        }
    }

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    public static void initForStubActivity(Context ctx) {
        if (__stub_hooked) {
            return;
        }
        try {
            // 检查路径有效性
            boolean needRefresh = sModulePath == null;
            if (!needRefresh) {
                File moduleFile = new File(sModulePath);
                if (!moduleFile.exists() || !moduleFile.canRead()) {
                    XposedBridge.log(TAG + ": sModulePath is invalid or unreadable: " + sModulePath);
                    needRefresh = true;
                }
            }

            // 尝试动态获取模块路径（解决ENOENT问题）
            if (needRefresh) {
                try {
                    Context moduleContext = ctx.createPackageContext("top.galqq", Context.CONTEXT_IGNORE_SECURITY);
                    sModulePath = moduleContext.getApplicationInfo().sourceDir;
                    XposedBridge.log(TAG + ": Resolved module path via createPackageContext: " + sModulePath);
                } catch (Exception e) {
                    XposedBridge.log(TAG + ": Failed to resolve module path: " + e.getMessage());
                }
            }

            Class<?> clazz_ActivityThread = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = clazz_ActivityThread.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object sCurrentActivityThread = currentActivityThread.invoke(null);
            
            Field mInstrumentation = clazz_ActivityThread.getDeclaredField("mInstrumentation");
            mInstrumentation.setAccessible(true);
            Instrumentation instrumentation = (Instrumentation) mInstrumentation.get(sCurrentActivityThread);
            if (!(instrumentation instanceof ProxyInstrumentation)) {
                mInstrumentation.set(sCurrentActivityThread, new ProxyInstrumentation(instrumentation));
            }

            Field field_mH = clazz_ActivityThread.getDeclaredField("mH");
            field_mH.setAccessible(true);
            Handler oriHandler = (Handler) field_mH.get(sCurrentActivityThread);
            Field field_mCallback = Handler.class.getDeclaredField("mCallback");
            field_mCallback.setAccessible(true);
            Handler.Callback current = (Handler.Callback) field_mCallback.get(oriHandler);
            if (current == null || !current.getClass().getName().equals(ProxyHandlerCallback.class.getName())) {
                field_mCallback.set(oriHandler, new ProxyHandlerCallback(current));
            }

            Class<?> activityManagerClass;
            Field gDefaultField;
            try {
                activityManagerClass = Class.forName("android.app.ActivityManagerNative");
                gDefaultField = activityManagerClass.getDeclaredField("gDefault");
            } catch (Exception err1) {
                try {
                    activityManagerClass = Class.forName("android.app.ActivityManager");
                    gDefaultField = activityManagerClass.getDeclaredField("IActivityManagerSingleton");
                } catch (Exception err2) {
                    XposedBridge.log(TAG + ": Unable to get IActivityManagerSingleton");
                    return;
                }
            }
            gDefaultField.setAccessible(true);
            Object gDefault = gDefaultField.get(null);
            Class<?> singletonClass = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonClass.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);
            Object mInstance = mInstanceField.get(gDefault);
            
            if (mInstance != null) {
                Object amProxy = Proxy.newProxyInstance(
                        Parasitics.class.getClassLoader(),
                        new Class[]{Class.forName("android.app.IActivityManager")},
                        new IActivityManagerHandler(mInstance, ctx));
                mInstanceField.set(gDefault, amProxy);
            }

            try {
                Class<?> activityTaskManagerClass = Class.forName("android.app.ActivityTaskManager");
                Field fIActivityTaskManagerSingleton = activityTaskManagerClass.getDeclaredField("IActivityTaskManagerSingleton");
                fIActivityTaskManagerSingleton.setAccessible(true);
                Object singleton = fIActivityTaskManagerSingleton.get(null);
                singletonClass.getMethod("get").invoke(singleton);
                Object mDefaultTaskMgr = mInstanceField.get(singleton);
                Object proxy2 = Proxy.newProxyInstance(
                        Parasitics.class.getClassLoader(),
                        new Class[]{Class.forName("android.app.IActivityTaskManager")},
                        new IActivityManagerHandler(mDefaultTaskMgr, ctx));
                mInstanceField.set(singleton, proxy2);
            } catch (Exception ignored) {
            }

            __stub_hooked = true;
            XposedBridge.log(TAG + ": Activity Proxy initialized");
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to init Activity Proxy: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    public static class IActivityManagerHandler implements InvocationHandler {
        private final Object mOrigin;
        private final Context mContext;

        public IActivityManagerHandler(Object origin, Context context) {
            mOrigin = origin;
            mContext = context;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("startActivity".equals(method.getName())) {
                int index = -1;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Intent) {
                        index = i;
                        break;
                    }
                }
                if (index != -1) {
                    Intent raw = (Intent) args[index];
                    ComponentName component = raw.getComponent();
                    if (component != null
                            && mContext.getPackageName().equals(component.getPackageName())
                            && ActProxyMgr.isModuleProxyActivity(component.getClassName())) {
                        
                        Intent wrapper = new Intent();
                        wrapper.setClassName(component.getPackageName(), ActProxyMgr.STUB_DEFAULT_ACTIVITY);
                        wrapper.putExtra(ActProxyMgr.STUB_DEFAULT_ACTIVITY, raw); // Use key as marker
                        args[index] = wrapper;
                        XposedBridge.log(TAG + ": Intercepted startActivity for " + component.getClassName());
                    }
                }
            }
            try {
                return method.invoke(mOrigin, args);
            } catch (InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        }
    }

    public static class ProxyHandlerCallback implements Handler.Callback {
        private final Handler.Callback mNextCallbackHook;

        public ProxyHandlerCallback(Handler.Callback next) {
            mNextCallbackHook = next;
        }

        @Override
        public boolean handleMessage(Message msg) {
            // Only log critical messages to avoid spam
            // Remove all logging for common messages like what=131, 115, 114, etc.
            if (msg.what == 159) { // EXECUTE_TRANSACTION - this is important
                onHandleExecuteTransaction(msg);
            }
            // Call next callback without logging other messages
            if (mNextCallbackHook != null) {
                return mNextCallbackHook.handleMessage(msg);
            }
            return false;
        }

        private void onHandleLaunchActivity(Message msg) {
            XposedBridge.log("GalQQ.ProxyHandlerCallback: onHandleLaunchActivity called");
            try {
                Object activityClientRecord = msg.obj;
                Field field_intent = activityClientRecord.getClass().getDeclaredField("intent");
                field_intent.setAccessible(true);
                Intent intent = (Intent) field_intent.get(activityClientRecord);
                if (intent != null) {
                    Bundle bundle = null;
                    // 关键：克隆Intent（QAuxiliary的做法）
                    Intent cloneIntent = new Intent(intent);
                    try {
                        Field fExtras = Intent.class.getDeclaredField("mExtras");
                        fExtras.setAccessible(true);
                        bundle = (Bundle) fExtras.get(cloneIntent);
                    } catch (Exception e) {
                        XposedBridge.log(e);
                    }
                    
                    if (bundle != null) {
                        // 设置为宿主(QQ)的ClassLoader
                        bundle.setClassLoader(Context.class.getClassLoader());
                        // 处理代理Activity
                        if (cloneIntent.hasExtra(ActProxyMgr.STUB_DEFAULT_ACTIVITY)) {
                            Intent realIntent = cloneIntent.getParcelableExtra(ActProxyMgr.STUB_DEFAULT_ACTIVITY);
                            field_intent.set(activityClientRecord, realIntent);
                        }
                    }
                }
            } catch (Exception e) {
                XposedBridge.log(e);
            }
        }

        private void onHandleExecuteTransaction(Message msg) {
            Object clientTransaction = msg.obj;
            try {
                if (clientTransaction != null) {
                    Method getCallbacks = Class.forName("android.app.servertransaction.ClientTransaction").getDeclaredMethod("getCallbacks");
                    getCallbacks.setAccessible(true);
                    List<?> clientTransactionItems = (List<?>) getCallbacks.invoke(clientTransaction);
                    if (clientTransactionItems != null) {
                        for (Object item : clientTransactionItems) {
                            String itemClassName = item.getClass().getName();
                            if (itemClassName.contains("LaunchActivityItem")) {
                                processLaunchActivityItem(item);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Only log errors, not normal operations
                XposedBridge.log("GalQQ.ProxyHandlerCallback: Error in EXECUTE_TRANSACTION: " + e.getMessage());
            }
        }

        private void processLaunchActivityItem(Object item) {
            try {
                Field fmIntent = item.getClass().getDeclaredField("mIntent");
                fmIntent.setAccessible(true);
                Intent wrapper = (Intent) fmIntent.get(item);
                if (wrapper != null) {
                    // Clone Intent following QAuxiliary's approach
                    Intent cloneIntent = (Intent) wrapper.clone();
                    Bundle bundle = null;
                    try {
                        Field fExtras = Intent.class.getDeclaredField("mExtras");
                        fExtras.setAccessible(true);
                        bundle = (Bundle) fExtras.get(cloneIntent);
                    } catch (Exception e) {
                        // Ignore
                    }
                    
                    if (bundle != null) {
                        // Set to host (QQ) ClassLoader
                        bundle.setClassLoader(Context.class.getClassLoader());
                        // Handle proxy Activity
                        if (cloneIntent.hasExtra(ActProxyMgr.STUB_DEFAULT_ACTIVITY)) {
                            Intent realIntent = cloneIntent.getParcelableExtra(ActProxyMgr.STUB_DEFAULT_ACTIVITY);
                            fmIntent.set(item, realIntent);
                        }
                    }
                }
            } catch (Exception e) {
                // Only log errors
                XposedBridge.log("GalQQ.ProxyHandlerCallback: Error in processLaunchActivityItem: " + e.getMessage());
            }
        }
    }

    public static class ProxyInstrumentation extends Instrumentation {
        private final Instrumentation mBase;

        public ProxyInstrumentation(Instrumentation base) {
            mBase = base;
        }

        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent)
                throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            try {
                return mBase.newActivity(cl, className, intent);
            } catch (Exception e) {
                if (ActProxyMgr.isModuleProxyActivity(className)) {
                    return (Activity) GalqqHook.class.getClassLoader().loadClass(className).newInstance();
                }
                throw e;
            }
        }

        @Override
        public void callActivityOnCreate(Activity activity, Bundle icicle) {
            // 对savedInstanceState Bundle设置ClassLoader（仅模块Activity需要）
            if (icicle != null) {
                String className = activity.getClass().getName();
                if (ActProxyMgr.isModuleProxyActivity(className)) {
                    icicle.setClassLoader(GalqqHook.class.getClassLoader());
                }
            }
            // 对所有Activity注入资源（QAuxiliary做法）
            injectModuleResources(activity.getResources());
            mBase.callActivityOnCreate(activity, icicle);
        }
        
        
        @Override
        public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
            // 对savedInstanceState Bundle设置ClassLoader（仅模块Activity需要）
            if (icicle != null) {
                String className = activity.getClass().getName();
                if (ActProxyMgr.isModuleProxyActivity(className)) {
                    icicle.setClassLoader(GalqqHook.class.getClassLoader());
                }
            }
            // 对所有Activity注入资源（QAuxiliary做法）
            injectModuleResources(activity.getResources());
            mBase.callActivityOnCreate(activity, icicle, persistentState);
        }
    }
}
