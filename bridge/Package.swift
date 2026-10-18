// swift-tools-version:6.0
import PackageDescription

// soulver/ is filled by build.sh with the per-ABI SoulverCore .so and its module interface.
let soulver = "\(Context.packageDirectory)/soulver"

let package = Package(
    name: "SoulverBridge",
    products: [.library(name: "SoulverBridge", type: .dynamic, targets: ["SoulverBridge"])],
    targets: [
        .target(name: "CJNI"),
        .target(
            name: "SoulverBridge",
            dependencies: ["CJNI"],
            swiftSettings: [.unsafeFlags(["-I", soulver])],
            linkerSettings: [.unsafeFlags(["-L", soulver, "-lSoulverCoreDynamic", "-Xlinker", "-z", "-Xlinker", "max-page-size=16384"])]
        ),
    ]
)
