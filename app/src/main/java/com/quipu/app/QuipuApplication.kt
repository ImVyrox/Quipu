package com.quipu.app

import android.app.Application
import com.quipu.app.data.QuipuDatabase
import com.quipu.app.data.QuipuRepository
import com.quipu.app.data.SettingsStore

class QuipuApplication : Application() {
    val repository: QuipuRepository by lazy {
        QuipuRepository(QuipuDatabase.get(this), SettingsStore(this))
    }
}
