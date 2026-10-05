# QuietSemantics reaches Compose's list of enabled accessibility services by name.
-keepclassmembernames class androidx.compose.ui.platform.AndroidComposeView {
    androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat composeAccessibilityDelegate;
}
-keepclassmembernames class androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat {
    java.util.List _enabledServices;
}
