# 상속한눈 Android

상속한눈 웹앱을 연결하는 Android 앱입니다. 앱별 UI·WebView 연결만 이 저장소에서 유지하고, 광고와 Google Play 인앱 업데이트는 기존 한눈 공통 AAR을 사용합니다.

| 항목 | 값 |
|---|---|
| 앱 이름 | 상속한눈 |
| Release applicationId | com.softm.sangsokhannun |
| Debug applicationId | com.softm.sangsokhannun.debug |
| 버전 | 0.1.0, versionCode 1 |
| 웹 저장소 | https://github.com/softm/sangsokhannun |
| 웹 URL | https://softm.github.io/sangsokhannun/ |
| 개인정보 안내 | https://softm.github.io/sangsokhannun/privacy.html |
| 지원 SDK | min 24 / compile·target 36 |
| 빌드 | JDK 17, Gradle 8.13, AGP 8.10.1, Kotlin 2.2.10 |

## 구현

- HTTPS 웹앱 연결, 연결 실패 화면, 시스템 뒤로가기 및 종료 확인
- 공통 하단 네이티브 광고 → 같은 지면의 적응형 배너 폴백
- 공통 시간 기반 전면광고와 종료 확인창 MREC 300×250
- UMP 동의·광고 개인정보 설정·광고 실패 시 빈 공간 제거
- 입력·암호·복원 흐름 동안 광고 일시 억제, 일반 화면에서 다시 사용
- Google Play 인앱 업데이트 및 앱 메뉴의 수동 업데이트 확인
- 사용자 동작으로 암호화 JSON 백업을 시스템 파일 선택기에 저장
- 파일 선택기를 통한 암호화 백업 가져오기, Android 인쇄·PDF 저장
- WebView UA AdMobNative/1, AdFit 요청 및 컨테이너 차단
- 외부 HTTPS는 브라우저로 열고 임의 scheme·평문 HTTP·파일 탐색 차단

## 공통 라이브러리

| 라이브러리 | 사용 버전 |
|---|---|
| https://github.com/softm/hannun-admob-android | 4416df5dafb110887ba7ad64951d6984a04b6da6 |
| https://github.com/softm/hannun-in-app-update-android | 1.0.0 |

AdMob은 [공통 PR #1](https://github.com/softm/hannun-admob-android/pull/1)의 수정 commit에 고정합니다. 이전 버전은 광고가 준비되지 않았을 때 종료 버튼이 활성화되지 않았습니다. 신규 앱만 수정 commit을 사용하며 기존 앱과 공통 main은 변경하지 않습니다. 수정은 광고의 노출 여부와 무관하게 종료를 허용하고, 광고가 없는 동안 빈 300×250 영역을 만들지 않습니다.

라이브러리 버전은 gradle.properties에서 고정합니다. 업데이트 시 README와 실제 클래스 API를 확인하고 앱 테스트를 수행합니다.

## 실행

Android Studio에서 프로젝트를 열고 Android SDK 36을 설치한 뒤 Debug를 실행합니다.

~~~bash
./gradlew testDebugUnitTest assembleDebug
~~~

APK 경로: app/build/outputs/apk/debug/app-debug.apk

GitHub Actions의 **Android debug build**는 main push, PR, 수동 실행을 지원합니다. 테스트 통과 후 debug APK를 1일간 보관합니다. 이 APK는 Google 공식 테스트 광고를 사용하며 Google Play 인앱 업데이트 설치 흐름은 실행하지 않습니다.

## Release 설정

이 저장소는 다른 앱의 운영 광고 ID 또는 서명키를 포함하지 않습니다. 아래 5개 값을 이 앱용으로 발급하여 사용자 홈의 ~/.gradle/gradle.properties 또는 CI 환경변수에 설정합니다.

~~~properties
ADMOB_APP_ID=상속한눈_앱_ID
ADMOB_NATIVE_ID=하단_네이티브_단위_ID
ADMOB_BANNER_ID=하단_적응형배너_단위_ID
ADMOB_INTERSTITIAL_ID=전면_단위_ID
ADMOB_EXIT_ID=종료창_300x250_배너_단위_ID
~~~

Release 빌드는 값 누락, Google 테스트 ID, 지면 ID 중복을 검사하고 실패시킵니다. Release signing은 별도로 안전하게 구성해야 합니다. 서명되지 않은 결과를 Play 배포본으로 취급하지 않습니다.

## 네이티브 메시지 계약

WebView 메시지 리스너 HannunNative는 정확한 https://softm.github.io origin, main frame, 현재 /sangsokhannun/ 경로를 검사합니다.

| type | 허용 데이터·동작 |
|---|---|
| sensitive | boolean value만 전달. 개인 데이터 없이 광고 억제 상태 변경 |
| print | 현재 상담 요약을 Android 인쇄 화면으로 연결 |
| exportBackup | 최대 2MiB 암호화 JSON content. 시스템 저장 위치를 사용자 선택 |
| importBackup | 시스템 파일 선택 후 최대 2MiB 암호화 백업을 검사. backupSelected·backupCancelled·backupError 중 하나로 응답 |

exportBackup/importBackup은 application:sangsokhannun, version 1, PBKDF2-SHA256 310000회, AES-GCM, 16byte salt, 12byte IV, 암호문 data 필드를 확인합니다. 지정된 8개 필드 외의 값이나 원문 case/assets/debts 객체를 직접 전달하면 거부합니다. 복원 응답은 WebView가 동일 문서·경로를 유지하는 경우에만 반환하며, 문서가 바뀌면 폐기합니다. 지원하지 않는 WebView 버전은 Android System WebView 업데이트가 필요합니다.

기기 자동백업은 비활성화되어 있습니다. 웹 저장 여부와 별개로 파일로 내보낸 암호화 백업은 사용자가 선택한 저장 위치에 남습니다.

## 검증 범위

- URL 정책 unit test는 허용 origin/path, 비슷한 공격자 도메인, sibling Pages 앱, 경로 이동, 임의 scheme, AdFit 차단을 확인합니다.
- 개발 APK 빌드와 테스트 결과는 Actions에서 확인합니다.
- 실제 광고 송출·UMP 지역별 동의·복원 파일 선택·인쇄·회전·백그라운드 복귀는 실제 Android 기기에서 확인해야 합니다.
- Play 업데이트는 Play 내부 테스트에 이전 versionCode와 새 versionCode를 등록해 별도로 검증합니다.
