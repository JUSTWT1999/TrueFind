package com.truefind.app

import android.app.Application
import com.truefind.app.data.CaptureCacheManager
import com.truefind.app.data.PromptTemplateRepository
import com.truefind.app.data.SettingsStore
import com.truefind.app.dispatch.AiAppRegistry
import com.truefind.app.dispatch.AiDispatcher

class AppContainer(application: Application) {
    val settingsStore = SettingsStore(application)
    val templateRepository = PromptTemplateRepository(settingsStore.keyValueStore)
    val cacheManager = CaptureCacheManager(application)
    val aiAppRegistry = AiAppRegistry(application)
    val aiDispatcher = AiDispatcher(application, cacheManager)
}

class TrueFindApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.cacheManager.cleanup()
    }
}
