import Android
import CJNI
import Foundation
import SoulverCore

// Kotlin calls in from a single background thread (Soulver.kt), so no locking.
nonisolated(unsafe) private var calculator: Calculator?

private func swiftString(_ env: UnsafeMutablePointer<JNIEnv?>, _ s: jstring?) -> String {
    guard let s, let fn = env.pointee?.pointee else { return "" }
    let count = Int(fn.GetStringLength!(env, s))
    guard let chars = fn.GetStringChars!(env, s, nil) else { return "" }
    defer { fn.ReleaseStringChars!(env, s, chars) }
    return String(decoding: UnsafeBufferPointer(start: chars, count: count), as: UTF16.self)
}

private func javaString(_ env: UnsafeMutablePointer<JNIEnv?>, _ s: String) -> jstring? {
    let utf16 = Array(s.utf16)
    return utf16.withUnsafeBufferPointer { env.pointee!.pointee.NewString!(env, $0.baseAddress, jsize($0.count)) }
}

// SoulverCore also resolves its bundle through SwiftPM's Bundle.module, which looks next to the main executable
// (/system/bin/app_process on Android). Point CoreFoundation's cached process path into the directory holding
// the bundle so that lookup succeeds. Must run before anything touches Bundle.main.
private func overrideProcessPath(inside dir: String) -> Bool {
    let handle = dlopen("libFoundation.so", RTLD_NOW)
    guard let sym = dlsym(handle, "_CFGetProcessPath") ?? dlsym(nil, "_CFGetProcessPath") else { return false }
    let getProcessPath = unsafeBitCast(sym, to: (@convention(c) () -> UnsafeMutablePointer<UnsafePointer<CChar>?>).self)
    getProcessPath().pointee = UnsafePointer(strdup(dir + "/onlysearch"))
    return Bundle.main.bundleURL.path == dir
}

@_cdecl("Java_app_onlysearch_Soulver_nativeInit")
public func nativeInit(_ env: UnsafeMutablePointer<JNIEnv?>, _ cls: jclass?, _ path: jstring?, _ locale: jstring?) -> jboolean {
    let url = URL(fileURLWithPath: swiftString(env, path))
    guard overrideProcessPath(inside: url.deletingLastPathComponent().path) else { return 0 }
    guard let bundle = ResourceBundle(url: url) else { return 0 }
    let customization = EngineCustomization(resourcesBundle: bundle, locale: Locale(identifier: swiftString(env, locale)))
    calculator = Calculator(customization: customization)
    return 1
}

@_cdecl("Java_app_onlysearch_Soulver_nativeEval")
public func nativeEval(_ env: UnsafeMutablePointer<JNIEnv?>, _ cls: jclass?, _ expression: jstring?) -> jstring? {
    guard let calculator else { return nil }
    let result = calculator.calculate(swiftString(env, expression))
    return javaString(env, result.isEmptyResult || result.isFailedResult || result.isPendingResult ? "" : result.stringValue)
}
