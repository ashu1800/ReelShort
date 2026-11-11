/// A minimal [VideoPlayerPlatform] for widget tests.
///
/// The test binding has no video decoder: the placeholder platform throws on
/// `init()`, so `VideoPlayerController.initialize()` fails and the player never
/// leaves its cover overlay. That hides every surface gesture behind
/// `_surfaceInteractive`. This fake answers the handful of calls initialize
/// makes and reports one fixed video, which is enough for the episode swipe to
/// run end to end.
library;

import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:video_player_platform_interface/video_player_platform_interface.dart';

/// Platform that hands back one ready 720x1280 video per controller.
class FakeVideoPlayerPlatform extends VideoPlayerPlatform {
  /// Creates the fake with the reported geometry.
  FakeVideoPlayerPlatform({
    this.size = const Size(720, 1280),
    this.duration = const Duration(minutes: 2),
  });

  /// Reported video size.
  final Size size;

  /// Reported video duration.
  final Duration duration;

  final Map<int, StreamController<VideoEvent>> _events =
      <int, StreamController<VideoEvent>>{};
  int _nextId = 1;

  @override
  Future<void> init() async {}

  @override
  Future<int?> createWithOptions(VideoCreationOptions options) async {
    final int id = _nextId++;
    final StreamController<VideoEvent> events = StreamController<VideoEvent>();
    // A single-subscription controller buffers events until the player
    // subscribes, so the initialized event cannot race the listener.
    events.add(
      VideoEvent(
        eventType: VideoEventType.initialized,
        duration: duration,
        size: size,
      ),
    );
    _events[id] = events;
    return id;
  }

  @override
  Stream<VideoEvent> videoEventsFor(int playerId) => _events[playerId]!.stream;

  @override
  Future<void> dispose(int playerId) async {
    await _events.remove(playerId)?.close();
  }

  @override
  Future<void> play(int playerId) async {}

  @override
  Future<void> pause(int playerId) async {}

  @override
  Future<void> setLooping(int playerId, bool looping) async {}

  @override
  Future<void> setVolume(int playerId, double volume) async {}

  @override
  Future<void> setPlaybackSpeed(int playerId, double speed) async {}

  @override
  Future<void> seekTo(int playerId, Duration position) async {}

  @override
  Future<void> setMixWithOthers(bool mixWithOthers) async {}

  @override
  Future<Duration> getPosition(int playerId) async => Duration.zero;

  @override
  Widget buildViewWithOptions(VideoViewOptions options) =>
      const SizedBox.shrink();
}
