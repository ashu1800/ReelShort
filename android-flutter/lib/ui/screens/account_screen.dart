/// Account: the member-centre "Me" page.
///
/// Behavioural parity with the Kotlin `AccountScreen`: no refresh and no withdrawal/VIP/diagnostics
/// entries (G10/G11 — only three of the four declared quick entries render), the continue-watching
/// preview capped at two rows with the `size/limit` pill, the wallet sheet that closes itself on
/// success through `walletMutationVersion` (bind, replace **and** unbind), the password sheet that
/// never auto-closes, the read-only bank-card explanation, the selectable support email, the
/// version row with the manual update check, a sign-out without a confirmation step, and the
/// guest entry that signs in or registers directly.
///
/// Presentation follows `docs/ui-redesign-notes.md` §7.1: one `screenTitle` page title, an
/// identity hero that carries the membership treatment, `AppSectionHeader` + `AppSectionCard`
/// sections instead of the hand-written cards of the first build, and the page inset from
/// [AppSpacing.pagePadding]. Every colour, radius, gap and type role is a token.
library;

import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/navigation_contract.dart';
import '../../core/formatting/app_ui_copy.dart';
import '../../core/formatting/ui_formats.dart';
import '../../core/formatting/update_ui_format.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';
import '../../update/update_coordinator.dart';
import '../../update/update_state.dart';
import '../components/app_button.dart';
import '../components/app_list_row.dart';
import '../components/app_loading.dart';
import '../components/app_section.dart';
import '../components/app_text_field.dart';
import '../components/poster_card.dart';
import '../update/update_dialog.dart';

/// `versionName (versionCode)` of the running build; `--` where the plugin is
/// unavailable (widget tests). Memoised like the Kotlin `BuildConfig` constant.
final Future<String> _appVersionLabel = () async {
  try {
    final PackageInfo info = await PackageInfo.fromPlatform();
    return '${info.version} (${info.buildNumber})';
  } catch (_) {
    return '--';
  }
}();

/// Key of the identity hero, so a test can assert the membership treatment
/// (premium fill plus accent border for VIP, plain surface otherwise) without
/// reaching into a private widget type.
const Key accountIdentityHeroKey = ValueKey<String>('account-identity-hero');

/// Diameter of the hero monogram.
const double _heroAvatarSize = 56;

/// Width of one poster in the continue-watching track, matching the Home grid's
/// minimum column width.
const double _posterLaneWidth = 136;

/// Height of the continue-watching track: the poster's 2:3 art plus its overlay.
const double _posterTrackHeight = 204;

/// Shown in the version row until the platform reports the build.
const String _versionPlaceholder = '--';

/// Size of the neutral plate that stands in for the membership chip until the
/// account snapshot arrives, at 1x text scale. The plate is scaled by the
/// ambient text scale and the real chip's label is capped at one line, so the
/// hero does not resize when the state lands (the delta stays under 20 px even
/// at 3.2x, where an uncapped label used to add 166 px). Exposed so a test can
/// measure the resize.
const Size accountMembershipChipPlaceholderSize = Size(112, 22);

/// The Me screen.
class AccountScreen extends StatelessWidget {
  /// Creates the account screen. The shell instantiates it without arguments.
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final bool guest = state.session == null;
    final double sectionGap = spacing.sectionGap;

