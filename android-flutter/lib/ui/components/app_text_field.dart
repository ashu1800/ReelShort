/// The design system's labelled text field, with error support and explicit
/// keyboard/IME contract handling.
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/formatting/app_ui_copy.dart';
import '../../theme/app_colors.dart';

/// Minimum hit target of the built-in reveal/conceal toggle.
const double appTextFieldToggleTargetExtent = 44;

/// Size of the toggle's glyph inside its 44 px target.
const double _toggleIconSize = 18;

/// Shape of the toggle's ink response.
const ShapeBorder _toggleShape = StadiumBorder();

/// A labelled text input.
///
/// The field owns its own [FocusNode] and [TextEditingController] when the
/// caller does not supply them, and disposes exactly the ones it created.
///
/// Both objects are captured when the field's state is created: the field is
/// built for a form that hands the same controller and node to the same field
/// for its whole life, and swapping them under a live field is not supported.
///
/// IME contract:
///  * [textInputAction] is what the soft keyboard shows; when it is
///    [TextInputAction.done] or [TextInputAction.go] the field also closes the
///    keyboard on submit instead of leaving a dangling IME connection;
///  * [onSubmitted] fires for every action, so a caller can advance focus by
///    passing [TextInputAction.next] on every field but the last;
///  * `onEditingComplete` is never overridden, so Flutter's default
///    `next`/`previous` focus traversal keeps working;
///  * [obscureText] forces a single line so a password cannot wrap.
///
/// Mask contract:
///  * an obscured field shows a built-in reveal toggle ([showObscureToggle])
///    whose label and tooltip come from [AppUiCopy];
///  * the toggle is a 44x44 target with a compact glyph, it never takes focus
///    from the field, and it never touches the controller - masking is a
///    render-time concern, so the text and the caret survive the swap;
///  * tapping outside the field closes the keyboard on every platform
///    ([dismissKeyboardOnTapOutside]); the framework's own default only does
///    that for mouse input, which left touch users with a stuck IME.
class AppTextField extends StatefulWidget {
  /// Creates a labelled field.
  const AppTextField({
    required this.label,
    this.controller,
    this.initialValue,
    this.errorText,
    this.helperText,
    this.hintText,
    this.keyboardType,
    this.textInputAction,
    this.textCapitalization = TextCapitalization.none,
    this.obscureText = false,
    this.showObscureToggle = true,
    this.dismissKeyboardOnTapOutside = true,
    this.enabled = true,
    this.readOnly = false,
    this.autofocus = false,
    this.maxLines = 1,
    this.maxLength,
    this.prefixIcon,
    this.suffix,
    this.inputFormatters,
    this.onChanged,
    this.onSubmitted,
    this.focusNode,
    this.selectAllOnFocus = false,
    super.key,
  });

  /// Floating label and the field's accessible name.
  final String label;

  /// Controller; when null the field creates and owns one.
  final TextEditingController? controller;

  /// Initial text used when no [controller] is supplied.
  final String? initialValue;

  /// Validation message. A non-null value puts the field in its error state and
  /// is announced to screen readers.
  final String? errorText;

  /// Supporting copy below the field.
  final String? helperText;

  /// Placeholder shown while the field is empty.
  final String? hintText;

  /// Keyboard layout to request.
  final TextInputType? keyboardType;

  /// Action key shown on the soft keyboard.
  final TextInputAction? textInputAction;

  /// Automatic capitalisation behaviour.
  final TextCapitalization textCapitalization;

  /// Whether the value is masked.
  final bool obscureText;

  /// Whether an obscured field shows its built-in reveal toggle. Ignored when
  /// [obscureText] is false.
  final bool showObscureToggle;

  /// Whether tapping outside the field closes the keyboard.
  ///
  /// The framework default only unfocuses for mouse input, so touch users on
  /// Android kept a stuck IME; this field closes it for every pointer kind on
  /// every platform. Set to false to keep the field focused when the user taps
  /// elsewhere, for example while a picker beside the field is being used.
  final bool dismissKeyboardOnTapOutside;

  /// Whether the field accepts input.
  final bool enabled;

