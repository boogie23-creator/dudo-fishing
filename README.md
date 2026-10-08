# 🌷 세은이·아인이의 꽃잎마을 — 픽셀 가족생활 게임

포켓몬스터 금 버전 등 고전 휴대용 RPG의 **탑다운 도트 모험 방식을 참고한 독자적인 가족생활 게임**입니다. 게임 캐릭터·지형·음악·대화와 지도는 원작 게임 자료를 복제하지 않았습니다.

**주인공:** 첫째 세은이 · 둘째 아인이 (딸 쌍둥이)

## 🎮 조작

- **휴대폰:** 화면의 방향키로 걸어다니기, `A` 대화/물건 사용, `B` 가방·앨범·조작법
- **PC:** 키보드 화살표 / `WASD` 이동, `Space`, `Z`, `Enter` 상호작용, `B` 가방
- **주요 장소:** 우리집(육아·잠자기), 꽃잎마을(주민), 벚꽃공원(꽃·피크닉), 햇살 상점(집 꾸미기)
- 집에서 **분홍 침대는 세은이, 보라 침대는 아인이**입니다. 침대에 다가가 A로 우유·낮잠·기저귀·놀이를 선택하세요.
- 각 아이의 포만감·에너지·행복·청결 수치가 따로 저장됩니다. 코인, 퀘스트, 이야기, 구매 내역도 브라우저/앱 내부에 자동 저장됩니다.

## ✅ GitHub에 업로드하기 — 이전 ZIP 프로젝트와 같은 구조

1. ZIP 파일의 **압축을 풉니다.** (GitHub에는 압축 ZIP 자체가 아니라 압축을 푼 파일과 폴더를 업로드)
2. 저장소(기존 `desktop-tutorial`도 가능)를 열고 **Code → Add file → Upload files**를 누릅니다.
3. 압축을 푼 `TwinsVillage` **폴더 자체가 아니라 그 안의 내용 전부**를 끌어 놓습니다. `.github`, `app`, `docs`, `gradle`, `web`, `tools`, `build.gradle.kts`, `gradlew`, `index.html` 등을 저장소 최상위에 넣으세요.
4. **Commit changes** 클릭. `index.html`과 `docs/index.html`은 웹게임이며 `app/`은 안드로이드 앱입니다.
5. GitHub **Actions** 탭에서 `Build Seeun Ain Pixel APK`를 확인하세요. 성공하면 해당 실행 기록 → 아래 **Artifacts** → `Seeun-Ain-PixelVillage-APK`를 받으세요.
6. 웹에서도 하려면 **Settings → Pages → Deploy from a branch → main /docs → Save** (또는 root `/(root)`도 index.html이 있어서 지원). Pages URL은 `https://사용자명.github.io/저장소이름/`.

**중요:** 기존 `.github/workflows/build.yml`이 이미 등록된 저장소라면 해당 오래된 워크플로는 삭제하여 두 빌드가 중복 실행되지 않게 하세요. 새로운 프로젝트에는 `.github/workflows/main.yml` 하나만 들어 있습니다. GitHub 웹 인터페이스로 폴더 내 파일 교체 시 같은 상대경로에 업로드해야 변경됩니다.

## ⚙️ 파일 구성

```
TwinsVillage/
├── index.html              ← GitHub Pages 루트 배포용
├── docs/index.html         ← GitHub Pages /docs 배포용
├── web/index.html          ← 게임 원본(이것을 수정)
├── app/src/main/assets/index.html ← Android 앱 내부 포함
├── app/src/main/java/...   ← Android WebView Activity
├── .github/workflows/main.yml ← GitHub Actions 빌드 자동화
├── gradle/                 ← Gradle wrapper
├── gradlew / gradlew.bat
└── tools/sync_web.py       ← 원본 HTML을 3곳에 복사
```

웹게임을 고친 후에는 `python tools/sync_web.py`를 실행해 Android 앱·Pages 파일을 동기화하세요.

## 참고사항

- 웹앱 단일 HTML이어서 인터넷 없이도 실행됩니다. 외부 이미지·외부 API 없음.
- 게임 저장은 브라우저 `localStorage` 또는 Android WebView 내부에 보관됩니다. 브라우저 데이터 삭제·앱 삭제·기기 변경 시 이동되지 않을 수 있습니다.
- Android `applicationId`: `com.flowervillage.twins.pixel`. 이전 꽃잎마을 앱과 따로 설치할 수 있습니다.
- APK는 GitHub Actions에서 실제로 성공해야 다운로드할 수 있습니다. `main.yml`은 오래된 `android-actions/setup-android@v3`에서 삭제된 `tools` 패키지를 요청하는 오류를 피하도록 구성했습니다.
- 저작권 있는 포켓몬 이름/캐릭터/그래픽 등은 게임에 사용하지 않았습니다.
