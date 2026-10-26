// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "SaarSDK",
    platforms: [.macOS(.v13)],
    products: [
        .library(name: "SaarSDK", targets: ["SaarSDK"])
    ],
    targets: [
        .target(name: "SaarSDK"),
        .testTarget(name: "SaarSDKTests", dependencies: ["SaarSDK"])
    ]
)
