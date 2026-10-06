# 앱 identity · signing · 데이터 규칙

## Identity (2026-10 확정)

| | 값 |
| --- | --- |
| applicationId (release) | `app.oneulmundeuk` — **실제 개인 기록은 이 앱에만** 쌓는다 |
| applicationId (debug) | `app.oneulmundeuk.debug` (`applicationIdSuffix = ".debug"`) — 개발 · debug fake AI 테스트용 별도 앱 |
| namespace / Kotlin package | `app.oneulmundeuk` |
| 표시 이름 | 오늘문득 (`@string/app_name`) |

- applicationId는 Play에 올린 뒤 바꿀 수 없다. 이전 placeholder(`app.placeholder.journal`)로 설치한 앱은 별개 앱이며 데이터는 이전하지 않는다 (삭제하면 된다).
- 같은 applicationId + 같은 서명 인증서 + versionCode가 낮아지지 않으면 `adb install -r` / `installDebug` / `installRelease`는 업데이트 설치이며 Room 데이터가 유지된다.
  서명이 다르면 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 삭제 후 재설치 = 데이터 소실 (backup이 꺼져 있어 복구 수단 없음).
- debug는 Mac의 `~/.android/debug.keystore`로 서명된다 (다른 Mac / keystore 재생성 시 debug 앱 데이터는 잃을 수 있음 — 그래서 실사용 기록을 두지 않는다).

## Release signing

- keystore: `~/.keys/oneulmundeuk/oneulmundeuk-release.jks` (repo 밖). 비밀번호 관리자에도 백업한다.
  **이 키를 잃으면 실사용 앱(나중에는 Play 앱)을 영원히 업데이트할 수 없다.**
- 비밀값: `~/.gradle/gradle.properties` (사용자 홈, repo 밖)에서 Gradle property로 읽는다.
  ```properties
  ONEULMUNDEUK_RELEASE_STORE_FILE=~/.keys/oneulmundeuk/oneulmundeuk-release.jks
  ONEULMUNDEUK_RELEASE_STORE_PASSWORD=...
  ONEULMUNDEUK_RELEASE_KEY_ALIAS=oneulmundeuk
  ONEULMUNDEUK_RELEASE_KEY_PASSWORD=...
  ```
  네 값이 모두 있을 때만 `app/build.gradle.kts`가 release signingConfig를 만든다. 없으면 release는 unsigned로 빌드된다 (빌드는 되지만 설치 불가).
- `.gitignore`: `*.jks` · `*.keystore` · `keystore.properties`. 비밀값 · keystore는 절대 커밋하지 않는다.

### 최초 1회 (Mac)

```bash
mkdir -p ~/.keys/oneulmundeuk && chmod 700 ~/.keys/oneulmundeuk
keytool -genkeypair -v \
  -keystore ~/.keys/oneulmundeuk/oneulmundeuk-release.jks \
  -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 36500 \
  -alias oneulmundeuk
# 비밀번호 · 이름(CN 등)은 keytool이 물어볼 때 직접 입력 (PKCS12는 key password = store password)
chmod 600 ~/.keys/oneulmundeuk/oneulmundeuk-release.jks
keytool -list -v -keystore ~/.keys/oneulmundeuk/oneulmundeuk-release.jks -alias oneulmundeuk   # SHA-256 지문 기록
```

그 다음 `~/.gradle/gradle.properties`에 위 네 줄을 추가하고 (`chmod 600 ~/.gradle/gradle.properties`), 확인:

```bash
./gradlew assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk   # 위 SHA-256과 같아야 함
adb uninstall app.placeholder.journal   # 이전 placeholder 앱 (테스트 데이터) 정리
./gradlew installRelease                # 실사용 앱 app.oneulmundeuk
```

## Play 출시 계획

- **Play App Signing에서 이 release key를 app signing key로 사용한다** ("내 앱 서명 키 사용", PEPK로 export해 업로드).
  그래야 지금 직접 설치해 쓰는 `app.oneulmundeuk`과 Play에서 받는 앱의 서명이 같아 데이터를 유지한 채 업데이트된다.
  (Google이 새 app signing key를 만들게 하면 서명이 달라져 sideload → Play 전환 때 데이터를 잃는다.)
- upload key는 Play 출시 준비 단계에서 별도로 만든다 (아직 만들지 않음). upload key는 잃어도 Play 지원으로 재설정 가능.

## Room / 데이터 규칙 (실사용 데이터 보호)

- DB 파일 `journal.db` 이름 유지. schema version / migration은 package와 무관하다 (rename으로 migration 만들지 않음).
- schema를 바꾸면: version +1 · `MIGRATION_n_(n+1)` 작성 · 새 `app/schemas/app.oneulmundeuk.data.db.AppDatabase/<n>.json` 커밋 · MigrationTest 추가.
- `fallbackToDestructiveMigration` 금지. 이미 배포(설치)된 migration · schema JSON은 수정하지 않는다. 컬럼 삭제 / rename보다 추가 위주.
- 실기기에서 이전 버전 위에 업데이트 설치(`installRelease`)해 기록이 유지되는지 확인한 뒤 실사용 앱에 올린다.
- backup / device transfer: `allowBackup=false` + `data_extraction_rules.xml`이 database · sharedpref를 제외 (개인 기록은 기기 밖으로 나가지 않음).
  대신 앱 삭제 · 기기 교체 시 복구 수단이 없다 — 내보내기 / 암호화 백업은 데이터 보호 milestone에서 결정.
