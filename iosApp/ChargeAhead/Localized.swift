import Shared

/// User-visible text from shared's composeResources, the same source Android uses.
/// Keys stay literal at the call site: shared's jvmTest checks every one of them exists.
func localized(_ key: String, _ args: Any...) -> String {
    Texts.shared.byKey(key: key, args: args)
}
