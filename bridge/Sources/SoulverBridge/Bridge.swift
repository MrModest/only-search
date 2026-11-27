import Android
import CJNI
import Foundation
import SoulverCore

// Kotlin calls in from a single background thread (Soulver.kt), so no locking.
nonisolated(unsafe) private var calculator: Calculator?
nonisolated(unsafe) private var base: EngineCustomization?
nonisolated(unsafe) private var currencyDigits: [String: Int] = [:]
nonisolated(unsafe) private var unitSystem = UnitMeasurementSystem.metric

/** Live rates fetched by the app (units of each currency per 1 USD). Unknown codes return nil. */
private final class Rates: CurrencyRateProvider {
    var perUSD: [String: Decimal] = [:]
    /** The last few requests the engine made, for nativeRateLog (diagnostics). */
    var log: [String] = []
    func rateFor(request: CurrencyRateRequest) -> Decimal? {
        let from = perUSD[request.fromCurrency.uppercased()], to = perUSD[request.toCurrency.uppercased()]
        let rate: Decimal? = if let from, let to, from != 0 { to / from } else { nil }
        log.append("\(request.fromCurrency)->\(request.toCurrency) q=\(request.quantity) unit=\(request.fromCurrencyUnit.rawValue)->\(request.toCurrencyUnit.rawValue) = \(rate.map { "\($0)" } ?? "nil")")
        if log.count > 20 { log.removeFirst() }
        return rate
    }
    func fetchRateInBackgroundFor(request: CurrencyRateRequest) async -> Decimal? { rateFor(request: request) }
}
nonisolated(unsafe) private let rates = Rates()
/** Lowercased crypto names with no rate: the engine would convert them 1:1, so nativeEval answers nothing instead. */
nonisolated(unsafe) private var unpriced: Set<String> = []
// Custom unit names are case-sensitive, so lowercase symbols are listed too ("dot" is left out: it's a word).
private let crypto: [String: [String]] = [
    "BTC": ["BTC", "btc", "bitcoin", "bitcoins", "Bitcoin"], "ETH": ["ETH", "eth", "ether", "ethereum", "Ethereum"],
    "SOL": ["SOL", "sol", "solana"], "XRP": ["XRP", "xrp"], "DOGE": ["DOGE", "doge", "dogecoin"], "ADA": ["ADA", "ada", "cardano"],
    "LTC": ["LTC", "ltc", "litecoin"], "USDT": ["USDT", "usdt", "tether"], "BNB": ["BNB", "bnb"], "DOT": ["DOT", "polkadot"],
]

// The calculator's customization is fixed at creation, so a settings change builds a new one.
private func rebuild() {
    guard var c = base else { return }
    c.unitSystem = unitSystem
    if !rates.perUSD.isEmpty { c.currencyRateProvider = rates }
    // The engine never asks the rate provider about crypto (it converts BTC 1:1), so crypto becomes custom units priced in USD.
    unpriced = []
    for (code, names) in crypto {
        guard let perUSD = rates.perUSD[code], perUSD != 0 else { unpriced.formUnion(names.map { $0.lowercased() }); continue }
        for name in names { c.customUnits.append(CustomUnit(name: name, definition: 1 / perUSD, equivalentUnit: .currencyUSD)) }
    }
    let calc = Calculator(customization: c)
    var formatting = FormattingPreferences()
    formatting.dp = 2
    formatting.notationPreferences = NotationPreferences(notationStyle: .auto, upperNotationThreshold: .trillion)
    calc.formattingPreferences = formatting
    calculator = calc
}

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

@_cdecl("Java_app_onlysearch_search_Soulver_nativeInit")
public func nativeInit(_ env: UnsafeMutablePointer<JNIEnv?>, _ cls: jclass?, _ path: jstring?, _ locale: jstring?, _ digits: jstring?) -> jboolean {
    currencyDigits = Dictionary(uniqueKeysWithValues: swiftString(env, digits).split(separator: ";").compactMap { pair in
        let parts = pair.split(separator: "=", maxSplits: 1)
        guard parts.count == 2, let digits = Int(parts[1]) else { return nil }
        return (String(parts[0]), digits)
    })
    let url = URL(fileURLWithPath: swiftString(env, path))
    guard overrideProcessPath(inside: url.deletingLastPathComponent().path) else { return 0 }
    guard ResourceBundle(url: url) != nil else { return 0 }
    // Load standard currency symbols too; the resource-only initializer misses currencies such as JPY/KWD.
    base = EngineCustomization.standardWith(locale: Locale(identifier: swiftString(env, locale)))
    rebuild()
    return 1
}

