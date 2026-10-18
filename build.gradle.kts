import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.jvm")
}

val modId = property("mod.id") as String
val modVersion = property("mod.version") as String
val mcVersion = property("deps.minecraft") as String

version = "$modVersion+$mcVersion"
group = property("mod.group") as String
base.archivesName = modId

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven")
    maven("https://maven.terraformersmc.com/releases")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

loom {
    splitEnvironmentSourceSets()
    mods {
        register(modId) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
    accessWidenerPath = file("src/main/resources/$modId.accesswidener")
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = file("run")
    }
    runs {
        named("client") {
            systemProperties.put("devauth.enabled", "true")
            systemProperties.put("devauth.account", project.property("devauth.account").toString())
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("deps.fabric_kotlin")}")
    "clientCompileOnly"("com.terraformersmc:modmenu:${property("deps.modmenu")}")
    localRuntime("me.djtheredstoner:DevAuth-fabric:${property("deps.devauth")}")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget = JvmTarget.JVM_25 }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

val modJsonProps = mapOf(
    "id" to modId,
    "name" to property("mod.name").toString(),
    "version" to modVersion,
    "minecraft" to property("mod.mc_compat").toString(),
    "loader" to property("deps.fabric_loader").toString(),
    "kotlin" to property("deps.fabric_kotlin").toString(),
)

tasks.processResources {
    val props = modJsonProps
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.withType<Jar> {
    from("LICENSE") { rename { "${it}_$modId" } }
}
