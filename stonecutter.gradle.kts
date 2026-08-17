plugins {
    id("dev.kikugie.stonecutter")
}

// The variant the source tree is currently switched to. Change it with
//   gradlew "Set active project to 1.21.1-fabric"
// or by editing versions/current — never by hand-editing the //? comments.
stonecutter active file("versions/current")

val modVersion: String by project
version = modVersion

// `dist/` stays the canonical handoff path it has always been; each variant's final jar lands there
// with the variant in its name, so 1.21.1 and 1.21.10 jars never overwrite each other.
tasks.register("buildAllVariants") {
    group = "steampad"
    description = "Builds every variant in versions/versions.json and collects the jars into dist/."
    dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}
