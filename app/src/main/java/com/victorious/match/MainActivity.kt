package com.victorious.match

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.victorious.match.fleet.FleetApi
import com.victorious.match.ui.App
import com.victorious.match.ui.Screen

class MainActivity : ComponentActivity() {
    private val requested = mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { App(requested) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        // Only reachable with OPEN_SCREEN via the permission-guarded activity-alias.
        if (i?.action == FleetApi.ACTION_OPEN_SCREEN && i.component?.className?.endsWith(".fleet.OpenScreenAlias") == true) requested.value = Screen.fromKey(i.getStringExtra(FleetApi.EXTRA_SCREEN))
    }
}
