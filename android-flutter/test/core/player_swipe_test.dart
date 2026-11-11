/// Unit tests for the episode-swipe contract (`player_swipe.dart`).
///
/// The player's swipe behaviour is a ported contract: travel past 80 px or a
/// release velocity past 400 px/s fires, up = next, down = previous. The reel
/// animation adds visual travel on top, so these numbers must not drift with
/// it; a regression here is a gesture that silently stops switching episodes.
library;

import 'package:flutter_test/flutter_test.dart';

import 'package:shortlink/core/formatting/player_swipe.dart';

void main() {
  group('episodeSwipeDirection', () {
    test('travel past the threshold fires in the drag direction', () {
      expect(episodeSwipeDirection(travel: -81, velocity: 0), 1);
      expect(episodeSwipeDirection(travel: 81, velocity: 0), -1);
    });

    test('travel under the threshold only settles back', () {
      expect(episodeSwipeDirection(travel: -79, velocity: 0), 0);
      expect(episodeSwipeDirection(travel: 79, velocity: 0), 0);
      expect(episodeSwipeDirection(travel: 0, velocity: 0), 0);
    });

    test('a short flick fires without enough travel', () {
      expect(episodeSwipeDirection(travel: -10, velocity: -401), 1);
      expect(episodeSwipeDirection(travel: 10, velocity: 401), -1);
      expect(episodeSwipeDirection(travel: -10, velocity: -399), 0);
      expect(episodeSwipeDirection(travel: 10, velocity: 399), 0);
    });
  });

  group('episodeSwipeDragOffset', () {
    test('an available direction follows the finger one to one', () {
      expect(
        episodeSwipeDragOffset(
          travel: -120,
          limit: 800,
          hasNext: true,
          hasPrevious: true,
        ),
        -120,
      );
    });

    test('a blocked edge only rubber-bands the travel', () {
      expect(
        episodeSwipeDragOffset(
          travel: -120,
          limit: 800,
          hasNext: false,
          hasPrevious: true,
        ),
        -120 * episodeSwipeResistance,
      );
      expect(
        episodeSwipeDragOffset(
          travel: 120,
          limit: 800,
          hasNext: true,
          hasPrevious: false,
        ),
        120 * episodeSwipeResistance,
      );
    });

    test('the offset never exceeds one screen height', () {
      expect(
        episodeSwipeDragOffset(
          travel: -2000,
          limit: 800,
          hasNext: true,
          hasPrevious: true,
        ),
        -800,
      );
    });
  });
}
