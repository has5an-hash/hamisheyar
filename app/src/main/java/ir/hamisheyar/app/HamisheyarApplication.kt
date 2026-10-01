package ir.hamisheyar.app

import android.app.Application
import android.os.Build
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger

class HamisheyarApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        DiagnosticsLogger.log(
            this,
            "APP",
            "start version=${BuildConfig.VERSION_NAME} device=${Build.MANUFACTURER} ${Build.MODEL} api=${Build.VERSION.SDK_INT}"
        )

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                DiagnosticsLogger.log(
                    this,
                    "CRASH",
                    "uncaught thread=${thread.name}",
                    throwable
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
