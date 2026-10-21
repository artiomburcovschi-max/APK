# Что добавить в app/build.gradle.kts

Твой файл я не видел, поэтому это шаблон. Если Gradle ругнётся, пришли текст ошибки и сам файл.

## 1. В самое начало файла (над блоком plugins)

```kotlin
import java.io.FileInputStream
import java.util.Properties
```

## 2. Версия одной строкой (в блоке android { defaultConfig { ... } })

Найди там строки versionCode и versionName и замени их на такие:

```kotlin
val vMajor = 0
val vMinor = 2
val vPatch = 0

defaultConfig {
    // ...остальное оставь как есть...
    versionCode = vMajor * 10000 + vMinor * 100 + vPatch   // 0.2.0 -> 200
    versionName = "$vMajor.$vMinor.$vPatch"
}
```

Правило: при каждом релизе меняешь только три числа vMajor/vMinor/vPatch,
versionCode всегда растёт сам (Android не даст поставить версию с меньшим versionCode).
Строки `val vMajor = 0` ... лучше поставить прямо над блоком android { ... }.

## 3. Подпись релиза (внутри android { ... })

```kotlin
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(FileInputStream(f))
}

signingConfigs {
    create("release") {
        if (keystoreProps.isNotEmpty()) {
            storeFile = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias = keystoreProps["keyAlias"] as String
            keyPassword = keystoreProps["keyPassword"] as String
        }
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = false   // пока без обфускации: меньше сюрпризов
    }
}
```

Блок buildTypes { release { ... } } в шаблоне студии уже есть: добавь в него строку
`signingConfig = ...` и не создавай второй.

## 4. Файл keystore.properties (в корне проекта, НЕ в git)

```
storeFile=C:\\keys\\agenda-release.jks
storePassword=ТВОЙ_ПАРОЛЬ
keyAlias=agenda
keyPassword=ТВОЙ_ПАРОЛЬ
```

## 5. В .gitignore добавь

```
keystore.properties
*.jks
*.keystore
```