  /// Whether the field is focusable but not editable, for copying a value out.
  final bool readOnly;

  /// Whether the field takes focus when the route opens.
  final bool autofocus;

  /// Number of visible lines; ignored while [obscureText] is true.
  final int maxLines;

  /// Optional hard character limit; also renders the counter.
  final int? maxLength;

  /// Optional leading glyph.
  final IconData? prefixIcon;

  /// Optional trailing widget, for example a clear or reveal button.
  final Widget? suffix;

  /// Input formatters applied before the value reaches the controller.
  final List<TextInputFormatter>? inputFormatters;

  /// Called on every change.
  final ValueChanged<String>? onChanged;

  /// Called when the action key is pressed.
  final ValueChanged<String>? onSubmitted;

  /// Optional external focus node.
  final FocusNode? focusNode;

  /// Whether focusing the field selects its whole value, for quickly
  /// overwriting a prefilled identifier such as a wallet address.
  final bool selectAllOnFocus;

  @override
  State<AppTextField> createState() => _AppTextFieldState();
}

class _AppTextFieldState extends State<AppTextField> {
  /// Controller in use. Resolved once, from the widget that created this state:
  /// a `late final` field would be initialised at its first *read*, which for
  /// the ownership flags below is `dispose()` - by then a rebuild without a
  /// caller-supplied controller would make the state believe it owns a
  /// controller it must not dispose.
  late final TextEditingController _controller;

  late final FocusNode _focusNode;

  /// Whether this state created the controller and must dispose it.
  late final bool _ownsController;

  /// Whether this state created the focus node and must dispose it.
  late final bool _ownsFocusNode;

  /// Whether this state registered [_handleFocusChange] on [_focusNode].
  ///
  /// Captured at the moment the listener is added, so `dispose` removes exactly
  /// what `initState` added. Deciding from the current
  /// [AppTextField.selectAllOnFocus] instead would leak the listener as soon as
  /// a rebuild turned the flag off first - and the leak fires against this
  /// state's already disposed controller the next time the caller's node is
  /// focused.
  late final bool _selectAllOnFocus;

  /// Whether the value is currently masked. Starts from [AppTextField.obscureText]
  /// and follows it when the caller changes the property, but is otherwise owned
  /// by the toggle: rebuilding the field must not reveal a password the user hid.
  late bool _obscured = widget.obscureText;

  @override
  void initState() {
    super.initState();
    _controller =
        widget.controller ?? TextEditingController(text: widget.initialValue);
    _focusNode = widget.focusNode ?? FocusNode();
    _ownsController = widget.controller == null;
    _ownsFocusNode = widget.focusNode == null;
    _selectAllOnFocus = widget.selectAllOnFocus;
    if (_selectAllOnFocus) {
      _focusNode.addListener(_handleFocusChange);
    }
  }

