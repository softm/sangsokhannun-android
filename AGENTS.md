# 상속한눈 Android

- 앱 전체 템플릿을 복제하지 않는다. 반복 광고·업데이트 기능은 독립 공통 라이브러리를 사용한다.
- softm/hannun-admob-android 및 softm/hannun-in-app-update-android의 공개 API를 사용한다. 공통 Java 소스를 앱에 복사하지 않는다.
- 공통 AdMob: 하단 컴팩트 네이티브 1개와 같은 지면 적응형 배너 폴백, 시간 기반 전면 1개, 종료창 300×250 MREC 1개. 광고 로드 실패·미동의·오프라인에도 앱을 종료할 수 있어야 한다.
- Debug는 Google 공식 테스트 ID만 사용한다. Release 앱 ID 및 4개 광고 단위는 이 앱용으로 발급한다. 다른 앱 운영 ID 및 서명키를 커밋하지 않는다.
- UMP 동의/개인정보 설정, 생명주기 연결, 빈 광고 지면 제거를 유지한다.
- WebView UA AdMobNative/1을 유지하고 AdFit 요청·빈 컨테이너를 차단한다. AdFit을 공통 필수 지면으로 추가하지 않는다.
- WebView 내부 탐색은 HTTPS의 softm.github.io/sangsokhannun/ 경로만 허용한다. 외부 HTTPS는 사용자 동작 시 브라우저로 연다.
- JS 메시지는 정확한 origin·main frame·현재 경로·action whitelist·크기 제한을 검사한다. addJavascriptInterface로 임의 기능을 노출하지 않는다.
- 가족·재산 데이터 및 비밀번호를 로그·분석·광고 SDK·서버로 전송하지 않는다. 백업 브리지는 암호화 envelope만 저장한다.
- compileSdk/targetSdk 36, minSdk 24, Java 17. 변경 시 debug 테스트 및 APK 빌드를 검증한다.
- GitHub Actions artifact 보관은 1일이며 불필요한 캐시 적재를 피한다.
