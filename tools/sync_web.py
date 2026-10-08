"""web/index.html 변경 후 docs와 Android assets에 동일한 게임을 복사합니다."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = (root / "web" / "index.html").read_bytes()
for target in [root / "docs" / "index.html", root / "app" / "src" / "main" / "assets" / "index.html"]:
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(source)
    print("updated:", target.relative_to(root))
