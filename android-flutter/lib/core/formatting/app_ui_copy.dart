/// Copy added by the UI redesign.
///
/// [AppStrings] is generated from the Kotlin origin (`tool/port_app_strings.py`)
/// and must stay byte-identical to it, so redesign-only wording lives here
/// instead of being hand-edited into that file. Everything the two clients
/// share still comes from [AppStrings]; this file only holds strings that exist
/// because the Flutter screens gained a state, an action or a label the Kotlin
/// app never had.
///
/// The app is English-only (see `AppLanguage`), so this is a plain constant
/// holder rather than a localisable table.
library;

/// User-visible copy introduced by the redesign.
abstract final class AppUiCopy {
  /// Trailing action that reveals the rest of a section ("See all").
  static const String seeAll = 'See all';

  /// Semantic label of a back affordance.
  static const String back = 'Back';

  /// Semantic label of a sheet/dialog close affordance.
  static const String close = 'Close';

  /// Tooltip of the message bar dismiss button.
  static const String dismissMessage = 'Dismiss message';

  /// Tooltip of a copy-to-clipboard affordance.
  static const String copy = 'Copy';

  /// Tone word shown and announced beside a success message.
  static const String toneSuccess = 'Success';

  /// Tone word shown and announced beside an error message.
  static const String toneError = 'Error';

  /// Tone word shown and announced beside an informational message.
  static const String toneInfo = 'Info';

  /// Helper under the new-password field: the length rule only, because the
  /// registration instructions (pick a username, solve a captcha) do not apply
  /// to changing an existing password.
  static const String passwordMinimumHint = 'At least 8 characters';

  /// Search: heading of the results list.
  static const String searchResultsSection = 'Results';

  /// Search: title of the empty result state.
  static const String searchNoResultsTitle = 'No dramas matched this search';

  /// Search: body of the empty result state.
  static const String searchNoResultsBody =
      'Try a shorter keyword, or pick one of the suggestions below.';

  /// Search: title shown before the user has searched anything.
  static const String searchStartTitle = 'Find your next drama';

  /// Search: body shown before the user has searched anything.
  static const String searchStartBody =
      'Search by title, or start from a tag below.';

  /// Search: non-interactive pill shown with the empty result state. It is a
  /// status prompt, not a control: the field above is where a new search
  /// starts.
  static const String searchAgain = 'Search again';

  /// Funds: section heading of the money actions.
  static const String fundsActionsSection = 'Actions';

  /// Funds: empty title of the VIP order list.
  static const String fundsVipOrdersEmptyTitle = 'No VIP orders yet';

  /// Funds: empty body of the VIP order list.
  static const String fundsVipOrdersEmptyBody =
      'Your VIP recharge orders will appear here.';

  /// Funds: empty title of the point ledger.
  static const String fundsPointRecordsEmptyTitle = 'No point activity yet';

  /// Funds: empty body of the point ledger.
  static const String fundsPointRecordsEmptyBody =
      'Watch an episode to start earning points.';

  /// Funds: guest-state call to action.
  static const String fundsGuestAction = 'Sign in';

  /// Generic retry action for an inline error state.
  static const String retry = 'Retry';

  /// Withdrawal: label of the step that returns to the amount input.
  static const String withdrawalEditAmount = 'Edit amount';

  /// Withdrawal: empty title of the withdrawal record list.
  static const String withdrawalRecordsEmptyTitle = 'No withdrawals yet';

  /// Account: section heading of the shortcut entries.
  static const String accountShortcutsSection = 'Shortcuts';

  /// Account: section heading of the settings rows.
  static const String accountSettingsSection = 'Settings';

  /// VIP: label of the validity row.
  static const String vipValidity = 'Valid for';

  /// VIP: label of the price row.
  static const String vipPrice = 'Price';

  /// Short "Expired" status word for a status pill.
  ///
  /// The full sentence (`vipPayExpired`) is long-form copy: it stays visible as
  /// a caption under the pill rather than being squeezed into the capsule.
  static const String statusExpired = 'Expired';

  /// Player: label of the like action.
  static const String playerLike = 'Like';

  /// Player: label of the comment action.
  static const String playerComments = 'Comments';

  /// Player: empty title of the comment list while it loads.
  static const String playerCommentsLoading = 'Loading comments';

  /// Player: empty title of the comment list.
  static const String playerCommentsEmptyTitle = 'No comments yet';

  /// Player: empty body of the comment list.
  static const String playerCommentsEmptyBody = 'Be the first to comment.';

  /// Player: semantic label of the comment sheet's send affordance, which is an
  /// icon-only button and was previously announced as nothing at all.
  static const String playerCommentSend = 'Send comment';

  /// Player: episode-drawer heading.
  static const String playerEpisodes = 'Episodes';

  /// Password field: semantic label and tooltip of the toggle that reveals a
  /// masked value.
  static const String showPassword = 'Show password';

  /// Password field: semantic label and tooltip of the toggle that masks the
  /// value again.
  static const String hidePassword = 'Hide password';

  /// Episode spine: state word for a partially watched episode, appended after
  /// the count in the generated description (for example `2 in progress`).
  static const String episodeInProgress = 'in progress';
}
