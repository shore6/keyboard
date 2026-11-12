plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.shore6.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.shore6.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }

    // リリース署名は環境変数で指定する (GitHub Actions では Secrets から渡す)。
    // 未指定ならデバッグ鍵で署名する。
    val storePath = System.getenv("SIGNING_STORE_FILE")
    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    androidResources {
        // 辞書は無圧縮で格納し、APK から直接 mmap する
        noCompress += "bin"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

val buildDictionary = tasks.register<BuildDictionaryTask>("buildDictionary") {
    // google/mozc の固定コミット
    mozcCommit.set("a4541df29db443b49f81cf54665873b68fde9efa")
    cacheDir.set(rootProject.layout.projectDirectory.dir(".dict-cache"))
    outputDir.set(layout.buildDirectory.dir("generated/dictionary"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(buildDictionary, BuildDictionaryTask::outputDir)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

tasks.withType<Test>().configureEach {
    dependsOn(buildDictionary)
    // Robolectric の android-all を取得する Maven リポジトリを変えたいとき用 (-Probolectric.repo=URL)
    providers.gradleProperty("robolectric.repo").orNull?.let {
        systemProperty("robolectric.dependency.repo.url", it)
    }
}
