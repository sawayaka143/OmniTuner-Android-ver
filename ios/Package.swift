// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "OmniTuner",
    platforms: [
        .iOS("27.0"),
        .macOS(.v14),
    ],
    products: [
        // An xtool project contains one library product representing the main app.
        .library(
            name: "OmniTuner",
            targets: ["OmniTunerApp"]
        ),
    ],
    dependencies: [
        .package(path: "Packages/OmniTunerCore"),
    ],
    targets: [
        .target(
            name: "OmniTunerApp",
            dependencies: [
                .product(name: "OmniTunerCore", package: "OmniTunerCore"),
            ],
            resources: [
                .process("Resources/Audio"),
            ]
        ),
    ]
)
