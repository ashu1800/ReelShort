package com.reelshort.app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/**
 * Single Flutter-backed activity. The whole ShortLink UI lives in Dart; this class only exists so
 * Android has a launcher entry point (the app previously used a Compose activity here). It also
 * registers the in-app update installer plugin the Dart self-update subsystem calls through.
 */
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        UpdateInstallerPlugin.registerWith(this, flutterEngine.dartExecutor.binaryMessenger)
    }
}