  @override
  void didUpdateWidget(AppTextField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.obscureText != widget.obscureText) {
      _obscured = widget.obscureText;
    }
  }

  @override
  void dispose() {
    if (_selectAllOnFocus) {
      _focusNode.removeListener(_handleFocusChange);
    }
    if (_ownsFocusNode) {
      _focusNode.dispose();
    }
    if (_ownsController) {
      _controller.dispose();
    }
    super.dispose();
  }

  void _handleFocusChange() {
    // The listener is removed on dispose, but a notification queued for the
    // current frame can still arrive afterwards: it must never write through a
    // detached state or a disposed controller.
    if (!mounted) {
      return;
    }
    if (_focusNode.hasFocus && _controller.text.isNotEmpty) {
      _controller.selection = TextSelection(
        baseOffset: 0,
        extentOffset: _controller.text.length,
      );
    }
  }

  void _handleSubmitted(String value) {
    final TextInputAction? action = widget.textInputAction;
    if (action == TextInputAction.done || action == TextInputAction.go) {
      _focusNode.unfocus();
    }
    widget.onSubmitted?.call(value);
  }

  /// Flips the mask without touching the text, the selection or the focus: the
  /// toggle is a view control, not an edit.
  void _toggleObscured() {
    setState(() => _obscured = !_obscured);
  }

  /// Closes the keyboard when the pointer lands outside the field.
  ///
  /// Deliberately pointer-kind agnostic: the framework's default action keeps
  /// touch input on mobile focused, so a phone user tapping a label beside the
  /// password field would keep the IME up.
  void _dismissKeyboard(PointerDownEvent event) => _focusNode.unfocus();

  /// Overrides the framework's platform-dependent tap-outside default with a
  /// no-op, so [AppTextField.dismissKeyboardOnTapOutside] being false really
  /// means "never dismiss on my behalf" instead of "use the desktop rule".
  void _keepFocus(PointerDownEvent event) {}

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final bool hasError =
        widget.errorText != null && widget.errorText!.trim().isNotEmpty;
    final bool showToggle = widget.obscureText && widget.showObscureToggle;

    return TextField(
      controller: _controller,
      focusNode: _focusNode,
      enabled: widget.enabled,
      readOnly: widget.readOnly,
      autofocus: widget.autofocus,
      obscureText: _obscured,
      keyboardType: widget.keyboardType,
      textInputAction: widget.textInputAction,
      textCapitalization: widget.textCapitalization,
      maxLines: _obscured ? 1 : widget.maxLines,
      maxLength: widget.maxLength,
      inputFormatters: widget.inputFormatters,
      onChanged: widget.onChanged,
      onSubmitted: _handleSubmitted,
      onTapOutside: widget.dismissKeyboardOnTapOutside
          ? _dismissKeyboard
          : _keepFocus,
      style: textTheme.bodyLarge,
      cursorColor: colors.accentPrimary,
      decoration: InputDecoration(
        labelText: widget.label,
        hintText: widget.hintText,
        helperText: widget.helperText,
        errorText: hasError ? widget.errorText : null,
        prefixIcon: widget.prefixIcon == null ? null : Icon(widget.prefixIcon),
        suffixIcon: _buildSuffix(colors, showToggle),
        errorStyle: textTheme.bodySmall?.copyWith(color: colors.accentDanger),
      ),
    );
  }

  /// Trailing slot: the caller's [AppTextField.suffix] plus the built-in
  /// reveal toggle when the value is masked.
  Widget? _buildSuffix(AppColors colors, bool showToggle) {
    final Widget? callerSuffix = widget.suffix;
    if (!showToggle) {
      return callerSuffix;
    }
    final Widget toggle = _ObscureToggle(
      label: _obscured ? AppUiCopy.showPassword : AppUiCopy.hidePassword,
      icon: _obscured ? Icons.visibility_outlined : Icons.visibility_off_outlined,
      onPressed: _toggleObscured,
      color: colors.textSecondary,
    );
    if (callerSuffix == null) {
      return toggle;
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[callerSuffix, toggle],
    );
  }
}

/// The reveal/conceal toggle of an obscured field.
///
/// The glyph stays compact; the target is a full
/// [appTextFieldToggleTargetExtent] square, so the field keeps its height while
/// the control clears the platform minimum. It deliberately does **not** join
/// the focus traversal (`canRequestFocus: false`): a `next` action must still
/// move to the following field, and the toggle would otherwise sit between the
/// two. Screen readers still reach it through the tap action on its own node.
class _ObscureToggle extends StatelessWidget {
  const _ObscureToggle({
    required this.label,
    required this.icon,
    required this.onPressed,
    required this.color,
  });

  /// Label and tooltip, from [AppUiCopy]: one source for both.
  final String label;

  /// Glyph naming the action the tap performs.
  final IconData icon;

  final VoidCallback onPressed;
  final Color color;

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    button: true,
    label: label,
    // The activation action lives on this node: `excludeSemantics` replaces the
    // InkWell's own semantics.
    onTap: onPressed,
    excludeSemantics: true,
    child: SizedBox(
      width: appTextFieldToggleTargetExtent,
      height: appTextFieldToggleTargetExtent,
      child: Tooltip(
        message: label,
        child: InkWell(
          onTap: onPressed,
          canRequestFocus: false,
          customBorder: _toggleShape,
          child: Center(
            child: Icon(icon, size: _toggleIconSize, color: color),
          ),
        ),
      ),
    ),
  );
}