@_cdecl("Java_app_onlysearch_search_Soulver_nativeRateLog")
public func nativeRateLog(_ env: UnsafeMutablePointer<JNIEnv?>, _ cls: jclass?) -> jstring? {
    javaString(env, rates.log.joined(separator: "\n"))
}

/** "metric", "imperial" or "customaryUS". */
@_cdecl("Java_app_onlysearch_search_Soulver_nativeSetUnitSystem")
public func nativeSetUnitSystem(_ env: UnsafeMutablePointer<JNIEnv?>, _ cls: jclass?, _ system: jstring?) {
    unitSystem = UnitMeasurementSystem(rawValue: swiftString(env, system)) ?? .metric
    rebuild()
}

/** "USD=1;EUR=0.92;BTC=0.0000094;…": units of each currency per 1 USD. */
@_cdecl("Java_app_onlysearch_search_Soulver_nativeSetRates")
public func nativeSetRates(_ env: UnsafeMutablePointer<JNIEnv?>, _ cls: jclass?, _ data: jstring?) {
    var parsed: [String: Decimal] = [:]
    for pair in swiftString(env, data).split(separator: ";") {
        let kv = pair.split(separator: "=", maxSplits: 1)
        if kv.count == 2, let v = Decimal(string: String(kv[1])) { parsed[String(kv[0]).uppercased()] = v }
    }
    rates.perUSD = parsed
    rebuild()
}

@_cdecl("Java_app_onlysearch_search_Soulver_nativeEval")
public func nativeEval(_ env: UnsafeMutablePointer<JNIEnv?>, _ cls: jclass?, _ expression: jstring?) -> jstring? {
    guard let calculator else { return nil }
    let text = swiftString(env, expression)
    if text.split(whereSeparator: { !$0.isLetter }).contains(where: { unpriced.contains($0.lowercased()) }) { return javaString(env, "") }
    var result = calculator.calculate(text)
    if var formatting = calculator.formattingPreferences {
        let normalFormatting = calculator.formattingPreferences
        defer { calculator.formattingPreferences = normalFormatting }
        if let unit = result.evaluationResult.unitExpressionValue?.unit,
           let code = unit.identifier.currencyCode {
            // Crypto custom units inherit USD's identifier, but retain their own symbol.
            let isCrypto = crypto.values.contains { $0.contains(unit.symbol) }
            formatting.dp = isCrypto ? 8 : currencyDigits[code.uppercased(), default: 2]
            formatting.currencyFormattingPreferences.roundResults = false
            formatting.currencyFormattingPreferences.showTrailingZeros = !isCrypto
            calculator.formattingPreferences = formatting
            // Currency rounding also changes evaluationResult, so recalculate at the chosen precision.
            result = calculator.calculate(text)
            if !isCrypto, var value = result.evaluationResult.unitExpressionValue?.value {
                // Android's currency formatter ignores dp below 1 when roundResults is false.
                var rounded = Decimal()
                NSDecimalRound(&rounded, &value, formatting.dp, .plain)
                result = calculator.calculate("\(NSDecimalString(&rounded, calculator.customization.locale)) \(unit.symbol)")
            }
        } else if let value = result.evaluationResult.decimalValue, abs(value) <= 1 {
            formatting.dp = 8
            calculator.formattingPreferences = formatting
            let precise = calculator.calculate(text)
            if let value = precise.evaluationResult.decimalValue, abs(value) < 1 { result = precise }
        }
    }
    return javaString(env, result.isEmptyResult || result.isFailedResult || result.isPendingResult ? "" : result.stringValue)
}
