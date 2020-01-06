package com.novv.dzdesk;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import com.novv.dzdesk.ui.activity.SplashActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Properties;
import java.util.TreeSet;

public class VVCrashHandler implements Thread.UncaughtExceptionHandler {
  /**
   * Debug Log tag
   */
  public static final String TAG = "VVCrashHandler";
  /**
   * 是否开启日志输出,在Debug状态下开启, 在Release状态下关闭以提示程序性能
   */
  public static final boolean DEBUG = BuildConfig.DEBUG;
  private static final String VERSION_NAME = "versionName";
  private static final String VERSION_CODE = "versionCode";
  private static final String STACK_TRACE = "STACK_TRACE";
  /**
   * 错误报告文件的扩展名
   */
  private static final String CRASH_REPORTER_EXTENSION = ".cr";
  /**
   * CrashHandler实例
   */
  private static VVCrashHandler mVVCrashHandler;
  /**
   * 系统默认的UncaughtException处理类
   */
  private Thread.UncaughtExceptionHandler mDefaultHandler;
  /**
   * 程序的Context对象
   */
  private Context mContext;
  /**
   * 使用Properties来保存设备的信息和错误堆栈信息
   */
  private Properties mDeviceCrashInfo = new Properties();

  /**
   * 初始化,注册Context对象, 获取系统默认的UncaughtException处理器, 设置该CrashHandler为程序的默认处理器
   */

  private VVCrashHandler(Context ctx) {
    mContext = ctx;
    mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(this);
  }

  /**
   * 获取CrashHandler实例 ,单例模式
   */
  public static VVCrashHandler getInstance(Context context) {
    if (mVVCrashHandler == null) {
      synchronized (VVCrashHandler.class) {
        if (mVVCrashHandler == null) {
          mVVCrashHandler = new VVCrashHandler(context);
        }
      }
    }
    return mVVCrashHandler;
  }

  /**
   * 当UncaughtException发生时会转入该函数来处理
   */
  @Override
  public void uncaughtException(Thread thread, Throwable ex) {
    Log.i("VVCrashHandler", "---uncaughtException()--->" + "UncaughtException发生");
    if (!handleException(ex) && mDefaultHandler != null) {
      // 如果用户没有处理 则让系统默认的异常处理器来处理
      mDefaultHandler.uncaughtException(thread, ex);
    } else {
      Log.i("VVCrashHandler", "---uncaughtException()--->" + "自定义处理");
      //自定义处理逻辑
      Intent intent = new Intent();
      intent.setClass(mContext, SplashActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
      mContext.startActivity(intent);
      android.os.Process.killProcess(android.os.Process.myPid());
    }
  }

  /**
   * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成. 开发者可以根据自己的情况来自定义异常处理逻辑
   *
   * @return true:如果处理了该异常信息;否则返回false
   */
  private boolean handleException(Throwable ex) {
    if (ex == null) {
      return true;
    }
    final String msg = ex.getLocalizedMessage();
    // 使用Toast来显示异常信息
    new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        Toast.makeText(mContext, "程序出现异常," + msg, Toast.LENGTH_LONG).show();
        Looper.loop();
      }
    }.start();
    // 收集设备信息
    collectCrashDeviceInfo(mContext);
    // 保存错误报告文件
    //String crashFileName = saveCrashInfoToFile(ex);
    // 发送错误报告到服务器
    //sendCrashReportsToServer(mContext);
    return true;
  }

  /**
   * 在程序启动时候, 可以调用该函数来发送以前没有发送的报告
   */
  public void sendPreviousReportsToServer() {
    sendCrashReportsToServer(mContext);
  }

  /**
   * 把错误报告发送给服务器,包含新产生的和以前没发送的.
   */
  private void sendCrashReportsToServer(Context ctx) {
    String[] crFiles = getCrashReportFiles(ctx);
    if (crFiles != null && crFiles.length > 0) {
      TreeSet<String> sortedFiles = new TreeSet<String>();
      sortedFiles.addAll(Arrays.asList(crFiles));

      for (String fileName : sortedFiles) {
        File cr = new File(ctx.getFilesDir(), fileName);
        postReport(cr);
        cr.delete();
      }
    }
  }

  private void postReport(File file) {
    //
  }

  /**
   * 获取错误报告文件名
   */
  private String[] getCrashReportFiles(Context ctx) {
    File filesDir = ctx.getFilesDir();
    FilenameFilter filter = new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(CRASH_REPORTER_EXTENSION);
      }
    };
    return filesDir.list(filter);
  }

  /**
   * 保存错误信息到文件中
   */
  private String saveCrashInfoToFile(Throwable ex) {
    Writer info = new StringWriter();
    PrintWriter printWriter = new PrintWriter(info);
    ex.printStackTrace(printWriter);

    Throwable cause = ex.getCause();
    while (cause != null) {
      cause.printStackTrace(printWriter);
      cause = cause.getCause();
    }

    String result = info.toString();
    printWriter.close();
    mDeviceCrashInfo.put(STACK_TRACE, result);
    String fileName = "";
    try {
      long timestamp = System.currentTimeMillis();
      fileName = "crash-" + timestamp + CRASH_REPORTER_EXTENSION;
      FileOutputStream trace = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
      mDeviceCrashInfo.store(trace, "");
      trace.flush();
      trace.close();
      Log.i("VVCrashHandler", "---saveCrashInfoToFile()--->" + "保存成功");
      return fileName;
    } catch (Exception e) {
      Log.e(TAG, "an error occured while writing report file..." + fileName, e);
    }
    return null;
  }

  /**
   * 收集程序崩溃的设备信息
   */
  public void collectCrashDeviceInfo(Context ctx) {
    try {
      PackageManager pm = ctx.getPackageManager();
      PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
      if (pi != null) {
        mDeviceCrashInfo.put(VERSION_NAME, pi.versionName == null ? "not set" : pi
            .versionName);
        mDeviceCrashInfo.put(VERSION_CODE, pi.versionCode);
        Log.i("VVCrashHandler", "---collectCrashDeviceInfo()--->name   " + pi.versionName);
        Log.i("VVCrashHandler", "---collectCrashDeviceInfo()--->code   " + pi.versionCode);
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Error while collect package info", e);
    }
    // 使用反射来收集设备信息.在Build类中包含各种设备信息,
    // 例如: 系统版本号,设备生产商 等帮助调试程序的有用信息
    // 具体信息请参考后面的截图
    Field[] fields = Build.class.getDeclaredFields();
    for (Field field : fields) {
      try {
        field.setAccessible(true);
        mDeviceCrashInfo.put(field.getName(), field.get(null));
        Log.i("VVCrashHandler", "---collectCrashDeviceInfo()--->设备信息   " + field.getName()
            + " : " + field.get(null));
        if (DEBUG) {
          Log.d(TAG, field.getName() + " : " + field.get(null));
        }
      } catch (Exception e) {
        Log.e(TAG, "Error while collect crash info", e);
      }
    }
  }
}
