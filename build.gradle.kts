plugins {
    id("dev.kikugie.stonecutter")
    id("dev.isxander.modstitch.base")
    `java-library`
}

/** Non-blank project property, or null. Lets a variant opt out simply by omitting the key. */
fun prop(name: String): String? = findProperty(name)?.toString()?.takeIf { it.isNotBlank() }

/** Required project property — fails loudly at configuration time instead of at runtime. */
fun reqProp(name: String): String =
    prop(name) ?: error("Missing property '$name' for variant ${stonecutter.current.project}")

val mcVersion = reqProp("mcVersion")
val steampadVersion = reqProp("modVersion")

modstitch {
    minecraftVersion = mcVersion

    // Mojang mappings come from ModStitch itself; Parchment only layers parameter names on top and
    // no source depends on it, so a variant without a Parchment release just omits the properties.
    parchment {
        prop("parchment.minecraft")?.let { minecraftVersion = it }
        prop("parchment.version")?.let { mappingsVersion = it }
    }

    metadata {
        modId = "steampad"
        modName = "SteamPad"
        modGroup = "dev.steampad"
        modAuthor = "SteamPad Contributors"
        modLicense = "LGPL-3.0-or-later"
        modDescription = "Native Steam Input integration for Minecraft with controller selection, " +
            "radial menu, gyro, haptics and full gamepad UI navigation."
        // Variant in the version string so two jars are never confusable: 0.86.0+1.21.10-fabric.
        modVersion = "$steampadVersion+${stonecutter.current.project}"

        replacementProperties.put("github", "steampad/steampad-mod")
        prop("meta.mcDep")?.let { replacementProperties.put("mc", it) }
        prop("meta.fapiDep")?.let { replacementProperties.put("fapi", it) }
        prop("packFormat")?.let { replacementProperties.put("pack_format", it) }
    }

    mixin {
        // Injected into the generated manifest, so the template carries no "mixins" array.
        addMixinsToModManifest = true
        configs.register("steampad")
        // Two emote mixins exist ONLY on the render-state era (>=1.21.2): the duck-field carrier and
        // the extractRenderState tagger. Older versions render GUI entities immediately instead of
        // queueing them, so the preview tag is read straight from EmotePreviewTagger and neither class
        // has anything to do — their bodies compile to nothing there (Stonecutter-gated), so
        // registering this config would point Mixin at absent classes. Registered per version rather
        // than conditionalising the JSON, which would stop being valid JSON.
        // The pose and body-transform mixins are NOT here: those have real implementations on both
        // eras and live in the main config.
        if (stonecutter.current.parsed >= "1.21.2") {
            configs.register("steampad-renderstate")
        }
    }

    loom {
        fabricLoaderVersion = reqProp("deps.fabricLoader")
        configureLoom {
            runConfigs.all { ideConfigGenerated(true) }
            // Loom 1.14 turns the mixin annotation processor off by default and remaps mixin
            // references in remapJar instead. Set explicitly so the choice is visible, not implied.
            mixin.useLegacyMixinAp = false
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases")
    maven("https://maven.parchmentmc.org")
}

dependencies {
    /** Bundle a dependency into the mod jar (Loom's include / NeoForge's jarJar, per variant). */
    fun Dependency?.jij() = this?.also(::modstitchJiJ)

    modstitchModImplementation("net.fabricmc.fabric-api:fabric-api:${reqProp("deps.fabricApi")}")

    // NOTE: Cloth Config used to be declared here as modCompileOnly. It was removed during the
    // multi-version port because nothing in src/ imports it — the config screens are hand-written in
    // dev.steampad.screen. The only `me.shedaniel` mentions left are comments and a reflective
    // Class.forName for REI (a different mod, no compile dependency). Dropping it removes one
    // per-variant version to track for every future MC version.

    // Mod Menu — optional integration, only loaded when the modmenu mod is present.
    modstitchModCompileOnly("com.terraformersmc:modmenu:${reqProp("deps.modMenu")}")

    // JNA — for the optional SDL3 gamepad backend. Minecraft already bundles JNA 5.15.0 at runtime
    // (oshi uses it), so compile-only here: no bundling, no duplicate. The code degrades gracefully
    // when libSDL3 is absent.
    compileOnly("net.java.dev.jna:jna:5.15.0")

    // Steamworks4j — Steam Input API binding, bundled into the mod jar.
    implementation("com.code-disaster.steamworks4j:steamworks4j:${reqProp("deps.steamworks4j")}") {
        isTransitive = false
    }.jij()

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    // Gradle 9 no longer puts the JUnit Platform launcher on the test runtime classpath implicitly.
    // Version tracks JUnit 5.10.2 (Jupiter 5.10.x ↔ Platform 1.10.x).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

// Constants available to Stonecutter `//? if` comments in the sources. Version comparisons
// (`//? if >=1.21.2`) work off the variant's MC version automatically and need nothing here.
stonecutter {
    constants {
        put("fabric", modstitch.isLoom)
        put("neoforge", modstitch.isModDevGradleRegular)
    }
}

// javac stops reporting after 100 errors by default, which hides most of the work when bringing up a
// new MC version. During a multi-version port you want the whole list in one pass.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    // Mixin classes need a live Fabric/MC environment, so they stay out of the plain unit run.
    exclude("**/mixin/**")

    // Each variant is its own Gradle project under versions/, so the default working directory is
    // versions/<variant>/. SteamInputManifestTest reads src/main/resources and src/main/java through
    // repo-relative paths (it cross-checks the Steam Input manifest against the action registry), and
    // src/ is shared by every variant — so point the working directory back at the repo root instead
    // of rewriting the tests. Keeps the invariant they were written against, for all future variants.
    workingDir = rootProject.projectDir
}

// Keep dist/ as the handoff path it has always been. The variant is already in the jar name via
// modVersion, so variants never overwrite each other.
val exportToDist by tasks.registering(Copy::class) {
    group = "steampad"
    from(modstitch.finalJarTask.flatMap { it.archiveFile })
    into(rootProject.layout.projectDirectory.dir("dist"))
}
tasks.named("build") { finalizedBy(exportToDist) }
