# The mic helper is started by name with app_process from Lumbina's APK (see MicInjection).
-keep class dev.lumbina.tv.platform.accessibility.MicInjector {
    public static void main(java.lang.String[]);
}
