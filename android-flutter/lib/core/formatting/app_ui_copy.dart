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

  /// Action label that expands a truncated paragraph.
  static const String readMore = 'More';

  /// Action label that collapses an expanded paragraph.
  static const String readLess = 'Less';

  /// Semantic label of a back affordance.
  static const String back = 'Back';

  /// Semantic label of a sheet/dialog close affordance.
  static const String close = 'Close';

  /// Tooltip of the message bar dismiss button.
  static const String dismissMessage = 'Dismiss message';

  /// Tooltip of a copy-to-clipboard affordance.
  static const String copy = 'Copy';

  /// Confirmation shown after a value was copied.
  static const String copied = 'Copied';

  /// Confirmation shown after the wallet address was copied.
  static const String addressCopied = 'Wallet address copied';

  /// Confirmation shown after the transaction hash was copied.
  static const String txHashCopied = 'Transaction hash copied';

  /// Tone word shown and announced beside a success message.
  static const String toneSuccess = 'Success';

  /// Tone word shown and announced beside an error message.
  static const String toneError = 'Error';

  /// Tone word shown and announced beside an informational message.
  static const String toneInfo = 'Info';

  /// Search: heading of the results list.
  static const String searchResultsSection = 'Results';

  /// Search: title of the empty result state.
  static const String searchNoResultsTitle = 'No dramas matched this search';

  /// Search: body of the empty result state.
  static const String searchNoResultsBody =
      'Try a shorter keyword, or pick one of the suggestions below.';

  /// Search: heading of the recent-search list.
  static const String searchRecentSection = 'Recent searches';

  /// Search: action that clears the recent-search list.
  static const String searchRecentClear = 'Clear';

  /// Search: title shown before the user has searched anything.
  static const String searchStartTitle = 'Find your next drama';

  /// Search: body shown before the user has searched anything.
  static const String searchStartBody =
      'Search by title, or start from a tag below.';

  /// Search: label of the in-field search affordance.
  static const String searchSubmit = 'Search';

  /// Favorites: page subtitle carrying the bookmarked count.
  static String favoritesCount(int count) =>
      count == 1 ? '1 drama saved' : '$count dramas saved';

  /// Favorites: empty-state action.
  static const String favoritesBrowse = 'Browse dramas';

  /// Funds: section heading of the balance overview.
  static const String fundsOverviewSection = 'Overview';

  /// Funds: section heading of the money actions.
  static const String fundsActionsSection = 'Actions';

  /// Funds: section heading of the detail lists.
  static const String fundsDetailsSection = 'Details';

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

  /// Generic title of an inline load failure.
  static const String loadFailedTitle = 'Something went wrong';

  /// Generic body of an inline load failure.
  static const String loadFailedBody =
      'Check your connection and try again.';

  /// Withdrawal: action that opens the wallet sheet when none is bound.
  static const String withdrawalBindWallet = 'Bind a wallet';

  /// Withdrawal: validation message when the amount is below the minimum.
  static String withdrawalBelowMinimum(String minimum) =>
      'Enter at least $minimum points';

  /// Withdrawal: validation message when the amount exceeds the balance.
  static const String withdrawalExceedsBalance =
      'This is more than your available balance';

  /// Withdrawal: label of the step that returns to the amount input.
  static const String withdrawalEditAmount = 'Edit amount';

  /// Withdrawal: confirmation hint on the review step.
  static const String withdrawalReviewHint =
      'Review the amount and the receiving wallet before you confirm.';

  /// Withdrawal: empty title of the withdrawal record list.
  static const String withdrawalRecordsEmptyTitle = 'No withdrawals yet';

  /// Account: section heading of the shortcut entries.
  static const String accountShortcutsSection = 'Shortcuts';

  /// Account: section heading of the settings rows.
  static const String accountSettingsSection = 'Settings';

  /// Account: title of the sign-out confirmation.
  static const String accountSignOutConfirmTitle = 'Sign out?';

  /// Account: body of the sign-out confirmation.
  static const String accountSignOutConfirmBody =
      'You can sign in again at any time; your points and orders stay on your account.';

  /// Account: confirm label of the sign-out confirmation.
  static const String accountSignOutConfirmAction = 'Sign out';

  /// Account: confirmation shown after the support email was copied.
  static const String accountSupportEmailCopied = 'Support email copied';

  /// VIP: title of the order-creation confirmation.
  static const String vipReviewTitle = 'Confirm your VIP order';

  /// VIP: body of the order-creation confirmation.
  static const String vipReviewBody =
      'Check the price, the receiving wallet and the validity window before you continue.';

  /// VIP: label of the receiving wallet row.
  static const String vipReceivingWallet = 'Receiving wallet';

  /// VIP: label of the validity row.
  static const String vipValidity = 'Valid for';

  /// VIP: label of the price row.
  static const String vipPrice = 'Price';

  /// VIP: notice shown when the order expired while the sheet was open.
  static const String vipOrderExpiredNotice =
      'This order expired. Create a new one to continue.';

  /// VIP: fallback shown when the QR code cannot be rendered.
  static const String vipQrUnavailable =
      'QR code unavailable — copy the wallet address instead.';

  /// VIP: action that re-checks the payment status on the create step.
  static const String vipRefreshStatus = 'Refresh status';

  /// Player: action that replays the finished episode.
  static const String playerReplay = 'Replay';

  /// Player: semantic label of the seek bar.
  static const String playerSeek = 'Playback position';

  /// Player: label of the like action.
  static const String playerLike = 'Like';

  /// Player: label of the comment action.
  static const String playerComments = 'Comments';

  /// Player: label of the favorite action.
  static const String playerFavorite = 'Favorite';

  /// Player: empty title of the comment list while it loads.
  static const String playerCommentsLoading = 'Loading comments';

  /// Player: empty title of the comment list.
  static const String playerCommentsEmptyTitle = 'No comments yet';

  /// Player: empty body of the comment list.
  static const String playerCommentsEmptyBody = 'Be the first to comment.';

  /// Player: message shown when the media URL is missing.
  static const String playerSourceMissing =
      'This episode has no playable source yet.';

  /// Player: episode-drawer heading.
  static const String playerEpisodes = 'Episodes';

  /// Detail: heading of the episode list.
  static const String detailEpisodesSection = 'Episodes';

  /// Detail: heading of the synopsis block.
  static const String detailSynopsisSection = 'Synopsis';

  /// Detail: heading of the series-progress block.
  static const String detailProgressSection = 'Your progress';

  /// Detail: primary action when nothing has been watched yet.
  static const String detailPlayFirst = 'Play episode 1';

  /// Detail: primary action when the series was already started.
  static String detailContinueEpisode(int number) => 'Continue EP $number';

  /// Detail: primary action when the series is finished.
  static const String detailWatchAgain = 'Watch again';
}
