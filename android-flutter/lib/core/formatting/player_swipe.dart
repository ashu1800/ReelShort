/// Episode-swipe contract for the player.
///
/// The player's middle drag swipes to the previous/next episode in list order;
/// this file owns the two numbers the gesture fires on and the offset the reel
/// surface follows while the finger is down. Keeping them here means the
/// thresholds are testable without a video platform, and the screen owns only
/// the animation around them.
library;

/// Minimum vertical travel (logical pixels) before an episode swipe fires.
const double episodeSwipeDistance = 80;

/// Minimum release velocity (logical pixels per second) that fires a swipe
/// even when the finger travelled less than [episodeSwipeDistance].
const double episodeSwipeVelocity = 400;

/// Fraction of the travel a blocked direction (first/last episode) follows.
///
/// The reel still answers the finger, but at a quarter of the travel, so the
/// edge reads as "nothing there" instead of animating a whole page away.
const double episodeSwipeResistance = 0.25;

/// Direction of an episode swipe: `1` = next, `-1` = previous, `0` = snapshot
/// back to the current episode.
///
/// [travel] is the accumulated drag distance (negative = finger moved up) and
/// [velocity] the release velocity (negative = flick up), matching the order
/// the player has always used: up/next, down/previous.
int episodeSwipeDirection({
  required double travel,
  required double velocity,
}) {
  if (velocity < -episodeSwipeVelocity || travel < -episodeSwipeDistance) {
    return 1;
  }
  if (velocity > episodeSwipeVelocity || travel > episodeSwipeDistance) {
    return -1;
  }
  return 0;
}

/// Vertical offset of the reel surface for the current drag, in logical pixels.
///
/// `hasNext`/`hasPrevious` describe the neighbouring episodes, so a blocked
/// direction only rubber-bands instead of following the finger one to one.
/// [limit] caps the offset at one screen height in either direction.
double episodeSwipeDragOffset({
  required double travel,
  required double limit,
  required bool hasNext,
  required bool hasPrevious,
}) {
  final bool blocked =
      (travel < 0 && !hasNext) || (travel > 0 && !hasPrevious);
  final double offset = blocked ? travel * episodeSwipeResistance : travel;
  return offset.clamp(-limit, limit);
}
