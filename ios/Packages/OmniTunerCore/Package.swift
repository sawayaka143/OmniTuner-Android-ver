// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "OmniTunerCore",
    platforms: [
        .iOS("27.0"),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "OmniTunerCore",
            targets: ["OmniTunerCore"]
        ),
    ],
    targets: [
        .target(
            name: "OmniTunerCore"
        ),
        .testTarget(
            name: "OmniTunerCoreTests",
            dependencies: ["OmniTunerCore"]
        ),
    ]
)
