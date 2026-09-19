// Expose the existing vendor binary as a dependency artifact, not an embedded AAR.
configurations.create("default")
artifacts.add("default", rootProject.file("app/libs/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar"))