    return Scaffold(
      backgroundColor: colors.canvas,
      body: SafeArea(
        bottom: false,
        child: ListView(
          // The single page inset of the system (18/12/18/28).
          padding: spacing.pagePadding,
          children: <Widget>[
            // One page-title role for the whole app, so Me matches its
            // sub-pages instead of shouting in `displayLarge`.
            Text(copy.accountTab, style: roles.screenTitle),
            SizedBox(height: sectionGap),
            _IdentityHero(state: state),
            // The quick entries and the wallet send a guest to the auth prompt
            // anyway, so a guest only sees the hero's two calls to action.
            if (!guest) ...<Widget>[
              SizedBox(height: sectionGap),
              _ShortcutsSection(state: state),
            ],
            if (!guest && state.watchHistory.isNotEmpty) ...<Widget>[
              SizedBox(height: sectionGap),
              _ContinueWatchingSection(state: state),
            ],
            SizedBox(height: sectionGap),
            _SettingsSection(state: state),
            if (!guest) ...<Widget>[
              SizedBox(height: spacing.space6),
              // Parity: signing out still has no confirmation step.
              AppButton(
                label: copy.accountSignOutTitle,
                variant: AppButtonVariant.destructive,
                isExpanded: true,
                onPressed: () => context.read<AppStateController>().logout(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// The identity hero: who the user is and what their membership is worth.
///
/// VIP wears the premium tint with an accent border, so membership is visible
/// before a word is read; everyone else gets the plain surface and the hairline
/// every other card uses. A guest gets the two calls to action in the same
/// block, which is their only way into the app from this page.
class _IdentityHero extends StatelessWidget {
  const _IdentityHero({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final bool guest = state.session == null;
    final bool vip = state.vipActive;
    final String name = state.session?.username.trim() ?? '';
    final String expiry = dateOnlyLabel(state.vipUntil);
    // A guest is announced as `accountGuestUser`, so the monogram uses its
    // initial instead of a bare question mark.
    final String displayName = guest ? copy.accountGuestUser : name;
    final String initial = displayName.isEmpty
        ? '?'
        : displayName.characters.first.toUpperCase();
    // The session lands seconds before the account snapshot does. Until then the
    // membership state is unknown: claiming "VIP not active" for that window was
    // a wrong first impression on device, so the chip becomes a neutral plate.
    final bool vipKnown =
        vip || expiry.isNotEmpty || state.pointAccount != null;
    // The placeholder follows the text scale, because the real chip's height is
    // one line of its label plus padding.
    final double textScale = MediaQuery.textScalerOf(context).scale(1);

    void openRegister() {
      controller.showRegisterAuthMode();
      controller.showAuthPrompt();
    }

    return Container(
      key: accountIdentityHeroKey,
      padding: EdgeInsets.all(spacing.panelPadding),
      decoration: BoxDecoration(
        color: vip ? colors.premiumSurface : colors.surface,
        borderRadius: shapes.containerRadius,
        border: Border.all(
          color: vip ? colors.accentPrimary : colors.hairline,
          width: vip ? shapes.strokeEmphasis : shapes.strokeHairline,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              // The initial is derived from the name next to it, so it is a
              // decoration: the hero node announces the name once.
              ExcludeSemantics(child: _HeroMonogram(initial: initial, vip: vip)),
              SizedBox(width: spacing.space4),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      guest ? copy.accountGuestUser : name,
                      style: roles.displayLarge,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    SizedBox(height: spacing.space2),
                    if (guest)
                      Text(
                        copy.accountGuestStatus,
                        style: roles.body.copyWith(color: colors.textSecondary),
                      )
                    else if (vipKnown)
                      _MembershipChip(
                        active: vip,
                        label: vip
                            ? copy.accountVipStatusActive
                            : copy.accountVipStatusInactive,
                      )
                    else
                      AppSkeleton(
                        width:
                            accountMembershipChipPlaceholderSize.width *
                            textScale,
                        height:
                            accountMembershipChipPlaceholderSize.height *
                            textScale,
                        radius: shapes.radiusInline,
                      ),
                    if (!guest && vip && expiry.isNotEmpty) ...<Widget>[
                      SizedBox(height: spacing.space2),
                      Text(
                        '${copy.accountVipExpiryPrefix}$expiry',
                        style: roles.caption,
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
          if (guest) ...<Widget>[
            SizedBox(height: spacing.space5),
            AppButton(
              label: copy.accountGuestSignIn,
              isExpanded: true,
              onPressed: controller.showAuthPrompt,
            ),
            SizedBox(height: spacing.space3),
            AppButton(
              label: copy.accountGuestRegister,
              variant: AppButtonVariant.secondary,
              isExpanded: true,
              onPressed: openRegister,
            ),
          ],
        ],
      ),
    );
  }
}

/// The hero monogram: one initial, scaled down rather than clipped when the
/// user's text scale outgrows the plate.
class _HeroMonogram extends StatelessWidget {
  const _HeroMonogram({required this.initial, required this.vip});

  final String initial;
  final bool vip;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppSpacing spacing = appSpacingOf(context);

    return Container(
      width: _heroAvatarSize,
      height: _heroAvatarSize,
      decoration: BoxDecoration(
        color: vip ? colors.accentPrimary : colors.surfaceAlt,
        shape: BoxShape.circle,
      ),
      child: Center(
        child: FittedBox(
          fit: BoxFit.scaleDown,
          child: Padding(
            padding: EdgeInsets.all(spacing.space2),
            child: Text(
              initial,
              style: roles.displayLarge.copyWith(
                color: vip ? colors.onAccent : colors.textPrimary,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The membership chip: a tinted pill with a glyph and a word, so the state is
/// never carried by the tint alone.
class _MembershipChip extends StatelessWidget {
  const _MembershipChip({required this.active, required this.label});

  final bool active;
  final String label;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppIconSize icons = appIconSizeOf(context);
    final AppSpacing spacing = appSpacingOf(context);
    final Color accent = active ? colors.accentSuccess : colors.accentWarning;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: spacing.space3,
        vertical: spacing.space1,
      ),
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.14),
        borderRadius: shapes.inlineRadius,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Icon(
            active ? Icons.verified_outlined : Icons.lock_outline,
            size: icons.sm,
            color: accent,
          ),
          SizedBox(width: spacing.space1),
          Flexible(
            child: Text(
              label,
              // One line at every text scale: the chip is a status badge, and
              // an uncapped label used to add 166 px to the hero at 3.2x. The
              // full wording stays in the semantics label.
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: accent,
                fontWeight: FontWeight.w700,
                letterSpacing: 1,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// The quick entries: Favorites, the full watch history and the password sheet.
///
/// Parity gap G11 is preserved: the declared entry list carries four labels but
/// only these three render, because the funds entry became a bottom tab when the
/// money screens moved down a level.
class _ShortcutsSection extends StatelessWidget {
  const _ShortcutsSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppStrings copy = strings(state.language);
    final AppSpacing spacing = appSpacingOf(context);
    final AppStateController controller = context.read<AppStateController>();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        const AppSectionHeader(title: AppUiCopy.accountShortcutsSection),
        SizedBox(height: spacing.space2),
        AppSectionCard(
          children: <Widget>[
            AppListRow(
              title: copy.accountFavoritesTitle,
              subtitle: copy.accountFavoritesSubtitle,
              leadingIcon: Icons.bookmark_border_outlined,
              showChevron: true,
              divider: false,
              onTap: controller.openFavorites,
            ),
            AppListRow(
              title: copy.accountWatchHistoryTitle,
              subtitle: copy.accountRecentWatchSubtitle,
              leadingIcon: Icons.history_outlined,
              showChevron: true,
              divider: false,
              onTap: () => _showWatchHistorySheet(context),
            ),
            AppListRow(
              title: copy.accountChangePasswordTitle,
              subtitle: copy.accountSignOutSubtitle,
              leadingIcon: Icons.vpn_key_outlined,
              showChevron: true,
              divider: false,
              onTap: () => _showPasswordSheet(context),
            ),
          ],
        ),
      ],
    );
  }
}

/// The continue-watching section: the section header carries the `size/limit`
/// pill, and the track keeps the poster cards that resume a record.
class _ContinueWatchingSection extends StatelessWidget {
  const _ContinueWatchingSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final int limit = accountContinueWatchingLimit();
    // The preview shows at most `accountContinueWatchingLimit()` records and the
    // pill counts that trimmed list: `size/limit`.
    final List<WatchRecord> records = state.watchHistory
        .take(limit)
        .toList(growable: false);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        AppSectionHeader(
          title: copy.accountRecentWatchTitle,
          trailing: _CountPill(text: '${records.length}/$limit'),
        ),
        SizedBox(height: spacing.space3),
        SizedBox(
          height: _posterTrackHeight,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: records.length,
            separatorBuilder: (_, _) => SizedBox(width: spacing.gutter),
            itemBuilder: (BuildContext context, int index) {
              final WatchRecord record = records[index];
              final BookSummary? book =
                  state.continueWatchingBooks[record.bookId];
              return SizedBox(
                width: _posterLaneWidth,
                child: PosterCard(
                  title:
                      (book?.title ?? '').isNotEmpty
                      ? book!.title
                      : record.bookTitle,
                  coverUrl: book?.coverUrl,
                  episodeCount: book?.chapterCount ?? 0,
                  currentEpisode: record.episode,
                  watchedEpisodeCount: record.progressPercent >= 100
                      ? record.episode
                      : record.episode - 1,
                  onTap: () => controller.openWatchRecord(record),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// The `size/limit` pill of the continue-watching header: a count, never a
/// control, so it carries no action.
class _CountPill extends StatelessWidget {
  const _CountPill({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppSpacing spacing = appSpacingOf(context);

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: spacing.space2,
        vertical: spacing.space1,
      ),
      decoration: BoxDecoration(
        color: colors.surfaceAlt,
        borderRadius: shapes.inlineRadius,
      ),
      child: Text(
        text,
        style: roles.numeric.copyWith(color: colors.textSecondary),
      ),
    );
  }
}

/// The settings section: the cold wallet, the read-only bank-card notice, the
/// support mailbox and the build/update row, in one card.
///
/// A guest only has the two rows that do not belong to an account: support and
/// the app version.
class _SettingsSection extends StatelessWidget {
  const _SettingsSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppStrings copy = strings(state.language);
    final AppSpacing spacing = appSpacingOf(context);
    final bool guest = state.session == null;
    final String? address = state.wallet?.walletAddress;
    final bool bound = (address ?? '').isNotEmpty;

    void openWallet() => _showWalletSheet(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        const AppSectionHeader(title: AppUiCopy.accountSettingsSection),
        SizedBox(height: spacing.space2),
        AppSectionCard(
          children: <Widget>[
            if (!guest)
              // The whole row opens the wallet sheet, like every other entry on
              // this page; the trailing button keeps the "Bind/Replace" wording
              // (and the touch target) it always had.
              AppListRow(
                title: copy.accountWalletTitle,
                subtitle: bound ? address : copy.accountWalletNetworkHint,
                // The address is a 42-character machine value: one line with an
                // ellipsis keeps the row at its regular height, and the sheet
                // (which pre-fills the full address) is where it is read.
                subtitleMaxLines: 1,
                // The action keeps its own width: "Replace wallet" needs
                // ~110-120 px on a 360 dp device, which the shared split used to
                // clip to "Replace wa…".
                trailingTakesIntrinsicWidth: true,
                leadingIcon: Icons.account_balance_wallet_outlined,
                divider: false,
                onTap: openWallet,
                trailing: TextButton(
                  onPressed: openWallet,
                  child: Text(
                    bound
                        ? copy.accountWalletReplaceAction
                        : copy.accountWalletBindAction,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ),
            if (!guest)
              // Read-only by contract: no card data is ever collected.
              AppListRow(
                title: copy.accountBankCardTitle,
                subtitle: copy.accountBankCardUnsupported,
                leadingIcon: Icons.credit_card_outlined,
                divider: false,
              ),
            // A support mailbox is a *value*, not an action: it sits in the
            // subtitle over the whole row width (a trailing slot squeezed it
            // into two lines and broke the address mid-word on a 360 dp device)
            // and stays selectable for long-press copy.
            _SupportRow(
              title: copy.accountContactSupportTitle,
              email: copy.accountContactSupportEmail,
            ),
            _VersionRow(state: state),
          ],
        ),
      ],
    );
  }
}

/// The support row: the title with the mailbox in the subtitle slot.
///
/// A deliberate variant of [AppListRow]: the mailbox has to sit in the subtitle
/// position - left-aligned over the whole row width, so it never breaks in the
/// middle - while staying a [SelectableText] so it keeps long-press copy and its
/// own semantics value, which the shared row's string subtitle cannot express.
/// The geometry ([AppSpacing.rowMinHeight] minimum, the same padding and the
/// same leading plate) and the §7.2 semantics contract are the component's: the
/// row is its own node, its description is announced once, the mailbox keeps its
/// node, and a plain row exposes no tap.
class _SupportRow extends StatelessWidget {
  const _SupportRow({required this.title, required this.email});

  final String title;
  final String email;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppIconSize icons = appIconSizeOf(context);
    final AppSpacing spacing = appSpacingOf(context);
    final TextTheme textTheme = Theme.of(context).textTheme;

    return Semantics(
      container: true,
      label: title,
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: spacing.rowMinHeight),
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: spacing.space4,
            vertical: spacing.space3,
          ),
          child: Row(
            children: <Widget>[
              ExcludeSemantics(
                child: Container(
                  width: spacing.space10,
                  height: spacing.space10,
                  decoration: BoxDecoration(
                    color: colors.surfaceDisabled,
                    borderRadius: shapes.controlRadius,
                  ),
                  child: Icon(
                    Icons.mail_outline,
                    size: icons.md,
                    color: colors.onDisabled,
                  ),
                ),
              ),
              SizedBox(width: spacing.space4),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: <Widget>[
                    // The row node announces the title; the mailbox below keeps
                    // its own readable, selectable node.
                    ExcludeSemantics(
                      child: Text(
                        title,
                        style: textTheme.titleMedium?.copyWith(
                          color: colors.onDisabled,
                        ),
                      ),
                    ),
                    SizedBox(height: spacing.space1),
                    SelectableText(
                      email,
                      maxLines: 1,
                      style: textTheme.bodySmall?.copyWith(
                        color: colors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// The version row: `versionTitle` + `versionName (versionCode)`, trailing a
/// manual check that is disabled while a manual check is in flight.
class _VersionRow extends StatefulWidget {
  const _VersionRow({required this.state});

  final AppUiState state;

  @override
  State<_VersionRow> createState() => _VersionRowState();
}

class _VersionRowState extends State<_VersionRow> {
  /// `--` until the platform reports the build, exactly as the previous
  /// `FutureBuilder` rendered its empty snapshot.
  String _label = _versionPlaceholder;

  @override
  void initState() {
    super.initState();
    _appVersionLabel.then((String label) {
      if (mounted) {
        setState(() => _label = label);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final UpdateStrings updateCopy = updateStrings(widget.state.language);
    final UpdateCoordinator? coordinator = UpdateDialogScope.maybeOf(context)
        ?.coordinator;

    return AppListRow(
      title: updateCopy.versionTitle,
      subtitle: _label,
      leadingIcon: Icons.system_update_outlined,
      divider: false,
      trailing: coordinator == null
          ? null
          : ValueListenableBuilder<UpdateState>(
              valueListenable: coordinator.state,
              builder:
                  (BuildContext context, UpdateState updateState, Widget? _) {
                    final bool checking =
                        updateState is Checking && updateState.manual;
                    return TextButton(
                      onPressed: checking
                          ? null
                          : () => coordinator.checkForUpdate(manual: true),
                      child: Text(
                        checking
                            ? updateCopy.checking
                            : updateCopy.checkForUpdates,
                      ),
                    );
                  },
            ),
    );
  }
}

Future<void> _showWatchHistorySheet(BuildContext context) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _WatchHistorySheet(),
  );
}

Future<void> _showPasswordSheet(BuildContext context) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _PasswordSheet(),
  );
}

Future<void> _showWalletSheet(BuildContext context) async {
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _WalletSheet(),
  );
}

/// The full watch-history sheet: every record, tap resumes that drama.
class _WatchHistorySheet extends StatelessWidget {
  const _WatchHistorySheet();

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final List<WatchRecord> records = state.watchHistory;

    return SafeArea(
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.7,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 18, 8, 12),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      accountDetailSheetTitle(
                        AccountDetailSheet.watchHistory,
                        state.language,
                      ),
                      style: roles.displayLarge,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    tooltip: copy.playerClose,
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
            ),
            if (records.isEmpty)
              Padding(
                padding: const EdgeInsets.all(18),
                child: Text(
                  accountEmptyDetailMessage(
                    AccountDetailSheet.watchHistory,
                    state.language,
                  ),
                  style: roles.body.copyWith(color: colors.textSecondary),
                ),
              )
            else
              Expanded(
                child: ListView.builder(
                  padding: const EdgeInsets.fromLTRB(18, 0, 18, 24),
                  itemCount: records.length,
                  itemBuilder: (BuildContext context, int index) {
                    final WatchRecord record = records[index];
                    return InkWell(
                      key: ValueKey<String>(
                        '${record.bookId}-${record.episode}',
                      ),
                      onTap: () {
                        Navigator.of(context).pop();
                        controller.openWatchRecord(record);
                      },
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 10),
                        child: Row(
                          children: <Widget>[
                            Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: colors.accentPrimary.withValues(
                                  alpha: 0.14,
                                ),
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                Icons.play_arrow,
                                color: colors.accentPrimary,
                                size: 20,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: <Widget>[
                                  Text(
                                    record.bookTitle,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: roles.body,
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${episodeNumberLabel(record.episode, state.language)}'
                                    ' · ${record.progressPercent.clamp(0, 100)}%',
                                    style: roles.body.copyWith(
                                      color: colors.textSecondary,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              Icons.chevron_right,
                              color: colors.textSecondary,
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _PasswordSheet extends StatefulWidget {
  const _PasswordSheet();

  @override
  State<_PasswordSheet> createState() => _PasswordSheetState();
}

class _PasswordSheetState extends State<_PasswordSheet> {
  final TextEditingController _oldPassword = TextEditingController();
  final TextEditingController _newPassword = TextEditingController();

  @override
  void initState() {
    super.initState();
    // The submit gate reads live text, so every keystroke re-evaluates it.
    _oldPassword.addListener(_rebuild);
    _newPassword.addListener(_rebuild);
  }

  void _rebuild() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    _oldPassword.dispose();
    _newPassword.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final bool busy = state.accountOperation != null;
    // `!isSubmitting && oldPassword.isNotBlank() && newPassword.length >= 8`.
    final bool enabled =
        !busy &&
        _oldPassword.text.trim().isNotEmpty &&
        _newPassword.text.length >= 8;

    return Padding(
      padding: EdgeInsets.only(
        left: 18,
        right: 18,
        top: 18,
        bottom: MediaQuery.viewInsetsOf(context).bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(copy.accountChangePasswordTitle, style: roles.displayLarge),
          const SizedBox(height: 16),
          AppTextField(
            controller: _oldPassword,
            label: copy.authPasswordLabel,
            obscureText: true,
          ),
          const SizedBox(height: 12),
          AppTextField(
            controller: _newPassword,
            label: copy.accountPasswordNewLabel,
            // Change-password needs the length rule, not the registration
            // instructions (username and captcha do not apply here).
            helperText: AppUiCopy.passwordMinimumHint,
            obscureText: true,
          ),
          const SizedBox(height: 18),
          // Parity: this sheet does NOT auto-close on success; the failure keeps the input.
          AppButton(
            label: copy.accountChangePasswordTitle,
            isExpanded: true,
            isLoading: busy,
            onPressed: enabled
                ? () => context.read<AppStateController>().changePassword(
                    _oldPassword.text,
                    _newPassword.text,
                  )
                : null,
          ),
        ],
      ),
    );
  }
}

class _WalletSheet extends StatefulWidget {
  const _WalletSheet();

  @override
  State<_WalletSheet> createState() => _WalletSheetState();
}

class _WalletSheetState extends State<_WalletSheet> {
  late final AppStateController _controller;
  final TextEditingController _address = TextEditingController();
  final TextEditingController _password = TextEditingController();
  late int _lastMutationVersion;
  bool _confirmOpen = false;

  @override
  void initState() {
    super.initState();
    _controller = context.read<AppStateController>();
    // Kotlin `remember(wallet)`: replacing pre-fills the bound address.
    final String? bound = _controller.state.wallet?.walletAddress;
    _address.text = bound ?? '';
    _lastMutationVersion = _controller.state.walletMutationVersion;
    // The submit gates read live text, so every keystroke re-evaluates them.
    _address.addListener(_rebuild);
    _password.addListener(_rebuild);
    // `walletMutationVersion` advances when a bind/replace/unbind lands; the
    // sheet closes itself on success exactly then, and stays open with the
    // user's input on failure.
    _controller.addListener(_onControllerChanged);
  }

  void _rebuild() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    _address.dispose();
    _password.dispose();
    super.dispose();
  }

  /// `walletSheetShouldDismiss`: any version advance closes the sheet — bind,
  /// replace **and** unbind alike.
  void _onControllerChanged() {
    final AppUiState state = _controller.state;
    if (state.walletMutationVersion == _lastMutationVersion) {
      return;
    }
    _lastMutationVersion = state.walletMutationVersion;
    if (mounted && !_confirmOpen) {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final bool bound = (state.wallet?.walletAddress ?? '').isNotEmpty;
    final bool busy = state.accountOperation != null;

    return Padding(
      padding: EdgeInsets.only(
        left: 18,
        right: 18,
        top: 18,
        bottom: MediaQuery.viewInsetsOf(context).bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            bound
                ? copy.accountWalletReplaceAction
                : copy.accountWalletBindAction,
            style: roles.displayLarge,
          ),
          const SizedBox(height: 4),
          Text(
            copy.accountWalletNetworkHint,
            style: roles.body.copyWith(color: colors.textSecondary),
          ),
          const SizedBox(height: 16),
          AppTextField(
            controller: _address,
            label: copy.accountWalletAddressLabel,
            hintText: '0x…',
          ),
          const SizedBox(height: 12),
          AppTextField(
            controller: _password,
            label: copy.authPasswordLabel,
            obscureText: true,
          ),
          const SizedBox(height: 18),
          // Parity: binding and replacing go straight through; only unbinding confirms.
          AppButton(
            label: bound
                ? copy.accountWalletReplaceAction
                : copy.accountWalletBindAction,
            isExpanded: true,
            isLoading: busy,
            onPressed:
                !busy &&
                    _address.text.trim().isNotEmpty &&
                    _password.text.isNotEmpty
                ? () => _controller.bindWallet(
                    'ERC20',
                    _address.text.trim(),
                    _password.text,
                  )
                : null,
          ),
          if (bound) ...<Widget>[
            const SizedBox(height: 12),
            AppButton(
              label: copy.accountWalletUnbindAction,
              variant: AppButtonVariant.secondary,
              isExpanded: true,
              onPressed: !busy && _password.text.isNotEmpty
                  ? () => _confirmUnbind(context)
                  : null,
            ),
          ],
        ],
      ),
    );
  }

  /// The unbind confirmation: title/body/copy from the shared formatter, both
  /// buttons frozen while a submission runs, and no dismissal path either.
  Future<void> _confirmUnbind(BuildContext context) async {
    _confirmOpen = true;
    bool? confirmed;
    try {
      confirmed = await showDialog<bool>(
        context: context,
        builder: (BuildContext dialogContext) => const _ConfirmDialog(),
      );
    } finally {
      // Lift the gate before the unbind runs so a successful mutation (which
      // advances `walletMutationVersion`) can close the sheet.
      _confirmOpen = false;
    }
    if (confirmed == true) {
      await _controller.unbindWallet(_password.text);
    }
  }
}

/// The wallet-unbind confirmation dialog.
class _ConfirmDialog extends StatelessWidget {
  const _ConfirmDialog();

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final bool busy = state.accountOperation != null;

    return PopScope(
      // The Kotlin dialog refuses dismissal while a submission runs.
      canPop: !busy,
      child: AlertDialog(
        title: Text(accountConfirmationTitle(state.language)),
        content: Text(
          accountConfirmationBody('unbind the wallet', state.language),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: busy ? null : () => Navigator.of(context).pop(false),
            child: Text(accountConfirmationCancel(state.language)),
          ),
          TextButton(
            onPressed: busy ? null : () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(foregroundColor: colors.accentDanger),
            child: Text(accountConfirmationConfirm(state.language)),
          ),
        ],
      ),
    );
  }
}
