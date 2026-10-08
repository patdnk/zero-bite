package com.mazesys.zerobite

import android.app.Application

class ZeroBiteApplication : Application() {
    val dependencies: AppDependencies by lazy { AppDependencies.live(this) }
}
