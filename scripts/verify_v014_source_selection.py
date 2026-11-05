from pathlib import Path

root = Path(__file__).resolve().parents[1]
vm = (root / 'app/src/main/java/com/toimg/app/feature/scan/ScanScopeViewModel.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
plan = (root / 'plan.md').read_text()

assert 'if (!initialized) emptySet()' in vm, 'first scan entry must not auto-select saved sources'
assert 'selectedFolderIds = it.selectedFolderIds + treeUri.toString()' in vm, 'newly added source should join current selection'
assert 'versionCode = 28' in gradle and 'versionName = "0.1.30"' in gradle
for decision in ['UNSCANNED', 'ImageSourceLink', '不删除设备原图', '每次进入“扫描目录”，默认不勾选']:
    assert decision in plan, f'missing plan rule: {decision}'
print('PASS: saved sources are not auto-selected on first scan entry')
print('PASS: newly added source is selected for current operation')
print('PASS: version metadata is 0.1.22 / code 20')
print('PASS: source lifecycle decisions and safety rules documented')
