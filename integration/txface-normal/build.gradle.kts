// Expose the existing vendor binary as a dependency artifact, not an embedded AAR.
configurations.create("default")
artifacts.add("default", rootProject.file("app/libs/WbCloudNormal-v5.1.10-4e3e198.aar"))
