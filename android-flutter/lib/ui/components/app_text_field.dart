/// The design system's labelled text field, with error support and explicit
/// keyboard/IME contract handling.
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../theme/app_colors.dart';

/// A labelled text input.
///
/// The field owns its own [FocusNode] and [TextEditingController] when the
/// caller does not supply them, and disposes exactly the ones it created.
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
  late final TextEditingController _controller =
      widget.controller ?? TextEditingController(text: widget.initialValue);

  late final FocusNode _focusNode = widget.focusNode ?? FocusNode();

  /// Whether this state created the controller and must dispose it.
  late final bool _ownsController = widget.controller == null;

  /// Whether this state created the focus node and must dispose it.
  late final bool _ownsFocusNode = widget.focusNode == null;

  @override
  void initState() {
    super.initState();
    if (widget.selectAllOnFocus) {
      _focusNode.addListener(_handleFocusChange);
    }
  }

  @override
  void dispose() {
    if (widget.selectAllOnFocus) {
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

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final bool hasError =
        widget.errorText != null && widget.errorText!.trim().isNotEmpty;

    return TextField(
      controller: _controller,
      focusNode: _focusNode,
      enabled: widget.enabled,
      readOnly: widget.readOnly,
      autofocus: widget.autofocus,
      obscureText: widget.obscureText,
      keyboardType: widget.keyboardType,
      textInputAction: widget.textInputAction,
      textCapitalization: widget.textCapitalization,
      maxLines: widget.obscureText ? 1 : widget.maxLines,
      maxLength: widget.maxLength,
      inputFormatters: widget.inputFormatters,
      onChanged: widget.onChanged,
      onSubmitted: _handleSubmitted,
      style: textTheme.bodyLarge,
      cursorColor: colors.accentPrimary,
      decoration: InputDecoration(
        labelText: widget.label,
        hintText: widget.hintText,
        helperText: widget.helperText,
        errorText: hasError ? widget.errorText : null,
        prefixIcon: widget.prefixIcon == null ? null : Icon(widget.prefixIcon),
        suffixIcon: widget.suffix,
        errorStyle: textTheme.bodySmall?.copyWith(color: colors.accentDanger),
      ),
    );
  }
}
