# 🧪 Test Çalıştırma Rehberi

## 1. PowerShell'i Aç

`Win + R` → `powershell` → Enter

---

## 2. Proje Klasörüne Git

```powershell
cd "C:\Users\kubil\OneDrive\Desktop\merve\core-android-main\core-android-main"
```

---

## 3. Java Sürümünü Ayarla (Her Oturumda Bir Kez)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

> ⚠️ Bu satırı atlarsan Gradle hata verir. PowerShell'i her kapatıp açtığında tekrar yazman gerekir.

---

## 4. Testleri Çalıştır

### Hızlı çalıştır (önbellek kullanır — kod değişmediyse anında biter):
```powershell
.\gradlew testProdDebugUnitTest
```

### Sıfırdan çalıştır (tüm testleri tekrar çalıştırır, sonuçları ekrana yazar):
```powershell
.\gradlew testProdDebugUnitTest --rerun-tasks
```

---

## 5. Sonucu Oku

| Çıktı | Anlamı |
|-------|--------|
| `BUILD SUCCESSFUL` | ✅ Tüm testler geçti |
| `BUILD FAILED` | ❌ En az bir test hata verdi |
| `43 up-to-date` | Önbellekten döndü, kod değişmemişti |
| `X tests completed, 0 failed` | Kaç test çalıştı ve hepsi geçti |

---

## 6. HTML Raporu Aç (İsteğe Bağlı)

Testler bittikten sonra bu dosyayı tarayıcında aç:

```
app\build\reports\tests\testProdDebugUnitTest\index.html
```

Tam yol:
```
C:\Users\kubil\OneDrive\Desktop\merve\core-android-main\core-android-main\app\build\reports\tests\testProdDebugUnitTest\index.html
```

Orada her testin adı, geçip geçmediği ve kaç milisaniye sürdüğü yazar.

---

## 7. Tek Bir Test Sınıfını Çalıştır

```powershell
.\gradlew testProdDebugUnitTest --tests "com.shade.app.ui.chat.ChatViewModelTest"
```

```powershell
.\gradlew testProdDebugUnitTest --tests "com.shade.app.data.repository.AuthRepositoryImplTest"
```

```powershell
.\gradlew testProdDebugUnitTest --tests "com.shade.app.util.AppErrorTest"
```

---

## 8. Mevcut Test Dosyaları

| Dosya | Test Sayısı | Ne Test Ediyor |
|-------|------------|----------------|
| `AppErrorTest.kt` | ~8 | Hata mesajları doğru mu |
| `TokenRefreshAuthenticatorTest.kt` | ~6 | Token yenileme akışı |
| `AuthRepositoryImplTest.kt` | ~10 | Giriş / kayıt / oturum kaydetme |
| `ChatViewModelTest.kt` | ~18 | Chat ekranı mantığı |

---

## 9. Sık Karşılaşılan Hatalar

### ❌ `SDK location not found`
`local.properties` dosyasını kontrol et. Android SDK yolu yazılı olmalı.

### ❌ `Suspend function should be called only from a coroutine`
Kod değişikliği yapıldıysa kontrol et — bir `suspend fun` yanlış yerde çağrılıyor olabilir.

### ❌ `Method X in android.util.Log not mocked`
`app/build.gradle.kts` içinde şu blok olmalı:
```kotlin
testOptions {
    unitTests {
        isReturnDefaultValues = true
    }
}
```

### ❌ `No matching client found for package name 'com.shade.app.dev'`
`testDevDebugUnitTest` yerine `testProdDebugUnitTest` kullan.

---

## Özet — Her Seferinde Yazacakların

```powershell
cd "C:\Users\kubil\OneDrive\Desktop\merve\core-android-main\core-android-main"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew testProdDebugUnitTest --rerun-tasks
```
