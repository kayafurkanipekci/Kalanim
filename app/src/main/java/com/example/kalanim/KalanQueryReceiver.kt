package com.example.kalanim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class KalanQueryReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START_QUERY = "com.example.kalanim.ACTION_START_QUERY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_START_QUERY) {
            KalanQueryService.startQuery(context)
        }
    }
}

