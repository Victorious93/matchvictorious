package com.victorious.match.fleet

/** Public fleet contract for droidcommand-AI (EXTERNAL_NODE). See docs/MANUAL.md#fleet-api. */
object FleetApi {
    const val PERMISSION = "com.victorious.match.permission.FLEET_CONTROL"
    const val ACTION_STATUS = "com.victorious.match.action.STATUS"
    const val ACTION_PING = "com.victorious.match.action.PING"
    const val ACTION_OPEN_SCREEN = "com.victorious.match.action.OPEN_SCREEN"
    const val ACTION_STATUS_REPLY = "com.victorious.match.action.STATUS_REPLY"
    const val EXTRA_SCREEN = "screen"
    const val EXTRA_REPLY_PACKAGE = "reply_package"
    val SCREENS = listOf("profiles", "matches", "messages", "filters", "import", "guide", "help", "settings")
}
